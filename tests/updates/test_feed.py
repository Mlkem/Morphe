import importlib.util,json,sys,unittest,tempfile
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]
sys.path.insert(0,str(ROOT/'scripts/updates'))
import refresh,catalog
from unittest.mock import patch,MagicMock
class FeedTests(unittest.TestCase):
 def page(self,package,version):
  app=[None]*146;app[77]=[package];app[140]=[[[version]]]
  return 'AF_initDataCallback({key: \'ds:8\', data:'+json.dumps([None,[None,None,app]])+', sideChannel: {}});'
 def test_identity_and_structure(self):
  page=self.page('test.app','26.41.2')
  self.assertEqual('26.41.2',refresh.play_version(page,'test.app'))
  self.assertIsNone(refresh.play_version(page,'other.app'))
 def test_missing_and_withheld(self):
  for page in ['no metadata',self.page('test.app','Varies with device'),self.page('test.app',None),'AF_initDataCallback({key: data:[']:
   self.assertIsNone(refresh.play_version(page,'test.app'))
 def test_reviews_are_not_versions(self):self.assertIsNone(refresh.play_version('Review version 99.0','test.app'))
 def test_source_revisions_isolate_apps(self):
  before=catalog.revisions();p=ROOT/'extensions/canada/src/main/java/revision_test.tmp'
  try:
   p.write_text('test');after=catalog.revisions()
   self.assertNotEqual(before['ca.walmart.ecommerceapp'],after['ca.walmart.ecommerceapp'])
   for key in before:
    if key!='ca.walmart.ecommerceapp':self.assertEqual(before[key],after[key])
  finally:p.unlink(missing_ok=True)
 def test_token_is_only_attached_to_github_api(self):
  for url,authorized in [('https://api.github.com/repos/MorpheApp/morphe-manager/releases/latest',True),('https://play.google.com/store/apps/details?id=test.app',False)]:
   response=MagicMock();response.__enter__.return_value=response;response.url=url;response.read.return_value=b'{}'
   opener=MagicMock();opener.open.return_value=response
   with patch.dict(refresh.os.environ,{'GITHUB_TOKEN':'test-only'}),patch.object(refresh.urllib.request,'build_opener',return_value=opener):
    refresh.fetch(url)
   request=opener.open.call_args.args[0]
   self.assertEqual('Bearer test-only' if authorized else None,request.get_header('Authorization'))
 def test_redirects_cannot_forward_authorization(self):
  self.assertIsNone(refresh.NoRedirect().redirect_request(None,None,302,'',{},'https://other.example/'))
 def test_no_private_data_or_apk_urls(self):
  data=json.loads((ROOT/'updates/app-feed.json').read_text());self.assertEqual(1,data['schema']);self.assertEqual(set(catalog.PATHS),set(data['apps']))
  self.assertLess(len(json.dumps(data)),128*1024)
if __name__=='__main__':unittest.main()
