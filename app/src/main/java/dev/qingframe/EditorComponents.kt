package dev.qingframe

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription

internal object EditorStyle {
 val gap=8.dp
 val group=16.dp
 val control=RoundedCornerShape(12.dp)
 val card=RoundedCornerShape(20.dp)
 val pill=RoundedCornerShape(50)
 val background=Color(0xfff2f2f7)
 val surface=Color.White
 val muted=Color(0xfff5f5f9)
 val secondary=Color(0xff636366)
 val accent=Color(0xff0066cc)
 val selection=Color(0xffeaf2ff)
 val separator=Color(0xffe5e5ea)
}

/** Original line artwork; geometry shares a 24-unit grid and one optical stroke. */
@Composable internal fun EditorIcon(kind:String,modifier:Modifier=Modifier,color:Color=LocalContentColor.current){
 Canvas(modifier.size(22.dp)){
  val u=size.width/24f;val stroke=1.65f*u
  fun line(x:Float,y:Float,x2:Float,y2:Float)=drawLine(color,Offset(x*u,y*u),Offset(x2*u,y2*u),stroke,StrokeCap.Round)
  fun path(block:Path.()->Unit){val p=Path().apply(block);drawPath(p,color,style=Stroke(width=stroke,cap=StrokeCap.Round))}
  when(kind){
   "undo","redo"->{val flip=kind=="redo";fun x(v:Float)=(if(flip)24-v else v)*u
    path{moveTo(x(5f),8*u);cubicTo(x(18f),2*u,x(24f),18*u,x(11f),20*u)}
    line(if(flip)19f else 5f,8f,if(flip)14f else 10f,4f);line(if(flip)19f else 5f,8f,if(flip)14f else 10f,12f)}
   "device"->{drawRoundRect(color,Offset(6*u,2*u),Size(12*u,20*u),CornerRadius(2*u),style=Stroke(stroke));line(10f,18.5f,14f,18.5f)}
   "background"->{drawRoundRect(color,Offset(3*u,3*u),Size(18*u,18*u),CornerRadius(3*u),style=Stroke(stroke));line(4f,17f,10f,11f);line(10f,11f,14f,15f);line(14f,15f,20f,9f);drawCircle(color,1.5f*u,Offset(8*u,8*u))}
   "angle"->{path{moveTo(4*u,17*u);lineTo(16*u,20*u);lineTo(20*u,5*u);lineTo(8*u,3*u);close()};line(9f,17f,13f,18f)}
   "light"->{drawCircle(color,4*u,Offset(12*u,12*u),style=Stroke(stroke));for(i in 0..7){val a=i*Math.PI/4;line((12+7*kotlin.math.cos(a)).toFloat(),(12+7*kotlin.math.sin(a)).toFloat(),(12+9*kotlin.math.cos(a)).toFloat(),(12+9*kotlin.math.sin(a)).toFloat())}}
   "canvas"->{line(3f,8f,3f,3f);line(3f,3f,8f,3f);line(16f,3f,21f,3f);line(21f,3f,21f,8f);line(21f,16f,21f,21f);line(21f,21f,16f,21f);line(8f,21f,3f,21f);line(3f,21f,3f,16f)}
   "plus"->{line(12f,5f,12f,19f);line(5f,12f,19f,12f)}
   "check"->{line(5f,12f,10f,17f);line(10f,17f,20f,6f)}
   "up"->{line(7f,14f,12f,9f);line(12f,9f,17f,14f)}
   "down"->{line(7f,10f,12f,15f);line(12f,15f,17f,10f)}
   "fit"->{drawRoundRect(color,Offset(4*u,3*u),Size(16*u,18*u),CornerRadius(2*u),style=Stroke(stroke));line(8f,8f,16f,8f);line(8f,16f,16f,16f)}
   else->{ // Four phone silhouettes make photographic presets recognizable.
    val points=when(kind){"正面"->listOf(7f to 3f,17f to 3f,17f to 21f,7f to 21f);"左前"->listOf(7f to 5f,18f to 2f,18f to 20f,7f to 22f);"右前"->listOf(6f to 2f,17f to 5f,17f to 22f,6f to 20f);else->listOf(5f to 7f,17f to 3f,20f to 17f,8f to 21f)}
    path{moveTo(points[0].first*u,points[0].second*u);points.drop(1).forEach{lineTo(it.first*u,it.second*u)};close()}
   }
  }
 }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable internal fun EditorSlider(value:Float,onValueChange:(Float)->Unit,onValueChangeFinished:()->Unit,valueRange:ClosedFloatingPointRange<Float>,modifier:Modifier=Modifier){
 Slider(value=value,onValueChange=onValueChange,onValueChangeFinished=onValueChangeFinished,valueRange=valueRange,modifier=modifier.heightIn(min=48.dp),
  thumb={Box(Modifier.size(24.dp).shadow(2.dp,CircleShape).background(Color.White,CircleShape).border(.5.dp,Color(0xffdddddf),CircleShape))},
  track={state->Canvas(Modifier.fillMaxWidth().height(4.dp)){
   val fraction=((state.value-valueRange.start)/(valueRange.endInclusive-valueRange.start)).coerceIn(0f,1f)
   drawLine(EditorStyle.separator,Offset(0f,size.height/2),Offset(size.width,size.height/2),size.height,StrokeCap.Round)
   drawLine(EditorStyle.accent,Offset(0f,size.height/2),Offset(size.width*fraction,size.height/2),size.height,StrokeCap.Round)
  }})
}

@Composable internal fun EditorField(value:String,onValueChange:(String)->Unit,label:String,modifier:Modifier=Modifier){
 Column(modifier,verticalArrangement=Arrangement.spacedBy(4.dp)){
  Text(label,style=MaterialTheme.typography.labelMedium,color=EditorStyle.secondary)
  TextField(value=value,onValueChange=onValueChange,singleLine=true,modifier=Modifier.fillMaxWidth().heightIn(min=48.dp).semantics{contentDescription=label},shape=EditorStyle.control,textStyle=MaterialTheme.typography.bodyMedium,
   colors=TextFieldDefaults.colors(focusedContainerColor=EditorStyle.muted,unfocusedContainerColor=EditorStyle.muted,focusedIndicatorColor=Color.Transparent,unfocusedIndicatorColor=Color.Transparent,cursorColor=EditorStyle.accent))
 }
}
