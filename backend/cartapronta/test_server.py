import copy,json,os,unittest
from unittest.mock import patch
from cryptography.fernet import Fernet
import server as s
os.environ['SESSION_KEY']=Fernet.generate_key().decode();os.environ['EBAY_CLIENT_ID']='test-client';os.environ['EBAY_CLIENT_SECRET']='test-secret';os.environ['EBAY_RUNAME']='test-runame'
META={'aspects':[{'localizedAspectName':'Gioco','aspectConstraint':{'aspectRequired':True}}],'conditions':[{'conditionId':'4000','conditionDescriptors':[{'conditionDescriptorId':'40001','conditionDescriptorConstraint':{'usage':'REQUIRED'},'conditionDescriptorValues':[{'conditionDescriptorValueId':'400010'}]}]}]}
DATA={'draftId':'11111111-2222-3333-4444-555555555555','title':'Morpeko 135/128 ITA','description':'Foto <originale>\nSeconda riga','price':'12.50','categoryId':'183454','imageUrls':['https://i.ebayimg.com/card.jpg'],'aspects':{'Gioco':['Pokémon']},'conditionId':'4000','conditionDescriptors':[{'name':'40001','values':['400010']}],'fulfillmentPolicyId':'shipping','paymentPolicyId':'pay','returnPolicyId':'returns','merchantLocationKey':'warehouse'}
class Tests(unittest.TestCase):
 def test_valid(self):
  i,o=s.build_payload(DATA,META);self.assertEqual(o['pricingSummary']['price']['value'],'12.50');self.assertEqual(i['condition'],'USED_VERY_GOOD');self.assertIn('&lt;originale&gt;',i['product']['description']);self.assertNotIn('<originale>',i['product']['description'])
 def test_invalid_payloads(self):
  for k,v in [('price','NaN'),('price','-2'),('price','1.234'),('imageUrls',['https://evil.com/x']),('conditionId','1000'),('conditionDescriptors',[]),('aspects',{}),('draftId','../../x'),('fulfillmentPolicyId','')]:
   with self.subTest(k=k,v=v):
    d=copy.deepcopy(DATA);d[k]=v
    with self.assertRaises(s.Error):s.build_payload(d,META)
 def test_seal_isolated_and_expired(self):
  t=s.seal('session',{'user':'Alice'},100);self.assertEqual(s.unseal('session',t)['user'],'Alice')
  for kind,token in [('ticket',t),('session',t[:-1]+'X'),('session',s.seal('session',{},-1))]:
   with self.assertRaises(s.Error):s.unseal(kind,token)
 def test_oauth_challenge(self):
  verifier='a'*64;ticket=s.seal('ticket',{'challenge':s.challenge(verifier),'refresh':'secret-refresh','user':'Alice','refresh_exp':int(s.time.time())+100},60)
  with self.assertRaises(s.Error):s.oauth_complete({'ticket':ticket,'verifier':'b'*64})
  r=s.oauth_complete({'ticket':ticket,'verifier':verifier});self.assertEqual(r['user'],'Alice');self.assertNotIn('secret-refresh',json.dumps(r))
 def test_oauth_missing_config(self):
  with patch.dict(os.environ,{'EBAY_RUNAME':''}):
   with self.assertRaises(s.Error) as e:s.oauth_start({'nonce':'a'*64,'challenge':'b'*64})
   self.assertEqual(e.exception.code,'SETUP_REQUIRED')
 def test_oauth_url(self):
  r=s.oauth_start({'nonce':'a'*64,'challenge':'b'*64});self.assertTrue(r['url'].startswith('https://auth.ebay.com/oauth2/authorize?'));self.assertNotIn('test-secret',r['url'])
 def test_publish_confirmation(self):
  with self.assertRaises(s.Error):s.publish({}, {'user':'Alice'},'token')
 def test_publish_account_mismatch(self):
  a=s.seal('approval',{'user':'Alice'},60)
  with self.assertRaises(s.Error):s.publish({'approval':a,'confirm':True},{'user':'Bob'},'token')
 def test_publish_modified(self):
  a=s.seal('approval',{'user':'Alice','offerId':'123','sku':'CP-test','digest':s.digest({'old':1})},60)
  with patch.object(s,'api',return_value={'status':'UNPUBLISHED'}),patch.object(s,'snapshot',return_value={'new':1}):
   with self.assertRaises(s.Error) as e:s.publish({'approval':a,'confirm':True},{'user':'Alice'},'token')
   self.assertEqual(e.exception.status,409)
 def test_publish_duplicate_returns_existing(self):
  a=s.seal('approval',{'user':'Alice','offerId':'123','sku':'CP-test','digest':'unused'},60)
  with patch.object(s,'api',return_value={'status':'PUBLISHED','listing':{'listingId':'123456789012'}}) as api:
   r=s.publish({'approval':a,'confirm':True},{'user':'Alice'},'token');self.assertTrue(r['published']);self.assertEqual(api.call_count,1)
 def test_publish_after_approval(self):
  snap={'item':{'title':'X'}};a=s.seal('approval',{'user':'Alice','offerId':'123','sku':'CP-test','digest':s.digest(snap)},60)
  with patch.object(s,'api',side_effect=[{'status':'UNPUBLISHED'},{'listingId':'123456789012'}]) as api,patch.object(s,'snapshot',return_value=snap):
   r=s.publish({'approval':a,'confirm':True},{'user':'Alice'},'token');self.assertTrue(r['published']);self.assertEqual(api.call_count,2);self.assertEqual(api.call_args.args[2],'POST')
 def test_prepare_does_not_publish(self):
  calls=[]
  def fake(path,tok,method='GET',body=None):
   calls.append((path,method));return {'offerId':'123'} if path.endswith('/offer') else {'offers':[]}
  with patch.object(s,'schema',return_value=META),patch.object(s,'get_offers',return_value=[]),patch.object(s,'api',side_effect=fake),patch.object(s,'snapshot',return_value={'snapshot':'test'}):
   r=s.prepare(DATA,{'user':'Alice'},'token');self.assertFalse(r['published']);self.assertTrue(r['approval']);self.assertFalse(any(p.endswith('/publish') for p,m in calls))
 def test_prepare_existing_skips_mutations(self):
  with patch.object(s,'schema',return_value=META),patch.object(s,'get_offers',return_value=[{'status':'PUBLISHED','listing':{'listingId':'123456789012'}}]),patch.object(s,'api') as api:
   self.assertTrue(s.prepare(DATA,{'user':'Alice'},'token')['published']);api.assert_not_called()
 def test_unknown_route_needs_auth(self):
  with self.assertRaises(s.Error):s.route('/v1/prepare',DATA,'')
if __name__=='__main__':unittest.main()
