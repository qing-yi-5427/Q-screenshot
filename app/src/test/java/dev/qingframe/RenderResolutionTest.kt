package dev.qingframe

import dev.qingframe.model.*
import org.junit.Assert.*
import org.junit.Test

class RenderResolutionTest {
    @Test fun smallPreviewGetsMoreSamplesWithoutChangingAspect(){
        val r=renderResolution(540,720,true,false,8192)
        assertEquals(RenderResolution(810,1080),r)
    }
    @Test fun largerOutputsRespectBudgetAndNativeFallback(){
        for((w,h) in listOf(2071 to 2560,2160 to 3840,3840 to 3840)){
            val r=renderResolution(w,h,false,false,8192)
            assertTrue(r.width>=w&&r.height>=h)
            if(r.exceeds(w,h))assertTrue(r.width.toLong()*r.height<=8_000_000L)
            assertEquals(w.toDouble()/h,r.width.toDouble()/r.height,.001)
        }
        assertEquals(RenderResolution(2160,3840),renderResolution(2160,3840,false,false,8192))
    }
    @Test fun lowerCapabilityPreservesNativeSize(){
        assertEquals(RenderResolution(900,1100),renderResolution(900,1100,true,true,8192))
        assertEquals(RenderResolution(2048,2048),renderResolution(2048,2048,false,false,2048))
    }
}
