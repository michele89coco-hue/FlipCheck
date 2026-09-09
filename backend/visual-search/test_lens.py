import base64, json, threading, time, unittest
from unittest.mock import patch
from urllib.request import Request, urlopen
from urllib.error import HTTPError
from http.server import ThreadingHTTPServer
import lens

PNG=b'\x89PNG\r\n\x1a\n'+b'x'*20
DATA={'visual_matches':[{'position':i,'title':'Card '+str(i),'link':'https://catalogue.example/'+str(i),'image':{'link':'https://images.example/'+str(i)+'.jpg'}} for i in range(1,31)]}
class Tests(unittest.TestCase):
 def service(self, answer=DATA):
  self.calls=[]
  def transport(route,params,timeout):
   self.calls.append((route,params))
   if route=='me': return {'account':{'remaining_credits':10,'monthly_allowance':100}}
   if isinstance(answer,Exception): raise answer
   self.assertTrue(self.svc.images.get(params['url'].rsplit('/',1)[1]))
   return answer
  self.svc=lens.LensService(lens.SearchApi('server-secret',transport),'https://lens.example','access-testing-123456',.004)
  return self.svc
 def request(self,scan='scan_test_1234'): return {'scan_id':scan,'image_base64':base64.b64encode(PNG).decode(),'remaining_usd':.03}
 def test_verified_contract_one_image_no_hint_and_twenty(self):
  s=self.service();r=s.run(self.request());self.assertEqual(r['providerCalls'],1);self.assertEqual(len(r['candidates']),20)
  self.assertEqual(set(self.calls[1][1]),{'engine','url','search_type','country','hl'})
  self.assertEqual(self.calls[1][1]['engine'],'google_lens');self.assertEqual(r['account']['remaining_credits'],10)
  self.assertTrue(r['imageDeleted']);self.assertFalse(s.images.items)
  self.assertNotIn('server-secret',json.dumps(r));self.assertNotIn('lens-image',json.dumps(r));self.assertNotIn(base64.b64encode(PNG).decode(),str(s.scans))
 def test_dedup_and_changed_image(self):
  s=self.service();s.run(self.request());r=s.run(self.request());self.assertEqual(r['providerCalls'],0);self.assertEqual(len(self.calls),2)
  bad=self.request();bad['image_base64']=base64.b64encode(PNG+b'b').decode()
  with self.assertRaisesRegex(lens.ServiceError,'scan_image_changed'):s.run(bad)
 def test_concurrent_same_scan_is_one_provider_request(self):
  s=self.service();out=[];threads=[threading.Thread(target=lambda:out.append(s.run(self.request()))) for _ in range(3)]
  for t in threads:t.start()
  for t in threads:t.join()
  self.assertEqual(sum(r['providerCalls'] for r in out),1)
 def test_timeout_releases_image_keeps_possible_charge_no_retry(self):
  s=self.service(TimeoutError());r=s.run(self.request());self.assertEqual(r['state'],'timeout');self.assertTrue(r['billingUnknown']);self.assertTrue(r['imageDeleted']);self.assertFalse(s.images.items)
  s.run(self.request());self.assertEqual(len(self.calls),2)
 def test_empty_results_continue_as_empty(self):
  r=self.service({}).run(self.request());self.assertEqual(r['state'],'empty_results');self.assertFalse(r['candidates'])
 def test_quota_failure_after_start_has_single_attempt(self):
  r=self.service(lens.ServiceError('quota_exhausted')).run(self.request());self.assertEqual(r['state'],'quota_exhausted');self.assertEqual(r['providerCalls'],1);self.assertTrue(r['imageDeleted'])
 def test_zero_credits_never_uploads_or_searches(self):
  s=self.service();s.account={'remaining_credits':0};s.account_at=time.monotonic();r=s.run(self.request());self.assertEqual(r['state'],'quota_exhausted');self.assertEqual(r['providerCalls'],0);self.assertFalse(s.images.items);self.assertFalse(self.calls)
 def test_budget_and_nan_do_not_call_provider(self):
  s=self.service()
  for v in [0,float('nan'),float('inf'),None]:
   r=s.run({**self.request(),'remaining_usd':v});self.assertEqual(r['state'],'budget_exhausted')
  self.assertFalse(self.calls)
 def test_unconfigured_cost_disables_service(self):
  s=self.service();s=lens.LensService(s.provider,'https://lens.example','valid-access',None)
  self.assertFalse(s.config()['enabled']);self.assertEqual(s.run(self.request())['providerCalls'],0)
 def test_images_expire_without_successful_provider(self):
  now=[1];im=lens.Images('https://lens.example',ttl=2,clock=lambda:now[0]);key,url=im.put(PNG);self.assertEqual(im.get(key),PNG);now[0]=4;self.assertIsNone(im.get(key));self.assertFalse(im.items)
 def test_http_authentication_and_public_image_expiry(self):
  s=self.service();http=ThreadingHTTPServer(('127.0.0.1',0),lens.handler(s));t=threading.Thread(target=http.serve_forever,daemon=True);t.start();origin='http://127.0.0.1:'+str(http.server_port)
  try:
   with self.assertRaises(HTTPError) as e:urlopen(origin+'/v1/lens/config')
   self.assertEqual(e.exception.code,401)
   req=Request(origin+'/v1/lens/config',headers={'Authorization':'Bearer '+s.token})
   with urlopen(req) as r:self.assertTrue(json.load(r)['enabled'])
   key,_=s.images.put(PNG)
   with urlopen(origin+'/v1/lens-image/'+key) as r:self.assertEqual(r.read(),PNG);self.assertIn('no-store',r.headers['Cache-Control'])
   s.images.delete(key)
   with self.assertRaises(HTTPError) as e:urlopen(origin+'/v1/lens-image/'+key)
   self.assertEqual(e.exception.code,404)
  finally:http.shutdown();http.server_close();t.join()
 def test_account_snapshot_is_cached_and_credit_reservation_decreases(self):
  s=self.service();a=s.run(self.request());b=s.run(self.request('scan_test_5678'))
  self.assertEqual(a['account']['remaining_credits'],10);self.assertEqual(b['account']['remaining_credits'],9);self.assertEqual([r for r,p in self.calls].count('me'),1)
 def test_secret_sent_in_header_never_query_and_redirects_disabled(self):
  class Response:
   def read(self,size):return b'{}'
   def __enter__(self):return self
   def __exit__(self,*args):pass
  with patch('lens.urllib.request.build_opener') as build:
   build.return_value.open.return_value=Response()
   lens.SearchApi('test-secret').request('search',{'engine':'google_lens','url':'https://temporary.example/image'},5)
   req=build.return_value.open.call_args.args[0]
   self.assertEqual(req.get_header('Authorization'),'Bearer test-secret');self.assertNotIn('test-secret',req.full_url)
   redirect=build.call_args.args[0];self.assertIsNone(redirect.redirect_request(None,None,302,'',{},'https://other.example'))
if __name__=='__main__':unittest.main()
