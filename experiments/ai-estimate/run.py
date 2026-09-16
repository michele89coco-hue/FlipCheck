import os,json,time,urllib.request,urllib.error
from pathlib import Path
key=os.environ.get('OPENAI_API_KEY','').strip()
if not key.startswith('sk-') or any(c.isspace() for c in key):
 print('TEST_NOT_RUN: OpenAI credential unavailable in this runner. API calls: 0.');raise SystemExit(0)
body=json.loads(Path('experiments/ai-estimate/request.json').read_text())
class NoRedirect(urllib.request.HTTPRedirectHandler):
 def redirect_request(self,*args,**kwargs):return None
opener=urllib.request.build_opener(NoRedirect)
req=urllib.request.Request('https://api.openai.com/v1/responses',data=json.dumps(body).encode(),headers={'Authorization':'Bearer '+key,'Content-Type':'application/json'},method='POST')
start=time.monotonic()
try:
 with opener.open(req,timeout=180) as r:answer=json.load(r)
except urllib.error.HTTPError as e:
 print('TEST_FAILED: HTTP',e.code,'; no retries.');raise SystemExit(1)
except Exception as e:
 print('TEST_FAILED:',type(e).__name__,'; no retries.');raise SystemExit(1)
calls=[o for o in answer.get('output',[]) if o.get('type')=='web_search_call']
print('RESULT_METADATA',json.dumps({'model':answer.get('model'),'status':answer.get('status'),'elapsed_seconds':round(time.monotonic()-start,1),'api_calls':1,'web_calls':len(calls),'usage':answer.get('usage'),'incomplete_details':answer.get('incomplete_details')}))
print('RESULT_JSON',json.dumps(answer,ensure_ascii=False))
if len(calls)!=1:raise SystemExit('Web call count differs from requested 1; no retry.')
