"""Apply the actual MPP to the pinned official APK and inspect every DEX method."""
from pathlib import Path
import hashlib, json, os, subprocess, urllib.request
root=Path(__file__).resolve().parents[1]
out=root/'build/niagara-apk-validation';out.mkdir(parents=True,exist_ok=True)
expected='974d3e82aaa82bc4de4ef76834aade5e2888464929b6b208730a5d88bd6a4b76'
apk=out/'Niagara_Launcher_v1.16.37-release.apk'
url='https://github.com/NiagaraLauncher/Niagara-Issues/releases/download/v1.16.37/Niagara_Launcher_v1.16.37-release.apk'
if not apk.exists() or hashlib.sha256(apk.read_bytes()).hexdigest()!=expected:
    with urllib.request.urlopen(url,timeout=120) as response: apk.write_bytes(response.read())
assert hashlib.sha256(apk.read_bytes()).hexdigest()==expected,'Official Niagara APK digest mismatch'
# Reuse the audited official Desktop download helper.
from test_manager_apks import asset
desktop=asset('morphe-desktop','1.20.0','morphe-desktop-1.20.0-all.jar')
version=json.loads((root/'patches-list.json').read_text())['version']
mpp=root/f'patches/build/libs/patches-{version}.mpp'
classes=out/'harness';classes.mkdir(exist_ok=True)
subprocess.run(['javac','-cp',str(desktop),'-d',str(classes),str(root/'tests/niagara/VerifyNiagaraMpp.java')],check=True)
subprocess.run(['java','-Xmx3g','-cp',os.pathsep.join(map(str,[classes,desktop])),'VerifyNiagaraMpp',str(apk),str(mpp),str(out)],check=True)
