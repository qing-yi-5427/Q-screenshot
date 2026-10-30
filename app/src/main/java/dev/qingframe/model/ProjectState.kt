package dev.qingframe.model

import kotlin.math.*

data class Quaternion(val x:Float=0f,val y:Float=0f,val z:Float=0f,val w:Float=1f) {
    fun normalized(): Quaternion { val n2=x*x+y*y+z*z+w*w;if(!n2.isFinite()||n2<1e-12f)return Quaternion();if(abs(n2-1f)<1e-6f)return this;val n=sqrt(n2);return Quaternion(x/n,y/n,z/n,w/n) }
    operator fun times(b:Quaternion)=Quaternion(w*b.x+x*b.w+y*b.z-z*b.y,w*b.y-x*b.z+y*b.w+z*b.x,w*b.z+x*b.y-y*b.x+z*b.w,w*b.w-x*b.x-y*b.y-z*b.z).normalized()
    fun matrix():FloatArray { val q=normalized(); val (x,y,z,w)=q; return floatArrayOf(1-2*y*y-2*z*z,2*x*y+2*w*z,2*x*z-2*w*y,0f,2*x*y-2*w*z,1-2*x*x-2*z*z,2*y*z+2*w*x,0f,2*x*z+2*w*y,2*y*z-2*w*x,1-2*x*x-2*y*y,0f,0f,0f,0f,1f) }
    fun euler():FloatArray { val m=matrix(); val pitch=asin((-m[9]).coerceIn(-1f,1f)); val yaw=atan2(m[8],m[10]); val roll=atan2(m[1],m[5]); return floatArrayOf(yaw*180f/PI.toFloat(),pitch*180f/PI.toFloat(),roll*180f/PI.toFloat()) }
    companion object {
        fun axis(x:Float,y:Float,z:Float,degrees:Float):Quaternion { val h=degrees*PI.toFloat()/360f; return Quaternion(x*sin(h),y*sin(h),z*sin(h),cos(h)) }
        fun euler(yaw:Float,pitch:Float,roll:Float)=axis(0f,1f,0f,yaw)*axis(1f,0f,0f,pitch)*axis(0f,0f,1f,roll)
    }
}

data class ProjectState(
    val schemaVersion:Int=1,
    val deviceId:String="xiaomi-18-fold", val stateId:String="folded", val portrait:Boolean=false,
    val colorwayId:String=if(deviceId=="redmi-k90-pro-max")"flowing-gold-white"else"warm-gold-white",
    val imagePath:String="", val imageWidth:Int=1168,val imageHeight:Int=1712,
    val pose:Quaternion=Quaternion.euler(-22f,12f,-6f),val zoom:Float=1f,val offsetX:Float=0f,val offsetY:Float=0f,
    val fitMode:String="fit",val cropX:Float=0f,val cropY:Float=0f,
    val fov:Float=35f,val orthographic:Boolean=false,
    val lighting:String="soft",val lightDirection:Float=-35f,val lightIntensity:Float=1f,
    val environmentRotation:Float=0f,val reflection:Float=.12f,val shadow:Float=.55f,val softness:Float=.6f,val grounded:Boolean=false,
    val background:String="gradient",val colorA:Int=0xffe9ede8.toInt(),val colorB:Int=0xffb9c7c3.toInt(),
    val canvasWidth:Int=4,val canvasHeight:Int=5,val exportLongEdge:Int=2560
) {
    fun outputSize():Pair<Int,Int> { val scale=exportLongEdge.toDouble()/max(canvasWidth,canvasHeight); return Pair(max(1,(canvasWidth*scale).roundToInt()),max(1,(canvasHeight*scale).roundToInt())) }
    fun safe()=FrontRotation.constrain(DeviceCatalog.normalize(copy(zoom=zoom.coerceIn(.35f,3f),offsetX=offsetX.coerceIn(-1f,1f),offsetY=offsetY.coerceIn(-1f,1f),fov=fov.coerceIn(20f,65f),reflection=reflection.coerceIn(0f,1f),shadow=shadow.coerceIn(0f,1f),pose=pose.normalized(),canvasWidth=canvasWidth.coerceIn(1,8192),canvasHeight=canvasHeight.coerceIn(1,8192),exportLongEdge=exportLongEdge.coerceIn(256,3840),cropX=cropX.coerceIn(-1f,1f),cropY=cropY.coerceIn(-1f,1f))))
}

