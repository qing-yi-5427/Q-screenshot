package dev.qingframe

import android.content.Intent
import android.graphics.Rect
import android.os.Build
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.qingframe.model.FrontRotation
import dev.qingframe.model.ProjectStore
import dev.qingframe.model.StateJson
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

/** One emulator-only behavior probe; display overrides are owned/restored by the external runner. */
@RunWith(AndroidJUnit4::class)
class SpacingTransformTest {
    private val ins get()=InstrumentationRegistry.getInstrumentation()
    private fun all():List<AccessibilityNodeInfo> {
        val out=mutableListOf<AccessibilityNodeInfo>()
        fun visit(n:AccessibilityNodeInfo){n.refresh();if(n.isVisibleToUser)out.add(n);for(i in 0 until n.childCount)n.getChild(i)?.let(::visit)}
        ins.uiAutomation.rootInActiveWindow?.let(::visit);return out
    }
    private fun id(key:String)=all().firstOrNull{it.contentDescription?.toString()==key}
    private fun text(label:String)=all().firstOrNull{it.text?.toString()==label}
    private fun box(n:AccessibilityNodeInfo)=Rect().also{n.getBoundsInScreen(it)}
    private fun node(key:String):AccessibilityNodeInfo {
        val end=SystemClock.uptimeMillis()+5000
        do{id(key)?.let{return it};SystemClock.sleep(80)}while(SystemClock.uptimeMillis()<end)
        throw AssertionError("Missing $key")
    }
    private fun click(key:String){var n=node(key);while(!n.isClickable)n=n.parent?:throw AssertionError("Not clickable $key");assertTrue(n.performAction(AccessibilityNodeInfo.ACTION_CLICK));ins.waitForIdleSync();SystemClock.sleep(350)}
    private fun swipe(x:Float,y:Float,toX:Float,toY:Float){
        val command="input swipe ${x.toInt()} ${y.toInt()} ${toX.toInt()} ${toY.toInt()} 450"
        ParcelFileDescriptor.AutoCloseInputStream(ins.uiAutomation.executeShellCommand(command)).use{it.readBytes()}
        SystemClock.sleep(500)
    }
    private fun seek(label:String){
        repeat(8){
            val panel=box(node("qf-panel"));val found=text(label)
            if(found!=null){val b=box(found);if(b.top>=panel.top&&b.bottom<=panel.bottom)return}
            val header=all().filter{it.text?.toString() in listOf("左右","俯仰","旋转","缩放","透视")}.map(::box)
                .filter{it.top>panel.top+12&&it.bottom<panel.bottom-12}.maxByOrNull{it.top}
                ?:throw AssertionError("No safe adjustment header for scrolling to $label")
            swipe(panel.exactCenterX(),header.exactCenterY(),panel.exactCenterX(),maxOf(panel.top+24f,header.exactCenterY()-panel.height()*.45f))
        }
        throw AssertionError("Adjustment unreachable: $label")
    }
    @Test fun collapsedAdvancedControlsAndPreviewGesturePreserveTheirContracts(){
        check(Build.HARDWARE in setOf("ranchu","goldfish")||Build.MODEL.contains("gphone")){"Touch probe is emulator-only"}
        val session="spacing-${UUID.randomUUID().toString().take(12)}"
        val a=ins.startActivitySync(Intent(ins.targetContext,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK).putExtra("test_session",session))
        try{
            click("qf-tab-angle");node("qf-angle-more")
            assertNull("New session starts collapsed",id("qf-angle-advanced"))
            val panel=box(node("qf-panel"))
            assertFalse("Default angle pane has no sliders",all().any{it.className?.toString()=="android.widget.SeekBar"&&Rect.intersects(panel,box(it))})
            val store=ProjectStore(ins.targetContext,session);val original=store.load();val baseline=StateJson.encode(original)
            click("qf-angle-more");node("qf-angle-advanced")
            for(label in listOf("左右","俯仰","旋转","缩放","透视","透视相机","正交相机"))seek(label)
            click("qf-tab-device");click("qf-tab-angle")
            assertNotNull("Expanded setting survives tab switch",id("qf-angle-advanced"))
            click("qf-angle-more");assertNull("Explicit collapse hides advanced controls",id("qf-angle-advanced"))
            SystemClock.sleep(600);assertEquals("Expand/scroll/tab/collapse changes no scene fields",baseline,StateJson.encode(store.load()))
            val preview=box(node("qf-preview"));val x=preview.exactCenterX();val y=preview.exactCenterY()
            swipe(x,y,x+minOf(preview.width()*.18f,100f),y)
            val deadline=SystemClock.uptimeMillis()+5000;var after=store.load()
            while(FrontRotation.equivalent(original.pose,after.pose)&&SystemClock.uptimeMillis()<deadline){SystemClock.sleep(100);after=store.load()}
            assertFalse("Real preview drag changes pose",FrontRotation.equivalent(original.pose,after.pose))
        }finally{ins.runOnMainSync{a.finish()}}
    }
}
