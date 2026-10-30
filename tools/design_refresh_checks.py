#!/usr/bin/env python3
"""Offline screenshot/UI-bound measurements for design-refresh evidence. No ADB."""
import argparse
from collections import deque
import hashlib
import json
from pathlib import Path
import re
import xml.etree.ElementTree as ET
from PIL import Image,ImageChops

ROOT=Path(__file__).resolve().parents[1]
def rectangle(value):
    parts=list(map(int,re.findall(r'-?\d+',value)))
    if len(parts)!=4:raise ValueError('Expected four rectangle coordinates')
    return parts

def extent(box):return (max(0,box[2]-box[0]),max(0,box[3]-box[1]))
def area(box):
    w,h=extent(box);return w*h

def dark_frame_bbox(image,viewport,threshold):
    """Select the dark connected component with largest bounding rectangle.

    The front frame encloses the display; connected-extent ranking avoids
    mistaking an illustration's larger dark pixel count for the phone frame.
    Light outer metal may lie outside this estimate: it is not CAD ground truth.
    """
    crop=image.crop(viewport).convert('RGB');channels=crop.split()
    maximum=ImageChops.lighter(ImageChops.lighter(channels[0],channels[1]),channels[2])
    values=maximum.point(lambda v:255 if v<threshold else 0).tobytes();w,h=crop.size
    visited=bytearray(w*h);best=None
    for seed,value in enumerate(values):
        if not value or visited[seed]:continue
        visited[seed]=1;queue=deque([seed]);count=0;x1=w;y1=h;x2=y2=0
        while queue:
            i=queue.popleft();x=i%w;y=i//w;count+=1
            x1=min(x1,x);y1=min(y1,y);x2=max(x2,x+1);y2=max(y2,y+1)
            neighbours=[]
            if x:neighbours.append(i-1)
            if x<w-1:neighbours.append(i+1)
            if y:neighbours.append(i-w)
            if y<h-1:neighbours.append(i+w)
            for j in neighbours:
                if values[j] and not visited[j]:visited[j]=1;queue.append(j)
        box=[x1,y1,x2,y2]
        if count>=48 and (best is None or area(box)>area(best['localBounds'])):
            best={'localBounds':box,'darkPixels':count}
    if not best:raise ValueError('No sufficiently connected dark phone frame detected; review manually')
    x1,y1,x2,y2=best.pop('localBounds');best['bounds']=[x1+viewport[0],y1+viewport[1],x2+viewport[0],y2+viewport[1]]
    return best

def measure(screenshot,ui,density,apk,threshold=100,preview_bounds=None):
    image=Image.open(screenshot);image.load();tree=ET.parse(ui);candidates=[];controls=[]
    for node in tree.iter('node'):
        attrs=node.attrib;box=rectangle(attrs.get('bounds','[0,0][0,0]'))
        if 'TextureView' in attrs.get('class','') and area(box)>0:candidates.append(box)
        if attrs.get('clickable')=='true' and area(box)>0:
            labels=[child.attrib.get('text') or child.attrib.get('content-desc') for child in node.iter('node')]
            label=' / '.join(dict.fromkeys(value for value in labels if value))
            w,h=extent(box);controls.append({'label':label,'boundsPx':box,'sizeDp':[round(w*160/density,2),round(h*160/density,2)],'enabled':attrs.get('enabled')=='true'})
    viewport=preview_bounds or max(candidates,key=area,default=None)
    if viewport is None:raise ValueError('No TextureView in UI XML; pass observed --preview-bounds')
    if viewport[0]<0 or viewport[1]<0 or viewport[2]>image.width or viewport[3]>image.height:raise ValueError('Viewport exceeds screenshot')
    phone=dark_frame_bbox(image,viewport,threshold);box=phone['bounds'];pw,ph=extent(box);vw,vh=extent(viewport)
    sensitivity=[{'threshold':t,**dark_frame_bbox(image,viewport,t)} for t in sorted({80,threshold,120})]
    return {'screenshot':str(screenshot),'screenshotSha256':hashlib.sha256(Path(screenshot).read_bytes()).hexdigest(),'uiXml':str(ui),'apkSha256':hashlib.sha256(Path(apk).read_bytes()).hexdigest(),
      'screenPx':[image.width,image.height],'densityDpi':density,'screenDp':[round(image.width*160/density,2),round(image.height*160/density,2)],
      'viewportBoundsPx':viewport,'viewportSizePx':[vw,vh],
      'phoneBoundsEstimatePx':box,'phoneSizeEstimatePx':[pw,ph],'segmentation':{'method':'Largest bounding-extent connected dark front-frame component; light outer metal may extend beyond this estimate','thresholdMaxRgb':threshold,'darkPixels':phone['darkPixels'],'sensitivity':sensitivity},
      'ratios':{'viewportAreaToScreen':round(area(viewport)/(image.width*image.height),5),'viewportHeightToScreen':round(vh/image.height,5),'phoneAreaToViewport':round(area(box)/area(viewport),5),'phoneHeightToViewport':round(ph/vh,5),'phoneWidthToViewport':round(pw/vw,5),'phoneHeightToScreen':round(ph/image.height,5),'phoneAreaToScreen':round(area(box)/(image.width*image.height),5)},
      'controls':controls,'scope':'Measured rendered UI only. BBox is a dark-frame estimate, not exact geometry or a claimed usability pass.'}

def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--screenshot',type=Path,required=True);p.add_argument('--ui',type=Path,required=True);p.add_argument('--apk',type=Path,required=True);p.add_argument('--density',type=int,required=True);p.add_argument('--threshold',type=int,default=100);p.add_argument('--preview-bounds',type=rectangle);p.add_argument('--output',type=Path,required=True);a=p.parse_args()
    if not a.output.resolve().is_relative_to((ROOT/'deliverables/design-refresh').resolve()):p.error('Output must stay within deliverables/design-refresh')
    result=measure(a.screenshot,a.ui,a.density,a.apk,a.threshold,a.preview_bounds);a.output.parent.mkdir(parents=True,exist_ok=True);a.output.write_text(json.dumps(result,ensure_ascii=False,indent=2)+'\n');print(json.dumps({'viewport':result['viewportBoundsPx'],'phoneEstimate':result['phoneBoundsEstimatePx'],'ratios':result['ratios']},indent=2))
if __name__=='__main__':main()
