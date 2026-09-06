"""Optional authenticated Web Detection adapter. Never stores uploaded image bytes."""
from __future__ import annotations
import base64, hashlib, hmac, http.client, ipaddress, json, os, re, socket, ssl, threading, time
from dataclasses import dataclass
from html.parser import HTMLParser
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import urlsplit, urljoin

REVISION = 'visual-api-1-build164'
GOOGLE_ENDPOINT = 'https://vision.googleapis.com/v1/images:annotate'
MAX_IMAGE = 7 * 1024 * 1024

class ServiceError(Exception):
    def __init__(self, state): self.state = state; super().__init__(state)

def clean_url(value):
    try:
        u = urlsplit(value)
        if u.scheme != 'https' or not u.hostname or u.username or u.password or u.port not in (None, 443): return ''
        if u.query: return ''  # Do not export signed URLs or temporary access parameters.
        return u._replace(fragment='').geturl()
    except (ValueError, TypeError): return ''

def public_addresses(host):
    addresses = list(dict.fromkeys(x[4][0] for x in socket.getaddrinfo(host,443,type=socket.SOCK_STREAM)))
    if not addresses or any(not ipaddress.ip_address(x).is_global for x in addresses): raise ServiceError('reference_address_blocked')
    return addresses

class PinnedHTTPS(http.client.HTTPSConnection):
    def __init__(self, host, address, timeout): super().__init__(host,timeout=timeout,context=ssl.create_default_context()); self.address=address
    def connect(self):
        raw=socket.create_connection((self.address,443),self.timeout)
        self.sock=self._context.wrap_socket(raw,server_hostname=self.host)

def public_fetch(url, deadline, limit=400000, redirects=2):
    """DNS checked, connection pinned to a public IP, redirects checked separately."""
    target=clean_url(url)
    if not target: raise ServiceError('reference_url_blocked')
    u=urlsplit(target); remaining=deadline-time.monotonic()
    if remaining<=0: raise ServiceError('timeout')
    addresses=public_addresses(u.hostname)
    conn=PinnedHTTPS(u.hostname,addresses[0],min(5,remaining))
    try:
        conn.request('GET',u.path or '/',headers={'User-Agent':'FlipCheck/visual-reference','Accept-Encoding':'identity'})
        response=conn.getresponse()
        if response.status in (301,302,303,307,308):
            if redirects<=0: raise ServiceError('reference_redirect_limit')
            return public_fetch(urljoin(target,response.getheader('Location','')),deadline,limit,redirects-1)
        if response.status!=200: raise ServiceError('reference_unavailable')
        data=response.read(limit+1)
        if len(data)>limit: raise ServiceError('reference_too_large')
        return data,response.getheader('Content-Type','').split(';')[0],target
    finally: conn.close()

class PageText(HTMLParser):
    def __init__(self): super().__init__();self.ignored=0;self.parts=[]
    def handle_starttag(self,tag,attrs):
        if tag in ('script','style','noscript','svg'): self.ignored+=1
    def handle_endtag(self,tag):
        if tag in ('script','style','noscript','svg'): self.ignored=max(0,self.ignored-1)
    def handle_data(self,data):
        if not self.ignored and data.strip(): self.parts.append(re.sub(r'\s+',' ',data.strip()))

def page_text(data, clues):
    parser=PageText();parser.feed(data.decode('utf-8','replace'))
    terms=[str(x).casefold() for x in clues if len(str(x))>3]
    scored=sorted(enumerate(parser.parts),key=lambda p:(-sum(t in p[1].casefold() for t in terms),p[0]))
    picked=[];count=0
    for i,s in scored:
        if count+len(s)>5000: continue
        picked.append((i,s));count+=len(s)
    return '\n'.join(s for _,s in sorted(picked))[:5000]

def image_kind(data):
    if data.startswith(b'\x89PNG\r\n\x1a\n'): return 'image/png'
    if data.startswith(b'\xff\xd8\xff'): return 'image/jpeg'
    if data[:4]==b'RIFF' and data[8:12]==b'WEBP': return 'image/webp'
    return ''

def normalize_detection(payload):
    responses=payload.get('responses') or []
    if not responses: raise ServiceError('empty_response')
    if responses[0].get('error'): raise ServiceError('provider_image_error')
    raw=responses[0].get('webDetection') or {}
    def images(items):
        return [{'url':clean_url(i.get('url'))} for i in (items or [])[:10] if clean_url(i.get('url'))]
    return {
      'webEntities':[{'entityId':str(e.get('entityId',''))[:200],'description':str(e.get('description',''))[:300],'providerScore':e.get('score')} for e in (raw.get('webEntities') or [])[:10]],
      'fullMatchingImages':images(raw.get('fullMatchingImages')),'partialMatchingImages':images(raw.get('partialMatchingImages')),
      'visuallySimilarImages':images(raw.get('visuallySimilarImages')),
      'bestGuessLabels':[{'label':str(e.get('label',''))[:300],'languageCode':str(e.get('languageCode',''))[:30]} for e in (raw.get('bestGuessLabels') or [])[:5]],
      'pagesWithMatchingImages':[{'url':clean_url(p.get('url')),'pageTitle':re.sub('<[^>]+>','',str(p.get('pageTitle','')))[:300],
        'fullMatchingImages':images(p.get('fullMatchingImages')),'partialMatchingImages':images(p.get('partialMatchingImages'))} for p in (raw.get('pagesWithMatchingImages') or [])[:8] if clean_url(p.get('url'))],
      'scoreMeaning':'provider ranking only; not identity probability'}

