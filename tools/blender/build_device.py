"""Shared original mesh primitives. Device production uses dedicated generators.
Legacy generic/MIX assembly removed; historical source remains in Git commits.
"""
import bpy, math, json, sys
from pathlib import Path
from mathutils import Vector
ROOT=Path(__file__).resolve().parents[2]
# Modeling coordinates are runtime (x,y,z); Blender conversion C=(x,-z,y).
def cv(v): return (v[0],-v[2],v[1])
def material(name,color,metal=0,rough=.3):
 m=bpy.data.materials.new(name); m.diffuse_color=(*color,1); m.use_nodes=True
 p=m.node_tree.nodes.get('Principled BSDF'); p.inputs['Base Color'].default_value=(*color,1); p.inputs['Metallic'].default_value=metal; p.inputs['Roughness'].default_value=rough
 return m
def outline(w,h,r,n=24):
 return [(cx+r*math.cos(a+i*math.pi/2/n),cy+r*math.sin(a+i*math.pi/2/n)) for cx,cy,a in [(w/2-r,h/2-r,0),(-w/2+r,h/2-r,math.pi/2),(-w/2+r,-h/2+r,math.pi),(w/2-r,-h/2+r,3*math.pi/2)] for i in range(n+1)]
def shape(name,w,h,d,r,center,mat,bevel=.00035,profile=None):
 pts=outline(w,h,min(r,w/2,h/2))
 N=len(pts)
 vs=[cv((x+center[0],y+center[1],z+center[2])) for z in [-d/2,d/2] for x,y in pts]
 fs=[tuple(range(N-1,-1,-1)),tuple(range(N,2*N))]+[(i,(i+1)%N,(i+1)%N+N,i+N) for i in range(N)]
 mesh=bpy.data.meshes.new(name); mesh.from_pydata(vs,[],fs); mesh.update(); ob=bpy.data.objects.new(name,mesh); bpy.context.collection.objects.link(ob); ob.data.materials.append(mat)
 if bevel and d>0:
  mod=ob.modifiers.new('machined_edge','BEVEL'); mod.width=min(bevel,d*.3); mod.segments=4
  mod=ob.modifiers.new('weighted_normals','WEIGHTED_NORMAL')
 for p in mesh.polygons:p.use_smooth=(p.index>=2)
 return ob
def screen(name,w,h,r,z,mat):
 pts=outline(w,h,r,32); N=len(pts); vs=[cv((0,0,z))]+[cv((x,y,z)) for x,y in pts]
 fs=[(0,i+1,(i+1)%N+1) for i in range(N)]
 mesh=bpy.data.meshes.new(name); mesh.from_pydata(vs,[],fs); mesh.update(); uv=mesh.uv_layers.new(name='UVMap')
 # Blender exporter flips V; Blender V up => glTF V down.
 for p in mesh.polygons:
  for li in p.loop_indices:
   v=vs[mesh.loops[li].vertex_index]; uv.data[li].uv=(v[0]/w+.5,v[2]/h+.5)
 ob=bpy.data.objects.new(name,mesh); bpy.context.collection.objects.link(ob); ob.data.materials.append(mat); return ob
