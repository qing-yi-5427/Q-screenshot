package dev.qingframe

import dev.qingframe.model.*
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.*

class FrontRotationTest {
 @Test fun downloadedGeometryAndPinnedVersionsConstrainExtremePanWithoutBorrowingFold(){
  for((id,w,h) in listOf(Triple("test-small-slab",.0712f,.1523f),Triple("test-large-slab",.078f,.166f))){
   DeviceGeometryCatalog.register(id,"1.0.0","portrait",DeviceGeometry(w,h,.008f,1200,2608))
   DeviceGeometryCatalog.register(id,"2.0.0","portrait",DeviceGeometry(w*.9f,h*.95f,.009f,1220,2670))
   val old=ProjectState(deviceId=id,stateId="portrait",modelVersion="1.0.0")
   assertEquals(w,DeviceGeometryCatalog.find(old)!!.width,0f)
   assertEquals(w*.9f,DeviceGeometryCatalog.find(old.copy(modelVersion="2.0.0"))!!.width,0f)
   for(x in listOf(-1f,1f))for(y in listOf(-1f,1f))for(zoom in listOf(.35f,3f)){
    val candidate=old.copy(pose=Quaternion.euler(60f,45f,30f),offsetX=x,offsetY=y,zoom=zoom,fov=65f)
    val safe=FrontRotation.constrain(candidate);assertTrue(FrontRotation.allowed(safe));assertTrue(FrontRotation.equivalent(safe.pose,FrontRotation.constrain(safe).pose))
   }
   val missing=old.copy(modelVersion="not-installed",pose=Quaternion.euler(75f,55f,20f));assertEquals(missing.pose,FrontRotation.constrain(missing).pose)
  }
 }
 @Test fun validFrontPosesArePreservedExactly(){
  for(y in listOf(-55f,0f,55f))for(p in listOf(-40f,0f,40f))for(r in listOf(-170f,0f,170f)){
   val s=ProjectState(pose=Quaternion.euler(y,p,r));assertTrue(FrontRotation.allowed(s));assertEquals(s.pose,FrontRotation.constrain(s).pose)
  }
 }
 @Test fun compoundPosesRespectActualCameraAndAreIdempotent(){
  for(y in listOf(-175f,-61f,-60f,0f,60f,61f,175f))for(p in listOf(-85f,-45f,0f,45f,85f))for(r in listOf(-179f,90f,179f))for(pan in listOf(-1f,0f,1f))for(ortho in listOf(false,true)){
   val s=ProjectState(stateId="unfolded",portrait=r==90f,pose=Quaternion.euler(y,p,r),zoom=3f,offsetX=pan,offsetY=pan,fov=65f,orthographic=ortho)
   val safe=s.safe();assertTrue("$y/$p/$r/$pan/$ortho facing=${FrontRotation.facing(safe)}",FrontRotation.allowed(safe))
   var again=safe;repeat(100){again=again.safe()};assertTrue(FrontRotation.equivalent(safe.pose,again.pose));assertEquals(safe.imagePath,again.imagePath)
  }
 }
 @Test fun limitRejectsOvershootButReversesImmediately(){
  val start=ProjectState(pose=Quaternion.euler(60f,0f,0f))
  assertEquals(start,FrontRotation.drag(start,1000f,0f))
  val reversed=FrontRotation.drag(start,-1f,0f);assertTrue(reversed.pose.euler()[0]<59.9f)
  val h=History<ProjectState>();h.begin(start);h.commit(FrontRotation.drag(start,1000f,0f));assertFalse(h.canUndo)
 }
 @Test fun rollWrapIsShortAndDoesNotTiltFrontNormal(){
  val start=ProjectState(pose=Quaternion.euler(30f,20f,179f));val after=FrontRotation.roll(start,2f)
  val a=start.pose.matrix();val b=after.pose.matrix();for(i in 8..10)assertEquals(a[i],b[i],1e-5f)
  val q=start.pose;val n=after.pose;val dot=abs(q.x*n.x+q.y*n.y+q.z*n.z+q.w*n.w).coerceIn(0f,1f)
  assertEquals(2f,(2*acos(dot)*180/PI).toFloat(),.01f);assertTrue(after.pose.euler()[2]<-178f)
 }
 @Test fun rearAndInvalidQuaternionsBecomeSafeWithoutReplacingComposition(){
  val old=ProjectState(imagePath="source-keep.img",pose=Quaternion.euler(150f,10f,24f),zoom=1.7f,canvasWidth=9,canvasHeight=16,reflection=.7f)
  val safe=old.safe();assertEquals(Quaternion(),safe.pose);assertEquals(old.copy(pose=Quaternion()),safe)
  assertTrue(FrontRotation.allowed(ProjectState(pose=Quaternion(Float.NaN,0f,0f,0f)).safe()))
 }
}
