"""CartaPronta eBay broker. Credentials never leave the server; no seller database."""
import base64, hashlib, hmac, html, json, os, re, secrets, threading, time
import urllib.request, urllib.error, urllib.parse
from decimal import Decimal, InvalidOperation
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from xml.etree import ElementTree as ET
from cryptography.fernet import Fernet, InvalidToken

API = 'https://api.ebay.com'
MARKET = 'EBAY_IT'
SCOPES = ' '.join('https://api.ebay.com/oauth/api_scope' + x for x in ('', '/sell.inventory', '/sell.account'))
VERSION = '1.0.4'
CACHE = {}
LOCKS = [threading.Lock() for _ in range(64)]

class Error(Exception):
    def __init__(self, message, status=400, code='INVALID_REQUEST'):
        self.message, self.status, self.code = message, status, code
        super().__init__(message)

def required(name):
    v = os.environ.get(name, '').strip()
    if not v: raise Error('Collegamento eBay in attesa di configurazione da parte del gestore di CartaPronta.', 503, 'SETUP_REQUIRED')
    return v

def seal(kind, data, seconds):
    return Fernet(required('SESSION_KEY').encode()).encrypt(json.dumps(dict(data, kind=kind, exp=int(time.time())+seconds)).encode()).decode()

def unseal(kind, value):
    try:
        if not isinstance(value, str) or len(value)>30000: raise ValueError()
        d=json.loads(Fernet(required('SESSION_KEY').encode()).decrypt(value.encode()))
        if d['kind']!=kind or d['exp']<time.time(): raise ValueError()
        return d
    except (InvalidToken, ValueError, KeyError, TypeError): raise Error('Autorizzazione scaduta o non valida. Collega di nuovo eBay.',401,'AUTH_REQUIRED')

def digest(v): return hashlib.sha256(json.dumps(v,sort_keys=True,separators=(',',':'),ensure_ascii=False).encode()).hexdigest()
def challenge(v): return hashlib.sha256(v.encode()).hexdigest()
def ident(v):
    v=str(v)
    if not re.fullmatch(r'[A-Za-z0-9_-]{1,100}',v): raise Error('Identificativo non valido.')
    return v

def http(url, method='GET', data=None, headers=None):
    if not url.startswith(API+'/'): raise Error('Destinazione non consentita.')
    req=urllib.request.Request(url,data=data,method=method,headers=headers or {})
    try:
        with urllib.request.urlopen(req,timeout=35) as r:
            raw=r.read(4000000)
            return json.loads(raw) if raw else {}
    except urllib.error.HTTPError as e:
        try:
            body=json.loads(e.read(100000)); errors=body.get('errors',[])
            msg='; '.join(str(x.get('longMessage') or x.get('message','')) for x in errors) or body.get('error_description') or body.get('error')
        except Exception: msg=''
        raise Error('eBay: '+str(msg or 'richiesta non riuscita')[:1500], 401 if e.code==401 else 422, 'EBAY_'+str(e.code))
    except (OSError,TimeoutError): raise Error('eBay non ha risposto in tempo. Verifica lo stato prima di riprovare la pubblicazione.',504,'REMOTE_TIMEOUT')

def token(fields):
    auth=base64.b64encode((required('EBAY_CLIENT_ID')+':'+required('EBAY_CLIENT_SECRET')).encode()).decode()
    return http(API+'/identity/v1/oauth2/token','POST',urllib.parse.urlencode(fields).encode(),{'Authorization':'Basic '+auth,'Content-Type':'application/x-www-form-urlencoded'})

def app_token():
    c=CACHE.get('app')
    if c and c[0]>time.time(): return c[1]
    d=token({'grant_type':'client_credentials','scope':'https://api.ebay.com/oauth/api_scope'})
    CACHE['app']=(time.time()+int(d['expires_in'])-90,d['access_token']);return d['access_token']

def access(session):
    user=unseal('session',session);key=challenge(session);c=CACHE.get(key)
    if c and c[0]>time.time(): return user,c[1]
    t=token({'grant_type':'refresh_token','refresh_token':user['refresh']})
    if len(CACHE)>2000: CACHE.clear()
    CACHE[key]=(time.time()+int(t['expires_in'])-90,t['access_token']);return user,t['access_token']

def api(path, tok, method='GET', body=None):
    return http(API+path,method,None if body is None else json.dumps(body).encode(),{'Authorization':'Bearer '+tok,'Content-Type':'application/json','Content-Language':'it-IT','Accept-Language':'it-IT','X-EBAY-C-MARKETPLACE-ID':MARKET})

