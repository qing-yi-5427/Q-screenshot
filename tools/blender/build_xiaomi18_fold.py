"""Original Xiaomi18Fold reconstruction from verified official dimensions and multiviews.
Blender4.5; dedicated asymmetric book-fold geometry, not a renamed wide-fold.
Front-focused production: real thickness, asymmetric screen corners, hinge and metal edges;
rear halves are simple occluders. No rear camera/Deco/inactive screen renderables.
"""
import bpy,json,math,sys
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2];DEVICE='xiaomi-18-fold'
sys.path.insert(0,str(Path(__file__).parent));from build_device import material,cv

def boundary(w,h,r,n=20):
 # r = top-left,top-right,bottom-right,bottom-left; explicit hinge-side shape.
 tl,tr,br,bl=r
 return [(cx+rr*math.cos(a+i*math.pi/2/n),cy+rr*math.sin(a+i*math.pi/2/n)) for cx,cy,rr,a in [(w/2-tr,h/2-tr,tr,0),(-w/2+tl,h/2-tl,tl,math.pi/2),(-w/2+bl,-h/2+bl,bl,math.pi),(w/2-br,-h/2+br,br,3*math.pi/2)] for i in range(n+1)]
def mesh(name,vertices,faces,mat,smooth=False):
 data=bpy.data.meshes.new(name);data.from_pydata([cv(v) for v in vertices],[],faces);data.update();ob=bpy.data.objects.new(name,data);bpy.context.collection.objects.link(ob);data.materials.append(mat)
 for p in data.polygons:p.use_smooth=smooth
 return ob

def shell(name,w,h,d,r,center,mat,bevel=.0002):
 p=boundary(w,h,r);N=len(p);cx,cy,cz=center;vs=[(x+cx,y+cy,z+cz) for z in [-d/2,d/2] for x,y in p];fs=[tuple(range(N-1,-1,-1)),tuple(range(N,2*N))]+[(i,(i+1)%N,(i+1)%N+N,i+N) for i in range(N)];ob=mesh(name,vs,fs,mat)
 for face in ob.data.polygons:face.use_smooth=face.index>=2
 if bevel:
  m=ob.modifiers.new('soft_machined_edge','BEVEL');m.width=min(bevel,d*.24);m.segments=3
  ob.modifiers.new('weighted_normals','WEIGHTED_NORMAL')
 return ob

def shallow_hinge(name,w,h,d,center,mat):
 # Isolated estimated spine profile: unchanged bbox, shallow X / long Z ellipse.
 # 1.2mm soft Y endcaps; no added detail. Analytic continuous surface normals.
 rx,rz=w/2,d/2;cap=.0012;cylen=h/2-cap;cx,cy,cz=center;N=64
 verts=[(cx,cy-h/2,cz)];normals=[(0,-1,0)];rings=[]
 for latitude in [-75,-60,-45,-30,-15,0,15,30,45,60,75]:
  a=math.radians(latitude);yy=(-cylen+cap*math.sin(a)) if latitude<=0 else (cylen+cap*math.sin(a))
  # Two equator rings bound the central straight extrusion.
  values=[(yy,math.cos(a),math.sin(a))] if latitude else [(-cylen,1,0),(cylen,1,0)]
  for yy,sc,nycap in values:
   ring=[]
   for j in range(N):
    t=j*2*math.pi/N;x=rx*sc*math.cos(t);z=rz*sc*math.sin(t);ring.append(len(verts));verts.append((cx+x,cy+yy,cz+z))
    no=(x/(rx*rx),nycap/cap if nycap else 0,z/(rz*rz));length=math.sqrt(sum(v*v for v in no));normals.append(tuple(v/length for v in no))
   rings.append(ring)
 top=len(verts);verts.append((cx,cy+h/2,cz));normals.append((0,1,0));faces=[]
 for j in range(N):faces.append((0,rings[0][j],rings[0][(j+1)%N]))
 for low,high in zip(rings,rings[1:]):
  for j in range(N):faces.append((low[j],high[j],high[(j+1)%N],low[(j+1)%N]))
 for j in range(N):faces.append((rings[-1][j],top,rings[-1][(j+1)%N]))
 ob=mesh(name,verts,faces,mat,True);ob.data.normals_split_custom_set_from_vertices([cv(v) for v in normals]);return ob

def roundplate(name,w,h,d,r,c,mat,bevel=.0001):return shell(name,w,h,d,[r]*4,c,mat,bevel)
def disc(name,x,y,z,r,mat,n=48,front=False):
 vs=[(x,y,z)]+[(x+r*math.cos(i*2*math.pi/n),y+r*math.sin(i*2*math.pi/n),z) for i in range(n)];fs=[(0,i+1,(i+1)%n+1) if front else (0,(i+1)%n+1,i+1) for i in range(n)];return mesh(name,vs,fs,mat)