class GoogleWebDetection:
    name='google_cloud_vision_web_detection'
    def __init__(self, credentials=None, transport=None): self.credentials=credentials;self.transport=transport
    @property
    def available(self): return self.credentials is not None or self.transport is not None
    def detect(self, image, timeout):
        body={'requests':[{'image':{'content':base64.b64encode(image).decode()},'features':[{'type':'WEB_DETECTION','maxResults':8}]}]}
        if self.transport: return normalize_detection(self.transport(GOOGLE_ENDPOINT,body,timeout))
        from google.auth.transport.requests import Request
        import requests
        deadline=time.monotonic()+timeout
        class BoundedRequest(Request):
            def __call__(self, *args, **kwargs):
                kwargs['timeout']=max(.1,min(5,deadline-time.monotonic()))
                return super().__call__(*args,**kwargs)
        if not self.credentials.valid: self.credentials.refresh(BoundedRequest(session=requests.Session()))
        timeout=deadline-time.monotonic()
        if timeout<=0: raise ServiceError('timeout')
        # Exactly one image annotation attempt. requests has no status retries here.
        headers={'Authorization':'Bearer '+self.credentials.token}
        if getattr(self.credentials,'quota_project_id',None): headers['x-goog-user-project']=self.credentials.quota_project_id
        response=requests.post(GOOGLE_ENDPOINT,json=body,headers=headers,timeout=timeout,allow_redirects=False)
        if response.status_code==429: raise ServiceError('quota_exhausted')
        if response.status_code!=200: raise ServiceError('provider_unavailable')
        return normalize_detection(response.json())

@dataclass
class Config:
    enabled: bool=False
    token: str=''
    unit_usd: float=.0035
    timeout: float=22
    max_per_minute: int=10
    allowed_origin: str='https://flipcheck.local'

class VisualService:
    def __init__(self, config, provider, fetcher=public_fetch):
        self.config=config;self.provider=provider;self.fetcher=fetcher
        self.lock=threading.Lock();self.scans={};self.started=[]
    def capabilities(self):
        available=self.config.enabled and self.provider.available and bool(self.config.token)
        return {'revision':REVISION,'protocol':1,'enabled':available,'state':'available' if available else 'not_configured',
          'provider':self.provider.name,'unitUsd':self.config.unit_usd,'maxImages':1,'maxReferences':3,'timeoutMs':int(self.config.timeout*1000)}
    def run(self, request):
        if not self.capabilities()['enabled']: return {'state':'not_configured','providerCalls':0,'references':[]}
        scan=str(request.get('scan_id',''))
        if not re.fullmatch(r'[a-zA-Z0-9_-]{8,100}',scan): raise ServiceError('invalid_scan_id')
        encoded=request.get('image_base64','')
        if not isinstance(encoded,str) or len(encoded)>MAX_IMAGE*4//3+4: raise ServiceError('invalid_image')
        try: image=base64.b64decode(encoded,validate=True)
        except Exception: raise ServiceError('invalid_image')
        if not image_kind(image) or len(image)>MAX_IMAGE: raise ServiceError('invalid_image')
        remaining=request.get('remaining_usd')
        if not isinstance(remaining,(float,int)) or remaining<self.config.unit_usd: return {'state':'budget_exhausted','providerCalls':0,'references':[]}
        digest=hashlib.sha256(image).hexdigest();now=time.monotonic()
        with self.lock:
            self.scans={k:v for k,v in self.scans.items() if now-v['at']<600 or not v['done'].is_set()}
            self.started=[t for t in self.started if now-t<60]
            cached=self.scans.get(scan)
            if cached and cached['digest']!=digest: raise ServiceError('scan_image_changed')
            owner=cached is None
            if owner:
                if len(self.started)>=self.config.max_per_minute or len(self.scans)>=500: return {'state':'quota_exhausted','providerCalls':0,'references':[]}
                cached={'at':now,'digest':digest,'done':threading.Event(),'result':None};self.scans[scan]=cached;self.started.append(now)
        if not owner:
            if not cached['done'].wait(self.config.timeout+2): return {'state':'timeout','providerCalls':0,'billingUnknown':True,'references':[]}
            return {**cached['result'],'cacheHit':True,'providerCalls':0}
        result={'state':'provider_unavailable','references':[],'providerCalls':0,'estimatedUsd':0,'billingUnknown':False}
        try:
            deadline=now+self.config.timeout
            result.update(providerCalls=1,estimatedUsd=self.config.unit_usd,billingUnknown=True)
            detection=self.provider.detect(image,max(1,deadline-time.monotonic()))
            del image  # No disk writes or user-image URLs.
            refs=[];visited=set();image_count=0
            for page in detection['pagesWithMatchingImages'][:3]:
                if time.monotonic()>=deadline: break
                try:
                    data,mime,final=self.fetcher(page['url'],deadline,400000)
                    if mime not in ('text/html','text/plain') or final in visited: continue
                    visited.add(final);text=page_text(data,request.get('clues',[])[:10])
                    ref={'id':'ref'+str(len(refs)+1),'url':final,'title':page['pageTitle'],'text':text,'image_url':'','image_data':''}
                    image_urls=page['fullMatchingImages']+page['partialMatchingImages']
                    for im in image_urls[:1]:
                        if image_count>=3: break
                        image_count+=1
                        try:
                            raw,_,imurl=self.fetcher(im['url'],deadline,5*1024*1024)
                            kind=image_kind(raw)
                            if kind: ref.update(image_url=imurl,image_data='data:'+kind+';base64,'+base64.b64encode(raw).decode())
                        except Exception: pass
                    refs.append(ref)
                except Exception: continue
            result.update(state='ok' if refs else 'no_references',detection=detection,references=refs,billingUnknown=False)
        except ServiceError as error: result['state']=error.state
        except (TimeoutError,socket.timeout): result['state']='timeout'
        except Exception: result['state']='provider_unavailable'
        finally:
            result.update(revision=REVISION,latencyMs=round((time.monotonic()-now)*1000),cacheHit=False)
            cached['result']=result;cached['done'].set()
        return result

