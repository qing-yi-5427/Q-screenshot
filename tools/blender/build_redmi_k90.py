"""Original REDMI K90 Pro Max slab reconstruction. Blender4.5; no imported vendor mesh/image."""
import bpy,math,json,sys,hashlib,shutil
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]
sys.path.insert(0,str(Path(__file__).parent))
from build_device import shape,screen,material,outline,cv
DEVICE='redmi-k90-pro-max'
def orient(ob,center,axis):
 for v in ob.data.vertices:
  x,y,z=v.co.x,v.co.z,-v.co.y
  q=(x,-z,y) if axis=='bottom' else ((x,z,-y) if axis=='top' else (z,y,-x))
  v.co=cv(tuple(q[i]+center[i] for i in range(3)))
 return ob
def combine(names,name):
 obs=[o for o in bpy.context.scene.objects if any(o.name.startswith(p) for p in names)]
 bpy.ops.object.select_all(action='DESELECT')
 for o in obs:o.select_set(True)
 bpy.context.view_layer.objects.active=obs[0];bpy.ops.object.convert(target='MESH');bpy.ops.object.join();obs[0].name=name;return obs[0]
def disc(name,x,y,z,r,mat,n=32):
 vs=[cv((x,y,z))]+[cv((x+r*math.cos(i*2*math.pi/n),y+r*math.sin(i*2*math.pi/n),z)) for i in range(n)]
 mesh=bpy.data.meshes.new(name);mesh.from_pydata(vs,[],[(0,(i+1)%n+1,i+1) for i in range(n)]);mesh.update();ob=bpy.data.objects.new(name,mesh);bpy.context.collection.objects.link(ob);mesh.materials.append(mat);return ob

