#!/usr/bin/env python3
"""Generate original privacy-free mapping/color fixtures. Requires Pillow 12.1.0."""
import argparse, hashlib, json
from pathlib import Path
from PIL import Image, ImageDraw, ImageFont
COLORS=['#ff0000','#00ff00','#0000ff','#00ffff','#ff00ff','#ffff00','#000000','#ffffff','#202020','#606060','#a0a0a0','#e0e0e0']
def font(size):
    for path in ['/System/Library/Fonts/Monaco.ttf','/usr/share/fonts/truetype/dejavu/DejaVuSansMono.ttf']:
        if Path(path).exists(): return ImageFont.truetype(path,size)
    return ImageFont.load_default(size=size)
def create(w,h,path):
    im=Image.new('RGB',(w,h),'#f3f5fa'); d=ImageDraw.Draw(im); m=40
    d.rectangle((m,m,w-m,h-m),outline='#101827',width=4)
    for text,x,y,color in [('1 TL',48,48,'#e83d58'),('2 TR',w-205,48,'#3c9f65'),('3 BL',48,h-105,'#3663d9'),('4 BR',w-205,h-105,'#a43ec9')]:
        d.rectangle((x,y,x+155,y+57),fill=color);d.text((x+8,y+8),text,font=font(30),fill='white')
    d.text((70,160),'QINGFRAME / ORIGINAL TEST',font=font(38),fill='#14243b')
    d.text((70,220),f'{w} x {h} / TOP UP / RGB sRGB',font=font(27),fill='#43546c')
    d.text((70,285),'Synthetic project 042  |  No personal data',font=font(22),fill='#43546c')
    cell=(w-140)//6; patches=[]
    for i,c in enumerate(COLORS):
        x=70+(i%6)*cell;y=370+(i//6)*140
        d.rectangle((x,y,x+cell-10,y+100),fill=c)
        d.text((x,y+105),c.upper(),font=font(18),fill='#152238')
        patches.append({'rgb':list(Image.new('RGB',(1,1),c).getpixel((0,0))),'center':[x+(cell-10)//2,y+50]})
    y=730
    for size in [12,16,20,24,32]:
        d.text((70,y),f'{size}px ABC abc 0123456789 / Sharp text + - =',font=font(size),fill='#101827');y+=size+25
    d.line((w//2,1050,w//2,h-155),fill='#3e70c8',width=3)
    d.text((70,h-180),'BOTTOM / orientation must remain readable',font=font(21),fill='#18304e')
    for row in range(8):
        for col in range(16):
            x=70+col*16;y=h-365+row*16
            d.rectangle((x,y,x+15,y+15),fill='#222222' if (row+col)%2 else '#ffffff')
    im.save(path)
    return {'file':str(path),'size':[w,h],'sha256':hashlib.sha256(path.read_bytes()).hexdigest(),'colorPatches':patches}
def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--output',type=Path,default=Path('tests/fixtures'));a=p.parse_args();a.output.mkdir(parents=True,exist_ok=True)
    items=[create(w,h,a.output/name) for w,h,name in [(1168,1712,'outer-1168x1712.png'),(2364,1672,'inner-landscape-2364x1672.png'),(1672,2364,'inner-portrait-1672x2364.png')]]
    (a.output/'manifest.json').write_text(json.dumps({'generator':'tools/generate_test_images.py','original':True,'privacy':'synthetic; no user data','images':items},indent=2)+'\n')
    print(json.dumps(items,indent=2))
if __name__=='__main__':main()
