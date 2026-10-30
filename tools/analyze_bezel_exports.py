#!/usr/bin/env python3
"""Measure existing Android exports; creates no substitute 3D output."""
from pathlib import Path
from PIL import Image,ImageOps,ImageDraw
import json,math,hashlib,statistics
from verify_exports import srgb_lab,delta_e_2000
R=Path(__file__).resolve().parents[1];D=R/'deliverables/bezel-rotation-fix';G=json.loads((R/'assets-source/devices/xiaomi-18-fold/geometry.json').read_text());M=json.loads((R/'tests/fixtures/manifest.json').read_text())
def darkruns(line):
 runs=[];start=None
 for i,v in enumerate(line+[(255,255,255)]):
  if max(v)<25 and start is None:start=i
  if max(v)>=25 and start is not None:
   if i-start>2:runs.append([start,i-1,i-start])
   start=None
 return runs
res={'apkSha256':hashlib.sha256((D/'apk/qing-frame-bezel-rotation-debug.apk').read_bytes()).hexdigest(),'method':'Actual orthographic PNG. Straight center rows/35% column; optical black maxRGB<25; excludes gray metal/hinge. One pixel boundary uncertainty. Corner rays use measured content edges and physical radii; thresholds/AA remain raster estimates.','cases':[]}
for case,state in [('X01-front','folded'),('X03-open','unfolded')]:
 im=Image.open(D/'narrow-android'/f'{case}.png').convert('RGB');w,h=im.size
 hr=darkruns([im.getpixel((x,h//2)) for x in range(w)]);vr=darkruns([im.getpixel((int(w*.35),y)) for y in range(h)]);assert len(hr)==len(vr)==2
 widths=[hr[0][2],hr[1][2],vr[0][2],vr[1][2]];assert max(widths)-min(widths)<=1
 left,right=hr[0][1]+.5,hr[1][0]-.5;top,bottom=vr[0][1]+.5,vr[1][0]-.5;g=G[state];sx=(right-left)/g['screen_width_mm'];sy=(bottom-top)/g['screen_height_mm'];corners=[]
 # Radius ordering TL,TR,BR,BL; screen node offset is already present in measured image edges.
 for label,r,xx,yy,sgx,sgy in zip(['TL','TR','BR','BL'],g['screen_corner_radii_mm'],[left,right,right,left],[top,top,bottom,bottom],[-1,1,1,-1],[-1,-1,1,1]):
  cx=xx-sgx*r*sx;cy=yy-sgy*r*sy
  for angle in [15,30,45,60,75]:
   rad=math.radians(angle);samples=[]
   for j in range(300):
    rr=r-.3+j*.008;x=round(cx+sgx*rr*sx*math.cos(rad));y=round(cy+sgy*rr*sy*math.sin(rad));samples.append(max(im.getpixel((x,y)))<25)
   runs=darkruns([(0,0,0) if v else (255,255,255) for v in samples]);near=[v for v in runs if v[0]<100 and v[1]>20]
   if near:corners.append({'corner':label,'degrees':angle,'blackWidthMmRaster':round(near[0][2]*.008,3)})
 res['cases'].append({'case':case,'state':state,'straightPixelsLeftRightTopBottom':widths,'horizontalRuns':hr,'verticalRuns':vr,'pixelsPerMm':[sx,sy],'cornerRays':corners})
# Color sample positions calibrated to original saturated-red card bounds.
def red(im):
 p=im.load();pts=[(x,y) for y in range(im.height) for x in range(im.width) if p[x,y][0]>245 and p[x,y][1]<8 and p[x,y][2]<8];xs,ys=zip(*pts);return[min(xs),min(ys),max(xs),max(ys)]
res['color']=[]
for case,item,folder in zip(['X01-front','X03-open','X04-portrait'],M['images'],['narrow-android','narrow-android','portrait-android']):
 src=Image.open(R/item['file']).convert('RGB');im=Image.open(D/folder/f'{case}.png').convert('RGB');sb=red(src);db=red(im);sx=(db[2]-db[0])/(sb[2]-sb[0]);sy=(db[3]-db[1])/(sb[3]-sb[1]);errors=[]
 for c in item['colorPatches']:
  x,y=c['center'];px=round(db[0]+(x-sb[0])*sx);py=round(db[1]+(y-sb[1])*sy);pix=list(im.crop((px-2,py-2,px+3,py+3)).getdata());rgb=[statistics.mean(v[k] for v in pix) for k in range(3)];errors.append(delta_e_2000(srgb_lab(c['rgb']),srgb_lab(rgb)))
 assert max(errors)<3;res['color'].append({'case':case,'meanDeltaE2000':statistics.mean(errors),'maxDeltaE2000':max(errors)})
(D/'evidence/android-band-and-color.json').write_text(json.dumps(res,indent=2)+'\n')
# Same-camera/light source images, uniformly downscaled for a readable comparison. Originals preserved.
canvas=Image.new('RGB',(1200,850),'#f1f1ef');draw=ImageDraw.Draw(canvas)
for i,(folder,label) in enumerate([('before-android','0.4 BEFORE'),('narrow-android','0.5 AFTER / 0.85 mm')]):
 im=Image.open(D/folder/'X01-front.png').convert('RGB');im.thumbnail((580,790));canvas.paste(im,(i*600+(600-im.width)//2,40));draw.text((i*600+20,15),label,fill='black')
canvas.save(D/'before-after.png');print(json.dumps(res,indent=2))
