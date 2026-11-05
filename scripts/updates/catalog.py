"""Deterministic per-app revisions and release feed. No private/device data."""
from pathlib import Path
import hashlib,json,sys,time
ROOT=Path(__file__).resolve().parents[2]
PATHS={
 'app.morphe.manager':['patches/src/main/kotlin/ca/mlkem/manager','extensions/manager'],
 'ca.walmart.ecommerceapp':['patches/src/main/kotlin/app/canada','extensions/canada'],
 'com.google.android.gm':['patches/src/main/kotlin/ca/mlkem/gmail','extensions/gmail'],
 'com.google.android.inputmethod.latin':['patches/src/main/kotlin/ca/mlkem/gboard'],
 'com.musicleague.app':['patches/src/main/kotlin/me/mikem/musicleague'],
 'com.neave.zoomearth':['patches/src/main/kotlin/me/mikem/zoomearth'],
 'com.oceanwing.battery.cam':['patches/src/main/kotlin/ca/mlkem/eufy','extensions/eufy'],
 'com.openwith.app':['patches/src/main/java/ca/mlkem/openwith'],
 'com.pushpress.memberportal':['patches/src/main/kotlin/ca/mikem/pushpress','extensions/pushpress'],
 'com.rosteam.gpsemulator':['patches/src/main/java/ca/mlkem/gps'],
 'com.songsterr':['patches/src/main/kotlin/me/mikem/songsterr'],
 'com.thaidigitalplatform.tagthai':['patches/src/main/kotlin/ca/mlkem/tagthai','extensions/tagthai'],
 'com.wongnai.android':['patches/src/main/java/local/wongnai'],
}
SHARED=['patches/src/main/kotlin/ca/mlkem/updates','extensions/updates','scripts/updates/catalog.py']
def revision(paths):
 h=hashlib.sha256()
 for entry in sorted(set(paths+SHARED)):
  path=ROOT/entry
  files=[path] if path.is_file() else sorted(p for p in path.rglob('*') if p.is_file() and 'build' not in p.relative_to(path).parts)
  for p in files:
   h.update(str(p.relative_to(ROOT)).encode());h.update(b'\0');h.update(p.read_bytes());h.update(b'\0')
 return h.hexdigest()
def revisions():return {package:revision(paths) for package,paths in sorted(PATHS.items())}
def write_revisions():
 p=ROOT/'patches/src/main/resources/updates/revisions.properties';p.parent.mkdir(parents=True,exist_ok=True)
 p.write_text('builtAt='+str(int(time.time()*1000))+'\n'+''.join(package+'='+value+'\n' for package,value in revisions().items()))

def release():
 from datetime import datetime,timezone
 metadata=json.loads((ROOT/'patches-list.json').read_text());version=metadata['version'];apps={};rev=revisions()
 for patch in metadata['patches']:
  for compat in patch.get('compatiblePackages') or []:
   package=compat['packageName'];app=apps.setdefault(package,{'name':compat.get('name') or package,'revision':rev[package],'supportedVersions':[]})
   for target in compat['targets']:
    v=target.get('version')
    if v and v not in app['supportedVersions']:app['supportedVersions'].append(v)
 assert set(apps)==set(PATHS)
 p=ROOT/'updates/app-feed.json';old=json.loads(p.read_text()) if p.exists() else {}
 for package,app in apps.items():app['upstream']=old.get('apps',{}).get(package,{}).get('upstream',{'status':'not_checked'})
 feed={'schema':1,'releaseVersion':version,'releaseUrl':f'https://github.com/Mlkem/Morphe/releases/tag/v{version}','generatedAt':datetime.now(timezone.utc).isoformat(),'apps':apps}
 p.parent.mkdir(exist_ok=True);p.write_text(json.dumps(feed,sort_keys=True,indent=2)+'\n')
if __name__=='__main__':
 if '--release' in sys.argv:release()
 else:write_revisions()
