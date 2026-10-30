package dev.qingframe

import dev.qingframe.model.*
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.*

class CameraFitTest {
 @Test fun fittedPoseCornersStayInsidePerspectiveFrustum(){
  for(aspect in listOf(.54f,.8f,16f/9f))for(yaw in listOf(0f,-32f,88f,155f))for(roll in listOf(0f,-6f,90f)){
   val m=Quaternion.euler(yaw,12f,roll).matrix();val fit=fitCamera(.07782f,.16333f,.014f,m,aspect,35f,false)
   val n=sqrt(1+.18*.18);val t=tan(35*PI/360)
   for(x in listOf(-.07782/2,.07782/2))for(y in listOf(-.16333/2,.16333/2))for(z in listOf(-.014/2,.014/2)){
    val px=m[0]*x+m[4]*y+m[8]*z;val py=m[1]*x+m[5]*y+m[9]*z;val pz=m[2]*x+m[6]*y+m[10]*z
    val cy=(py-.18*pz)/n;val cz=(.18*py+pz)/n;val distance=fit.distance*n-cz
    assertTrue(abs(px)/(distance*t*aspect)<.90);assertTrue(abs(cy)/(distance*t)<.90)
   }
  }
 }
 @Test fun orthographicFitUsesRotatedExtent(){
  val pose=Quaternion.euler(25f,12f,90f).matrix();val fit=fitCamera(.08f,.16f,.014f,pose,.54f,35f,true)
  for(x in listOf(-.04f,.04f))for(y in listOf(-.08f,.08f))for(z in listOf(-.007f,.007f)){
   assertTrue(abs(pose[0]*x+pose[4]*y+pose[8]*z)<fit.halfHeight*.54)
   assertTrue(abs(pose[1]*x+pose[5]*y+pose[9]*z)<fit.halfHeight)
  }
 }
 @Test fun frontPresetIsFlatAndClearWithoutReplacingDraft(){
  val before=ProjectState(deviceId="xiaomi-18-fold",stateId="unfolded",portrait=true,imagePath="source-test.img",zoom=2f,offsetX=.4f,offsetY=-.3f,reflection=.8f,shadow=.2f,canvasWidth=9,canvasHeight=16)
  val after=anglePreset(before,"正面")
  assertEquals(Quaternion(),after.pose);assertTrue(after.orthographic);assertEquals(0f,after.reflection,0f)
  assertEquals(1f,after.zoom,0f);assertEquals(0f,after.offsetX,0f);assertEquals(0f,after.offsetY,0f)
  assertEquals(before.imagePath,after.imagePath);assertEquals(before.stateId,after.stateId);assertTrue(after.portrait);assertEquals(9,after.canvasWidth);assertEquals(16,after.canvasHeight);assertEquals(.2f,after.shadow,0f)
 }
 @Test fun obliquePresetReturnsToPerspective(){
  val before=ProjectState(orthographic=true,reflection=.25f,lighting="cool")
  val side=anglePreset(before,"左前");assertFalse(side.orthographic);assertEquals(.25f,side.reflection,0f);assertEquals("cool",side.lighting)
 }

}
