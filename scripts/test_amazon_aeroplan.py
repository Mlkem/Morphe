"""Offline account validation and official-form autofill tests; no real account data."""
from pathlib import Path
import os
import subprocess

root = Path(__file__).resolve().parents[1]
out = root / 'build/amazon-aeroplan-tests'
out.mkdir(parents=True, exist_ok=True)
jdk = Path(os.environ['JAVA_HOME']) / 'bin' if os.environ.get('JAVA_HOME') else None
java = str(jdk / 'java') if jdk else 'java'
javac = str(jdk / 'javac') if jdk else 'javac'
source = root / 'extensions/amazon/src/main/java/ca/mlkem/amazon'
subprocess.run([javac, '--release', '8', '-encoding', 'UTF-8', '-d', str(out),
                str(source / 'SessionRules.java'), str(source / 'AutofillScript.java'),
                str(root / 'tests/amazon/RulesTest.java')], check=True)
subprocess.run([java, '-cp', str(out), 'ca.mlkem.amazon.RulesTest', str(out)], check=True)
subprocess.run(['node', str(root / 'tests/amazon/autofill.test.cjs'), str(out)], check=True)
