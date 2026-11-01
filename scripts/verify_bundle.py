"""Check generated metadata and bundle contents before releasing."""
from pathlib import Path
import json, zipfile
root=Path(__file__).resolve().parents[1]
patches=json.loads((root/'patches-list.json').read_text())['patches']
names=[p['name'] for p in patches]
assert len(patches)==27, f'Expected 27 patches, found {len(patches)}'
assert len(names)==len(set(names)), 'Duplicate patch names'
packages={c['packageName'] for p in patches for c in (p.get('compatiblePackages') or [])}
expected={'com.musicleague.app','ca.walmart.ecommerceapp','com.wongnai.android','com.neave.zoomearth','com.pushpress.memberportal','com.openwith.app','app.morphe.manager'}
assert packages==expected, f'Unexpected app set: {packages}'
openwith=next(p for p in patches if p['name']=='Enable Pro file-type handling')
assert openwith['default'] is False, 'Open with must remain opt-in'
assert len(openwith['compatiblePackages']) == 1
openwith_target=openwith['compatiblePackages'][0]
assert openwith_target['packageName'] == 'com.openwith.app'
assert [t['version'] for t in openwith_target['targets']] == ['1.2.1']
assert all(t['minSdk'] == 32 for t in openwith_target['targets'])
manager=next(p for p in patches if p['name']=='APKMirror search for collection apps')
assert manager['default'] is False, 'Manager search must remain opt-in'
assert len(manager['compatiblePackages']) == 1
manager_target=manager['compatiblePackages'][0]
assert manager_target['packageName'] == 'app.morphe.manager'
assert [t['version'] for t in manager_target['targets']] == ['2.4.0-dev.1','2.4.0-dev.2','2.4.0-dev.3']
assert all(t['minSdk'] == 26 for t in manager_target['targets'])
merge=next(p for p in patches if p['name']=='Merge XAPK only')
assert merge['default'] is False and merge['compatiblePackages'] is None
for p in patches:
    if 'Hermes hide' in p['name']: assert p['default'] is False
    for c in p.get('compatiblePackages') or []:
        if c['packageName'] in {'ca.walmart.ecommerceapp','com.pushpress.memberportal','com.openwith.app','app.morphe.manager'}:
            assert all(t['isExperimental'] for t in c['targets']), 'Unverified target must stay experimental'
bundles=[p for p in (root/'patches/build/libs').glob('*.mpp')
         if not any(kind in p.name for kind in ('-sources', '-javadoc'))]
assert bundles, 'No MPP generated'
for p in bundles:
    with zipfile.ZipFile(p) as z:
        files=z.namelist()
        assert len(files)==len(set(files))
        assert z.read('classes.dex').startswith(b'dex\n')
        assert any(f.endswith('.class') for f in files)
        assert 'ca/mlkem/openwith/OpenWithProPatch.class' in files
        assert b'Lca/mlkem/openwith/OpenWithProPatch;' in z.read('classes.dex')
        assert 'ca/mlkem/manager/ApkMirrorSearchPatchKt.class' in files
        assert b'Lca/mlkem/manager/ApkMirrorSearchPatchKt;' in z.read('classes.dex')
        for name in ['extensions/canada.mpe','extensions/pushpress-crossfit-only.mpe','extensions/manager-apkmirror.mpe']:
            assert z.read(name).startswith(b'dex\n'), name
            assert b'Lkotlin/' not in z.read(name), f'Unexpected Kotlin runtime in Java extension: {name}'
        assert b'Lca/mlkem/manager/ApkMirrorUrls;' in z.read('extensions/manager-apkmirror.mpe')
        assert not any(f.startswith(('kotlin/','app/morphe/patcher/')) for f in files)
        assert not any(f.endswith(('.key','.keystore','.jks','.apk','.xapk')) for f in files)
        manifest=z.read('META-INF/MANIFEST.MF').decode()
        metadata=json.loads((root/'patches-list.json').read_text())
        assert f"Version: {metadata['version']}" in manifest, 'Bundle/catalog version mismatch'
print(f'Verified {len(patches)} patches, {len(packages)} apps, plus universal merge-only.')
