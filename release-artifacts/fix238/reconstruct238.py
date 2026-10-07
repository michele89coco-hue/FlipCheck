from pathlib import Path
import json,zipfile,hashlib,sys
base=Path(sys.argv[1]);patch=Path(sys.argv[2]);output=Path(sys.argv[3]);sha=lambda p:hashlib.sha256(p.read_bytes()).hexdigest()
with zipfile.ZipFile(patch) as z:manifest=json.loads(z.read('manifest.json'));new=z.read('patch.bin')
assert manifest['format']=='flipcheck-exact-reconstruction-v1' and sha(base)==manifest['base_sha256']
with base.open('rb') as source,output.open('wb') as destination:
 for seg in manifest['segments']:
  if 'base_offset' in seg:
   source.seek(seg['base_offset']);data=source.read(seg['length']);assert len(data)==seg['length']
  else:data=new[seg['patch_offset']:seg['patch_offset']+seg['length']];assert len(data)==seg['length']
  destination.write(data)
assert output.stat().st_size==manifest['final_bytes'] and sha(output)==manifest['final_sha256']
with zipfile.ZipFile(output) as z:assert z.testzip() is None
print('Exact signed APK reconstructed and SHA-256 verified:',manifest['final_sha256'])
