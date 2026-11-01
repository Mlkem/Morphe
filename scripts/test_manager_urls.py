"""Exercise the Manager URL helper on the JVM, without APKs or network access."""
from pathlib import Path
import os, subprocess

root = Path(__file__).resolve().parents[1]
out = root / 'build/manager-url-tests'
out.mkdir(parents=True, exist_ok=True)
jdk = Path(os.environ['JAVA_HOME']) / 'bin' if os.environ.get('JAVA_HOME') else None
java = str(jdk / 'java') if jdk else 'java'
javac = str(jdk / 'javac') if jdk else 'javac'
sources = [*root.glob('extensions/manager/src/main/java/**/*.java'), root / 'tests/manager/ApkMirrorUrlsTest.java']
subprocess.run([javac, '--release', '8', '-encoding', 'UTF-8', '-d', str(out), *map(str, sources)], check=True)
subprocess.run([java, '-cp', str(out), 'ApkMirrorUrlsTest'], check=True)
