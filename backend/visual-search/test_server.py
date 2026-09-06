import base64, json, unittest, threading, time
from unittest.mock import patch
import server
PNG=b'\x89PNG\r\n\x1a\n'+b'x'*20
DETECTION={'responses':[{'webDetection':{'webEntities':[{'description':'suggestion','score':1.4}],
 'fullMatchingImages':[{'url':'https://public.example/full.png'}],'partialMatchingImages':[{'url':'https://public.example/partial.png'}],
 'visuallySimilarImages':[{'url':'https://public.example/similar.png'}],'bestGuessLabels':[{'label':'candidate label'}],
 'pagesWithMatchingImages':[{'url':'https://public.example/page','pageTitle':'Catalogue','fullMatchingImages':[{'url':'https://public.example/full.png'}]}]}}]}
class FakeProvider:
 name='fake'
 available=True
 def __init__(self):self.calls=0
 def detect(self,image,timeout):self.calls+=1;return server.normalize_detection(DETECTION)
def fetch(url,deadline,limit):
 return (PNG,'image/png',url) if url.endswith('.png') else (b'<html><script>ignore me</script><p>Historical portrait panel, issue 37.</p></html>','text/html',url)
class Tests(unittest.TestCase):
 def request(self,scan='scan_12345678'):return {'scan_id':scan,'image_base64':base64.b64encode(PNG).decode(),'remaining_usd':.025,'clues':['portrait']}
 def service(self):return server.VisualService(server.Config(enabled=True,token='testing'),FakeProvider(),fetch)
 def test_request_uses_inline_image_and_exactly_web_detection(self):
  calls=[]
  def transport(url,body,timeout):calls.append((url,body));return DETECTION
  result=server.GoogleWebDetection(transport=transport).detect(PNG,10)
  self.assertEqual(calls[0][0],server.GOOGLE_ENDPOINT)
  self.assertEqual(calls[0][1]['requests'][0]['features'],[{'type':'WEB_DETECTION','maxResults':8}])
  self.assertNotIn('source',calls[0][1]['requests'][0]['image'])
  for field in ['webEntities','fullMatchingImages','partialMatchingImages','visuallySimilarImages','pagesWithMatchingImages','bestGuessLabels']:self.assertTrue(result[field])
  self.assertEqual(result['webEntities'][0]['providerScore'],1.4)
 def test_missing_fields_and_per_image_error(self):
  self.assertEqual(server.normalize_detection({'responses':[{}]})['pagesWithMatchingImages'],[])
  with self.assertRaisesRegex(server.ServiceError,'provider_image_error'):server.normalize_detection({'responses':[{'error':{'code':3}}]})
 def test_provider_unconfigured_and_budget_do_not_call(self):
  svc=self.service();svc.config.enabled=False
  self.assertEqual(svc.run(self.request())['state'],'not_configured');svc.config.enabled=True
  r=self.request();r['remaining_usd']=0
  self.assertEqual(svc.run(r)['state'],'budget_exhausted');self.assertEqual(svc.provider.calls,0)
 def test_reference_content_and_per_scan_dedup(self):
  svc=self.service();a=svc.run(self.request());b=svc.run(self.request())
  self.assertEqual(a['state'],'ok');self.assertEqual(b['providerCalls'],0);self.assertEqual(svc.provider.calls,1)
  self.assertNotIn('ignore me',a['references'][0]['text']);self.assertTrue(a['references'][0]['image_data'])
  self.assertNotIn('image_base64',str(svc.scans));self.assertNotIn(base64.b64encode(PNG).decode(),str({k:{kk:vv for kk,vv in v.items() if kk!='result'} for k,v in svc.scans.items()}))
 def test_simultaneous_duplicate_annotation_runs_once(self):
  svc=self.service();outputs=[]
  threads=[threading.Thread(target=lambda:outputs.append(svc.run(self.request()))) for _ in range(2)]
  for t in threads:t.start()
  for t in threads:t.join()
  self.assertEqual(svc.provider.calls,1);self.assertEqual(sum(x['providerCalls'] for x in outputs),1)
 def test_no_cross_scan_identity_cache(self):
  svc=self.service();svc.run(self.request());svc.run(self.request('another_123456'))
  self.assertEqual(svc.provider.calls,2)
 def test_timeout_keeps_unknown_billing_and_never_retries(self):
  svc=self.service()
  def fail(image,timeout):svc.provider.calls+=1;raise TimeoutError()
  svc.provider.detect=fail;r=svc.run(self.request());svc.run(self.request())
  self.assertEqual(r['state'],'timeout');self.assertTrue(r['billingUnknown']);self.assertEqual(svc.provider.calls,1)
 def test_private_url_and_signed_url_are_rejected(self):
  self.assertEqual(server.clean_url('https://public.example/image?token=secret'),'')
  with patch('socket.getaddrinfo',return_value=[(2,1,6,'',('127.0.0.1',443))]):
   with self.assertRaisesRegex(server.ServiceError,'blocked'):server.public_addresses('public.example')
 def test_authentication_and_capability(self):
  svc=self.service();self.assertTrue(svc.capabilities()['enabled']);svc.config.token='';self.assertFalse(svc.capabilities()['enabled'])
if __name__=='__main__':unittest.main()
