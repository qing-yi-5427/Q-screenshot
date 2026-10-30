"""Compose existing evidence images without modifying their pixel content."""
import bpy
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]
def compose(paths,out):
 images=[bpy.data.images.load(str(p),check_existing=False) for p in paths];w=sum(i.size[0] for i in images);h=max(i.size[1] for i in images);pixels=[1.]*(w*h*4);offset=0
 for im in images:
  sw,sh=im.size;src=list(im.pixels)
  for y in range(sh):
   for x in range(sw):
    si=(y*sw+x)*4;di=(y*w+x+offset)*4;pixels[di:di+4]=src[si:si+4]
  offset+=sw
 outim=bpy.data.images.new(out.stem,width=w,height=h,alpha=True);outim.pixels.foreach_set(pixels);outim.filepath_raw=str(out);outim.file_format='PNG';outim.save()
compose([ROOT/'deliverables/evidence/model-before-back.png',ROOT/'deliverables/evidence/model-wide-fold-folded/back.png'],ROOT/'deliverables/evidence/model-before-after.png')
compose([ROOT/'.cache/references/target-nav.png'],ROOT/'.cache/references/contact-sheet.png')
