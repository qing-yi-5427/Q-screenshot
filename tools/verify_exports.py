#!/usr/bin/env python3
"""Inspect actual PNG exports; never generates stand-in renderings. Requires Pillow."""
import argparse, hashlib, json, statistics, sys, math
from pathlib import Path
from PIL import Image


def srgb_lab(rgb):
    """sRGB (0..255), D65 white -> CIELAB, using IEC sRGB transfer."""
    c=[v/255 for v in rgb]
    r,g,b=[v/12.92 if v<=0.04045 else ((v+0.055)/1.055)**2.4 for v in c]
    xyz=[(0.4124564*r+0.3575761*g+0.1804375*b)/0.95047,
         0.2126729*r+0.7151522*g+0.0721750*b,
         (0.0193339*r+0.1191920*g+0.9503041*b)/1.08883]
    f=[v**(1/3) if v>(6/29)**3 else v/(3*(6/29)**2)+4/29 for v in xyz]
    return (116*f[1]-16,500*(f[0]-f[1]),200*(f[1]-f[2]))

def delta_e_2000(lab1,lab2):
    """CIEDE2000, kL=kC=kH=1; hue angles handled in degrees."""
    L1,a1,b1=lab1;L2,a2,b2=lab2
    C1=math.hypot(a1,b1);C2=math.hypot(a2,b2);Cbar=(C1+C2)/2
    G=.5*(1-math.sqrt(Cbar**7/(Cbar**7+25**7)))
    ap1=(1+G)*a1;ap2=(1+G)*a2
    cp1=math.hypot(ap1,b1);cp2=math.hypot(ap2,b2)
    hp1=math.degrees(math.atan2(b1,ap1))%360 if cp1 else 0
    hp2=math.degrees(math.atan2(b2,ap2))%360 if cp2 else 0
    dL=L2-L1;dC=cp2-cp1;dh=hp2-hp1
    if cp1*cp2==0:dh=0
    elif dh>180:dh-=360
    elif dh< -180:dh+=360
    dH=2*math.sqrt(cp1*cp2)*math.sin(math.radians(dh/2))
    lp=(L1+L2)/2;cp=(cp1+cp2)/2
    if cp1*cp2==0:hp=hp1+hp2
    elif abs(hp1-hp2)<=180:hp=(hp1+hp2)/2
    elif hp1+hp2<360:hp=(hp1+hp2+360)/2
    else:hp=(hp1+hp2-360)/2
    cos=lambda x:math.cos(math.radians(x))
    T=1-.17*cos(hp-30)+.24*cos(2*hp)+.32*cos(3*hp+6)-.20*cos(4*hp-63)
    sl=1+.015*(lp-50)**2/math.sqrt(20+(lp-50)**2)
    sc=1+.045*cp;sh=1+.015*cp*T
    theta=30*math.exp(-((hp-275)/25)**2)
    rt=-2*math.sqrt(cp**7/(cp**7+25**7))*math.sin(math.radians(2*theta))
    x=dL/sl;y=dC/sc;z=dH/sh
    return math.sqrt(max(0,x*x+y*y+z*z+rt*y*z))

def inspect(path, expected=None, transparent=False, composites=None, samples=None):
    with Image.open(path) as src:
        src.load(); im=src.convert('RGBA'); alpha=im.getchannel('A'); hist=alpha.histogram(); n=im.width*im.height
        corners=[im.getpixel(p)[3] for p in [(0,0),(im.width-1,0),(0,im.height-1),(im.width-1,im.height-1)]]
        errors=[]
        if src.format!='PNG':errors.append('not PNG')
        if expected and list(im.size)!=expected:errors.append(f'dimensions {im.size} != {expected}')
        if len(im.getcolors(256) or [])==1:errors.append('uniform image')
        if transparent:
            if src.mode!='RGBA':errors.append('transparent export lacks RGBA channel')
            if any(corners):errors.append('canvas corners are not fully transparent')
            if not hist[255]:errors.append('no opaque body pixels')
            if not hist[0]:errors.append('no transparent pixels')
            if composites:
                composites.mkdir(parents=True,exist_ok=True)
                for name,color in [('white','#ffffff'),('black','#000000'),('saturated','#ff00a0')]:
                    bg=Image.new('RGBA',im.size,color);bg.alpha_composite(im);bg.convert('RGB').save(composites/f'{path.stem}-{name}.png')
        color=[]
        for sample in samples or []:
            x,y=sample['center'];radius=sample.get('radius',2)
            crop=im.crop((x-radius,y-radius,x+radius+1,y+radius+1)).convert('RGB')
            observed=[round(statistics.mean(p[c] for p in crop.getdata()),3) for c in range(3)]
            color.append({'expectedRgb':sample['rgb'],'observedRgb':observed,'absoluteRgbError':[round(abs(v-t),3) for v,t in zip(observed,sample['rgb'])],'deltaE2000':round(delta_e_2000(srgb_lab(sample['rgb']),srgb_lab(observed)),4)})
        return {'colorSamples':color,'file':str(path),'sha256':hashlib.sha256(path.read_bytes()).hexdigest(),'size':list(im.size),'mode':src.mode,'alpha':{'zero':hist[0],'opaque':hist[255],'partial':sum(hist[1:255]),'cornerValues':corners,'nonzeroBounds':alpha.getbbox()},'checks':'pass' if not errors else 'fail','errors':errors,'visualReview':'required; numerical checks do not certify appearance'}

def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--input',type=Path,required=True);p.add_argument('--manifest',type=Path,help='JSON object mapping filename to {size:[w,h],transparent:bool}');p.add_argument('--output',type=Path);p.add_argument('--composites',type=Path);a=p.parse_args()
    specs=json.loads(a.manifest.read_text()) if a.manifest else {}
    files=sorted(a.input.glob('*.png')) if a.input.is_dir() else [a.input]
    reports=[]
    for path in files:
        try:
            s=specs.get(path.name,{});reports.append(inspect(path,s.get('size'),s.get('transparent','transparent' in path.stem),a.composites,s.get('colorSamples')))
        except Exception as e:reports.append({'file':str(path),'checks':'fail','errors':[str(e)]})
    missing=[x for x in specs if x not in {p.name for p in files}]
    result={'status':'pass' if reports and not missing and all(r['checks']=='pass' for r in reports) else 'fail','scope':'PNG dimensions/alpha/decode only; Android provenance and visual/color acceptance require separate evidence','images':reports,'missing':missing}
    value=json.dumps(result,indent=2)+'\n';print(value)
    if a.output:a.output.parent.mkdir(parents=True,exist_ok=True);a.output.write_text(value)
    return 0 if result['status']=='pass' else 1
if __name__=='__main__':sys.exit(main())
