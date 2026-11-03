"""Verify a merged patched Eufy APK against the original APKMirror APKM.

Usage: python verify_apk.py original.apkm patched.apk
Requires androguard 4.x. Does not launch Android or validate signed-in cameras.
"""
from pathlib import Path
import sys, zipfile, io, hashlib
from loguru import logger
logger.remove()
from androguard.core.dex import DEX

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
for split in bundle.namelist():
    if not split.endswith('.apk'): continue
    with zipfile.ZipFile(io.BytesIO(bundle.read(split))) as apk:
        for name in apk.namelist():
            if name.endswith('/'): continue
            if name.startswith('assets/') or name.startswith('lib/arm64-v8a/'):
                assert apk.read(name) == out.read(name), f'missing/changed asset or arm64 library: {name}'
                assets.add(name)

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
        if cls.get_name() == 'Ls/h/e/l/l/S;' and k == ('onCreate', '()V'):
            ins = code(patched)
            hooks = [(i, text) for i, (op, text) in enumerate(ins) if 'Lca/mlkem/eufy/CameraOnly;' in text]
            assert len(hooks) == 2
            assert {('enableNavigation' if 'enableNavigation' in s else 'enablePromotions' if 'enablePromotions' in s else '?') for _, s in hooks} == {'enableNavigation', 'enablePromotions'}
            assert all('v0,' in s for _, s in hooks)
            call = next(i for i,(op,s) in enumerate(ins) if op == 'invoke-virtual' and 'Landroid/app/Application;->onCreate()V' in s)
            assert [i for i,_ in hooks] == [call+1, call+2]
            assert ins[call-1][0] == 'sget-object' and 'Ls/h/e/l/l/S;->n' in ins[call-1][1]
            assert ins[call+3][0] == 'invoke-super'
            assert patched.get_code().get_registers_size() == method.get_code().get_registers_size() == 6
            # Branch offsets change around insertions; every other non-branch
            # operation remains in order, including the original loader calls.
            def semantic(instructions):
                return [(op,s) for op,s in instructions if not op.startswith(('if-', 'goto')) and 'Lca/mlkem/eufy/CameraOnly;' not in s]
            assert semantic(ins) == semantic(code(method))
        else:
            assert code(method) == code(patched), f'unrelated loader method changed: {k}'

assert 'Lca/mlkem/eufy/CameraOnly;' in classes
print(f'PASS: two correctly typed startup hooks; other loader methods unchanged; '
      f'{len(assets)} assets/arm64 libraries preserved; no duplicate classes; '
      'encrypted payload and secondary DEX files byte-identical.')
print('APK SHA-256:', hashlib.file_digest(Path(sys.argv[2]).open('rb'), 'sha256').hexdigest())