def build(out=None,revision=3):
 bpy.ops.object.select_all(action='SELECT');bpy.ops.object.delete(use_global=False)
 g=json.loads((ROOT/'assets-source/devices'/DEVICE/'geometry.json').read_text());w,h,d=[g[k]*.001 for k in ['width_mm','height_mm','thickness_mm']];r=g['corner_radius_mm']*.001
 def pbr(name,color,metal=0,rough=.3):
  settings=g.get('material_parameters',{}).get(name,{})
  return material(name,tuple(settings.get('baseColorLinear',color)),settings.get('metallic',metal),settings.get('roughness',rough))
 silver=pbr('k90_aircraft_aluminum',(.72,.74,.76),.95,.21)
 pearl=pbr('k90_warm_white_glass_fiber',(.70,.685,.64),0,.42)
 platform=pbr('k90_sculpted_aluminum_deco',(.58,.58,.55),.90,.26)
 dark=material('k90_optical_black',(.002,.003,.004),0,.30)
 optical=pbr('k90_optical_coating',(.008,.014,.020),0,.12)
 dark.node_tree.nodes.get('Principled BSDF').inputs['Specular IOR Level'].default_value=.08
 optical.node_tree.nodes.get('Principled BSDF').inputs['Specular IOR Level'].default_value=.15
 bezel=material('k90_bezel',(.0015,.002,.0025),0,.18)
 flash=material('k90_flash_diffuser',(.83,.79,.59),0,.40)
 content=material('screen_content',(.025,.07,.11),0,1)
 shape('chassis_unibody',w,h,d-.0003,r,(0,0,0),silver,.0006)
 shape('back_glass_fiber',w-.00065,h-.00065,.00055,r-.00035,(0,0,-d/2+.000275),pearl,.00018)
 shape('screen_bezel',w-.00055,h-.00055,.00035,r-.0003,(0,0,d/2-.00030),bezel,.00008)
 sw,sh=g['screen_width_mm']*.001,g['screen_height_mm']*.001
 screen('screen_main_content',sw,sh,r-.0018,d/2-.00008,content)
 shape('selfie_punch_hole',.0028,.0028,.00012,.0014,(0,h/2-.0048,d/2-.00005),dark,.00002)
 shape('earpiece_grille',.012,.00045,.00014,.0002,(0,h/2-.0011,d/2-.00010),dark,.000025)
 # Volcanic metal platform: smooth sloping rings, separate from slab back.
 cx,cy=0,h/2-.026;N=len(outline(.071,.045,.008,24));vs=[]
 layers=6 if revision>=2 else 2
 for j in range(layers):
  t=j/(layers-1);ease=(1-math.cos(t*math.pi))/2
  ww=.071-.003*ease;hh=.045-.003*ease;rr=.008-.0009*ease;zz=-d/2-.00005-.00215*t
  vs.extend(cv((x+cx,y+cy,zz)) for x,y in outline(ww,hh,rr,24))
 fs=[]
 for j in range(layers-1):
  fs.extend((j*N+i,j*N+(i+1)%N,(j+1)*N+(i+1)%N,(j+1)*N+i) for i in range(N))
 fs.append(tuple(range((layers-1)*N,layers*N))[::-1]);mesh=bpy.data.meshes.new('k90_volcano_deco');mesh.from_pydata(vs,[],fs);mesh.update();ob=bpy.data.objects.new('camera_volcano_platform',mesh);bpy.context.collection.objects.link(ob);mesh.materials.append(platform)
 for p in mesh.polygons:p.use_smooth=p.index<len(mesh.polygons)-1
 # Coordinates are front-facing X; reverse positions when reading official rear photo.
 centers=[(.023,.010),(.006,.010),(.023,-.009),(.006,-.009)];face=-d/2-.00235
 for i,(x,y) in enumerate(centers):
  rad=.0071 if i in [0,2] else .0068
  shape('ring_'+str(i),rad*2,rad*2,.0007,rad,(x,cy+y,face-.00008),silver,.00009)
  shape('optic_'+str(i),rad*2-.0013,rad*2-.0013,.00025,rad-.00065,(x,cy+y,face-.00050),dark,.000025)
  if i==2:
   shape('periscope_window',.0066,.0093,.00012,.0018,(x,cy+y,face-.0007),optical,.00002)
  elif i in [0,1]:
   shape('optic_coating_'+str(i),.0045 if i==0 else .0031,.0045 if i==0 else .0031,.0001,.00225 if i==0 else .00155,(x,cy+y,face-.00069),optical,.000015)
  else:
   for j,(dx,dy,rad2) in enumerate([(-.0025,.0025,.0011),(.0025,.0025,.001), (0,0,.0017),(0,-.0039,.0005)]):disc('sensor_'+str(j),x+dx,cy+y+dy,face-.00068,rad2,optical,20)
 shape('camera_flash',.0028,.0028,.0002,.0014,(.0145,cy+.0005,face-.00065),flash,.00003)
 # Rear low-frequency speaker, broad circular cap + genuine geometry micro-perforation pattern.
 wx,wy=-.0215,cy;rr=.0086
 shape('woofer_outer_rim',rr*2,rr*2,.00035,rr,(wx,wy,face-.00008),silver,.00007)
 woofer=material('k90_woofer_brushed_mesh',(.23,.25,.25),.75,.39)
 shape('woofer_center',.014,.014,.00014,.007,(wx,wy,face-.00036),woofer,.000025)
 for ring in range(3):
  rad=.00725+ring*.00040
  for j in range(72):
   a=(j+(ring%2)*.5)*2*math.pi/72;disc('woofer_hole_'+str(ring)+'_'+str(j),wx+rad*math.cos(a),wy+rad*math.sin(a),face-.00046,.00018,dark,8)
 # Side keys, machined port and symmetric top/bottom loudspeaker slots.
 for name,yy,hh in [('volume',.031,.018),('power',.010,.011)]:
  orient(shape('key_'+name,.0028,hh,.0006,.0012,(0,0,0),silver,.00012),(w/2-.00005,yy,0),'right')
 orient(shape('port_usb_c',.0086,.0026,.00013,.0011,(0,0,0),dark,.000025),(0,-h/2-.00002,0),'bottom')
 orient(shape('port_usb_tongue',.0060,.0007,.00016,.00028,(0,0,0),silver,.00002),(0,-h/2-.00011,0),'bottom')
 for axis,yy in [('bottom',-h/2-.00003),('top',h/2+.00003)]:
  for j in range(5):orient(shape('port_slot_'+axis+str(j),.001,.0026,.00015,.00048,(0,0,0),dark,.000025),(-.025+j*.0022,yy,0),axis)
 orient(shape('port_sim_tray',.013,.0021,.00012,.0005,(0,0,0),dark,.000015),(.019,-h/2-.000025,0),'bottom')
 for name,x,y in [('bottom_mic',.009,-h/2-.00004),('top_mic',.012,h/2+.00004)]:orient(shape('port_'+name,.0009,.0009,.00012,.00045,(0,0,0),dark,.00001),(x,y,0),'top' if y>0 else 'bottom')
 # Low draw-call grouping, preserve semantic major structures.
 combine(['ring_'],'camera_lens_rings');combine(['optic_'],'camera_lens_glass');combine(['sensor_'],'camera_auxiliary_sensors');combine(['woofer_hole_'],'rear_speaker_perforations');combine(['port_'],'ports_and_loudspeaker_slots');combine(['key_'],'side_buttons')
 root=bpy.data.objects.new('device_root',None);bpy.context.collection.objects.link(root)
 for ob in list(bpy.context.scene.objects):
  if ob!=root:ob.parent=root
 out=Path(out) if out else ROOT/'app/src/main/assets/devices'/DEVICE/'portrait.glb';out.parent.mkdir(parents=True,exist_ok=True)
 bpy.ops.export_scene.gltf(filepath=str(out),export_format='GLB',export_yup=True,export_apply=True,export_extras=True,export_cameras=False,export_lights=False)
 print('EXPORTED',out)
if __name__=='__main__':
 args=sys.argv[sys.argv.index('--')+1:] if '--' in sys.argv else []
 build(args[0] if args and args[0]!='default' else None,int(args[1]) if len(args)>1 else 3)
