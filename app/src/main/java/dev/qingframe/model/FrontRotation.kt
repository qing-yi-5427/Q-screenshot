package dev.qingframe.model

import kotlin.math.*

/** A front hemisphere shared by gestures, persisted state, preview and export. */
object FrontRotation {
 const val YAW_LIMIT=60f
 const val PITCH_LIMIT=45f
 const val MIN_FACING=.05f
 private fun degrees(v:Float)=((v+180f)%360f+360f)%360f-180f
 fun equivalent(a:Quaternion,b:Quaternion,tolerance:Float=2e-6f):Boolean {
  val x=a.normalized();val y=b.normalized()
  fun distance(sign:Float)=maxOf(abs(x.x-sign*y.x),abs(x.y-sign*y.y),abs(x.z-sign*y.z),abs(x.w-sign*y.w))
  return min(distance(1f),distance(-1f))<=tolerance
 }
 private fun body(s:ProjectState):FloatArray?=DeviceGeometryCatalog.find(s)?.let{floatArrayOf(it.width,it.height,ColorwayCatalog.depth(s,it.depth))}
 /** Perspective uses the actual panned camera eye; orthographic uses its parallel ray direction. */
 fun facing(s:ProjectState):Float {
  val m=s.pose.matrix();if(s.orthographic)return m[10]
  val b=body(s)?:return m[10];val matrix=(s.pose*if(s.portrait)Quaternion.axis(0f,0f,1f,90f)else Quaternion()).matrix()
  val d=fitCamera(b[0],b[1],b[2]+.006f,matrix,s.canvasWidth.toFloat()/s.canvasHeight,s.fov,false).distance/s.zoom
  val bw=if(s.portrait)b[1]else b[0];val bh=if(s.portrait)b[0]else b[1]
  val z=b[2]/2+.0005f
  val ex=-s.offsetX*bw-m[8]*z;val ey=s.offsetY*bh+d*.18-m[9]*z;val ez=max(d,.035)-m[10]*z
  return ((m[8]*ex+m[9]*ey+m[10]*ez)/sqrt(ex*ex+ey*ey+ez*ez)).toFloat()
 }
 fun allowed(s:ProjectState):Boolean {
  val q=s.pose;if(listOf(q.x,q.y,q.z,q.w).any{!it.isFinite()})return false
  val e=q.euler()
  return abs(e[0])<=YAW_LIMIT+.0001f&&abs(e[1])<=PITCH_LIMIT+.0001f&&facing(s)>=MIN_FACING
 }
 fun constrain(s:ProjectState):ProjectState {
  val q=s.pose.normalized();val normalized=s.copy(pose=q)
  if(body(normalized)==null)return normalized // Missing pinned metadata must not borrow Fold geometry and rewrite a draft.
  if(allowed(normalized))return normalized
  val e=q.euler()
  // A loaded rear-facing pose is safely returned to a readable front, with no source/layout changes.
  if(q.matrix()[10]<=0f)return s.copy(pose=Quaternion())
  val yaw=e[0].coerceIn(-YAW_LIMIT,YAW_LIMIT);val pitch=e[1].coerceIn(-PITCH_LIMIT,PITCH_LIMIT);val roll=degrees(e[2])
  val candidate=s.copy(pose=Quaternion.euler(yaw,pitch,roll))
  if(allowed(candidate))return candidate
  var lo=0f;var hi=1f
  repeat(22){val mid=(lo+hi)/2;val at=s.copy(pose=Quaternion.euler(yaw*mid,pitch*mid,roll));if(facing(at)>=MIN_FACING+.0002f)lo=mid else hi=mid}
  return s.copy(pose=Quaternion.euler(yaw*lo,pitch*lo,roll))
 }
 fun drag(s:ProjectState,dx:Float,dy:Float):ProjectState {
  val e=s.pose.euler();val yaw=(e[0]+dx*.35f).coerceIn(-YAW_LIMIT,YAW_LIMIT);val pitch=(e[1]+dy*.35f).coerceIn(-PITCH_LIMIT,PITCH_LIMIT)
  val next=constrain(s.copy(pose=Quaternion.euler(yaw,pitch,e[2])))
  return if(equivalent(s.pose,next.pose))s else next
 }
 fun roll(s:ProjectState,degrees:Float):ProjectState {
  // Local Z changes screen roll without rotating its front normal around world Z.
  val next=constrain(s.copy(pose=s.pose*Quaternion.axis(0f,0f,1f,degrees)))
  return if(equivalent(s.pose,next.pose))s else next
 }
}
