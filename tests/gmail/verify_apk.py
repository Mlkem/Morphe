"""Inspect a real patched Gmail; this does not replace signed-in phone testing.

Usage: python3 tests/gmail/verify_apk.py original.apkm patched.apk
Requires androguard 4.x. Proprietary inputs never belong in the repository.
"""
import hashlib
import io
import sys
import zipfile
from collections import Counter
from loguru import logger
logger.remove()
from androguard.core.apk import APK
from androguard.core.axml import AXMLPrinter
from androguard.core.dex import DEX

ORIGINAL = 'com.google.android.gm'
CLONE = 'ca.mlkem.gmail'
ANDROID = '{http://schemas.android.com/apk/res/android}'
HOOK = 'Lca/mlkem/gmail/AccountVisibility;'
MAIN = 'Lcom/google/android/apps/gmail/libraries/mailactivity/MailActivityGmail;'
OLD = 'Lcom/google/android/gm/preference/GmailPreferenceActivity;'
NEW = 'Lcom/google/android/apps/gmail/featurelibraries/settingsv2/impl/activity/HubPreferenceActivity;'
AVATAR = 'Lcom/google/android/libraries/onegoogle/accountmenu/SelectedAccountDisc;'

archive, output = sys.argv[1:]
with zipfile.ZipFile(archive) as bundle:
    original_bytes = bundle.read('base.apk')
    original = APK(original_bytes, raw=True)
    expected_assets = {}
    for split in bundle.namelist():
        if not split.endswith('.apk'):
            continue
        with zipfile.ZipFile(io.BytesIO(bundle.read(split))) as apk:
            for name in apk.namelist():
                if name.startswith(('assets/', 'lib/arm64-v8a/')) and not name.endswith('/'):
                    expected_assets.setdefault(name, set()).add(hashlib.sha256(apk.read(name)).digest())
patched = APK(output)
assert original.get_package() == ORIGINAL
assert original.get_androidversion_name() == '2026.10.26.1012468012.Release'
assert original.get_androidversion_code() == '66141086'
assert patched.get_package() == CLONE
assert patched.get_androidversion_name() == original.get_androidversion_name()
assert patched.get_androidversion_code() == original.get_androidversion_code()

before = original.get_android_manifest_xml()
after = patched.get_android_manifest_xml()
def owned(root, tag, attribute):
    return {s for e in root.findall('application/' + tag)
            for s in e.get(ANDROID + attribute, '').split(';') if s}
old_authorities = owned(before, 'provider', 'authorities')
new_authorities = owned(after, 'provider', 'authorities')
assert len(old_authorities) == len(new_authorities)
assert old_authorities.isdisjoint(new_authorities), 'Provider conflict with installed stock Gmail'
assert all(CLONE in a for a in new_authorities)
for tag in ['activity', 'activity-alias', 'service', 'provider', 'receiver']:
    assert owned(before, tag, 'name') == owned(after, tag, 'name'), 'Host class identity changed: ' + tag
    assert owned(before, tag, 'targetActivity') == owned(after, tag, 'targetActivity')
old_permissions = {e.get(ANDROID + 'name') for e in before.findall('permission')}
new_permissions = {e.get(ANDROID + 'name') for e in after.findall('permission')}
assert old_permissions.isdisjoint(new_permissions)
assert len(new_permissions) == len(old_permissions)
metadata = {e.get(ANDROID + 'name'): e.get(ANDROID + 'value') for e in after.findall('application/meta-data')}
assert metadata['app.revanced.android.gms.SPOOFED_PACKAGE_NAME'] == ORIGINAL
assert metadata['app.revanced.android.gms.SPOOFED_PACKAGE_SIGNATURE'] == '38918a453d07199354f8b19af05ec6562ced5788'
assert metadata['app.revanced.MICROG_PACKAGE_NAME'] == 'app.revanced.android.gms'
assert after.find('application').get(ANDROID + 'allowBackup') == 'false'

def resource(apk, kind, name):
    table = apk.get_android_resources()
    package = table.get_packages_names()[0]
    rid = table.get_res_id_by_key(package, kind, name)
    assert rid, name
    return table.get_resolved_res_configs(rid)[0][1]

for name in ['account_manager_type_exchange', 'account_manager_type_pop3', 'account_manager_type_legacy_imap',
             'authority_email_provider', 'syncadapter_task_authority']:
    assert resource(patched, 'string', name) == resource(original, 'string', name).replace(ORIGINAL, CLONE), name
