"""Verify a merged patched Eufy APK against the original APKMirror APKM.

Usage: python verify_apk.py original.apkm patched.apk
Requires androguard 4.x. Does not launch Android or validate signed-in cameras.
"""
from pathlib import Path
import sys, zipfile, io, hashlib
from loguru import logger
logger.remove()
from androguard.core.dex import DEX

assert len(sys.argv) == 4 and sys.argv[3] in ('31181', '32823'), 'Usage: verify_apk.py original.apkm patched.apk 31181|32823'
expected_hooks = ({'enableNavigation', 'enablePromotions', 'enableMobileStreaming'} if sys.argv[3] == '31181'
                  else {'enableNavigation32823', 'enablePromotions32823', 'enableMobileStreaming32823'})
bundle = zipfile.ZipFile(sys.argv[1])
base = zipfile.ZipFile(io.BytesIO(bundle.read('base.apk')))
out = zipfile.ZipFile(sys.argv[2])
assert len(out.namelist()) == len(set(out.namelist())), 'duplicate ZIP entries'

# The protected code payload and both untouched secondary DEX files are exact.
for name in ('assets/ijiami.dat', 'assets/IJMDal.Data', 'classes2.dex', 'classes3.dex'):
    assert base.read(name) == out.read(name), f'changed protected/original payload: {name}'

# Verify every original asset and every retained native library, across splits.
# The test command keeps arm64-v8a; other architectures may be stripped by Morphe.
assets = set()
unmatched = []
output_names = set(out.namelist())
for split in bundle.namelist():
    if not split.endswith('.apk'): continue
    with zipfile.ZipFile(io.BytesIO(bundle.read(split))) as apk:
        for name in apk.namelist():
            if name.endswith('/'): continue
            if name.startswith('assets/') or name.startswith('lib/arm64-v8a/'):
                content = apk.read(name)
                if name in output_names:
                    assert content == out.read(name), f'changed asset or arm64 library: {name}'
                else:
                    # APKEditor may rewrite the path encoding for non-ASCII ZIP
                    # filenames while retaining their exact content. Never
                    # permit absent bytes: verify every such entry by digest.
                    unmatched.append((name, hashlib.sha256(content).digest()))
                assets.add(name)

if unmatched:
    output_digests = {}
    for name in out.namelist():
        if not (name.startswith('assets/') or name.startswith('lib/arm64-v8a/')):
            continue
        digest = hashlib.sha256(out.read(name)).digest()
        output_digests.setdefault(digest, []).append(name)
    for name, digest in unmatched:
        matches = output_digests.get(digest, [])
        assert matches, f'missing asset or arm64 library bytes after merge: {name}'
        print('Preserved bytes under normalized ZIP filename:', repr(name),
              '->', repr(matches[0]))

original = DEX(base.read('classes.dex'))
classes = {}
for name in out.namelist():
    if not name.endswith('.dex'): continue
    for cls in DEX(out.read(name)).get_classes():
        assert cls.get_name() not in classes, f'duplicate class: {cls.get_name()}'
        classes[cls.get_name()] = cls

def key(method):
    return method.get_name(), method.get_descriptor()

def code(method):
    return [(ins.get_name(), ins.get_output()) for ins in method.get_instructions()]

for cls in original.get_classes():
    actual = classes[cls.get_name()]
    expected_methods = {key(m): m for m in cls.get_methods()}
    actual_methods = {key(m): m for m in actual.get_methods()}
    assert expected_methods.keys() == actual_methods.keys()
    for k, method in expected_methods.items():
        patched = actual_methods[k]
        assert method.get_access_flags() == patched.get_access_flags()
        if cls.get_name() == 'Ls/h/e/l/l/A;' and method.get_name() == 'instantiateActivity':
            ins = code(patched)
            hooks = [(i, text) for i, (op, text) in enumerate(ins) if 'Lca/mlkem/eufy/CameraOnly;' in text]
            assert len(hooks) == 6, 'all three options must cover BOTH factory paths'
            calls = [i for i,(op,s) in enumerate(ins) if op in ('invoke-virtual', 'invoke-super')
                     and 'Landroid/app/AppComponentFactory;->instantiateActivity' in s]
            assert len(calls) == 2
            for call in calls:
                assert ins[call+1] == ('move-result-object', 'v0')
                entries = ins[call+2:call+5]
                assert {text.split('->')[1].split('(')[0] for _,text in entries} == expected_hooks, 'wrong version-specific hook'
                assert all(op == 'invoke-static' and text.startswith('v0, ') and
                           '(Landroid/app/Activity;)V' in text for op,text in entries)
            assert [i for i,_ in hooks] == [c+j for c in calls for j in (2,3,4)]
            assert patched.get_code().get_registers_size() == method.get_code().get_registers_size() == 5
            # Match all original instructions, including exact control-flow
            # destinations, after excluding hooks and resolving byte offsets.
            def control_flow(m):
                instructions = list(m.get_instructions())
                offsets, offset = {}, 0
                retained = [i for i,x in enumerate(instructions) if 'Lca/mlkem/eufy/CameraOnly;' not in x.get_output()]
                for i,x in enumerate(instructions):
                    offsets[offset] = i
                    offset += x.get_length()
                position = {i:n for n,i in enumerate(retained)}
                result, offset = [], 0
                for i,x in enumerate(instructions):
                    if i in position:
                        op, text = x.get_name(), x.get_output()
                        if op.startswith(('if-', 'goto')):
                            target = offsets[offset + x.get_ref_off()*2]
                            assert target in position, 'original branch must retain its original destination'
                            text = (text.split(',')[0] if op.startswith('if-') else '', position[target])
                        result.append((op, text))
                    offset += x.get_length()
                return result
            assert control_flow(patched) == control_flow(method), 'original factory flow changed'
        else:
            assert code(method) == code(patched), f'unrelated loader method changed: {k}'

assert 'Lca/mlkem/eufy/CameraOnly;' in classes
print(f'PASS: Eufy {sys.argv[3]} uses {sorted(expected_hooks)}; both Activity factory paths hooked with all three options; original branch targets preserved; other loader methods unchanged; '
      f'{len(assets)} assets/arm64 libraries preserved; no duplicate classes; '
      'encrypted payload and secondary DEX files byte-identical.')
print('APK SHA-256:', hashlib.file_digest(Path(sys.argv[2]).open('rb'), 'sha256').hexdigest())
