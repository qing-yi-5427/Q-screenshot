#!/usr/bin/env python3
"""Rebuild matched Filament materials and original neutral, high-contrast studio IBL."""
import argparse,hashlib,json,math,pathlib,shutil,struct,subprocess,sys,tempfile
ROOT=pathlib.Path(__file__).resolve().parents[1]

def studio_rgb(lon,lat):
 # Neutral light only: all channels identical. Black cards are the unlit spaces.
 # Area-light borders have a small soft rolloff; no baked phone-specific highlights.
 def wrap(a):return (a+math.pi)%(2*math.pi)-math.pi
 def rect(cx,cy,hw,hh):
  return math.exp(-(abs(wrap(lon-cx))/hw)**12-(abs(lat-cy)/hh)**12)
 base=.003+.004*max(0,math.sin(lat))
 key=12.0*rect(-.80,.34,.26,.65) # tall key strip, contrasted by adjacent black card
 ribbon=7.0*rect(1.12,.08,.085,.82) # narrow opposite silver edge
 back=5.5*rect(2.65,.25,.19,.58) # rear-facing softbox for lens/platform separation
 ceiling=1.4*rect(.10,1.12,.75,.17) # restrained top bounce
 return .52*(base+key+ribbon+back+ceiling)

def write_hdr(path,w=1024,h=512):
 """Portable Radiance RGBE writer; no image/photography inputs, linear neutral radiance."""
 pixels=bytearray();total=0.;weighted=0.;weight=0.;peak=0.;minimum=100.
 for y in range(h):
  lat=math.pi*(.5-(y+.5)/h);rowweight=math.cos(lat)
  for x in range(w):
   value=studio_rgb(2*math.pi*((x+.5)/w-.5),lat);peak=max(peak,value);minimum=min(minimum,value);total+=value;weighted+=value*rowweight;weight+=rowweight
   mantissa,exponent=math.frexp(value);channel=min(255,int(mantissa*256))
   pixels.extend((channel,channel,channel,exponent+128))
 path.parent.mkdir(parents=True,exist_ok=True)
 path.write_bytes(b'#?RADIANCE\n# QingFrame original neutral strip studio v2\nFORMAT=32-bit_rle_rgbe\n\n'+f'-Y {h} +X {w}\n'.encode()+pixels)
 return {'width':w,'height':h,'neutralRgb':True,'radianceMin':minimum,'radiancePeak':peak,'solidAngleMean':weighted/weight,'peakToBase':peak/minimum,'definition':'neutral strip softboxes with black-card gaps; analytic original source'}

def digest(path):return hashlib.sha256(path.read_bytes()).hexdigest()
def main():
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('--filament-bin',type=pathlib.Path,default=ROOT/'tools/bin/filament/bin');p.add_argument('--environment-only',action='store_true');p.add_argument('--ibl-size',type=int,choices=[128,256,512],default=256);p.add_argument('--output-assets',type=pathlib.Path,default=ROOT/'app/src/main/assets');p.add_argument('--evidence-dir',type=pathlib.Path,default=ROOT/'deliverables/design-refresh/assets');p.add_argument('--studio-profile',choices=['contrast','legacy'],default='contrast');a=p.parse_args()
 matc=a.filament_bin/'matc';cmgen=a.filament_bin/'cmgen'
 if not matc.is_file() or not cmgen.is_file():p.error('Install existing official Filament1.79.0 tools, or supply --filament-bin. See TOOLCHAIN.md.')
 version=subprocess.check_output([str(matc),'--version'],text=True).strip()
 if version!='79':p.error(f'Expected matc79(Filament1.79.0), found {version}')
 a.evidence_dir.mkdir(parents=True,exist_ok=True);target=a.output_assets/'environments';target.mkdir(parents=True,exist_ok=True)
 before=a.evidence_dir/'before';before.mkdir(parents=True,exist_ok=True)
 # Idempotent: preserve the first original environment, never overwrite it in later iterations.
 for source in target.iterdir():
  if source.is_file() and not (before/source.name).exists():shutil.copy2(source,before/source.name)
 oldhdr=ROOT/'assets-source/environments/studio.hdr'
 if oldhdr.exists() and not (before/'project-studio-source.hdr').exists():shutil.copy2(oldhdr,before/'project-studio-source.hdr')
 if not a.environment_only:
  materials=a.output_assets/'materials';materials.mkdir(parents=True,exist_ok=True)
  for source in sorted((ROOT/'assets-source/materials').glob('*.mat')):subprocess.run([str(matc),'-p','mobile','-a','opengl','-o',str(materials/(source.stem+'.filamat')),str(source)],check=True)
 source_dir=a.evidence_dir/'source';source_dir.mkdir(parents=True,exist_ok=True);hdr=source_dir/'studio.hdr'
 if a.studio_profile=='contrast':stats=write_hdr(hdr)
 else:shutil.copy2(oldhdr,hdr);stats={'profile':'existing project HDR'}
 with tempfile.TemporaryDirectory(prefix='qf-ibl-') as td:
  out=pathlib.Path(td)/'studio';subprocess.run([str(cmgen),'--quiet','--format=ktx',f'--size={a.ibl_size}',f'--deploy={out}',str(hdr)],check=True)
  after=a.evidence_dir/'after';after.mkdir(parents=True,exist_ok=True)
  for source in out.iterdir():
   shutil.copy2(source,target/source.name);shutil.copy2(source,after/source.name)
 report={'profile':a.studio_profile,'filamentVersion':'1.79.0','iblFaceSize':a.ibl_size,'radiance':stats,'hdrSha256':digest(hdr),'before':{p.name:digest(p) for p in before.iterdir() if p.is_file()},'after':{p.name:{'bytes':p.stat().st_size,'sha256':digest(p)} for p in (a.evidence_dir/'after').iterdir() if p.is_file()},'commands':['python3 tools/build_render_assets.py --environment-only --studio-profile contrast --ibl-size 256'],'referenceImageEmbedded':False,'modelsChanged':False,'note':'New HDR does not alter screen material, source images, exposure or tone mapper. Android A/B required.'}
 (a.evidence_dir/'environment-build.json').write_text(json.dumps(report,indent=2));print(json.dumps(report,indent=2))
if __name__=='__main__':
 try:main()
 except subprocess.CalledProcessError as e:sys.exit(e.returncode)