def get_user(tok):
    req=urllib.request.Request(API+'/ws/api.dll',data=b'<?xml version="1.0" encoding="utf-8"?><GetUserRequest xmlns="urn:ebay:apis:eBLBaseComponents"/>',headers={'X-EBAY-API-CALL-NAME':'GetUser','X-EBAY-API-SITEID':'101','X-EBAY-API-COMPATIBILITY-LEVEL':'1423','X-EBAY-API-IAF-TOKEN':tok,'Content-Type':'text/xml'})
    try:
        with urllib.request.urlopen(req,timeout=30) as r: root=ET.fromstring(r.read(1000000))
        if root.findtext('{*}Ack') not in ('Success','Warning'): raise ValueError()
        uid=root.findtext('.//{*}UserID')
        if not uid: raise ValueError()
        return uid
    except Exception: raise Error('Impossibile verificare l’account venditore eBay. Riprova il collegamento.',422,'ACCOUNT_FAILED')

def oauth_start(data):
    ru=required('EBAY_RUNAME');nonce=data.get('nonce','');ch=data.get('challenge','')
    if not re.fullmatch('[a-f0-9]{64}',ch) or not re.fullmatch('[a-f0-9]{64}',nonce): raise Error('Richiesta di collegamento non valida.')
    state=seal('state',{'nonce':nonce,'challenge':ch},600)
    return {'url':'https://auth.ebay.com/oauth2/authorize?'+urllib.parse.urlencode({'client_id':required('EBAY_CLIENT_ID'),'redirect_uri':ru,'response_type':'code','scope':SCOPES,'state':state})}

def oauth_callback(q):
    state=unseal('state',q.get('state',''))
    if q.get('error'): return 'cartapronta://ebay-connected?'+urllib.parse.urlencode({'nonce':state['nonce'],'error':'denied'})
    code=q.get('code','')
    if not code or len(code)>10000: raise Error('Autorizzazione eBay incompleta.')
    t=token({'grant_type':'authorization_code','code':code,'redirect_uri':required('EBAY_RUNAME')})
    if not t.get('refresh_token'): raise Error('eBay non ha rilasciato un’autorizzazione rinnovabile.')
    ticket=seal('ticket',{'nonce':state['nonce'],'challenge':state['challenge'],'refresh':t['refresh_token'],'refresh_exp':int(time.time())+int(t.get('refresh_token_expires_in',86400)),'user':get_user(t['access_token'])},300)
    return 'cartapronta://ebay-connected?'+urllib.parse.urlencode({'ticket':ticket,'nonce':state['nonce']})

def oauth_complete(data):
    t=unseal('ticket',data.get('ticket',''));v=data.get('verifier','')
    if len(v)!=64 or not hmac.compare_digest(challenge(v),t['challenge']): raise Error('Collegamento non avviato da questo telefono.',401)
    session=seal('session',{'refresh':t['refresh'],'user':t['user']},max(1,t['refresh_exp']-int(time.time())))
    return {'session':session,'user':t['user']}

def schema(category):
    category=ident(category);key='schema:'+category;c=CACHE.get(key)
    if c and c[0]>time.time():return c[1]
    tok=app_token();tree=api('/commerce/taxonomy/v1/get_default_category_tree_id?marketplace_id=EBAY_IT',tok)['categoryTreeId']
    aspects=api('/commerce/taxonomy/v1/category_tree/'+ident(tree)+'/get_item_aspects_for_category?category_id='+category,tok).get('aspects',[])
    policies=api('/sell/metadata/v1/marketplace/EBAY_IT/get_item_condition_policies?filter='+urllib.parse.quote('categoryIds:{'+category+'}'),tok).get('itemConditionPolicies',[])
    conditions=next((p.get('itemConditions',[]) for p in policies if str(p.get('categoryId'))==category),[])
    result={'aspects':aspects,'conditions':conditions,'categoryId':category}
    CACHE[key]=(time.time()+3600,result);return result

CONDITIONS={'1000':'NEW','1500':'NEW_OTHER','1750':'NEW_WITH_DEFECTS','2000':'MANUFACTURER_REFURBISHED','2010':'CERTIFIED_REFURBISHED','2020':'EXCELLENT_REFURBISHED','2030':'VERY_GOOD_REFURBISHED','2500':'SELLER_REFURBISHED','2750':'LIKE_NEW','3000':'USED_EXCELLENT','4000':'USED_VERY_GOOD','5000':'USED_GOOD','6000':'USED_ACCEPTABLE','7000':'FOR_PARTS_OR_NOT_WORKING'}

