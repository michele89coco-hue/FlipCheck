import copy,io,json,os,unittest
from unittest.mock import patch
from cryptography.fernet import Fernet
import server as s
os.environ['SESSION_KEY']=Fernet.generate_key().decode();os.environ['EBAY_CLIENT_ID']='test-client';os.environ['EBAY_CLIENT_SECRET']='test-secret';os.environ['EBAY_RUNAME']='test-runame'
META={'aspects':[{'localizedAspectName':'Gioco','aspectConstraint':{'aspectRequired':True}}],'conditions':[{'conditionId':'4000','conditionDescriptors':[{'conditionDescriptorId':'40001','conditionDescriptorConstraint':{'usage':'REQUIRED'},'conditionDescriptorValues':[{'conditionDescriptorValueId':'400010'}]}]}]}
DATA={'draftId':'11111111-2222-3333-4444-555555555555','title':'Morpeko 135/128 ITA','description':'Foto <originale>\nSeconda riga','price':'12.50','categoryId':'183454','imageUrls':['https://i.ebayimg.com/card.jpg'],'aspects':{'Gioco':['Pokémon']},'conditionId':'4000','conditionDescriptors':[{'name':'40001','values':['400010']}],'fulfillmentPolicyId':'shipping','paymentPolicyId':'pay','returnPolicyId':'returns','merchantLocationKey':'warehouse'}
class Tests(unittest.TestCase):
 def test_inventory_retry_preserves_sku_payload_and_stops_on_success(self):
  item={'condition':'USED_VERY_GOOD','conditionDescriptors':[{'name':'40001','values':['400010']}]}
  with patch.object(s,'api',side_effect=[s.Error('internal',422,'EBAY_500'),{}]) as api,patch.object(s.time,'sleep') as wait:
   self.assertEqual(s.inventory_item_request('token','CP-test',item),{})
   self.assertEqual(api.call_count,2);self.assertEqual(api.call_args_list[0],api.call_args_list[1])
   self.assertEqual(api.call_args.args,('/sell/inventory/v1/inventory_item/CP-test','token','PUT',item));wait.assert_called_once_with(1)
 def test_inventory_retry_is_bounded_and_prepare_stops_before_offer_creation(self):
  calls=[]
  def fake(path,tok,method='GET',body=None):
   calls.append((path,method))
   if method=='PUT':raise s.Error('core inventory',422,'EBAY_500')
   return {}
  with patch.object(s,'api',side_effect=fake),patch.object(s,'schema',return_value=META),patch.object(s,'get_offers',return_value=[]),patch.object(s.time,'sleep') as wait:
   with self.assertRaises(s.Error) as e:s.prepare(DATA,{'user':'Alice'},'token')
   self.assertEqual(e.exception.status,503);self.assertIn('3 tentativi',e.exception.message)
   self.assertEqual(len(calls),3);self.assertTrue(all(method=='PUT' for _,method in calls));self.assertEqual(wait.call_count,2)
 def test_inventory_does_not_retry_validation_auth_or_unknown_timeout(self):
  for code in ['EBAY_400','EBAY_401','EBAY_403','EBAY_404','EBAY_429','REMOTE_TIMEOUT']:
   with self.subTest(code=code),patch.object(s,'api',side_effect=s.Error('failure',422,code)) as api,patch.object(s.time,'sleep') as wait:
    with self.assertRaises(s.Error):s.inventory_item_request('token','CP-test',{})
    self.assertEqual(api.call_count,1);wait.assert_not_called()
 def test_inventory_get_retry_has_no_writes(self):
  with patch.object(s,'api',side_effect=[s.Error('internal',422,'EBAY_503'),{'condition':'USED_VERY_GOOD'}]) as api,patch.object(s.time,'sleep'):
   self.assertEqual(s.inventory_item_request('token','CP-test')['condition'],'USED_VERY_GOOD')
   self.assertTrue(all(len(c.args)==2 for c in api.call_args_list))
 def test_offer_creation_is_not_retried_on_server_error(self):
  calls=[]
  def fake(path,tok,method='GET',body=None):
   calls.append((path,method))
   if path.endswith('/offer') and method=='POST':raise s.Error('internal',422,'EBAY_500')
   return {}
  with patch.object(s,'api',side_effect=fake),patch.object(s,'schema',return_value=META),patch.object(s,'get_offers',return_value=[]),patch.object(s.time,'sleep') as wait:
   with self.assertRaises(s.Error):s.prepare(DATA,{'user':'Alice'},'token')
   self.assertEqual(sum(method=='POST' for _,method in calls),1);wait.assert_not_called()
 def test_error_retains_ebay_error_id_and_inventory_operation(self):
  url=s.API+'/sell/inventory/v1/inventory_item/CP-test'
  failure=s.urllib.error.HTTPError(url,500,'Internal',{},io.BytesIO(b'{"errors":[{"errorId":25001,"message":"Core inventory service error"}]}'))
  with patch.object(s.urllib.request,'urlopen',side_effect=failure):
   with self.assertRaises(s.Error) as e:s.http(url,'PUT',b'{}')
  self.assertIn('salvataggio dati carta',e.exception.message);self.assertIn('[25001]',e.exception.message)
 def test_image_upload_uses_media_host_and_multipart(self):
  photo=b'\xff\xd8\xff'+b'x'*200
  with patch.object(s.urllib.request,'urlopen') as remote:
   remote.return_value.__enter__.return_value.read.return_value=b'{"imageUrl":"https://i.ebayimg.com/card.jpg"}'
   result=s.upload_image({'image':s.base64.b64encode(photo).decode()},'test-user-token')
   request=remote.call_args.args[0]
   self.assertEqual(request.full_url,'https://apim.ebay.com/commerce/media/v1_beta/image/create_image_from_file')
   self.assertEqual(request.method,'POST');self.assertEqual(request.get_header('Authorization'),'Bearer test-user-token')
   self.assertIn(b'name="image"; filename="card.jpg"',request.data);self.assertIn(photo,request.data)
   boundary=request.get_header('Content-type').split('boundary=')[1]
   self.assertTrue(request.data.endswith(('--'+boundary+'--\r\n').encode()))
   self.assertEqual(result,{'url':'https://i.ebayimg.com/card.jpg'})
 def test_empty_remote_error_identifies_operation_and_status(self):
  url='https://apim.ebay.com/commerce/media/v1_beta/image/create_image_from_file'
  failure=s.urllib.error.HTTPError(url,404,'Not Found',{},io.BytesIO(b''))
  with patch.object(s.urllib.request,'urlopen',side_effect=failure):
   with self.assertRaises(s.Error) as e:s.http(url,'POST',b'',{'Authorization':'Bearer secret-test'})
  self.assertIn('caricamento foto (HTTP 404)',e.exception.message)
  self.assertEqual(e.exception.code,'EBAY_404');self.assertNotIn('secret-test',e.exception.message)
 def test_remote_error_keeps_ebay_explanation(self):
  url=s.API+'/sell/inventory/v1/offer/get_listing_fees'
  failure=s.urllib.error.HTTPError(url,400,'Bad Request',{},io.BytesIO(b'{"errors":[{"message":"Specifics missing"}]}'))
  with patch.object(s.urllib.request,'urlopen',side_effect=failure):
   with self.assertRaises(s.Error) as e:s.http(url)
  self.assertIn('controllo tariffe (HTTP 400)',e.exception.message);self.assertIn('Specifics missing',e.exception.message)
 def test_http_destination_stays_restricted(self):
  with patch.object(s.urllib.request,'urlopen') as remote:
   for url in ['https://apim.ebay.com.evil.test/commerce/media/v1_beta/image','https://apim.ebay.com/other','https://evil.test/','http://apim.ebay.com/commerce/media/v1_beta/image']:
    with self.subTest(url=url):
     with self.assertRaises(s.Error):s.http(url)
   remote.assert_not_called()
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
 def test_update_excludes_creation_keys(self):
  with patch.object(s,'schema',return_value=META),patch.object(s,'get_offers',return_value=[{'offerId':'123','status':'UNPUBLISHED'}]),patch.object(s,'api',return_value={}) as api,patch.object(s,'snapshot',return_value={}):
   s.prepare(DATA,{'user':'Alice'},'token')
   update=next(c for c in api.call_args_list if c.args[0]=='/sell/inventory/v1/offer/123')
   self.assertFalse(set(update.args[3])&{'sku','format','marketplaceId'})
 def test_prepare_existing_skips_mutations(self):
  with patch.object(s,'schema',return_value=META),patch.object(s,'get_offers',return_value=[{'status':'PUBLISHED','listing':{'listingId':'123456789012'}}]),patch.object(s,'api') as api:
   self.assertTrue(s.prepare(DATA,{'user':'Alice'},'token')['published']);api.assert_not_called()
 def test_unknown_route_needs_auth(self):
  with self.assertRaises(s.Error):s.route('/v1/prepare',DATA,'')
 def test_account_not_enrolled_is_read_only(self):
  with patch.object(s,'api',return_value={'programs':[]}) as api:
   r=s.seller_account({'user':'NotEnrolled'},'token');self.assertEqual(r['policyStatus'],'opt_in_required');self.assertEqual(api.call_count,1);self.assertEqual(len(api.call_args.args),2)
 def test_enable_requires_confirmation(self):
  with patch.object(s,'api') as api:
   with self.assertRaises(s.Error) as e:s.enable_policies({}, {'user':'Alice'},'token')
   self.assertEqual(e.exception.code,'CONFIRM_REQUIRED');api.assert_not_called()
 def test_enable_pending_and_no_duplicate(self):
  calls=[];user={'user':'PendingTest'};s.CACHE.pop('policy-opt-in:PendingTest',None)
  def fake(path,tok,method='GET',body=None):
   calls.append((path,method,body));return {'programs':[]} if method=='GET' else {}
  try:
   with patch.object(s,'api',side_effect=fake):
    self.assertEqual(s.enable_policies({'confirm':True},user,'token')['policyStatus'],'pending')
    self.assertEqual(s.enable_policies({'confirm':True},user,'token')['policyStatus'],'pending')
   writes=[x for x in calls if x[1]=='POST'];self.assertEqual(len(writes),1);self.assertEqual(writes[0],('/sell/account/v1/program/opt_in','POST',{'programType':'SELLING_POLICY_MANAGEMENT'}))
  finally:s.CACHE.pop('policy-opt-in:PendingTest',None)
 def test_enabled_account_needs_no_activation(self):
  def fake(path,tok,method='GET',body=None):
   self.assertEqual(method,'GET');return {'programs':[{'programType':'SELLING_POLICY_MANAGEMENT'}]} if 'program/' in path else {}
  with patch.object(s,'api',side_effect=fake):self.assertEqual(s.enable_policies({'confirm':True},{'user':'EnrolledTest'},'token')['policyStatus'],'ready')
 def test_policy_ineligibility_is_actionable(self):
  with patch.object(s,'policy_program_enabled',return_value=True),patch.object(s,'api',side_effect=s.Error('eBay: User is not eligible for Business Policy.')):
   self.assertEqual(s.seller_account({'user':'Alice'},'token')['policyStatus'],'unavailable')
 def test_policy_auth_error_not_hidden(self):
  with patch.object(s,'policy_program_enabled',return_value=True),patch.object(s,'api',side_effect=s.Error('Expired',401,'EBAY_401')):
   with self.assertRaises(s.Error) as e:s.seller_account({'user':'Alice'},'token')
   self.assertEqual(e.exception.status,401)
 def test_policy_create_requires_confirm_before_remote(self):
  with patch.object(s,'api') as api:
   with self.assertRaises(s.Error):s.create_policy({'kind':'payment'}, {'user':'Alice'},'token')
   api.assert_not_called()
 def test_policy_payment_uses_managed_payments(self):
  d={'kind':'payment','immediatePay':True,'confirm':True,'draftId':'draft'}
  with patch.object(s,'api',side_effect=[{'paymentPolicies':[]},{'paymentPolicyId':'123'}]) as api:
   r=s.create_policy(d,{'user':'Alice'},'token');body=api.call_args.args[3]
   self.assertTrue(body['immediatePay']);self.assertNotIn('paymentMethods',body);self.assertEqual(r['policy']['paymentPolicyId'],'123');self.assertEqual(r['draftId'],'draft')
 def test_policy_retry_reuses_unchanged(self):
  d={'kind':'payment','immediatePay':False,'confirm':True};_,body=s.policy_payload(d,'token');existing=dict(body,paymentPolicyId='123');existing.pop('immediatePay')
  with patch.object(s,'api',return_value={'paymentPolicies':[existing]}) as api:
   self.assertEqual(s.create_policy(d,{'user':'Alice'},'token')['policy']['paymentPolicyId'],'123');self.assertEqual(api.call_count,1)
 def test_policy_retry_rejects_changed_rule(self):
  d={'kind':'payment','immediatePay':False,'confirm':True};_,body=s.policy_payload(d,'token');existing=dict(body,paymentPolicyId='123',immediatePay=True)
  with patch.object(s,'api',return_value={'paymentPolicies':[existing]}) as api:
   with self.assertRaises(s.Error):s.create_policy(d,{'user':'Alice'},'token')
   self.assertEqual(api.call_count,1)
 def test_shipping_xml_filters_domestic_flat_active(self):
  def service(code,extra=''):return '<ShippingServiceDetails><ShippingService>'+code+'</ShippingService><Description>'+code+'</Description><ValidForSellingFlow>true</ValidForSellingFlow><ServiceType>Flat</ServiceType>'+extra+'</ShippingServiceDetails>'
  xml='<GeteBayDetailsResponse xmlns="urn:ebay:apis:eBLBaseComponents">'+service('IT_Test')+service('International','<InternationalService>true</InternationalService>')+service('Pickup')+service('CalculatedOnly').replace('<ServiceType>Flat</ServiceType>','<ServiceType>Calculated</ServiceType>')+service('Mixed','<ServiceType>Calculated</ServiceType>')+'<ShippingServiceDetails><ShippingService>Inactive</ShippingService><ServiceType>Flat</ServiceType></ShippingServiceDetails><DispatchTimeMaxDetails><DispatchTimeMax>3</DispatchTimeMax><Description>3 giorni</Description></DispatchTimeMaxDetails></GeteBayDetailsResponse>'
  r=s.parse_shipping_options(s.ET.fromstring(xml));self.assertEqual([x['code'] for x in r['services']],['IT_Test','Mixed']);self.assertEqual(r['handling'][0]['value'],3)
 def test_shipping_payload_and_validation(self):
  options={'services':[{'code':'IT_Test'}],'handling':[{'value':3}]};d={'kind':'fulfillment','shippingServiceCode':'IT_Test','handlingDays':3,'shippingCost':'4,50'}
  with patch.object(s,'shipping_options',return_value=options):
   _,b=s.policy_payload(d,'token');self.assertEqual(b['shippingOptions'][0]['costType'],'FLAT_RATE');self.assertEqual(b['shippingOptions'][0]['shippingServices'][0]['shippingCost']['value'],'4.50')
   for key,value in [('shippingCost',''),('shippingCost','NaN'),('shippingCost','-1'),('shippingServiceCode','INVENTED'),('handlingDays',True)]:
    with self.subTest(key=key):
     with self.assertRaises(s.Error):s.policy_payload(dict(d,**{key:value}),'token')
 def test_return_policy_uses_category_metadata(self):
  options={'returnPeriods':[{'unit':'DAY','value':30}],'returnShippingCostPayers':['BUYER']};d={'kind':'return','returnsAccepted':True,'returnDays':30,'returnShippingCostPayer':'BUYER','categoryId':'183454'}
  with patch.object(s,'return_options',return_value=options):
   _,b=s.policy_payload(d,'token');self.assertEqual(b['returnPeriod']['value'],30)
   with self.assertRaises(s.Error):s.policy_payload(dict(d,returnDays=14),'token')
  with patch.object(s,'return_options') as read:
   _,b=s.policy_payload({'kind':'return','returnsAccepted':False},'token');self.assertFalse(b['returnsAccepted']);self.assertNotIn('returnPeriod',b);read.assert_not_called()
 def test_policy_semantic_match_ignores_extra_defaults(self):
  self.assertTrue(s.policy_matches({'shippingCost':{'currency':'EUR','value':'4.50'},'freeShipping':False},{'shippingCost':{'currency':'EUR','value':'4.5'},'sortOrder':1}))
  self.assertFalse(s.policy_matches({'shippingCost':{'currency':'EUR','value':'4.50'}},{'shippingCost':{'currency':'EUR','value':'5.50'}}))
 def test_actual_metadata_calendar_day_normalized(self):
  s.CACHE.pop('return-options:183454',None)
  data={'returnPolicies':[{'categoryId':'183454','domestic':{'returnPeriods':[{'value':14,'unit':'CALENDAR_DAY'},{'value':30,'unit':'CALENDAR_DAY'}],'returnShippingCostPayers':['SELLER','BUYER']}}]}
  try:
   with patch.object(s,'app_token',return_value='token'),patch.object(s,'api',return_value=data):
    self.assertEqual(s.return_options('183454')['returnPeriods'],[{'unit':'DAY','value':14},{'unit':'DAY','value':30}])
  finally:s.CACHE.pop('return-options:183454',None)
if __name__=='__main__':unittest.main()
