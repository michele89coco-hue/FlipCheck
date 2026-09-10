"""SearchApi image-only adapter. Credentials and ephemeral originals stay on this server."""
import base64
import hashlib
import hmac
import json
import math
import os
import secrets
import threading
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import urlencode, urlsplit
import urllib.request
import urllib.error
import socket
from server import MAX_IMAGE, ServiceError, image_kind

ENDPOINT = 'https://www.searchapi.io/api/v1/'

def safe_url(value):
    try:
        u = urlsplit(str(value or ''))
        if u.scheme != 'https' or not u.hostname or u.username or u.password or u.port not in (None, 443): return ''
        if any(word in u.query.lower() for word in ('api_key=', 'token=', 'signature=', 'credential=')): return ''
        return u._replace(fragment='').geturl()
    except ValueError: return ''

def candidates(data):
    result, seen = [], set()
    for group in ('visual_matches', 'exact_matches'):
        for item in data.get(group) or []:
            if not isinstance(item, dict): continue
            link = safe_url(item.get('link'))
            title = str(item.get('title') or '')[:500]
            key = (link, title)
            if not link or not title or key in seen: continue
            seen.add(key)
            im = item.get('image') or {}
            result.append({'id': 'lens-' + str(len(result)+1), 'position': item.get('position'),
                'title': title, 'url': link, 'source': str(item.get('source') or '')[:200],
                'snippet': str(item.get('snippet') or '')[:1000],
                'image_url': safe_url(im.get('link') if isinstance(im, dict) else im),
                'thumbnail': safe_url(item.get('thumbnail')), 'origin': 'searchapi_google_lens',
                'match_group': group, 'identity_verified': False})
            if len(result) == 60: return result
    return result

class Images:
    """Bounded RAM-only images; opaque URL, hard TTL and deletion after provider returns."""
    def __init__(self, origin, ttl=90, clock=time.monotonic):
        self.origin = origin.rstrip('/'); self.ttl = min(120, max(1, ttl)); self.clock = clock
        self.lock = threading.Lock(); self.items = {}
    def put(self, raw):
        with self.lock:
            self._purge()
            if len(self.items) >= 12: raise ServiceError('capacity_exhausted')
            token = secrets.token_urlsafe(32)
            self.items[token] = (self.clock()+self.ttl, raw)
        timer = threading.Timer(self.ttl, self.delete, (token,)); timer.daemon = True; timer.start()
        return token, self.origin+'/v1/lens-image/'+token
    def _purge(self):
        now = self.clock()
        for key in list(self.items):
            if self.items[key][0] <= now: del self.items[key]
    def get(self, token):
        with self.lock:
            self._purge()
            return self.items.get(token, (None, None))[1]
    def delete(self, token):
        with self.lock: self.items.pop(token, None)

class SearchApi:
    def __init__(self, key, transport=None): self.key = key; self.transport = transport
    def request(self, route, params, timeout):
        if self.transport: return self.transport(route, params, timeout)
        # Verified experiment contract: GET search, bearer header, no q and no retries.
        class NoRedirect(urllib.request.HTTPRedirectHandler):
            def redirect_request(self, req, fp, code, msg, headers, newurl): return None
        request=urllib.request.Request(ENDPOINT+route+('?' + urlencode(params) if params else ''),
            headers={'Authorization':'Bearer '+self.key,'Accept':'application/json'})
        opener=urllib.request.build_opener(NoRedirect())
        try:
            with opener.open(request,timeout=timeout) as response:
                raw=response.read(5000001)
                if len(raw)>5000000: raise ServiceError('response_too_large')
                data=json.loads(raw)
                if not isinstance(data,dict): raise ServiceError('invalid_response')
                return data
        except urllib.error.HTTPError as error:
            if error.code in (402,429): raise ServiceError('quota_exhausted')
            if error.code in (401,403): raise ServiceError('authentication_failed')
            raise ServiceError('provider_unavailable')
        except urllib.error.URLError as error:
            if isinstance(error.reason,(TimeoutError,socket.timeout)): raise TimeoutError()
            raise ServiceError('provider_unavailable')

