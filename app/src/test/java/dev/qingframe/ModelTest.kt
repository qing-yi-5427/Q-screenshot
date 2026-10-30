package dev.qingframe
import dev.qingframe.model.*
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs
class ModelTest {
 @Test fun recommendations(){ assertEquals("folded" to false,recommendState(1168,1712));assertEquals("unfolded" to false,recommendState(2364,1672));assertEquals("unfolded" to true,recommendState(1672,2364)) }
 @Test fun fitPreservesAspect(){ val m=screenMapping(100,200,1f,false);assertEquals(2f,m.scaleX,.001f);assertEquals(1f,m.scaleY,.001f); val f=screenMapping(100,200,1f,true,1f,1f);assertEquals(1f,f.scaleX,.001f);assertEquals(.5f,f.scaleY,.001f);assertTrue(f.offsetY>=0f) }
 @Test fun quaternionStable(){ var q=Quaternion(); repeat(10000){q=Quaternion.axis(0f,1f,0f,.1f)*q};assertEquals(1f,q.x*q.x+q.y*q.y+q.z*q.z+q.w*q.w,.0001f);val right=Quaternion.axis(0f,1f,0f,90f).matrix();assertEquals(1f,right[8],.001f) }
 @Test fun cameraAndOutput(){assertTrue(cameraDistance(.1f,.17f,.5f,35f)>cameraDistance(.1f,.17f,1f,35f));assertEquals(2160 to 3840,ProjectState(canvasWidth=9,canvasHeight=16,exportLongEdge=3840).outputSize());assertTrue(validateOutput(2160,3840));assertFalse(validateOutput(8192,8192))}
 @Test fun undoCoalesces(){val h=History<Int>();h.begin(0);h.begin(1);h.commit(10);assertEquals(0,h.undo(10));assertFalse(h.canUndo);assertEquals(10,h.redo(0));repeat(50){h.push(it,it+1)};var count=0;while(h.canUndo){h.undo(0);count++};assertEquals(40,count)}
 @Test fun alphaConversion(){assertEquals(0,unpremultiplyArgb(80,80,80,0));assertEquals(0x37585858,unpremultiplyArgb(19,19,19,55));assertEquals(0xff123456.toInt(),unpremultiplyArgb(18,52,86,255))}
 @Test(expected=IllegalArgumentException::class) fun badImage(){screenMapping(0,10,1f,false)}
}
