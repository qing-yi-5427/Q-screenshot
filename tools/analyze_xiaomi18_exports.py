#!/usr/bin/env python3
"""Inspect existing actual Fold PNGs. Never generates substitute renderings."""
from pathlib import Path
from PIL import Image
import hashlib,json,statistics
from verify_exports import srgb_lab,delta_e_2000
ROOT=Path(__file__).resolve().parents[1];D=ROOT/'deliverables/xiaomi18-refresh';manifest=json.loads((ROOT/'tests/fixtures/manifest.json').read_text())
def red(im):
 p=im.load();pts=[(x,y) for y in range(im.height) for x in range(im.width) if p[x,y][0]>245 and p[x,y][1]<8 and p[x,y][2]<8]
 xs,ys=zip(*pts);return [min(xs),min(ys),max(xs),max(ys)]
r={'apkSha256':hashlib.sha256((D/'apk/qing-frame-xiaomi18-debug.apk').read_bytes()).hexdigest(),'scope':'SDR sRGB PNG 12-card internal 5x5 means, affine calibrated saturated red card bounds; not physical-display colorimetry.','cases':[]}
for case,item in zip(['X01-front','X03-open','X04-portrait'],manifest['images']):
 src=Image.open(ROOT/item['file']).convert('RGB');out=Image.open(D/'renders'/f'{case}.png').convert('RGB');sb=red(src);db=red(out);sx=(db[2]-db[0])/(sb[2]-sb[0]);sy=(db[3]-db[1])/(sb[3]-sb[1]);samples=[]
 for card in item['colorPatches']:
  x,y=card['center'];px=round(db[0]+(x-sb[0])*sx);py=round(db[1]+(y-sb[1])*sy);data=list(out.crop((px-2,py-2,px+3,py+3)).getdata());observed=[statistics.mean(v[c] for v in data) for c in range(3)];samples.append({'sourceCenter':[x,y],'outputCenter':[px,py],'expectedRgb':card['rgb'],'observedRgb':observed,'deltaE2000':delta_e_2000(srgb_lab(card['rgb']),srgb_lab(observed))})
 e=statistics.mean(s['deltaE2000'] for s in samples);assert e<3;r['cases'].append({'case':case,'sourceRedBounds':sb,'outputRedBounds':db,'meanDeltaE2000':e,'maxDeltaE2000':max(s['deltaE2000'] for s in samples),'samples':samples})
def roi(path):
 im=Image.open(path).convert('RGB');p=list(im.crop((518,248,523,253)).getdata());return [statistics.mean(v[c] for v in p) for c in range(3)]
r['spacingAB']={'case':'X09-hero-open','roi':[518,248,523,253],'beforeMeanRgb':roi(D/'low/X09-hero-open.png'),'afterMeanRgb':roi(D/'spacing-low/X09-hero-open.png'),'changed':'Only active screen + punch POSITION.Z moved 0.20mm. Shader, lights, exposure, UV and MSAA unchanged.'}
(D/'evidence/color-and-spacing.json').write_text(json.dumps(r,indent=2)+'\n');print([(c['case'],c['meanDeltaE2000'],c['maxDeltaE2000']) for c in r['cases']]);print(r['spacingAB'])
