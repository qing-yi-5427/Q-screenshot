#!/usr/bin/env python3
"""Fetch a small explicit allowlist of public Xiaomi references, never package them."""
from pathlib import Path
import urllib.request,json,hashlib,datetime,re,argparse
ap=argparse.ArgumentParser(description=__doc__);ap.add_argument("--device",default="xiaomi-18-fold");args=ap.parse_args()
ROOT=Path(__file__).resolve().parents[1];cache=ROOT/'.cache/references';cache.mkdir(parents=True,exist_ok=True)
refs=[('target-specs','https://www.mi.com/prod/xiaomi-18-fold/specs','target-specs.html','official_page'),('mix-specs','https://www.mi.com/prod/xiaomi-mix-fold-4/specs','mix-specs.html','official_page'),('target-nav-render','https://cdn.cnbj1.fds.api.mi-img.com/mi-mall/4c88499fe66e8661b0fa9c1c629757f0.png','target-nav.png','official_product_render'),('mix-investor','https://ir.mi.com/system/files-encrypted/nasdaq_kms/assets/2024/08/21/6-09-16/Xiaomi%20Corp_24Q2_ER_ENG_v22_upload.pdf','mix-investor.pdf','official_presentation')]
items=[]
for id,url,name,kind in refs:
 req=urllib.request.Request(url,headers={'User-Agent':'Mozilla/5.0','Referer':'https://www.mi.com/'})
 try:
  with urllib.request.urlopen(req,timeout=40) as res:data=res.read();mime=res.headers.get('Content-Type')
  (cache/name).write_bytes(data)
  items.append(dict(sourceId=id,publisher='Xiaomi',pageUrl=url,assetUrl=url,retrievedAt=datetime.datetime.now(datetime.timezone.utc).isoformat(),localPath=str((cache/name).relative_to(ROOT)),sha256=hashlib.sha256(data).hexdigest(),mime=mime,sourceType=kind,verifiedProductIdentity=False,identityEvidence='Official page product metadata / navigation association only; insufficient body content',usage='local_visual_reference_only',redistributionPermission='not_verified',notes='Navigation thumbnail is not sufficient for measured dimensional claims.' if id=='target-nav-render' else 'Page content may contain dynamic shell; never infer dimensions from search excerpts.'))
 except Exception as e:items.append({'sourceId':id,'pageUrl':url,'error':str(e)})
ledger=ROOT/'assets-source/devices/xiaomi-18-fold/sources.json'
previous=json.loads(ledger.read_text()) if ledger.exists() else []
by_id={x['sourceId']:x for x in previous}
for item in items:
 old=by_id.get(item['sourceId'],{})
 # A fetch refreshes bytes, not the human/agent's verified identity judgment.
 if item.get('sha256') and item.get('sha256')==old.get('sha256'):
  for field in ['verifiedProductIdentity','identityEvidence','notes','view']:
   if field in old:item[field]=old[field]
 elif old.get('verifiedProductIdentity'):
  item['notes']='Content changed; previous identity review must be repeated.'
 by_id[item['sourceId']]=item
ledger.write_text(json.dumps(list(by_id.values()),ensure_ascii=False,indent=2))
print(json.dumps(items,ensure_ascii=False,indent=2))
