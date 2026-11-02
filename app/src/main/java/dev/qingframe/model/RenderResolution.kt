package dev.qingframe.model

import kotlin.math.floor
import kotlin.math.min
import kotlin.math.sqrt

data class RenderResolution(val width:Int,val height:Int) {
    fun exceeds(w:Int,h:Int)=width>w||height>h
}
/** Bounded spatial samples. Native-size output is retained on low-memory devices and at the cap. */
fun renderResolution(w:Int,h:Int,preview:Boolean,lowMemory:Boolean,maxTextureSize:Int):RenderResolution {
    require(w>0&&h>0&&maxTextureSize>0)
    if(lowMemory)return RenderResolution(w,h)
    val pixelCap=if(preview)2_000_000.0 else 8_000_000.0
    val edgeCap=min(maxTextureSize,if(preview)2400 else 4096).toDouble()
    val scale=min(1.5,min(sqrt(pixelCap/(w.toDouble()*h)),edgeCap/maxOf(w,h)))
    if(scale<1.05)return RenderResolution(w,h)
    return RenderResolution(maxOf(w,floor(w*scale).toInt()),maxOf(h,floor(h*scale).toInt()))
}
