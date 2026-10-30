#!/usr/bin/env python3
"""Numerical color QA and contact sheet from actual final Android PNGs."""
from pathlib import Path
from PIL import Image,ImageDraw,ImageFont
import json,statistics,hashlib
from verify_exports import srgb_lab,delta_e_2000
R=Path(__file__).resolve().parents[1];D=R/'deliverables/colors-panels';out={'scope':'Actual final Android sRGB PNG numerical RGB samples; not calibrated physical-display colorimetry. Screens unlit, shell PBR only.','apkSha256':json.loads((D/'renders/xiaomi-18-fold/warm-gold-white/run.json').read_text())['apkSha256'],'colors':[]}
def red(im):
 p=im.load();pts=[(x,y) for y in range(im.height) for x in range(im.width) if p[x,y][0]>245 and p[x,y][1]<8 and p[x,y][2]<8];xs,ys=zip(*pts);return[min(xs),min(ys),max(xs),max(ys)]
canvas=Image.new('RGB',(1280,940),'#f6f7f3');draw=ImageDraw.Draw(canvas)
try:font=ImageFont.truetype('/System/Library/Fonts/STHeiti Light.ttc',20)
except OSError:font=ImageFont.load_default()
for row,(dev,case) in enumerate([('xiaomi-18-fold','X01-front'),('redmi-k90-pro-max','R01-front')]):
 j=json.loads((R/f'assets-source/devices/{dev}/colorways.json').read_text());m=json.loads((R/'tests/fixtures/manifest.json').read_text())['images'][0] if row==0 else json.loads((R/'tests/fixtures/redmi-k90-pro-max-manifest.json').read_text());src=Image.open(R/(m['file'] if row==0 else m['files'][0]['file'])).convert('RGB');sb=red(src)
 for col,c in enumerate(j['colorways']):
  file=D/f'renders/{dev}/{c["id"]}/{case}.png';im=Image.open(file).convert('RGB');db=red(im);sx=(db[2]-db[0])/(sb[2]-sb[0]);sy=(db[3]-db[1])/(sb[3]-sb[1]);errors=[]
  for patch in m.get('colorPatches',m.get('diagnosticColorPatches')):
   x,y=patch['center'];px=round(db[0]+(x-sb[0])*sx);py=round(db[1]+(y-sb[1])*sy);pixels=list(im.crop((px-2,py-2,px+3,py+3)).getdata());rgb=[statistics.mean(v[k] for v in pixels)for k in range(3)];errors.append(delta_e_2000(srgb_lab(patch['rgb']),srgb_lab(rgb)))
  assert max(errors)<3;out['colors'].append({'deviceId':dev,'colorwayId':c['id'],'title':c['officialNameZh'],'pngSha256':hashlib.sha256(file.read_bytes()).hexdigest(),'meanDeltaE2000':statistics.mean(errors),'maxDeltaE2000':max(errors)})
  im.thumbnail((310,420));canvas.paste(im,(col*320+(320-im.width)//2,row*470+35));draw.text((col*320+8,row*470+8),c['officialNameZh'],font=font,fill='#17281f')
canvas.save(D/'official-colors-android.png');(D/'evidence/color-regression.json').write_text(json.dumps(out,ensure_ascii=False,indent=2)+'\n');print([(c['deviceId'],c['colorwayId'],round(c['meanDeltaE2000'],4))for c in out['colors']])
