"""Read public upstream metadata; never download APKs or infer from reviews.

Google Play's private page layout can change. Verify package identity at [77][0]
AND a version at [140][0][0][0]; fail closed when either moves/is withheld.
Walmart's actual public response was checked against this path on 2026-11-05.
"""
from pathlib import Path
from datetime import datetime,timezone
import argparse,json,os,re,time,urllib.request,urllib.error
ROOT=Path(__file__).resolve().parents[2]
MAX_BYTES=4_000_000
VERSION=re.compile(r'^[vV]?\d+(?:[._]\d+)*(?:[-._][A-Za-z0-9][A-Za-z0-9._+\-]*)?$')
def valid_version(value):return isinstance(value,str) and len(value)<=100 and bool(VERSION.fullmatch(value))
def play_version(page,package):
 for match in re.finditer(r'AF_initDataCallback\(\{key:.*?data:',page):
  try:
   data,_=json.JSONDecoder().raw_decode(page[match.end():].lstrip());app=data[1][2]
   if app[77][0]!=package:continue
   version=app[140][0][0][0]
   return version if valid_version(version) else None
  except (ValueError,TypeError,IndexError,KeyError):continue
 return None
class NoRedirect(urllib.request.HTTPRedirectHandler):
 def redirect_request(self,request,fp,code,message,headers,newurl):return None
def fetch(url):
 headers={'User-Agent':'mlkemac-release-check/1.0 (+https://github.com/Mlkem/Morphe)','Accept':'text/html,application/json'}
 # The Actions token is sent only to the official GitHub API, never Google Play.
 token=os.environ.get('GITHUB_TOKEN')
 if token and url.startswith('https://api.github.com/'):
  headers['Authorization']='Bearer '+token
 request=urllib.request.Request(url,headers=headers)
 with urllib.request.build_opener(NoRedirect).open(request,timeout=20) as response:
  if not response.url.startswith(('https://play.google.com/','https://api.github.com/')):raise ValueError('Unexpected redirect')
  data=response.read(MAX_BYTES+1)
  if len(data)>MAX_BYTES:raise ValueError('Response too large')
  return data.decode('utf-8')
def check(package):
 checked=datetime.now(timezone.utc).isoformat()
 if package=='app.morphe.manager':
  source='GitHub';url='https://github.com/MorpheApp/morphe-manager/releases/latest';api='https://api.github.com/repos/MorpheApp/morphe-manager/releases/latest'
 else:
  source='Google Play';url=f'https://play.google.com/store/apps/details?id={package}&hl=en&gl=CA';api=url
 try:
  content=fetch(api)
  if source=='GitHub':
   release=json.loads(content);version=release.get('tag_name','').removeprefix('v')
   if release.get('draft') or release.get('prerelease') or not valid_version(version):version=None
  else:version=play_version(content,package)
  result={'status':'ok' if version else 'version_unavailable','source':source,'url':url,'checkedAt':checked}
  if version:result['version']=version
  return result
 except (urllib.error.URLError,ValueError,TimeoutError,OSError) as error:
  return {'status':'unavailable','source':source,'url':url,'checkedAt':checked,'error':type(error).__name__}
def refresh(path):
 feed=json.loads(path.read_text());assert feed['schema']==1
 for package,app in feed['apps'].items():
  app['upstream']=check(package)
  print(package,app['upstream']['status'],app['upstream'].get('version',''),flush=True)
  time.sleep(0.7)
 feed['upstreamCheckedAt']=datetime.now(timezone.utc).isoformat()
 path.write_text(json.dumps(feed,sort_keys=True,indent=2)+'\n')
if __name__=='__main__':
 parser=argparse.ArgumentParser();parser.add_argument('--feed',type=Path,default=ROOT/'updates/app-feed.json');args=parser.parse_args();refresh(args.feed)
