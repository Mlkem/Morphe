"""Fixture-based input preflight regression tests; no vendor APKs bundled."""
import importlib.util
import hashlib
import io
import json
import tempfile
import unittest
import zipfile
from pathlib import Path

SCRIPT=Path(__file__).resolve().parents[2]/'scripts'/'updates'/'inspect_apk_input.py'
spec=importlib.util.spec_from_file_location('input_checker',SCRIPT)
subject=importlib.util.module_from_spec(spec)
spec.loader.exec_module(subject)

def fake(path,kind='xapk',package='org.example',version='1.2.3',basename=None):
    buf=io.BytesIO()
    with zipfile.ZipFile(buf,'w') as z:
        z.writestr('AndroidManifest.xml',b'manifest')
        z.writestr('classes.dex',b'dex\n035\0')
    with zipfile.ZipFile(path,'w') as z:
        if kind=='xapk':
            z.writestr('manifest.json',json.dumps({'package_name':package,'version_name':version,'version_code':'42'}))
        if kind=='apkm':
            z.writestr('info.json',json.dumps({'pname':package,'release_version':version,'versioncode':'42'}))
        z.writestr(basename or ('base.apk' if kind=='apkm' else package+'.apk'),buf.getvalue())
        z.writestr('config.arm64_v8a.apk',b'example')

class InputCheck(unittest.TestCase):
    def setUp(self):
        self.dir=tempfile.TemporaryDirectory()
        self.path=Path(self.dir.name)/'example.zip'
    def tearDown(self):
        self.dir.cleanup()
    def inspect(self,sha=None):
        return subject.inspect(self.path,'org.example','1.2.3',sha)
    def test_xapk(self):
        fake(self.path)
        report=self.inspect()
        self.assertEqual(report['split_apk_count'],2)
        self.assertFalse(report['patch_compatibility_verified'])
    def test_apkm(self):
        fake(self.path,'apkm')
        self.assertEqual(self.inspect()['metadata'],'apkm_info')
    def test_verified_hash(self):
        fake(self.path)
        expected=hashlib.sha256(self.path.read_bytes()).hexdigest()
        self.assertTrue(self.inspect(expected)['published_checksum_verified'])
    def test_bad_hash(self):
        fake(self.path)
        with self.assertRaisesRegex(ValueError,'SHA-256'):
            self.inspect('0'*64)
    def test_wrong_version(self):
        fake(self.path,version='1.2.4')
        with self.assertRaisesRegex(ValueError,'package/version'):
            self.inspect()
    def test_missing_metadata(self):
        fake(self.path,'none')
        with self.assertRaisesRegex(ValueError,'metadata'):
            self.inspect()
    def test_wrong_base(self):
        fake(self.path,basename='unrelated.apk')
        with self.assertRaisesRegex(ValueError,'base APK'):
            self.inspect()
    def test_unsafe_archive(self):
        with zipfile.ZipFile(self.path,'w') as z:
            z.writestr('../attack',b'bad')
        with self.assertRaisesRegex(ValueError,'unsafe archive'):
            self.inspect()

if __name__=='__main__':
    unittest.main()
