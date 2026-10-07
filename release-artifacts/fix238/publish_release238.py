from pathlib import Path
import os,json,urllib.request,urllib.parse,urllib.error,hashlib
repo=os.environ['GITHUB_REPOSITORY'];token=os.environ['GH_TOKEN'];root=Path('release-artifacts/fix238');tag='fix238-20261007-no-ai';api='https://api.github.com/repos/'+repo
headers={'Authorization':'Bearer '+token,'Accept':'application/vnd.github+json','X-GitHub-Api-Version':'2022-11-28'}
def request(url,method='GET',data=None,content_type=None):
 h=dict(headers)
 if data is not None:
  if isinstance(data,dict):data=json.dumps(data).encode();h['Content-Type']='application/json'
  else:h['Content-Type']=content_type or 'application/octet-stream'
 req=urllib.request.Request(url,data=data,method=method,headers=h)
 with urllib.request.urlopen(req,timeout=240) as response:return json.load(response)
try:release=request(api+'/releases/tags/'+tag)
except urllib.error.HTTPError as error:
 if error.code!=404:raise
 candidates=[r for r in request(api+'/releases?per_page=100') if r['tag_name']==tag]
 assert len(candidates)<=1
 if candidates:release=candidates[0]
 else:release=request(api+'/releases','POST',{'tag_name':tag,'name':'FlipCheck 238 — fix prezzi, stime AI dormienti','body':(root/'release238-notes.txt').read_text(),'target_commitish':os.environ['GITHUB_SHA'],'draft':True,'prerelease':True,'make_latest':'false'})
assert release['tag_name']==tag
release_id=str(release['id'])
expected=['FlipCheck238-fix-prezzi-no-AI.apk','FlipCheck238-sorgenti-e-verifiche.zip','FlipCheck238-verifiche.json','LEGGIMI-FlipCheck238.txt','SHA256SUMS.txt']
assets={a['name']:a for a in release['assets']};base=release['upload_url'].split('{',1)[0]
assert base.startswith('https://uploads.github.com/repos/'+repo+'/releases/')
for name in expected:
 data=(root/name).read_bytes();sha=hashlib.sha256(data).hexdigest()
 if name in assets:
  assert assets[name].get('digest')=='sha256:'+sha and assets[name]['size']==len(data)
 else:
  asset=request(base+'?'+urllib.parse.urlencode({'name':name}),'POST',data)
  assert asset['state']=='uploaded' and asset['size']==len(data) and asset.get('digest')=='sha256:'+sha
 print('Verified release asset:',name,sha,flush=True)
# Publish only after every exact asset is present.
release=request(api+'/releases/'+release_id,'PATCH',{'draft':False,'prerelease':True,'make_latest':'false','target_commitish':os.environ['GITHUB_SHA']})
assert not release['draft'];print('Published:',release['html_url'],flush=True)
