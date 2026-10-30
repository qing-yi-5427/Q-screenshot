package dev.qingframe

import android.app.Activity
import android.content.Intent
import android.graphics.Rect
import android.os.SystemClock
import android.os.Build
import android.view.InputDevice
import android.view.MotionEvent
import dev.qingframe.model.ProjectStore
import dev.qingframe.model.StateJson
import dev.qingframe.model.FrontRotation
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

/** UI contract uses platform accessibility; no extra Compose/UIAutomator dependency. */
@RunWith(AndroidJUnit4::class)
class ColorPanelTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private fun find(description: String): AccessibilityNodeInfo? {
        fun walk(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
            if (node.contentDescription?.toString() == description && node.isVisibleToUser) return node
            for (i in 0 until node.childCount) node.getChild(i)?.let { walk(it)?.let { match -> return match } }
            return null
        }
        return instrumentation.uiAutomation.rootInActiveWindow?.let(::walk)
    }
    private fun awaitNode(description: String): AccessibilityNodeInfo {
        val deadline = SystemClock.uptimeMillis() + 8000
        do { find(description)?.let { return it }; SystemClock.sleep(50) } while (SystemClock.uptimeMillis() < deadline)
        throw AssertionError("Visible accessibility selector missing: $description")
    }
    private fun bounds(description: String): Rect = Rect().also { awaitNode(description).getBoundsInScreen(it) }
    private fun click(description: String) {
        var node = awaitNode(description)
        while (!node.isClickable) { val parent = node.parent ?: break; node = parent }
        assertTrue("Click $description", node.performAction(AccessibilityNodeInfo.ACTION_CLICK))
        instrumentation.waitForIdleSync()
    }
    private fun inIsolatedEditor(block: (String) -> Unit) {
        val session = "colors-ui-${UUID.randomUUID().toString().take(12)}"
        val intent = Intent(instrumentation.targetContext, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            .putExtra("test_session", session)
        val activity = instrumentation.startActivitySync(intent) as Activity
        try { awaitNode("qf-preview"); block(session) }
        finally { instrumentation.runOnMainSync { activity.finish() } }
        // Keep the tiny isolated draft for failure diagnosis; never clear normal project data.
    }
    @Test fun everyPanelLeavesPreviewAndOtherTabsAvailable() = inIsolatedEditor { session ->
        val store = ProjectStore(instrumentation.targetContext, session)
        val baseline = StateJson.encode(store.load())
        val tabs = listOf("device", "background", "angle", "light", "canvas")
        for (tab in tabs) {
            click("qf-tab-$tab")
            var preview = bounds("qf-preview")
            var panel = bounds("qf-panel")
            val layoutDeadline = SystemClock.uptimeMillis() + 4000
            while (Rect.intersects(preview, panel) && SystemClock.uptimeMillis() < layoutDeadline) {
                SystemClock.sleep(80)
                preview = bounds("qf-preview"); panel = bounds("qf-panel")
            }
            assertTrue("Nonempty preview for $tab", preview.width() > 0 && preview.height() > 0)
            val intersection = Rect(preview)
            val overlaps = intersection.intersect(panel)
            assertFalse("Panel $tab covers preview: preview=$preview panel=$panel", overlaps && intersection.width() > 2 && intersection.height() > 2)
            for (other in tabs) {
                var otherNode = awaitNode("qf-tab-$other")
                while (!otherNode.isClickable) { val parent = otherNode.parent ?: break; otherNode = parent }
                assertTrue("Tab $other reachable while $tab open", otherNode.isClickable)
            }
            awaitNode("qf-panel-collapse")
            SystemClock.sleep(600)
            assertEquals("Opening/switching $tab changes no scene fields", baseline, StateJson.encode(store.load()))
        }
    }
    @Test fun collapseReturnsSpaceToPreview() = inIsolatedEditor { session ->
        val store = ProjectStore(instrumentation.targetContext, session)
        val baseline = StateJson.encode(store.load())
        click("qf-tab-device")
        val expanded = bounds("qf-preview")
        click("qf-panel-collapse")
        val deadline = SystemClock.uptimeMillis() + 3000
        while (find("qf-panel") != null && SystemClock.uptimeMillis() < deadline) SystemClock.sleep(50)
        assertNull("Panel disappears after collapse", find("qf-panel"))
        val collapsed = bounds("qf-preview")
        assertTrue("Collapse does not shrink preview", collapsed.width().toLong() * collapsed.height() >= expanded.width().toLong() * expanded.height())
        SystemClock.sleep(600)
        assertEquals("Open/collapse changes no scene fields", baseline, StateJson.encode(store.load()))
    }
    @Test fun expandedPanelStillAllowsOneUndoablePreviewDrag() {
        org.junit.Assume.assumeTrue("Input injection is emulator-only", Build.HARDWARE in setOf("ranchu", "goldfish") || Build.FINGERPRINT.startsWith("generic") || Build.MODEL.contains("sdk_gphone"))
        inIsolatedEditor { session ->
            val store = ProjectStore(instrumentation.targetContext, session)
            click("qf-tab-device")
            val before = store.load()
            val bounds = bounds("qf-preview")
            val startX = bounds.exactCenterX()
            val startY = bounds.exactCenterY()
            val distance = minOf(bounds.width() * .18f, 70f)
            val downTime = SystemClock.uptimeMillis()
            fun inject(action: Int, x: Float) {
                val event = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), action, x, startY, 0)
                event.source = InputDevice.SOURCE_TOUCHSCREEN
                try { assertTrue("Inject preview drag action $action", instrumentation.uiAutomation.injectInputEvent(event, true)) }
                finally { event.recycle() }
            }
            inject(MotionEvent.ACTION_DOWN, startX)
            for (step in 1..8) { SystemClock.sleep(25); inject(MotionEvent.ACTION_MOVE, startX + distance * step / 8f) }
            inject(MotionEvent.ACTION_UP, startX + distance)
            val deadline = SystemClock.uptimeMillis() + 5000
            var changed = store.load()
            while (FrontRotation.equivalent(before.pose, changed.pose) && SystemClock.uptimeMillis() < deadline) {
                SystemClock.sleep(100); changed = store.load()
            }
            assertFalse("Preview rotates while panel remains open", FrontRotation.equivalent(before.pose, changed.pose))
            awaitNode("qf-panel")
            click("撤销")
            val undoDeadline = SystemClock.uptimeMillis() + 5000
            var restored = store.load()
            while (StateJson.encode(before) != StateJson.encode(restored) && SystemClock.uptimeMillis() < undoDeadline) {
                SystemClock.sleep(100); restored = store.load()
            }
            assertEquals("Single drag is one undoable scene change", StateJson.encode(before), StateJson.encode(restored))
        }
    }

}
