"""Local category and install-policy checks; no phone data or network."""
from pathlib import Path
import os, subprocess
root=Path(__file__).resolve().parents[1]
out=root/'build/niagara-logic';out.mkdir(parents=True,exist_ok=True)
bin=Path(os.environ['JAVA_HOME'])/'bin' if os.environ.get('JAVA_HOME') else Path('/usr/bin')
subprocess.run([str(bin/'javac'),'--release','8','-d',str(out),str(root/'extensions/niagara/src/main/java/ca/mlkem/niagara/Categories.java'),str(root/'tests/niagara/CategoriesTest.java')],check=True)
subprocess.run([str(bin/'java'),'-cp',str(out),'CategoriesTest'],check=True)
