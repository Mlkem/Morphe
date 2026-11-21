"""Fetch a digest-verified release asset for APK regression tests."""
from pathlib import Path
import hashlib
import json
import os
import urllib.request

ROOT = Path(__file__).resolve().parents[1]
CACHE = ROOT / 'build/official-morphe-assets'



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
    CACHE.mkdir(parents=True, exist_ok=True)
    path = CACHE / name
    if not path.exists() or hashlib.sha256(path.read_bytes()).hexdigest() != expected:
        # Do not forward the GitHub API token to the release CDN.
        with urllib.request.urlopen(entry['browser_download_url'], timeout=120) as response, path.open('wb') as target:
            while chunk := response.read(1024 * 1024):
                target.write(chunk)
    if hashlib.sha256(path.read_bytes()).hexdigest() != expected:
        raise RuntimeError('Official asset digest mismatch: ' + name)
    return path