def content(name,w,h,r,z,mat):
 p=boundary(w,h,r,28);N=len(p);vs=[(0,0,z)]+[(x,y,z) for x,y in p];ob=mesh(name,vs,[(0,i+1,(i+1)%N+1) for i in range(N)],mat);uv=ob.data.uv_layers.new(name='UVMap')
 for face in ob.data.polygons:
  for li in face.loop_indices:
   v=vs[ob.data.loops[li].vertex_index];uv.data[li].uv=(v[0]/w+.5,v[1]/h+.5)
 return ob

def axis_patch(name,w,h,d,r,center,axis,mat):
 ob=roundplate(name,w,h,d,r,(0,0,0),mat,.000025)
 if name.startswith('port_'):
  # Subpixel dark ports retain solid geometry, without costly invisible micro-bevels.
  for modifier in list(ob.modifiers):ob.modifiers.remove(modifier)
 for v in ob.data.vertices:
  x,y,z=v.co.x,v.co.z,-v.co.y
  p=(x,-z,y) if axis=='bottom' else ((x,z,-y) if axis=='top' else (z,y,-x));v.co=cv(tuple(p[i]+center[i] for i in range(3)))
 return ob

def join_prefix(prefix,name):
 obs=[o for o in bpy.context.scene.objects if o.type=='MESH' and o.name.startswith(prefix)]
 if len(obs)<2:
  if obs:obs[0].name=name
  return
 bpy.ops.object.select_all(action='DESELECT')
 for ob in obs:ob.select_set(True)
 bpy.context.view_layer.objects.active=obs[0];bpy.ops.object.convert(target='MESH');bpy.ops.object.join();obs[0].name=name