def build_payload(data, meta):
    title=str(data.get('title','')).strip();description=str(data.get('description','')).strip()
    if not 1<=len(title)<=80 or not 1<=len(description)<=15000:raise Error('Completa titolo (massimo 80 caratteri) e descrizione.')
    try:
        price=Decimal(str(data.get('price','')))
        if not price.is_finite() or price<=0 or price>9999999 or price!=price.quantize(Decimal('.01')):raise ValueError()
    except (InvalidOperation,ValueError):raise Error('Prezzo non valido.')
    photos=data.get('imageUrls',[])
    if not isinstance(photos,list) or not 1<=len(photos)<=12:raise Error('Carica da 1 a 12 foto.')
    for p in photos:
        u=urllib.parse.urlsplit(p)
        if u.scheme!='https' or not u.hostname or not (u.hostname=='ebayimg.com' or u.hostname.endswith('.ebayimg.com')):raise Error('Foto non caricata su eBay.')
    aspects=data.get('aspects',{})
    if not isinstance(aspects,dict):raise Error('Specifiche non valide.')
    for a in meta['aspects']:
        name=a['localizedAspectName'];v=aspects.get(name,[]);c=a.get('aspectConstraint',{})
        if c.get('aspectRequired') and not v:raise Error('Completa la specifica: '+name)
        if v and (not isinstance(v,list) or any(not isinstance(x,str) or not x.strip() or len(x)>300 for x in v)):raise Error('Specifica non valida: '+name)
        if c.get('aspectMode')=='SELECTION_ONLY' and any(x not in [x['localizedValue'] for x in a.get('aspectValues',[])] for x in v):raise Error('Seleziona un valore valido per '+name)
    cid=str(data.get('conditionId',''));cond=next((x for x in meta['conditions'] if str(x['conditionId'])==cid),None)
    if cond is None or cid not in CONDITIONS:raise Error('Seleziona le condizioni richieste da eBay.')
    descriptors=data.get('conditionDescriptors',[])
    for cd in cond.get('conditionDescriptors',[]):
        name=str(cd['conditionDescriptorId']);entry=next((x for x in descriptors if str(x.get('name'))==name),None)
        required_cd=cd.get('conditionDescriptorConstraint',{}).get('usage')=='REQUIRED'
        if required_cd and not entry:raise Error('Completa '+cd.get('conditionDescriptorName','condizioni'))
        if entry:
            vals=cd.get('conditionDescriptorValues',[])
            if vals and (not entry.get('values') or any(str(v) not in [str(x['conditionDescriptorValueId']) for x in vals] for v in entry['values'])):raise Error('Dettaglio condizioni non valido.')
    item={'availability':{'shipToLocationAvailability':{'quantity':1}},'condition':CONDITIONS[cid],'product':{'title':title,'description':html.escape(description).replace('\n','<br>'),'aspects':aspects,'imageUrls':photos}}
    if descriptors:item['conditionDescriptors']=descriptors
    policies={k:ident(data.get(k,'')) for k in ('fulfillmentPolicyId','paymentPolicyId','returnPolicyId')}
    offer={'sku':'CP-'+ident(data.get('draftId','')),'marketplaceId':MARKET,'format':'FIXED_PRICE','availableQuantity':1,'categoryId':ident(data.get('categoryId','')),'merchantLocationKey':ident(data.get('merchantLocationKey','')),'listingDescription':item['product']['description'],'listingDuration':'GTC','listingPolicies':policies,'pricingSummary':{'price':{'currency':'EUR','value':format(price,'.2f')}}}
    return item,offer

def get_offers(tok,sku):
    try:return api('/sell/inventory/v1/offer?sku='+urllib.parse.quote(sku)+'&marketplace_id=EBAY_IT&format=FIXED_PRICE',tok).get('offers',[])
    except Error as e:
        if e.code=='EBAY_404':return []
        raise

def published(offer):
    lid=offer.get('listing',{}).get('listingId')
    if offer.get('status')=='PUBLISHED' and lid:return {'published':True,'listingId':str(lid),'url':'https://www.ebay.it/itm/'+ident(lid)}
    return None

