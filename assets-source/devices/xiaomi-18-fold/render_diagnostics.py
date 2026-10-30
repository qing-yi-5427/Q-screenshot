"""Render model diagnostics only; never Android export evidence."""
import bpy,sys,math
from pathlib import Path
from mathutils import Vector
ROOT=Path.cwd()
sys.path.insert(0,str(ROOT/'tools/blender'));from build_device import cv
args=sys.argv[sys.argv.index('--')+1:]; device=args[0]; state=args[1]
bpy.ops.object.select_all(action='SELECT');bpy.ops.object.delete(use_global=False)
bpy.ops.import_scene.gltf(filepath=str(ROOT/'app/src/main/assets/devices'/device/(state+'.glb')))
scene=bpy.context.scene;scene.render.engine='CYCLES';scene.cycles.samples=12;scene.cycles.use_denoising=True;scene.render.resolution_x=640;scene.render.resolution_y=640;scene.render.resolution_percentage=100
scene.world.color=(.3,.3,.3);scene.view_settings.view_transform='AgX';scene.render.image_settings.file_format='PNG';scene.render.film_transparent=True
# Original neutral test content (not vendor wallpaper).
m=bpy.data.materials.get('screen_content');m.use_nodes=True;nodes=m.node_tree.nodes;nodes.clear();out=nodes.new('ShaderNodeOutputMaterial');em=nodes.new('ShaderNodeEmission');em.inputs[0].default_value=(.025,.12,.20,1);em.inputs[1].default_value=.8;m.node_tree.links.new(em.outputs[0],out.inputs[0])
for name,pos,energy,size in [('key',(.12,.20,.22),.8,.25),('fill',(-.20,.02,.10),.45,.22),('rim',(.04,.12,-.19),1,.15)]:
 bpy.ops.object.light_add(type='AREA',location=cv(pos));light=bpy.context.object;light.name=name;light.data.energy=energy;light.data.shape='DISK';light.data.size=size;light.rotation_euler=(Vector((0,0,0))-light.location).to_track_quat('-Z','Y').to_euler()
bpy.ops.object.camera_add();camera=bpy.context.object;scene.camera=camera;camera.data.type='ORTHO';camera.data.ortho_scale=.22 if device=='mix-fold-4' or state=='unfolded' else .17
outdir=ROOT/'deliverables/xiaomi18-refresh/evidence/front-focused'/state;outdir.mkdir(parents=True,exist_ok=True)
views=[('front',0,0),('left-30',-30,0),('right-30',30,0),('top-20',0,20),('bottom-15',0,-15),('side',85,0)]
for name,yaw,pitch in views:
 y,p=map(math.radians,[yaw,pitch]);camera.location=cv((.5*math.sin(y)*math.cos(p),.5*math.sin(p),.5*math.cos(y)*math.cos(p)));camera.rotation_euler=(-camera.location).to_track_quat('-Z','Y').to_euler();scene.render.filepath=str(outdir/(name+'.png'));bpy.ops.render.render(write_still=True)
# Dedicated wire diagnostic with same front camera.
for ob in scene.objects:
 if ob.type=='MESH':ob.show_wire=True;ob.show_all_edges=True
scene.render.engine='BLENDER_WORKBENCH';scene.display.shading.light='STUDIO';scene.display.shading.show_cavity=True;scene.display.shading.show_object_outline=True
camera.location=cv((.15,.09,.5));camera.rotation_euler=(-camera.location).to_track_quat('-Z','Y').to_euler();scene.render.filepath=str(outdir/'wire-diagnostic.png');bpy.ops.render.render(write_still=True)

scene.render.engine='CYCLES';scene.cycles.samples=4
for mat in bpy.data.materials:
 if not mat.use_nodes:continue
 nodes=mat.node_tree.nodes;nodes.clear();output=nodes.new('ShaderNodeOutputMaterial');emit=nodes.new('ShaderNodeEmission');geo=nodes.new('ShaderNodeNewGeometry');mapping=nodes.new('ShaderNodeVectorMath');mapping.operation='MULTIPLY_ADD';mapping.inputs[1].default_value=(.5,.5,.5);mapping.inputs[2].default_value=(.5,.5,.5);mat.node_tree.links.new(geo.outputs['Normal'],mapping.inputs[0]);mat.node_tree.links.new(mapping.outputs[0],emit.inputs[0]);mat.node_tree.links.new(emit.outputs[0],output.inputs[0])
camera.location=cv((.16,.08,.5));camera.rotation_euler=(-camera.location).to_track_quat('-Z','Y').to_euler();scene.render.filepath=str(outdir/'normal-diagnostic.png');bpy.ops.render.render(write_still=True)
