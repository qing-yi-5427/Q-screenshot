package dev.qingframe

import android.graphics.Bitmap
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.qingframe.model.*
import dev.qingframe.render.RenderController
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class RenderQualityTest {
    @Test fun cancelledSamplingCanRetryAndRetainsExactSizeAndAlpha() {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        lateinit var renderer:RenderController
        val state=ProjectState(background="transparent",shadow=0f,reflection=0f,
            pose=Quaternion.euler(-28f,12f,-6f),canvasWidth=3,canvasHeight=4)
        instrumentation.runOnMainSync {
            renderer=RenderController(instrumentation.targetContext,
                ProjectStore(instrumentation.targetContext,"quality-${UUID.randomUUID().toString().take(12)}")){}
            renderer.debugAllowSampling=true
            renderer.applyState(state)
        }
        try {
            val deadline=SystemClock.uptimeMillis()+15000
            var ready=false
            while(!ready&&SystemClock.uptimeMillis()<deadline){instrumentation.runOnMainSync{ready=renderer.ready};SystemClock.sleep(25)}
            assertTrue("Renderer reaches ready",ready)
            val cancelled=CountDownLatch(1);var failed=false
            instrumentation.runOnMainSync {
                renderer.export(state,384,512){failed=it.isFailure;it.getOrNull()?.recycle();cancelled.countDown()}
                renderer.cancelExport()
            }
            assertTrue("Cancellation callback",cancelled.await(20,TimeUnit.SECONDS));assertTrue("Cancelled request fails cleanly",failed)
            val completed=CountDownLatch(1);var bitmap:Bitmap?=null;var failure:Throwable?=null
            instrumentation.runOnMainSync{renderer.export(state,384,512){bitmap=it.getOrNull();failure=it.exceptionOrNull();completed.countDown()}}
            assertTrue("Retry callback",completed.await(30,TimeUnit.SECONDS))
            assertNull("Same renderer recovers",failure)
            val output=requireNotNull(bitmap)
            try {
                assertEquals(384,output.width);assertEquals(512,output.height)
                for((x,y) in listOf(0 to 0,383 to 0,0 to 511,383 to 511))assertEquals("Exact transparent corner",0,output.getPixel(x,y))
                var partial=0;var opaque=0
                val pixels=IntArray(384*512);output.getPixels(pixels,0,384,0,0,384,512)
                for(pixel in pixels){val alpha=pixel ushr 24;if(alpha in 1..254)partial++;if(alpha==255)opaque++}
                assertTrue("Fractional edge coverage survives PNG bitmap",partial>0);assertTrue("Opaque screen retained",opaque>0)
            }finally{output.recycle()}
        }finally{instrumentation.runOnMainSync{renderer.close()}}
    }
}
