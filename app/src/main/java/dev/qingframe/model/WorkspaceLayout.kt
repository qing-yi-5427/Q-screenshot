package dev.qingframe.model

import kotlin.math.*

/** Full window dimensions: keyboard and panel visibility never decide the workspace. */
fun isWideWorkspace(widthDp:Float,heightDp:Float)=widthDp>=600f||(widthDp>=400f&&heightDp<=widthDp*1.65f)
data class WorkspaceCorner(val radius:Float,val centerX:Float,val centerY:Float)
data class WorkspaceSafety(val top:Float,val bottom:Float,val headerLeft:Float,val headerRight:Float,val navLeft:Float,val navRight:Float)
fun workspaceSafety(height:Float,systemTop:Float,systemBottom:Float,topLeft:WorkspaceCorner?,topRight:WorkspaceCorner?,bottomLeft:WorkspaceCorner?,bottomRight:WorkspaceCorner?,width:Float):WorkspaceSafety {
    val top=ceil((systemTop+2f)/4f)*4f
    val bottom=max(systemBottom,max(8f,max(bottomLeft?.radius?:0f,bottomRight?.radius?:0f)/2f))
    fun inset(c:WorkspaceCorner?,y:Float,left:Boolean,topCorner:Boolean):Float {
        if(c==null||c.radius<=0f)return 12f
        val dy=y-c.centerY
        if((topCorner&&dy>=0)||(!topCorner&&dy<=0))return 12f
        val inner=max(0f,c.radius-8f)
        val dx=sqrt(max(0f,inner*inner-dy*dy))
        return max(12f,if(left)c.centerX-dx else width-(c.centerX+dx))
    }
    return WorkspaceSafety(top,bottom,inset(topLeft,top+4,true,true),inset(topRight,top+4,false,true),inset(bottomLeft,height-bottom,true,false),inset(bottomRight,height-bottom,false,false))
}
