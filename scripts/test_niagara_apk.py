"""Apply the actual MPP to the pinned official APK and inspect every DEX method."""
from pathlib import Path
import hashlib, json, os, subprocess, urllib.request
root=Path(__file__).resolve().parents[1]
out=root/'build/niagara-apk-validation';out.mkdir(parents=True,exist_ok=True)
from official_morphe_asset import asset
desktop=asset('morphe-desktop','1.20.0','morphe-desktop-1.20.0-all.jar')
version=json.loads((root/'patches-list.json').read_text())['version']
mpp=root/f'patches/build/libs/patches-{version}.mpp'
classes=out/'harness';classes.mkdir(exist_ok=True)
subprocess.run(['javac','-cp',str(desktop),'-d',str(classes),str(root/'tests/niagara/VerifyNiagaraMpp.java')],check=True)
releases = {
    '1.16.37': ('974d3e82aaa82bc4de4ef76834aade5e2888464929b6b208730a5d88bd6a4b76', ('auto', 'pro', 'both')),
    '1.16.38': ('5418283952b13848eb1891b0f01e3acba98e29d017b933b2e034c99a5df5f0df', ('auto',)),
}
for release, (expected, modes) in releases.items():
    apk = out / f'Niagara_Launcher_v{release}-release.apk'
    if not apk.exists() or hashlib.sha256(apk.read_bytes()).hexdigest() != expected:
        url = f'https://github.com/NiagaraLauncher/Niagara-Issues/releases/download/v{release}/{apk.name}'
        with urllib.request.urlopen(url, timeout=120) as response:
            apk.write_bytes(response.read())
    assert hashlib.sha256(apk.read_bytes()).hexdigest() == expected, f'Official APK digest mismatch for {release}'
    for mode in modes:
        subprocess.run(['java', '-Xmx3g', '-cp', os.pathsep.join(map(str, [classes, desktop])),
                        'VerifyNiagaraMpp', str(apk), str(mpp), str(out/release/mode), mode], check=True)