assert resource(patched, 'string', 'syncadapter_content_authority') == CLONE + '.mail'
# In particular the stock backspace shortcut must not become an empty string:
# Gmail's menu and keyboard code use charAt(0), including during startup.
for name in ['archive', 'change_label', 'compose', 'delete', 'help', 'print', 'refresh',
             'reply_all', 'reply', 'save', 'send', 'settings', 'toggle_drawer', 'toggle_read_unread']:
    key = 'trigger_' + name + '_char'
    assert resource(patched, 'string', key) == resource(original, 'string', key), key

def resolve(apk, value):
    if value.startswith('@'):
        return apk.get_android_resources().get_resolved_res_configs(int(value[1:], 16))[0][1]
    return value

for name in ['syncadapter', 'syncadapter_calendar', 'syncadapter_contacts', 'syncadapter_email',
             'syncadapter_legacy_imap', 'syncadapter_pop3', 'syncadapter_tasks']:
    path = resource(patched, 'xml', name)
    xml = AXMLPrinter(patched.get_file(path)).get_xml_obj()
    kind = resolve(patched, xml.get(ANDROID + 'accountType'))
    authority = resolve(patched, xml.get(ANDROID + 'contentAuthority'))
    assert kind == 'app.revanced' or kind.startswith(CLONE + '.'), (name, kind)
    assert authority in new_authorities or authority in {'com.android.calendar', 'com.android.contacts'}, (name, authority)

with zipfile.ZipFile(output) as zip_out:
    assert len(zip_out.namelist()) == len(set(zip_out.namelist()))
    for name, hashes in expected_assets.items():
        assert hashlib.sha256(zip_out.read(name)).digest() in hashes, 'Changed/lost asset or native library: ' + name

def code(method):
    return [(i.get_name(), i.get_output()) for i in method.get_instructions()]

targets = {'Lawxw;', HOOK, MAIN, OLD, NEW, AVATAR}
found = {}
types = Counter()
filter_calls = []
for dex_bytes in patched.get_all_dex():
    dex = DEX(dex_bytes)
    has_hook = HOOK.encode() in dex_bytes
    for cls in dex.get_classes():
        types[cls.get_name()] += 1
        for method in cls.get_methods():
            if cls.get_name() in targets:
                found[(cls.get_name(), method.get_name())] = code(method)
            if not has_hook:
                continue
            for instruction in method.get_instructions():
                text = instruction.get_output()
                if HOOK + '->filter(' in text:
                    filter_calls.append((cls.get_name(), method.get_name()))
assert all(n == 1 for n in types.values()), 'Duplicate DEX class definitions'
assert filter_calls == [('Lawxw;', 'g')], 'Account filter escaped its UI-only scope'
wrapper = found[('Lawxw;', 'g')]
assert [op for op, _ in wrapper] == ['invoke-virtual', 'move-result-object', 'invoke-virtual',
    'move-result-object', 'invoke-static', 'move-result-object', 'invoke-static', 'move-result-object', 'return-object']
assert 'mlkemAllAccounts()' in wrapper[0][1]
assert 'Lawxw;->a()' in wrapper[2][1]
assert 'Lcgzq;->i(Ljava/util/Collection;)' in wrapper[6][1]
for cls, name, hook in [(MAIN, 'onCreate', 'initialize'), (OLD, 'onBuildHeaders', 'addHeader'),
    (OLD, 'onHeaderClick', 'selectHeader'), (NEW, 'onPrepareOptionsMenu', 'addMenu'),
    (NEW, 'onOptionsItemSelected', 'selectMenu'), (AVATAR, 'c', 'attach')]:
    assert sum(HOOK + '->' + hook + '(' in text for _, text in found[(cls, name)]) == 1, (name, hook)
assert any('GmailAndroidAccount;->a Landroid/accounts/Account;' in text for _, text in found[(HOOK, 'account')])
original_model = None
for dex_bytes in original.get_all_dex():
    dex = DEX(dex_bytes)
    cls = dex.get_class('Lawxw;')
    if cls:
        original_model = next(code(m) for m in cls.get_methods() if m.get_name() == 'g')
        break
assert original_model == found[('Lawxw;', 'mlkemAllAccounts')], 'Complete original menu model was changed'
print(f'PASS: Gmail clone identity, sync resources, shortcuts, UI hooks, {len(types)} unique classes, '
      f'{len(expected_assets)} original assets/native libraries. Phone sign-in/sync remain unverified.')
