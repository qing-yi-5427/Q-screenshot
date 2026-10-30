#!/usr/bin/env python3
"""Create an original offline editorial demo; no external images or private data."""
from pathlib import Path
from PIL import Image,ImageDraw,ImageFont
ROOT=Path(__file__).resolve().parents[1];w,h=1168,1712
im=Image.new('RGB',(w,h),'#F5F5ED');d=ImageDraw.Draw(im)
font=next((str(p) for p in [Path('/System/Library/Fonts/STHeiti Medium.ttc'),Path('/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf')] if p.exists()),None)
def f(n):return ImageFont.truetype(font,n) if font else ImageFont.load_default(size=n)
def text(x,y,s,n=32,color='#203E34'):d.text((x,y),s,font=f(n),fill=color)
text(76,68,'QING / MOMENTS',24,'#61766A');text(940,68,'09:41',26)
text(76,154,'留住好时光。',76);text(80,260,'A little pause, a wider perspective.',30,'#6F8072')
# Original landscape illustration, clipped to rounded rectangle.
land=Image.new('RGB',(1016,630),'#C9D8CA');p=ImageDraw.Draw(land)
for y in range(630):
 t=y/630;p.line((0,y,1016,y),fill=(int(214-37*t),int(225-24*t),int(204-30*t)))
p.ellipse((690,64,870,244),fill='#F8F0CA')
p.polygon([(0,440),(130,292),(265,397),(420,230),(635,441),(770,337),(1016,484),(1016,630),(0,630)],fill='#8AA893')
p.polygon([(0,545),(190,451),(376,520),(555,391),(756,483),(1016,426),(1016,630),(0,630)],fill='#4E7766')
p.polygon([(0,579),(249,554),(458,600),(722,491),(1016,569),(1016,630),(0,630)],fill='#244D40')
mask=Image.new('L',land.size);ImageDraw.Draw(mask).rounded_rectangle((0,0,1015,629),radius=54,fill=255);im.paste(land,(76,356),mask)
d=ImageDraw.Draw(im);text(122,838,'山野之间',58,'#FFFFFF');text(126,917,'Find your own quiet.',24,'#DCE9DD')
text(80,1048,'TODAY’S COLLECTION',23,'#7D8E7D')
d.rounded_rectangle((76,1104,1092,1350),radius=42,fill='#FFFFFF')
text(122,1145,'让灵感慢慢发生',42);text(125,1220,'用新的角度，重新看见日常。',28,'#819082')
d.ellipse((916,1162,1024,1270),fill='#E3EBDD');text(949,1170,'↗',58,'#466651')
d.rounded_rectangle((76,1390,561,1598),radius=38,fill='#DFE8DA');text(118,1424,'专注时刻',27,'#6C826B');text(116,1474,'42 min',58)
d.rounded_rectangle((601,1390,1092,1598),radius=38,fill='#E8EADD');text(643,1424,'本周记录',27,'#6C826B');text(641,1474,'08',58);text(782,1510,'pieces of life',23,'#6C826B')
text(80,1648,'ORIGINAL DEMO  ·  NO PERSONAL DATA',20,'#8C9D8B')
out=ROOT/'app/src/main/assets/demo.png';im.save(out);print(out)
