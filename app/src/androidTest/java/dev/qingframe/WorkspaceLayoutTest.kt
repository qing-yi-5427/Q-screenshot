package dev.qingframe

import android.app.Activity
import android.content.Intent
import android.graphics.Rect
import android.os.Build
import android.os.SystemClock
import android.util.DisplayMetrics
import android.view.accessibility.AccessibilityNodeInfo
import android.view.MotionEvent
import android.view.InputDevice
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.qingframe.model.ProjectStore
import dev.qingframe.model.StateJson
import dev.qingframe.model.isWideWorkspace
import dev.qingframe.model.ProjectState
import dev.qingframe.model.anglePreset
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

/** Parameterized by the actual window; external runner owns and restores display overrides. */
@RunWith(AndroidJUnit4::class)
class WorkspaceLayoutTest {
    private val ins get()=InstrumentationRegistry.getInstrumentation()
    private fun find(id:String):AccessibilityNodeInfo? {
        fun walk(n:AccessibilityNodeInfo):AccessibilityNodeInfo? {
            if(n.isVisibleToUser&&n.contentDescription?.toString()==id)return n
            for(i in 0 until n.childCount)n.getChild(i)?.let{walk(it)?.let{return it}}
            return null
        }
        return ins.uiAutomation.rootInActiveWindow?.let(::walk)
    }
    private fun node(id:String):AccessibilityNodeInfo {
        val end=SystemClock.uptimeMillis()+6000
        do{find(id)?.let{return it};SystemClock.sleep(50)}while(SystemClock.uptimeMillis()<end)
        throw AssertionError("Missing $id")
    }
    private fun rect(id:String)=Rect().also{node(id).getBoundsInScreen(it)}
    private fun actionNode(id:String):AccessibilityNodeInfo {var n=node(id);while(!n.isClickable)n=n.parent?:throw AssertionError("Not actionable: $id");return n}
    private fun click(id:String){assertTrue(actionNode(id).performAction(AccessibilityNodeInfo.ACTION_CLICK));ins.waitForIdleSync();SystemClock.sleep(350)}
    @Suppress("DEPRECATION")
    private fun editor(block:(Activity,String,Boolean)->Unit){
        val session="workspace-${UUID.randomUUID().toString().take(12)}"
        val a=ins.startActivitySync(Intent(ins.targetContext,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK).putExtra("test_session",session))
        try{
            node("qf-preview");val bounds=Rect()
            ins.runOnMainSync{
                if(Build.VERSION.SDK_INT>=30)bounds.set(a.windowManager.currentWindowMetrics.bounds)
                else {val metrics=DisplayMetrics();a.windowManager.defaultDisplay.getRealMetrics(metrics);bounds.set(0,0,metrics.widthPixels,metrics.heightPixels)}
            }
            val density=a.resources.displayMetrics.density
            val expected=isWideWorkspace(bounds.width()/density,bounds.height()/density)
            val until=SystemClock.uptimeMillis()+3000
            while((find("qf-workspace-side")!=null)!=expected&&SystemClock.uptimeMillis()<until)SystemClock.sleep(50)
            assertEquals("Mode follows full WindowMetrics, not reduced preview/IME area",expected,find("qf-workspace-side")!=null)
            block(a,session,expected)
        }finally{ins.runOnMainSync{a.finish()}}
    }
    @Test fun thresholdContractIncludesCoverAndPreservesTraditionalPortrait(){
        assertTrue(isWideWorkspace(425f,623f));assertFalse(isWideWorkspace(412f,915f))
        assertFalse(isWideWorkspace(399f,623f));assertTrue(isWideWorkspace(400f,660f));assertFalse(isWideWorkspace(400f,660.1f))
        assertTrue(isWideWorkspace(600f,1800f));assertFalse(isWideWorkspace(599f,1800f))
    }
    @Test fun explicitSidePresetsUseLowCameraAngleWithoutReplacingProject(){
        val original=ProjectState(imagePath="source-keep.img",canvasWidth=9,canvasHeight=16,colorwayId="black").safe()
        val left=anglePreset(original,"左前");val right=anglePreset(original,"右前")
        val l=left.pose.euler();val r=right.pose.euler()
        assertEquals(-30f,l[0],.01f);assertEquals(30f,r[0],.01f)
        assertEquals(-18f,l[1],.01f);assertEquals(-18f,r[1],.01f)
        assertEquals(6f,kotlin.math.abs(l[2]),.01f);assertEquals(-l[2],r[2],.01f)
        for(state in listOf(left,right)){
            assertEquals(original.imagePath,state.imagePath);assertEquals(original.colorwayId,state.colorwayId)
            assertEquals(original.canvasWidth,state.canvasWidth);assertEquals(original.canvasHeight,state.canvasHeight)
        }
    }
    @Test fun parameterScrollAndFullscreenArePresentationOnly()=editor{_,session,wide->
        val store=ProjectStore(ins.targetContext,session);val before=StateJson.encode(store.load())
        click("qf-tab-angle")
        for(label in listOf("正面","左前","右前","俯视")){
            fun visiblePreset():AccessibilityNodeInfo? {
                fun walk(n:AccessibilityNodeInfo):AccessibilityNodeInfo? {
                    if(n.isVisibleToUser&&n.text?.toString()==label)return n
                    for(i in 0 until n.childCount)n.getChild(i)?.let{walk(it)?.let{return it}}
                    return null
                }
                return ins.uiAutomation.rootInActiveWindow?.let(::walk)
            }
            var found=visiblePreset()
            repeat(5){if(found==null){node("qf-workspace-parameters").performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD);SystemClock.sleep(150);found=visiblePreset()}}
            var preset=found?:throw AssertionError("Preset unreachable by scrolling: $label")
            while(!preset.isClickable)preset=preset.parent?:throw AssertionError("Preset not actionable: $label")
            assertTrue("Preset enabled: $label",preset.isEnabled)
        }
        fun hasHorizontal(n:AccessibilityNodeInfo):Boolean {
            if(n.isVisibleToUser&&n.text?.toString()=="横放")return true
            for(i in 0 until n.childCount)if(n.getChild(i)?.let(::hasHorizontal)==true)return true
            return false
        }
        assertFalse("Removed horizontal preset must not appear",hasHorizontal(requireNotNull(ins.uiAutomation.rootInActiveWindow)))
        click("qf-tab-device")
        if(wide){
            // A fresh Redmi device pane can fit entirely; choose a genuinely long pane instead.
            click("qf-tab-light")
            val preview=rect("qf-preview");val side=rect("qf-panel")
            assertFalse("Parameter pane cannot cover preview",Rect.intersects(preview,side))
            fun lightReady(n:AccessibilityNodeInfo):Boolean {
                if(n.isVisibleToUser&&n.text?.toString()=="摄影棚")return true
                for(i in 0 until n.childCount)if(n.getChild(i)?.let(::lightReady)==true)return true
                return false
            }
            val lightDeadline=SystemClock.uptimeMillis()+4000
            while(ins.uiAutomation.rootInActiveWindow?.let(::lightReady)!=true&&SystemClock.uptimeMillis()<lightDeadline)SystemClock.sleep(80)
            assertTrue("Light pane has actually replaced previous tab",ins.uiAutomation.rootInActiveWindow?.let(::lightReady)==true)
            fun visibleParameterTexts():List<String> {
                val entries=mutableListOf<String>()
                fun visit(n:AccessibilityNodeInfo){
                    n.refresh()
                    val b=Rect().also{n.getBoundsInScreen(it)}
                    if(n.isVisibleToUser&&!n.text.isNullOrBlank()&&Rect.intersects(b,side))entries.add("${n.text}@${b.left},${b.top}")
                    for(i in 0 until n.childCount)n.getChild(i)?.let(::visit)
                }
                ins.uiAutomation.rootInActiveWindow?.let(::visit)
                return entries.sorted()
            }
            val beforeTexts=visibleParameterTexts()
            check(Build.HARDWARE in setOf("ranchu","goldfish")||Build.MODEL.contains("gphone")){"Touch scrolling probe is emulator-only"}
            val scroll=rect("qf-workspace-parameters")
            fun directionLabel(n:AccessibilityNodeInfo):AccessibilityNodeInfo? {
                if(n.isVisibleToUser&&n.text?.toString()=="灯光方位")return n
                for(i in 0 until n.childCount)n.getChild(i)?.let{directionLabel(it)?.let{return it}}
                return null
            }
            val label=requireNotNull(ins.uiAutomation.rootInActiveWindow?.let(::directionLabel)){"Visible light-direction header required"}
            val labelBounds=Rect().also{label.getBoundsInScreen(it)}
            // Inside the header's blank label/value gap, away from screen-edge gestures and sliders.
            val x=scroll.exactCenterX()
            val yStart=labelBounds.exactCenterY();val yEnd=maxOf(scroll.top+24*ins.targetContext.resources.displayMetrics.density,yStart-scroll.height()*.35f)
            val downTime=SystemClock.uptimeMillis()
            fun inject(action:Int,y:Float){
                val event=MotionEvent.obtain(downTime,SystemClock.uptimeMillis(),action,x,y,0)
                event.source=InputDevice.SOURCE_TOUCHSCREEN
                try{assertTrue("Real parameter touch injected",ins.uiAutomation.injectInputEvent(event,true))}finally{event.recycle()}
            }
            inject(MotionEvent.ACTION_DOWN,yStart)
            for(step in 1..12){SystemClock.sleep(25);inject(MotionEvent.ACTION_MOVE,yStart+(yEnd-yStart)*step/12)}
            inject(MotionEvent.ACTION_UP,yEnd);SystemClock.sleep(600)
            assertNotEquals("Visible parameter labels/positions change after real swipe",beforeTexts,visibleParameterTexts())
            assertEquals("Scrolling parameters leaves preview viewport fixed",preview,rect("qf-preview"))
        }else{
            node("qf-panel-collapse");assertNull("Traditional portrait stays traditional",find("qf-workspace-side"))
        }
        if(wide){click("qf-preview-fullscreen");node("qf-preview-back");click("qf-preview-back");node("qf-workspace-side")}
        else click("qf-panel-collapse")
        SystemClock.sleep(600)
        assertEquals("Scroll/fullscreen/back must preserve every scene field",before,StateJson.encode(store.load()))
    }
}
