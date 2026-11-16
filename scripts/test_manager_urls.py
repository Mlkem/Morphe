"""Exercise the Manager URL helper on the JVM, without APKs or network access."""
from pathlib import Path
import json, os, subprocess

root = Path(__file__).resolve().parents[1]
out = root / 'build/manager-url-tests'
out.mkdir(parents=True, exist_ok=True)
jdk = Path(os.environ['JAVA_HOME']) / 'bin' if os.environ.get('JAVA_HOME') else None
java = str(jdk / 'java') if jdk else 'java'
javac = str(jdk / 'javac') if jdk else 'javac'
sources = [*root.glob('extensions/manager/src/main/java/**/*.java'), root / 'tests/manager/ApkMirrorUrlsTest.java']
subprocess.run([javac, '--release', '8', '-encoding', 'UTF-8', '-d', str(out), *map(str, sources)], check=True)
metadata = json.loads((root / 'patches-list.json').read_text())
targets = sorted({(c['packageName'], t.get('version') or '')
                  for patch in metadata['patches']
                  for c in patch.get('compatiblePackages') or []
                  for t in c['targets']})
packages = {package for package, _ in targets}
# Missing/unknown versions must still search, never select an old pinned APK.
targets += [(package, '') for package in sorted(packages)]
targets += [(package, '999.123.456') for package in sorted(packages)]
subprocess.run([java, '-cp', str(out), 'ApkMirrorUrlsTest',
                *[value for pair in targets for value in pair]], check=True)
print(f'Covered all {len(packages)} catalogue apps, including every declared target')