data class ScreenMapping(val scaleX:Float,val scaleY:Float,val offsetX:Float,val offsetY:Float)
fun screenMapping(iw:Int,ih:Int,screenAspect:Float,fill:Boolean,panX:Float=0f,panY:Float=0f):ScreenMapping {
    require(iw>0 && ih>0 && screenAspect>0 && screenAspect.isFinite())
    val imageAspect=iw.toFloat()/ih
    val drawnW:Float; val drawnH:Float
    if ((imageAspect>screenAspect) xor fill) { drawnW=1f; drawnH=screenAspect/imageAspect } else { drawnH=1f; drawnW=imageAspect/screenAspect }
    val ox=(1-drawnW)/2+panX.coerceIn(-1f,1f)*abs(1-drawnW)/2
    val oy=(1-drawnH)/2+panY.coerceIn(-1f,1f)*abs(1-drawnH)/2
    return ScreenMapping(1/drawnW,1/drawnH,-ox/drawnW,-oy/drawnH)
}
fun recommendState(w:Int,h:Int):Pair<String,Boolean> { require(w>0&&h>0); return when(Pair(w,h)) { Pair(1168,1712)->"folded" to false; Pair(2364,1672)->"unfolded" to false; Pair(1672,2364)->"unfolded" to true; else->if(w.toFloat()/h>1.1f) "unfolded" to false else "folded" to false } }
fun cameraDistance(width:Float,height:Float,aspect:Float,fov:Float):Double { require(width>0&&height>0&&aspect>0); return max(height,width/aspect).toDouble()*.5/tan(fov*PI/360)*1.4 }
fun validateOutput(w:Int,h:Int):Boolean=w in 256..3840 && h in 256..3840 && w.toLong()*h<=14_745_600L

class History<T>(private val capacity:Int=40) {
    private val past=ArrayDeque<T>(); private val future=ArrayDeque<T>(); private var checkpoint:T?=null
    val canUndo get()=past.isNotEmpty(); val canRedo get()=future.isNotEmpty()
    fun begin(state:T) { if(checkpoint==null) checkpoint=state }
    fun commit(state:T) { val start=checkpoint?:return; checkpoint=null; if(start!=state){ past.addLast(start); if(past.size>capacity)past.removeFirst(); future.clear() } }
    fun push(old:T,new:T){ begin(old); commit(new) }
    fun undo(current:T):T { checkpoint=null; if(past.isEmpty())return current; future.addLast(current); return past.removeLast() }
    fun referencedStates(current:T):List<T> = past.toList()+future.toList()+listOfNotNull(checkpoint)+current
    fun redo(current:T):T { checkpoint=null; if(future.isEmpty())return current; past.addLast(current);return future.removeLast() }
}

/** Filament translucent output is premultiplied. Bitmap.setPixels requires straight ARGB. */
fun unpremultiplyArgb(r:Int,g:Int,b:Int,a:Int):Int {
    if(a==0)return 0
    fun straight(c:Int)=if(a==255)c else ((c*255+a/2)/a).coerceIn(0,255)
    return (a shl 24) or (straight(r) shl 16) or (straight(g) shl 8) or straight(b)
}


/** Fit transformed 3D corners to the camera frustum. The margin is shared by preview/export. */
data class CameraFit(val distance:Double,val halfHeight:Double)
fun fitCamera(width:Float,height:Float,depth:Float,m:FloatArray,aspect:Float,fov:Float,ortho:Boolean):CameraFit {
 require(width>0&&height>0&&depth>0&&aspect>0)
 val tilt=if(ortho)0.0 else .18;val norm=sqrt(1+tilt*tilt);val tangent=tan(fov*PI/360)
 var eye=0.0;var extent=0.0
 for(x in listOf(-width/2,width/2))for(y in listOf(-height/2,height/2))for(z in listOf(-depth/2,depth/2)){
  val px=m[0]*x+m[4]*y+m[8]*z;val py=m[1]*x+m[5]*y+m[9]*z;val pz=m[2]*x+m[6]*y+m[10]*z
  val cy=(py-tilt*pz)/norm;val cz=(tilt*py+pz)/norm
  extent=max(extent,max(abs(px).toDouble()/aspect,abs(cy)))
  eye=max(eye,max(abs(px).toDouble()/(tangent*aspect),abs(cy)/tangent)*1.12+cz)
 }
 return CameraFit(max(.04,eye/norm),extent*1.12)
}


/** Existing angle actions fit a front-facing screenshot, without changing its canvas or source. */
fun anglePreset(s:ProjectState,name:String):ProjectState {
 val pose=when(name){"正面"->Quaternion();"左前"->Quaternion.euler(-30f,12f,-6f);"右前"->Quaternion.euler(30f,12f,6f);"俯视"->Quaternion.euler(-12f,35f,-5f);else->Quaternion.euler(-18f,15f,90f)}
 return s.copy(pose=pose,orthographic=name=="正面",reflection=if(name=="正面")0f else s.reflection,zoom=1f,offsetX=0f,offsetY=0f)
}
