"""Original linear HDR equirectangular studio windows. No external image inputs."""
import bpy,math
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2];out=ROOT/'assets-source/environments';out.mkdir(parents=True,exist_ok=True)
w,h=1024,512;img=bpy.data.images.new('qingframe_original_studio',width=w,height=h,float_buffer=True);pixels=[]
for y in range(h):
 lat=(y/h-.5)*math.pi
 for x in range(w):
  lon=(x/w-.5)*2*math.pi
  base=.06+.09*max(0,math.sin(lat))
  key=6*math.exp(-((lon+.9)/.28)**10-((lat-.45)/.40)**10)
  fill=2*math.exp(-((lon-1.4)/.15)**10-((lat-.15)/.52)**10)
  rim=3.8*math.exp(-((lon-2.8)/.18)**10-((lat-.1)/.55)**10)
  pixels.extend((base+key+fill*.87+rim*.9,base+key+fill*.95+rim*.95,base+key+fill+rim,1))
img.pixels.foreach_set(pixels);img.filepath_raw=str(out/'studio.hdr');img.file_format='HDR';img.save()
print(img.filepath_raw)
