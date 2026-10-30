#!/usr/bin/env python3
"""Measure existing physical-device PNGs; never renders or changes pixels."""
import argparse,hashlib,json,statistics
from pathlib import Path
from PIL import Image,ImageChops,ImageStat
from verify_exports import srgb_lab,delta_e_2000
p=argparse.ArgumentParser(description=__doc__);p.add_argument('--input',type=Path,required=True);p.add_argument('--output',type=Path,required=True);a=p.parse_args()
root=Path(__file__).resolve().parents[1];manifest=json.loads((root/'tests/fixtures/redmi-k90-pro-max-manifest.json').read_text())
source=Image.open(root/'tests/fixtures/redmi-k90-pro-max-1200x2608.png').convert('RGB');output=Image.open(a.input/'R01-front.png').convert('RGB')
def red_bounds(im):
 pix=im.load();points=[(x,y) for y in range(im.height) for x in range(im.width) if pix[x,y][0]>245 and pix[x,y][1]<8 and pix[x,y][2]<8]
 if not points:raise ValueError('Missing saturated red calibration card')
 xs,ys=zip(*points);return [min(xs),min(ys),max(xs),max(ys)]
sb=red_bounds(source);db=red_bounds(output);sx=(db[2]-db[0])/(sb[2]-sb[0]);sy=(db[3]-db[1])/(sb[3]-sb[1]);samples=[]
for sample in manifest['diagnosticColorPatches']:
 x,y=sample['center'];dx=round(db[0]+(x-sb[0])*sx);dy=round(db[1]+(y-sb[1])*sy)
 crop=output.crop((dx-2,dy-2,dx+3,dy+3));pixels=list(crop.getdata());rgb=[statistics.mean(t[c] for t in pixels) for c in range(3)]
 samples.append({'sourceCenter':[x,y],'outputCenter':[dx,dy],'radius':2,'expectedRgb':sample['rgb'],'observedRgb':rgb,'deltaE2000':delta_e_2000(srgb_lab(sample['rgb']),srgb_lab(rgb))})
report={'scope':'Numerical SDR sRGB PNG readback, not physical display colorimetry. Front orthographic reflection=0, independent per-axis red-card bounds calibration; internal 5x5 means.','input':str(a.input),'apkSha256':json.loads((a.input/'run.json').read_text())['installedApkSha256'],'sourceRedBounds':sb,'outputRedBounds':db,'scale':[sx,sy],'samples':samples,'meanDeltaE2000':statistics.mean(s['deltaE2000'] for s in samples),'maxDeltaE2000':max(s['deltaE2000'] for s in samples)}
reference=Image.open(a.input/'R07-zero.png').convert('RGB');reflection={}
for name in ['default','strong']:
 im=Image.open(a.input/f'R07-{name}.png').convert('RGB');difference=ImageChops.difference(reference,im);means=ImageStat.Stat(difference).mean
 reflection[name]={'meanAbsoluteRgbDifference8bit':sum(means)/3,'perChannel':means,'differenceBounds':difference.getbbox(),'sha256':hashlib.sha256((a.input/f'R07-{name}.png').read_bytes()).hexdigest()}
report['reflectionSamePose']=reflection
assert report['meanDeltaE2000']<=3,'Color target failed'
assert reflection['strong']['meanAbsoluteRgbDifference8bit']>reflection['default']['meanAbsoluteRgbDifference8bit']>0,'Reflection response failed'
a.output.parent.mkdir(parents=True,exist_ok=True);a.output.write_text(json.dumps(report,indent=2)+'\n');print(json.dumps({k:v for k,v in report.items() if k not in ['samples','scope']},indent=2))
