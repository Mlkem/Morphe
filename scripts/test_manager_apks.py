"""Apply the built collection to every declared official Manager APK.

Only official GitHub release assets are downloaded. Check their published
SHA-256 digests; keep APK inputs/outputs in ignored build storage, never releases.
"""
from pathlib import Path
import hashlib
import json
import os
import subprocess
import urllib.request

ROOT = Path(__file__).resolve().parents[1]
CACHE = ROOT / 'build/manager-apk-regressions'
CACHE.mkdir(parents=True, exist_ok=True)


def asset(repo, version, name):
    url = f'https://api.github.com/repos/MorpheApp/{repo}/releases/tags/v{version}'
    headers = {'User-Agent': 'mlkemac-manager-regressions'}
    token = os.environ.get('GITHUB_TOKEN')
    if token:
        headers['Authorization'] = 'Bearer ' + token
    with urllib.request.urlopen(urllib.request.Request(url, headers=headers), timeout=30) as response:
        release = json.load(response)
    entry = next(a for a in release['assets'] if a['name'] == name)
    digest = entry.get('digest', '')
    if not digest.startswith('sha256:'):
        raise RuntimeError('Official asset has no SHA-256 digest: ' + name)
    expected = digest.removeprefix('sha256:')
    path = CACHE / name
    if not path.exists() or hashlib.sha256(path.read_bytes()).hexdigest() != expected:
        # Do not forward the GitHub API token to the release CDN.
        with urllib.request.urlopen(entry['browser_download_url'], timeout=120) as response, path.open('wb') as target:
            while chunk := response.read(1024 * 1024):
                target.write(chunk)
    if hashlib.sha256(path.read_bytes()).hexdigest() != expected:
        raise RuntimeError('Official asset digest mismatch: ' + name)
    return path


def main():
    metadata = json.loads((ROOT / 'patches-list.json').read_text())
    patch = next(p for p in metadata['patches'] if p['name'] == 'APKMirror search for collection apps')
    versions = [t['version'] for t in patch['compatiblePackages'][0]['targets']]
    mpp = ROOT / f'patches/build/libs/patches-{metadata["version"]}.mpp'
    if not mpp.is_file():
        raise RuntimeError('Build the collection first: ' + str(mpp))
    desktop = asset('morphe-desktop', '1.20.0', 'morphe-desktop-1.20.0-all.jar')
    classes = CACHE / 'harness'
    classes.mkdir(exist_ok=True)
    subprocess.run(['javac', '-cp', str(desktop), '-d', str(classes),
                    str(ROOT / 'tests/manager/VerifyManagerMpp.java')], check=True)
    for version in versions:
        apk = asset('morphe-manager', version, f'morphe-manager-{version}.apk')
        output = CACHE / version
        subprocess.run(['java', '-Xmx3g', '-cp', os.pathsep.join(map(str, [classes, desktop])),
                        'VerifyManagerMpp', str(apk), str(mpp), str(output)], check=True)
    print(f'PASS: three resolver hooks, preserved method bodies and structural DEX checks on all {len(versions)} official Manager APKs')


if __name__ == '__main__':
    main()