def handler(service):
    class Handler(BaseHTTPRequestHandler):
        def log_message(self,*args): pass  # No tokens, photographs or temporary URLs in access logs.
        def send(self,status,body):
            data=json.dumps(body).encode();self.send_response(status)
            self.send_header('Content-Type','application/json');self.send_header('Cache-Control','no-store')
            self.send_header('Access-Control-Allow-Origin',service.config.allowed_origin)
            self.send_header('Vary','Origin');self.send_header('Content-Length',str(len(data)));self.end_headers();self.wfile.write(data)
        def do_OPTIONS(self):
            self.send_response(204);self.send_header('Access-Control-Allow-Origin',service.config.allowed_origin)
            self.send_header('Access-Control-Allow-Methods','GET, POST, OPTIONS');self.send_header('Access-Control-Allow-Headers','Authorization, Content-Type');self.end_headers()
        def authorized(self):
            expected='Bearer '+service.config.token
            return bool(service.config.token) and hmac.compare_digest(self.headers.get('Authorization',''),expected)
        def do_GET(self):
            if not self.authorized(): return self.send(401,{'state':'unauthorized'})
            self.send(200,service.capabilities()) if self.path=='/v1/config' else self.send(404,{'state':'not_found'})
        def do_POST(self):
            if not self.authorized(): return self.send(401,{'state':'unauthorized'})
            if self.path!='/v1/visual-search': return self.send(404,{'state':'not_found'})
            try:
                size=int(self.headers.get('Content-Length','0'))
                if not 0<size<MAX_IMAGE*4//3+10000: return self.send(413,{'state':'invalid_image'})
                self.connection.settimeout(30)
                req=json.loads(self.rfile.read(size));self.send(200,service.run(req))
            except ServiceError as e: self.send(400,{'state':e.state,'providerCalls':0})
            except Exception: self.send(400,{'state':'invalid_request','providerCalls':0})
    return Handler

def main():
    config=Config(enabled=os.getenv('VISUAL_ENABLED','false').lower()=='true',token=os.getenv('FLIPCHECK_ACCESS_TOKEN',''),
      unit_usd=float(os.getenv('GOOGLE_WEB_DETECTION_UNIT_USD','.0035')),timeout=min(30,float(os.getenv('VISUAL_TIMEOUT_SECONDS','22'))),
      max_per_minute=int(os.getenv('VISUAL_REQUESTS_PER_MINUTE','10')),allowed_origin=os.getenv('ALLOWED_ORIGIN','https://flipcheck.local'))
    credentials=None
    if config.enabled:
        try:
            import google.auth
            credentials,_=google.auth.default(scopes=['https://www.googleapis.com/auth/cloud-platform'])
        except Exception: pass
    service=VisualService(config,GoogleWebDetection(credentials))
    ThreadingHTTPServer((os.getenv('BIND_ADDRESS','127.0.0.1'),int(os.getenv('PORT','8080'))),handler(service)).serve_forever()
if __name__=='__main__': main()
