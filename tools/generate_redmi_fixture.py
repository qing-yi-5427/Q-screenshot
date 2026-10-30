#!/usr/bin/env python3
"""Generate original 1200x2608 Redmi diagnostic and presentation images, offline."""
import argparse
import hashlib
import json
from pathlib import Path
from PIL import Image, ImageDraw, ImageFont

ROOT=Path(__file__).resolve().parents[1]
SIZE=(1200,2608)
COLORS=['#ff0000','#00ff00','#0000ff','#00ffff','#ff00ff','#ffff00','#000000','#ffffff','#202020','#606060','#a0a0a0','#e0e0e0']

def font(size):
    for path in ['/System/Library/Fonts/Monaco.ttf','/usr/share/fonts/truetype/dejavu/DejaVuSansMono.ttf']:
        if Path(path).is_file(): return ImageFont.truetype(path,size)
    return ImageFont.load_default(size=size)

def text(draw,xy,value,size=30,fill='#233d35'):
    draw.text(xy,value,font=font(size),fill=fill)

def scene(d,box):
    x,y,w,h=box
    d.rounded_rectangle((x,y,x+w,y+h),radius=45,fill='#d8e2cd')
    d.ellipse((x+w*.70,y+h*.10,x+w*.90,y+h*.10+w*.20),fill='#fff0bd')
    d.polygon([(x,y+h*.72),(x+w*.20,y+h*.32),(x+w*.48,y+h*.80),(x+w*.72,y+h*.42),(x+w,y+h*.78),(x+w,y+h),(x,y+h)],fill='#8eae99')
    d.polygon([(x,y+h*.85),(x+w*.33,y+h*.58),(x+w*.62,y+h*.93),(x+w*.84,y+h*.63),(x+w,y+h*.84),(x+w,y+h),(x,y+h)],fill='#527c69')
    d.polygon([(x,y+h*.94),(x+w*.42,y+h*.82),(x+w*.74,y+h*.97),(x+w,y+h*.85),(x+w,y+h),(x,y+h)],fill='#294f42')
    text(d,(x+40,y+h-125),'A QUIET HORIZON',34,'#ffffff')
    text(d,(x+40,y+h-70),'Original geometric illustration',20,'#e2eee6')

def interface(d,diagnostic):
    text(d,(74,188 if diagnostic else 120),'QING / MOMENTS',25)
    text(d,(74,250 if diagnostic else 195),'A day, in focus.',52)
    text(d,(74,332 if diagnostic else 280),'Make room for the little things.',25,'#72847a')
    scene(d,(74,430 if diagnostic else 390,1052,670 if diagnostic else 880))
    y=1175 if diagnostic else 1360
    text(d,(74,y),'TODAY\'S COLLECTION',22,'#72847a')
    d.rounded_rectangle((74,y+65,1126,y+315),radius=42,fill='white')
    text(d,(112,y+102),'Ideas in progress',36)
    text(d,(112,y+170),'A fictional notebook for visual testing.',22,'#72847a')
    text(d,(112,y+224),'Nothing here belongs to a real account.',21,'#72847a')
    if not diagnostic:
        for x,title,value in [(74,'FOCUS TIME','42 min'),(615,'SMALL MOMENTS','08')]:
            d.rounded_rectangle((x,1760,x+511,1990),radius=38,fill='#e1e9dc')
            text(d,(x+36,1800),title,20,'#72847a');text(d,(x+36,1860),value,48)
        text(d,(74,2100),'LESS RUSH. MORE NOTICE.',26)
        text(d,(74,2165),'An original composition, made for this screen.',23,'#72847a')
        d.line((74,2280,1126,2280),fill='#cbd6c9',width=2)
        text(d,(74,2350),'ORIGINAL DEMO / NO PERSONAL DATA',22,'#72847a')
        text(d,(74,2410),'1200 x 2608  |  QINGFRAME',21,'#72847a')

def diagnostic():
    im=Image.new('RGB',SIZE,'#f5f5ec');d=ImageDraw.Draw(im)
    interface(d,True)
    for label,x,y,color in [('1 TL',38,38,'#e83d58'),('2 TR',1007,38,'#329663'),('3 BL',38,2510,'#3663d9'),('4 BR',1007,2510,'#a43ec9')]:
        d.rectangle((x,y,x+155,y+58),fill=color);text(d,(x+9,y+9),label,29,'white')
    d.rectangle((30,30,1170,2578),outline='#243d35',width=3)
    text(d,(74,130),'REDMI K90 PRO MAX / ORIGINAL TEST',26)
    patches=[];cell=176
    for i,color in enumerate(COLORS):
        x=74+(i%6)*cell;y=1600+(i//6)*135
        d.rectangle((x,y,x+cell-12,y+95),fill=color)
        text(d,(x,y+101),color.upper(),17)
        patches.append({'rgb':list(Image.new('RGB',(1,1),color).getpixel((0,0))),'center':[x+82,y+47],'safeSampleRadius':10})
    y=1910
    for size in [12,16,20,24,32]:
        text(d,(74,y),f'{size}px ABC abc 0123456789 / Sharp + - =',size,'#14281f');y+=size+23
    for row in range(8):
        for col in range(16):
            x=74+col*14;y=2260+row*14
            d.rectangle((x,y,x+13,y+13),fill='#ffffff' if (row+col)%2 else '#202020')
    text(d,(74,2410),'1200 x 2608 / TOP UP / SDR sRGB',23)
    text(d,(74,2460),'BOTTOM / ORIGINAL SYNTHETIC CONTENT',21)
    return im,patches

def main():
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('--root',type=Path,default=ROOT,help='Project root or isolated output tree')
    a=p.parse_args();root=a.root.resolve()
    diagnostic_image,patches=diagnostic()
    demo=Image.new('RGB',SIZE,'#f5f5ec');interface(ImageDraw.Draw(demo),False)
    outputs=[('tests/fixtures/redmi-k90-pro-max-1200x2608.png',diagnostic_image),('app/src/main/assets/fixtures/redmi.png',diagnostic_image),('app/src/main/assets/demo-redmi.png',demo)]
    entries=[]
    for relative,im in outputs:
        path=root/relative;path.parent.mkdir(parents=True,exist_ok=True);im.save(path)
        entries.append({'file':relative,'size':list(SIZE),'mode':im.mode,'sha256':hashlib.sha256(path.read_bytes()).hexdigest()})
    manifest={'generator':'tools/generate_redmi_fixture.py','original':True,'privacy':'Entirely synthetic. No personal account, gallery, notifications, or user image.','dimensionsPurpose':'Official input aspect verified separately by device research; these are image pixels, not dp or body millimeters.','colorSpace':'SDR sRGB numerical RGB values','files':entries,'diagnosticColorPatches':patches,'cornerOrder':['1 TL','2 TR','3 BL','4 BR'],'fontSizesPx':[12,16,20,24,32]}
    for relative in ['tests/fixtures/redmi-k90-pro-max-manifest.json','deliverables/evidence/redmi-k90-pro-max/fixture-manifest.json']:
        path=root/relative;path.parent.mkdir(parents=True,exist_ok=True);path.write_text(json.dumps(manifest,indent=2)+'\n')
    print(json.dumps(manifest,indent=2))
if __name__=='__main__':main()
