package dev.qingframe

import android.content.Intent
import android.graphics.Rect
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.qingframe.model.ProjectStore
import dev.qingframe.model.StateJson
import org.junit.Assert.*
import dev.qingframe.model.isWideWorkspace
import android.os.Build
import android.util.DisplayMetrics
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

/** Explicit wide/traditional branches; never assumes away the new cover layout. */
@RunWith(AndroidJUnit4::class)
class CoverLayoutTest {
    private val instrumentation get()=InstrumentationRegistry.getInstrumentation()
    private fun find(description:String):AccessibilityNodeInfo? {
        fun walk(node:AccessibilityNodeInfo):AccessibilityNodeInfo? {
            if(node.contentDescription?.toString()==description&&node.isVisibleToUser)return node
            for(i in 0 until node.childCount)node.getChild(i)?.let{walk(it)?.let{return it}}
            return null
        }
        return instrumentation.uiAutomation.rootInActiveWindow?.let(::walk)
    }
    private fun node(description:String):AccessibilityNodeInfo {
        val deadline=SystemClock.uptimeMillis()+6000
        do{find(description)?.let{return it};SystemClock.sleep(50)}while(SystemClock.uptimeMillis()<deadline)
        throw AssertionError("Missing visible selector $description")
    }
    private fun clickable(description:String):AccessibilityNodeInfo {
        var n=node(description)
        while(!n.isClickable){n=n.parent?:throw AssertionError("No clickable ancestor for $description")}
        return n
    }
    private fun rect(n:AccessibilityNodeInfo)=Rect().also{n.getBoundsInScreen(it)}
    private fun bounds(description:String)=rect(node(description))
    private fun click(description:String){assertTrue(clickable(description).performAction(AccessibilityNodeInfo.ACTION_CLICK));instrumentation.waitForIdleSync();SystemClock.sleep(250)}
    @Test fun shortPortraitCoverKeepsPreviewTargetsAndSceneStable() {
        val context=instrumentation.targetContext
        val session="cover-layout-${UUID.randomUUID().toString().take(12)}"
        val activity=instrumentation.startActivitySync(Intent(context,MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK).putExtra("test_session",session))
        try {
            val tabs=listOf("device","background","angle","light","canvas")
            val density=context.resources.displayMetrics.density
            val closed=bounds("qf-preview")
            val window=Rect()
            instrumentation.runOnMainSync {
                if(Build.VERSION.SDK_INT>=30)window.set(activity.windowManager.currentWindowMetrics.bounds)
                else { val dm=DisplayMetrics(); activity.windowManager.defaultDisplay.getRealMetrics(dm);window.set(0,0,dm.widthPixels,dm.heightPixels) }
            }
            val wide=isWideWorkspace(window.width()/density,window.height()/density)
            val modeDeadline=SystemClock.uptimeMillis()+3000
            while((find("qf-workspace-side")!=null)!=wide&&SystemClock.uptimeMillis()<modeDeadline)SystemClock.sleep(50)
            assertEquals("Actual mode matches full WindowMetrics",wide,find("qf-workspace-side")!=null)
            val closedBottom=tabs.maxOf{rect(clickable("qf-tab-$it")).bottom}
            val workspaceHeight=closedBottom-closed.top
            if(!wide) assertTrue("Traditional closed preview retains useful height",closed.height().toFloat()/workspaceHeight>=.63f)
            val store=ProjectStore(context,session);val original=StateJson.encode(store.load())
            for(tab in tabs){
                click("qf-tab-$tab")
                val deadline=SystemClock.uptimeMillis()+4000
                val paneId="qf-panel"
                var preview=bounds("qf-preview");var panel=bounds(paneId)
                while(Rect.intersects(preview,panel)&&SystemClock.uptimeMillis()<deadline){SystemClock.sleep(80);preview=bounds("qf-preview");panel=bounds(paneId)}
                val tabRects=tabs.map{rect(clickable("qf-tab-$it"))}
                val bottom=maxOf(panel.bottom,tabRects.maxOf{it.bottom})
                if(wide){
                    assertNotNull("Wide workspace remains present",find("qf-workspace-side"))
                }else{
                    val minimumOpenRatio=if(workspaceHeight/density<480f).45f else .48f
                    assertTrue("Traditional open preview proportion >= $minimumOpenRatio",preview.height().toFloat()/(bottom-preview.top)>=minimumOpenRatio)
                }
                assertFalse("$tab panel covers preview",Rect.intersects(preview,panel))
                tabRects.forEachIndexed{i,r->
                    assertTrue("${tabs[i]} width >=48dp",r.width()+1>=48*density)
                    assertTrue("${tabs[i]} height >=48dp",r.height()+1>=48*density)
                    assertFalse("Tab covers preview",Rect.intersects(r,preview))
                    assertFalse("Tab covered by panel",Rect.intersects(r,panel))
                    tabRects.drop(i+1).forEach{assertFalse("Tabs overlap",Rect.intersects(r,it))}
                }
                if(!wide)assertTrue("Collapse target >=48dp",rect(clickable("qf-panel-collapse")).height()+1>=48*density)
                SystemClock.sleep(500)
                assertEquals("Opening/switching panel must not mutate scene",original,StateJson.encode(store.load()))
            }
            if(!wide){
                click("qf-panel-collapse");SystemClock.sleep(500)
                assertNull("Traditional panel collapses",find("qf-panel"))
                assertTrue("Preview space restored",bounds("qf-preview").height()>=closed.height()-1)
            }
            assertEquals("Presentation changes preserve every scene field",original,StateJson.encode(store.load()))
        }finally{instrumentation.runOnMainSync{activity.finish()}}
    }
}