class LensService:
    def __init__(self, provider, origin, token, unit_usd, timeout=25, images=None):
        self.provider=provider; self.token=token; self.unit_usd=unit_usd; self.timeout=min(30,timeout)
        self.images=images or Images(origin); self.lock=threading.Lock(); self.scans={}; self.started=[]
        self.account_lock=threading.Lock(); self.account={}; self.account_at=0
        self.available=bool(provider.key and token and safe_url(origin) and urlsplit(origin).path in ('','/')
            and isinstance(unit_usd,(float,int)) and math.isfinite(unit_usd) and unit_usd>=0)
    def config(self):
        return {'protocol':2,'provider':'searchapi_google_lens','enabled':self.available,
            'state':'available' if self.available else 'not_configured','unitUsd':self.unit_usd,
            'costBasis':'configured_per_search_reservation','maxImages':1,'maxCandidates':60,
            'timeoutMs':int(self.timeout*1000),'imageTtlSeconds':self.images.ttl}
    def credits(self):
        with self.account_lock:
            if not self.account_at or time.monotonic()-self.account_at > 300:
                data=self.provider.request('me',{},5).get('account') or {}
                self.account={k:data[k] for k in ('remaining_credits','monthly_allowance','current_month_usage')
                    if isinstance(data.get(k),(float,int)) and math.isfinite(data[k])}
                self.account_at=time.monotonic()
            left=self.account.get('remaining_credits')
            if left is not None and left<1: raise ServiceError('quota_exhausted')
            snapshot=dict(self.account)
            if left is not None: self.account['remaining_credits']=max(0,left-1)
            return snapshot
    def run(self, req):
        base={'provider':'searchapi_google_lens','engine':'google_lens','mode':'image_only_no_text_hint',
            'providerCalls':0,'accountCalls':0,'estimatedUsd':0,'billingUnknown':False,'candidates':[]}
        if not self.available: return {**base,'state':'not_configured'}
        scan=str(req.get('scan_id',''))
        import re
        if not re.fullmatch(r'[a-zA-Z0-9_-]{8,100}',scan): raise ServiceError('invalid_scan_id')
        encoded=req.get('image_base64','')
        if not isinstance(encoded,str) or len(encoded)>MAX_IMAGE*4//3+4: raise ServiceError('invalid_image')
        try: raw=base64.b64decode(encoded,validate=True)
        except Exception: raise ServiceError('invalid_image')
        if len(raw)>MAX_IMAGE or not image_kind(raw): raise ServiceError('invalid_image')
        remaining=req.get('remaining_usd')
        if not isinstance(remaining,(int,float)) or not math.isfinite(remaining) or remaining<self.unit_usd:
            return {**base,'state':'budget_exhausted'}
        digest=hashlib.sha256(raw).hexdigest(); start=time.monotonic()
        with self.lock:
            self.scans={k:v for k,v in self.scans.items() if start-v['at']<600 or not v['done'].is_set()}
            cached=self.scans.get(scan); owner=cached is None
            if cached and cached['digest']!=digest: raise ServiceError('scan_image_changed')
            if owner:
                self.started=[t for t in self.started if start-t<60]
                if len(self.started)>=10 or len(self.scans)>=500: return {**base,'state':'quota_exhausted'}
                cached={'at':start,'digest':digest,'done':threading.Event(),'result':None}
                self.scans[scan]=cached; self.started.append(start)
        if not owner:
            if not cached['done'].wait(self.timeout+8): return {**base,'state':'timeout','billingUnknown':True}
            return {**cached['result'],'cacheHit':True,'providerCalls':0,'accountCalls':0,'estimatedUsd':0}
        result=dict(base); token=None
        try:
            result['accountCalls']=int(not self.account_at or time.monotonic()-self.account_at>300)
            result['account']=self.credits(); result['accountSnapshotAgeMs']=round((time.monotonic()-self.account_at)*1000)
            token,url=self.images.put(raw)
            result.update(providerCalls=1,estimatedUsd=self.unit_usd,billingUnknown=True)
            data=self.provider.request('search',{'engine':'google_lens','url':url,'search_type':'all','country':'it','hl':'en'},self.timeout)
            if data.get('error'): raise ServiceError('provider_error')
            matches=candidates(data)
            result.update(state='ok' if matches else 'empty_results',candidates=matches,billingUnknown=False,
                candidateCount=len(matches),returnedVisualCount=len(data.get('visual_matches') or []),
                returnedExactCount=len(data.get('exact_matches') or []),creditsReserved=1,
                costBasis='configured_per_search_estimate',rankingMeaning='retrieval_only_not_identity')
        except ServiceError as e: result['state']=e.state
        except (TimeoutError,socket.timeout): result['state']='timeout'
        except Exception: result['state']='provider_unavailable'
        finally:
            if token: self.images.delete(token)
            result.update(imageDeleted=token is not None,imageTtlSeconds=self.images.ttl,
                latencyMs=round((time.monotonic()-start)*1000),cacheHit=False)
            # Neither credentials, request URL nor original image enter the report or dedup cache.
            cached['result']=result; cached['done'].set()
        return result

