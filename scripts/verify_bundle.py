"""Check generated metadata and bundle contents before releasing."""
from pathlib import Path
import json, zipfile
root=Path(__file__).resolve().parents[1]
patches=json.loads((root/'patches-list.json').read_text())['patches']
names=[p['name'] for p in patches]
assert len(patches)==39, f'Expected 39 patches, found {len(patches)}'
assert len(names)==len(set(names)), 'Duplicate patch names'
packages={c['packageName'] for p in patches for c in (p.get('compatiblePackages') or [])}
expected={'com.google.android.inputmethod.latin','com.songsterr','com.musicleague.app','ca.walmart.ecommerceapp','com.wongnai.android','com.neave.zoomearth','com.pushpress.memberportal','com.openwith.app','com.rosteam.gpsemulator','com.thaidigitalplatform.tagthai','app.morphe.manager'}
assert packages==expected, f'Unexpected app set: {packages}'
zoom_names={'Enable Zoom Earth Pro features','Remove Zoom Earth ads','100 Zoom Earth favourites','Quiet Zoom Earth prompts','Allow Zoom Earth patched installation','Remove Zoom Earth LiteAPKs promotions'}
zoom=[p for p in patches if p['name'] in zoom_names]
assert len(zoom)==6
for p in zoom:
    assert not p['default'] and len(p['compatiblePackages'])==1
    target=p['compatiblePackages'][0]
    assert target['packageName']=='com.neave.zoomearth'
    assert [t['version'] for t in target['targets']]==['6.3.3']
    min_sdk=32 if p['name']=='Allow Zoom Earth patched installation' else 26
    assert all(t['minSdk']==min_sdk and t['isExperimental'] for t in target['targets'])
gboard=next(p for p in patches if p['name']=='Disable Gboard floating keyboard')
assert not gboard['default'] and len(gboard['compatiblePackages'])==1
assert gboard['description'].find('original Gboard APK') >= 0
gboard_target=gboard['compatiblePackages'][0]
assert gboard_target['packageName']=='com.google.android.inputmethod.latin'
assert [t['version'] for t in gboard_target['targets']]==['18.5.4.993608717-release-arm64-v8a']
assert all(t['isExperimental'] for t in gboard_target['targets'])
songsterr=next(p for p in patches if p['name']=='Enable Songsterr premium features')
assert not songsterr['default'] and len(songsterr['compatiblePackages'])==1
song_target=songsterr['compatiblePackages'][0]
assert song_target['packageName']=='com.songsterr'
assert [t['version'] for t in song_target['targets']]==['7.1.6']
assert all(t['minSdk']==32 and t['isExperimental'] for t in song_target['targets'])
tagthai=[p for p in patches if any(c['packageName']=='com.thaidigitalplatform.tagthai' for c in p.get('compatiblePackages') or [])]
assert len(tagthai)==3
for p in tagthai:
    assert not p['default']
    assert [t['version'] for t in p['compatiblePackages'][0]['targets']]==['4.25.1']
    assert all(t['minSdk']==28 and t['isExperimental'] for t in p['compatiblePackages'][0]['targets'])
gps=next(p for p in patches if p['name']=='Enable GPS Emulator Pro')
assert gps['default'] is False, 'GPS Emulator must remain opt-in'
assert len(gps['compatiblePackages']) == 1
gps_target=gps['compatiblePackages'][0]
assert gps_target['packageName'] == 'com.rosteam.gpsemulator'
assert [t['version'] for t in gps_target['targets']] == ['3.36']
assert all(t['minSdk'] == 29 for t in gps_target['targets'])
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
        if c['packageName'] in {'ca.walmart.ecommerceapp','com.pushpress.memberportal','com.openwith.app','com.rosteam.gpsemulator','com.thaidigitalplatform.tagthai','app.morphe.manager'}:
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
        assert 'ca/mlkem/gboard/DisableFloatingKeyboardPatchKt.class' in files
        assert b'Lca/mlkem/gboard/DisableFloatingKeyboardPatchKt;' in z.read('classes.dex')
        assert 'me/mikem/songsterr/SongsterrPremiumPatchKt.class' in files
        assert b'Lme/mikem/songsterr/SongsterrPremiumPatchKt;' in z.read('classes.dex')
        assert 'me/mikem/zoomearth/ZoomEarth633PatchesKt.class' in files
        assert b'Lme/mikem/zoomearth/ZoomEarth633PatchesKt;' in z.read('classes.dex')
        assert 'me/mikem/zoomearth/ZoomEarthStartupPatchKt.class' in files
        assert b'Lme/mikem/zoomearth/ZoomEarthStartupPatchKt;' in z.read('classes.dex')
        assert 'me/mikem/zoomearth/ZoomEarthLiteApksPatchKt.class' in files
        assert b'Lme/mikem/zoomearth/ZoomEarthLiteApksPatchKt;' in z.read('classes.dex')
        assert 'ca/mlkem/tagthai/TagthaiPatchesKt.class' in files
        assert b'Lca/mlkem/tagthai/TagthaiPatchesKt;' in z.read('classes.dex')
        assert b'Lca/mlkem/tagthai/TextSelection;' in z.read('extensions/tagthai-text.mpe')
        assert 'ca/mlkem/gps/GpsEmulatorProPatch.class' in files
        assert b'Lca/mlkem/gps/GpsEmulatorProPatch;' in z.read('classes.dex')
        assert 'ca/mlkem/openwith/OpenWithProPatch.class' in files
        assert b'Lca/mlkem/openwith/OpenWithProPatch;' in z.read('classes.dex')
        assert 'ca/mlkem/manager/ApkMirrorSearchPatchKt.class' in files
        assert b'Lca/mlkem/manager/ApkMirrorSearchPatchKt;' in z.read('classes.dex')
        for name in ['extensions/canada.mpe','extensions/pushpress-crossfit-only.mpe','extensions/manager-apkmirror.mpe','extensions/tagthai-text.mpe']:
            assert z.read(name).startswith(b'dex\n'), name
            assert b'Lkotlin/' not in z.read(name), f'Unexpected Kotlin runtime in Java extension: {name}'
        assert b'Lca/mlkem/manager/ApkMirrorUrls;' in z.read('extensions/manager-apkmirror.mpe')
        assert not any(f.startswith(('kotlin/','app/morphe/patcher/')) for f in files)
        assert not any(f.endswith(('.key','.keystore','.jks','.apk','.xapk')) for f in files)
        manifest=z.read('META-INF/MANIFEST.MF').decode()
        metadata=json.loads((root/'patches-list.json').read_text())
        assert f"Version: {metadata['version']}" in manifest, 'Bundle/catalog version mismatch'
print(f'Verified {len(patches)} patches, {len(packages)} apps, plus universal merge-only.')