def build(state,out=None,hinge_profile=None):
 bpy.ops.object.select_all(action='SELECT');bpy.ops.object.delete(use_global=False)
 g=json.loads((ROOT/'assets-source/devices'/DEVICE/'geometry.json').read_text());s=g[state];w,h,d=[s[k]*.001 for k in ['width_mm','height_mm','thickness_mm']];sw,sh=[s[k]*.001 for k in ['screen_width_mm','screen_height_mm']]
 clearance=g.get('screen_front_clearance_mm',0.0)*.001 # explicit rendering separation, not hardware measurement
 hinge_profile=hinge_profile or g.get('hinge_profile','shallow-ellipse')
 black_border=g.get('bezel_black_border_mm',.85)*.001
 center_x=s.get('screen_center_x_mm',0)*.001
 content_radii=[r*.001 for r in s.get('screen_corner_radii_mm',[2.2,8.1,8.1,2.2] if state=='folded' else [8]*4)]
 metal=material('x18_7series_warm_silver',(.73,.72,.68),.95,.21);glass=material('x18_warm_gold_white_AG_glass',(.74,.72,.67),0,.37);dark=material('x18_optical_black',(.002,.003,.004),0,.27)
 front_metal=material('x18_front_satin_aluminum',(.78,.77,.73),.95,g.get('front_aluminum_roughness',.38))
 dark.node_tree.nodes.get('Principled BSDF').inputs['Specular IOR Level'].default_value=.10
 screen=material('screen_content',(.025,.07,.10),0,1);R=g['outer_corner_radius_mm']*.001;small=g['hinge_corner_radius_mm']*.001
 if state=='folded':
  gap=(d-2*g['unfolded']['thickness_mm']*.001);half=(d-gap)/2;halfcenter=(half+gap)/2;cw=w-.0012;cx=.0006;r=[small,R,R,small]
  shell('chassis_main',cw,h,half-.00024,r,(cx,0,halfcenter),metal,.00022)
  shell('chassis_secondary',cw,h-.00014,half-.00024,r,(cx,0,-halfcenter),metal,.00022)
  shell('fold_seam_gasket',cw-.0003,h-.0005,max(.0002,gap-.0001),r,(cx,0,0),dark,.00008)
  (shallow_hinge('hinge_shell',.0025,h-.0022,d-.0006,(-w/2+.00125,0,0),metal) if hinge_profile=='shallow-ellipse' else roundplate('hinge_shell',.0025,h-.0022,d-.0006,.0012,(-w/2+.00125,0,0),metal,.00018))
  shell('back_panel_main',cw-.0007,h-.0008,.00032,[small,R-.0004,R-.0004,small],(cx,0,-d/2+.00016),glass,.00007)
  shell('screen_bezel',sw+2*black_border,sh+2*black_border,.0002,[r+black_border for r in content_radii],(center_x,0,d/2-.00016),dark,.00004)
  active=content('screen_outer_content',sw,sh,content_radii,d/2-.000035+clearance,screen)
  active.location.x=center_x # local mesh/UV centered; explicit runtime node translation
  disc('selfie_outer_punch',center_x,h/2-.0047,d/2+.000015+clearance,.00105,dark,48,True)
  hardware_half_center=0;rear=-d/2;key_x=w/2-.00003;key_z=-halfcenter
 else:
  gap=.0006;cw=(w-gap)/2;cent=(cw+gap)/2
  shell('chassis_main',cw,h,d-.00025,[R,small,small,R],(-cent,0,0),metal,.00022)
  shell('chassis_secondary',cw,h,d-.00025,[small,R,R,small],(cent,0,0),metal,.00022)
  roundplate('hinge_shell',.0026,h-.006,d-.00035,.0012,(0,0,-.00010),metal,.00014)
  # Back viewing reverses X: camera half left, inactive outer screen half right.
  shell('back_panel_main',cw-.0005,h-.0007,.0003,[small,R-.0003,R-.0003,small],(cent,0,-d/2+.00015),glass,.00006)
  shell('back_panel_secondary',cw-.00055,h-.0007,.0002,[R-.0003,small,small,R-.0003],(-cent,0,-d/2+.0001),glass,.00004)
  shell('screen_bezel',sw+2*black_border,sh+2*black_border,.00016,[r+black_border for r in content_radii],(center_x,0,d/2-.00013),dark,.00003)
  active=content('screen_inner_content',sw,sh,content_radii,d/2-.000025+clearance,screen)
  active.location.x=center_x
  disc('selfie_inner_punch',g['selfie_inner_center_mm'][0]*.001,g['selfie_inner_center_mm'][1]*.001,d/2+.000015+clearance,.00105,dark,48,True)
  hardware_half_center=cent;rear=-d/2;key_x=w/2-.00003;key_z=0
 # Real planar front aluminum uses wider satin reflection; sides retain narrow highlight.
 # Assign actual existing front-cap polygons, not a decal/unlit or extra overlay.
 for name in ['chassis_main','chassis_secondary','hinge_shell']:
  ob=bpy.data.objects.get(name)
  if ob:
   ob.data.materials.append(front_metal)
   for poly in ob.data.polygons:
    if poly.normal.y < -.999: poly.material_index=len(ob.data.materials)-1
 # Back-facing lens/Deco and inactive display omitted by the front-focused product scope.
 # Hardware visible in official side/bottom diagram; placement dimensions remain estimated.
 axis_patch('button_volume',.0022,.014,.00045,.0010,(key_x,.027,key_z),'right',metal)
 axis_patch('button_power',.0022,.010,.00045,.0010,(key_x,.009,key_z),'right',metal)
 bottom_z=key_z if state=='folded' else 0
 bottom_x=0 if state=='folded' else hardware_half_center
 axis_patch('port_usb_c',.0081,.0024,.00010,.0010,(bottom_x,-h/2-.000015,bottom_z),'bottom',dark)
 for j in range(7):axis_patch('port_speaker_'+str(j),.00075,.0019,.00010,.00035,(bottom_x-.020+j*.0015,-h/2-.00002,bottom_z),'bottom',dark)
 axis_patch('port_microphone',.0008,.0008,.00010,.00035,(bottom_x+.009,-h/2-.00002,bottom_z),'bottom',dark)
 # Antenna band inserts are geometry, not photographic decals.
 for y in [h/2-.013,-h/2+.013]:axis_patch('antenna_right_'+str(y),.0024,.0007,.00006,.00012,(key_x+.00001,y,key_z),'right',glass)
 join_prefix('port_','ports_and_speaker');join_prefix('button_','side_buttons');join_prefix('antenna_','antenna_breaks')
 root=bpy.data.objects.new('device_root',None);bpy.context.collection.objects.link(root)
 for ob in list(bpy.context.scene.objects):
  if ob!=root:ob.parent=root
 out=Path(out) if out else ROOT/'app/src/main/assets/devices'/DEVICE/(state+'.glb');out.parent.mkdir(parents=True,exist_ok=True)
 bpy.ops.export_scene.gltf(filepath=str(out),export_format='GLB',export_yup=True,export_apply=True,export_extras=True,export_cameras=False,export_lights=False)
 print('EXPORTED',state,out)
if __name__=='__main__':
 args=sys.argv[sys.argv.index('--')+1:] if '--' in sys.argv else [];state=args[0] if args else 'folded';assert state in ['folded','unfolded'];build(state,args[1] if len(args)>1 else None,args[2] if len(args)>2 else None)