def handler(service):
    class Handler(BaseHTTPRequestHandler):
        def log_message(self,*args): pass
        def authorized(self): return bool(service.token) and hmac.compare_digest(self.headers.get('Authorization',''),'Bearer '+service.token)
        def send(self,status,data,mime='application/json'):
            if not isinstance(data,bytes): data=json.dumps(data).encode()
            self.send_response(status); self.send_header('Content-Type',mime)
            self.send_header('Cache-Control','no-store, max-age=0'); self.send_header('X-Robots-Tag','noindex, noarchive')
            self.send_header('Content-Length',str(len(data))); self.end_headers(); self.wfile.write(data)
        def do_GET(self):
            if self.path.startswith('/v1/lens-image/'):
                raw=service.images.get(self.path.removeprefix('/v1/lens-image/'))
                return self.send(200,raw,image_kind(raw)) if raw else self.send(404,{'state':'expired_or_deleted'})
            if not self.authorized(): return self.send(401,{'state':'unauthorized'})
            return self.send(200,service.config()) if self.path=='/v1/lens/config' else self.send(404,{'state':'not_found'})
        def do_POST(self):
            if not self.authorized(): return self.send(401,{'state':'unauthorized'})
            if self.path!='/v1/lens/search': return self.send(404,{'state':'not_found'})
            try:
                size=int(self.headers.get('Content-Length','0'))
                if not 0<size<MAX_IMAGE*4//3+10000: return self.send(413,{'state':'invalid_image'})
                self.connection.settimeout(15)
                return self.send(200,service.run(json.loads(self.rfile.read(size))))
            except ServiceError as e: return self.send(400,{'state':e.state,'providerCalls':0})
            except Exception: return self.send(400,{'state':'invalid_request','providerCalls':0})
    return Handler

if __name__=='__main__':
    price=os.getenv('SEARCHAPI_UNIT_USD','')
    service=LensService(SearchApi(os.getenv('SEARCHAPI_API_KEY','')),os.getenv('PUBLIC_ORIGIN',''),
        os.getenv('FLIPCHECK_ACCESS_TOKEN',''),float(price) if price else None)
    httpd=ThreadingHTTPServer((os.getenv('BIND_ADDRESS','127.0.0.1'),int(os.getenv('PORT','8080'))),handler(service))
    # Operational readiness only: no credentials, image URLs, account queries or Lens calls.
    print(json.dumps({'event':'lens_startup','configured':service.available,
        'provider':'searchapi_google_lens','unitUsd':service.unit_usd,'providerCalls':0}),flush=True)
    httpd.serve_forever()