def snapshot(tok,sku,offer_id):
    i=api('/sell/inventory/v1/inventory_item/'+ident(sku),tok);o=api('/sell/inventory/v1/offer/'+ident(offer_id),tok)
    # Ignore counters/creation dates; bind user approval to all editable listing data.
    return {'item':{k:i.get(k) for k in ('product','condition','conditionDescriptors','availability')},'offer':{k:o.get(k) for k in ('sku','marketplaceId','format','availableQuantity','categoryId','merchantLocationKey','listingDescription','listingDuration','listingPolicies','pricingSummary')}}

def prepare(data,user,tok):
    category=str(data.get('categoryId',''));item,offer=build_payload(data,schema(category));sku=offer['sku']
    with LOCKS[int(challenge(user['user']+sku),16)%len(LOCKS)]:
        offers=get_offers(tok,sku)
        for o in offers:
            p=published(o)
            if p:return p
        if len(offers)>1:raise Error('Più offerte presenti per questa bozza. Controlla il tuo account eBay.')
        api('/sell/inventory/v1/inventory_item/'+sku,tok,'PUT',item)
        if offers:
            oid=offers[0]['offerId'];api('/sell/inventory/v1/offer/'+ident(oid),tok,'PUT',offer)
        else:oid=api('/sell/inventory/v1/offer',tok,'POST',offer)['offerId']
        fees=api('/sell/inventory/v1/offer/get_listing_fees',tok,'POST',{'offers':[{'offerId':oid}]})
        snap=snapshot(tok,sku,oid)
        approval=seal('approval',{'user':user['user'],'sku':sku,'offerId':oid,'digest':digest(snap)},900)
        return {'approval':approval,'offerId':oid,'fees':fees,'title':data['title'],'price':offer['pricingSummary']['price'],'published':False}

def publish(data,user,tok):
    if data.get('confirm') is not True:raise Error('Conferma prima la pubblicazione.')
    a=unseal('approval',data.get('approval',''))
    if a['user']!=user['user']:raise Error('Account diverso da quello che ha preparato la bozza.',403)
    with LOCKS[int(challenge(user['user']+a['sku']),16)%len(LOCKS)]:
        o=api('/sell/inventory/v1/offer/'+ident(a['offerId']),tok)
        p=published(o)
        if p:return p
        if not hmac.compare_digest(digest(snapshot(tok,a['sku'],a['offerId'])),a['digest']):raise Error('La bozza eBay è cambiata. Ripeti il controllo prima di pubblicare.',409)
        r=api('/sell/inventory/v1/offer/'+ident(a['offerId'])+'/publish',tok,'POST',{})
        lid=ident(r['listingId']);return {'published':True,'listingId':lid,'url':'https://www.ebay.it/itm/'+lid}

def upload_image(data,tok):
    try:b=base64.b64decode(data.get('image',''),validate=True)
    except Exception:raise Error('Foto non valida.')
    if not 100<len(b)<=6000000 or not b.startswith(b'\xff\xd8\xff'):raise Error('La foto deve essere JPEG e inferiore a 6 MB.')
    boundary='cp'+secrets.token_hex(16)
    body=('--'+boundary+'\r\nContent-Disposition: form-data; name="image"; filename="card.jpg"\r\nContent-Type: image/jpeg\r\n\r\n').encode()+b+('\r\n--'+boundary+'--\r\n').encode()
    r=http(API+'/commerce/media/v1_beta/image/create_image_from_file','POST',body,{'Authorization':'Bearer '+tok,'Content-Type':'multipart/form-data; boundary='+boundary})
    if not r.get('imageUrl'):raise Error('eBay non ha restituito l’indirizzo della foto.',502)
    return {'url':r['imageUrl']}

def route(path,data,session):
    if path=='/v1/oauth/start':return oauth_start(data)
    if path=='/v1/oauth/complete':return oauth_complete(data)
    user,tok=access(session)
    if path=='/v1/account':
        result={'user':user['user']}
        for kind in ('fulfillment','payment','return'):result[kind+'Policies']=api('/sell/account/v1/'+kind+'_policy?marketplace_id=EBAY_IT',tok).get(kind+'Policies',[])
        result['locations']=api('/sell/inventory/v1/location?limit=100',tok).get('locations',[])
        return result
    if path=='/v1/schema':return schema(data.get('categoryId','183454'))
    if path=='/v1/location':
        city=str(data.get('city','')).strip();postal=str(data.get('postalCode','')).strip()
        if not city or not re.fullmatch('[0-9]{5}',postal):raise Error('Inserisci città e CAP italiano validi.')
        key='cp-'+challenge(city+postal)[:16]
        api('/sell/inventory/v1/location/'+key,tok,'POST',{'name':city,'merchantLocationStatus':'ENABLED','locationTypes':['WAREHOUSE'],'location':{'address':{'city':city,'postalCode':postal,'country':'IT'}}})
        return {'merchantLocationKey':key}
    if path=='/v1/image':return upload_image(data,tok)
    if path=='/v1/prepare':return prepare(data,user,tok)
    if path=='/v1/publish':return publish(data,user,tok)
    raise Error('Operazione non disponibile.',404)

