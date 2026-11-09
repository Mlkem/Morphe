#!/usr/bin/env python3
"""Fail-closed original-APK archive preflight for Morphe upgrades.

This verifies original package/version metadata and APK structure only.
It does NOT validate patch bytecode, installation, billing, or phone behavior.
"""
import argparse
import hashlib
import io
import json
import re
import sys
import zipfile
from pathlib import Path

MAX_BASE_BYTES = 600 * 1024 * 1024

def names_safely(z):
    seen=set()
    for e in z.infolist():
        name=e.filename
        if '\x00' in name or '\\' in name or name.startswith('/') or '..' in name.split('/'):
            raise ValueError('unsafe archive path: '+name)
        if name in seen:
            raise ValueError('duplicate archive member: '+name)
        seen.add(name)
    return seen

def inspect(path, package, version, original_sha256=None):
    digest=hashlib.sha256()
    with Path(path).open('rb') as f:
        for chunk in iter(lambda: f.read(1048576), b''):
            digest.update(chunk)
    actual=digest.hexdigest()
    if original_sha256 and actual.lower()!=original_sha256.lower():
        raise ValueError('original archive SHA-256 does not match')
    with zipfile.ZipFile(path) as archive:
        names=names_safely(archive)
        if 'manifest.json' in names:
            m=json.loads(archive.read('manifest.json'))
            declared=(m.get('package_name'),m.get('version_name'),str(m.get('version_code','')))
            metadata='xapk_manifest'
        elif 'info.json' in names:
            m=json.loads(archive.read('info.json'))
            declared=(m.get('pname'),m.get('release_version'),str(m.get('versioncode','')))
            metadata='apkm_info'
        else:
            raise ValueError('missing XAPK/APKM metadata; ZIP or raw APK needs manifest inspection')
        if not all(declared) or declared[:2] != (package,version):
            raise ValueError('package/version does not match requested original')
        apks=sorted(n for n in names if n.endswith('.apk'))
        bases=[n for n in apks if n in ('base.apk', package+'.apk')]
        if len(bases)!=1:
            raise ValueError('expected exactly one named base APK')
        if archive.getinfo(bases[0]).file_size > MAX_BASE_BYTES:
            raise ValueError('base APK too large')
        with zipfile.ZipFile(io.BytesIO(archive.read(bases[0]))) as inner:
            entries=names_safely(inner)
            dex=[n for n in entries if re.fullmatch(r'classes(?:\d+)?\.dex',n)]
            if not dex or 'AndroidManifest.xml' not in entries:
                raise ValueError('base APK is missing AndroidManifest.xml or DEX')
    return {
        'package':package,'version':version,'version_code':declared[2],
        'sha256':actual,'metadata':metadata,'split_apk_count':len(apks),
        'base_dex_count':len(dex),'published_checksum_verified':bool(original_sha256),
        'patch_compatibility_verified':False,'phone_tested':False,
        'status':'original_archive_structure_only'
    }

def main():
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('archive',type=Path)
    p.add_argument('--package',required=True)
    p.add_argument('--version',required=True)
    p.add_argument('--sha256',help='independently verified original archive digest')
    a=p.parse_args()
    try:
        value=inspect(a.archive,a.package,a.version,a.sha256)
    except (OSError, ValueError, KeyError, zipfile.BadZipFile) as e:
        print(json.dumps({'status':'fail_closed','error':str(e)}),file=sys.stderr)
        return 2
    print(json.dumps(value,sort_keys=True,indent=2))
    return 0

if __name__ == '__main__':
    sys.exit(main())