class Handler(BaseHTTPRequestHandler):
    def log_message(self,*args): pass  # OAuth query strings and tokens must never reach logs.
    def output(self,status,data,ctype='application/json'):
        raw=(json.dumps(data,ensure_ascii=False) if ctype=='application/json' else data).encode()
        self.send_response(status);self.send_header('Content-Type',ctype+'; charset=utf-8');self.send_header('Content-Length',str(len(raw)));self.send_header('Cache-Control','no-store');self.send_header('Referrer-Policy','no-referrer');self.send_header('X-Content-Type-Options','nosniff');self.send_header('Content-Security-Policy',"default-src 'none'; style-src 'unsafe-inline'; frame-ancestors 'none'");self.end_headers();self.wfile.write(raw)
    def do_GET(self):
        u=urllib.parse.urlsplit(self.path)
        try:
            if u.path=='/health':return self.output(200,{'ok':True,'version':VERSION})
            if u.path=='/v1/config':return self.output(200,{'version':VERSION,'ready':all(os.environ.get(x) for x in ('SESSION_KEY','EBAY_CLIENT_ID','EBAY_CLIENT_SECRET','EBAY_RUNAME')),'message':'La configurazione del collegamento eBay deve essere completata dal gestore di CartaPronta.'})
            if u.path=='/oauth/callback':
                url=oauth_callback(dict(urllib.parse.parse_qsl(u.query)))
                return self.output(200,'<!doctype html><meta name="viewport" content="width=device-width"><title>CartaPronta</title><h1>Autorizzazione eBay ricevuta</h1><p><a href="'+html.escape(url,quote=True)+'">Torna a CartaPronta</a></p><p>Se non si apre, torna all’app e ripeti il collegamento.</p>','text/html')
            if u.path=='/privacy':return self.output(200,'<!doctype html><meta name="viewport" content="width=device-width"><h1>CartaPronta: collegamento eBay di prova</h1><p>Il servizio riceve l’autorizzazione eBay e trasmette foto e dati della bozza a eBay quando richiesto dall’utente. Le password vengono inserite esclusivamente su eBay. Foto e bozze non sono conservate dal server; la sessione cifrata è conservata sul telefono. Il servizio è ospitato su Render. Gli annunci pubblicati restano su eBay e vanno gestiti da eBay. Per interrompere l’accesso, scollega il telefono e revoca CartaPronta nelle autorizzazioni eBay. Questo servizio è in prova e non è ancora distribuito al pubblico.</p>','text/html')
            raise Error('Pagina non trovata.',404)
        except Error as e:self.output(e.status,{'message':e.message,'code':e.code})
        except Exception:self.output(500,{'message':'Errore del servizio. Riprova più tardi.'})
    def do_POST(self):
        try:
            self.connection.settimeout(90)
            n=int(self.headers.get('Content-Length','0'))
            if n<2 or n>8500000:raise Error('Richiesta troppo grande o vuota.',413)
            if self.headers.get('Content-Type','').split(';')[0]!='application/json':raise Error('Formato non valido.',415)
            data=json.loads(self.rfile.read(n));session=self.headers.get('Authorization','').removeprefix('Bearer ')
            self.output(200,route(urllib.parse.urlsplit(self.path).path,data,session))
        except Error as e:self.output(e.status,{'message':e.message,'code':e.code})
        except (ValueError,KeyError,TypeError):self.output(400,{'message':'Richiesta incompleta o non valida.'})
        except Exception:self.output(500,{'message':'Errore del servizio. Verifica lo stato dell’annuncio prima di riprovare.'})

if __name__=='__main__':
    required('SESSION_KEY');ThreadingHTTPServer(('0.0.0.0',int(os.environ.get('PORT','10000'))),Handler).serve_forever()
