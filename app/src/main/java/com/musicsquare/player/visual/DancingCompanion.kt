package com.musicsquare.player.visual

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.*
import kotlin.math.sin
import kotlin.math.cos

/**
 * Lightweight transparent animated dancer overlay, fully offline.
 * Stylized 2.5D illustration (not a skeletal 3D model).
 * Does not intercept the entire player surface: only its compact drawing area.
 */
@Composable
fun DancingCompanion(playing:Boolean,onDismiss:()->Unit,bpm:Float?=null,energy:Float=0.5f) {
 val infinite=rememberInfiniteTransition(label="dance")
 val phase by infinite.animateFloat(
  initialValue=0f,targetValue=(Math.PI*2).toFloat(),
  animationSpec=infiniteRepeatable(tween(if(playing) (60000f/(bpm?.coerceIn(55f,190f) ?: 100f)).toInt().coerceIn(315,1100) else 1200,easing=LinearEasing)),label="dance phase"
 )
 val bounce=if(playing)sin(phase.toDouble()).toFloat() else 0f
 val intensity=energy.coerceIn(0f,1f)
 val groove=if(intensity>0.68f) 1.4f else if(intensity<0.32f) 0.55f else 1f
 Box(Modifier.width(170.dp).height(270.dp),contentAlignment=Alignment.Center) {
  Canvas(Modifier.fillMaxSize()) {
   val cx=size.width*0.5f
   val scale=size.width/170f
   fun pt(x:Float,y:Float)=Offset(cx+x*scale,y*scale)
   val shift=bounce*7f*groove
   val shadow=Brush.radialGradient(listOf(Color(0x88582EC9),Color.Transparent),center=pt(0f,210f),radius=95f*scale)
   drawCircle(shadow,100f*scale,pt(0f,210f))
   // Shaded hair and face, rendered with gradients for dimensionality.
   drawOval(Brush.verticalGradient(listOf(Color(0xFF53336F),Color(0xFF20112F))),pt(-28f,25f+shift),androidx.compose.ui.geometry.Size(56f*scale,72f*scale))
   drawOval(Brush.linearGradient(listOf(Color(0xFFFFD6B4),Color(0xFFCB866F))),pt(-18f,37f+shift),androidx.compose.ui.geometry.Size(36f*scale,44f*scale))
   drawCircle(Color(0xFF24162B),2.2f*scale,pt(-7f,57f+shift))
   drawCircle(Color(0xFF24162B),2.2f*scale,pt(8f,57f+shift))
   // Dress and moving arms.
   val dress=Path().apply {
    moveTo(pt(-19f,89f+shift).x,pt(-19f,89f+shift).y)
    lineTo(pt(19f,89f+shift).x,pt(19f,89f+shift).y)
    lineTo(pt(34f,162f+shift).x,pt(34f,162f+shift).y)
    lineTo(pt(-34f,162f+shift).x,pt(-34f,162f+shift).y)
    close()
   }
   drawPath(dress,Brush.linearGradient(listOf(Color(0xFFEA73DB),Color(0xFF7542CC),Color(0xFF2E225F))))
   val swing=if(playing)(sin(phase.toDouble()).toFloat()*17f + sin(phase.toDouble()*2.0).toFloat()*8f)*groove else 0f
   fun limb(a:Offset,b:Offset,color:Color,width:Float) {
    drawLine(Color(0x5524142F),a.copy(x=a.x+2*scale,y=a.y+2*scale),b.copy(x=b.x+2*scale,y=b.y+2*scale),strokeWidth=(width+3)*scale,cap=StrokeCap.Round)
    drawLine(color,a,b,strokeWidth=width*scale,cap=StrokeCap.Round)
   }
   limb(pt(-19f,96f+shift),pt(-43f-swing,124f+shift-swing),Color(0xFFE4A08C),10f)
   limb(pt(19f,96f+shift),pt(43f+swing,124f+shift+swing),Color(0xFFE4A08C),10f)
   limb(pt(-13f,160f+shift),pt(-22f-swing*0.45f,207f+if(playing)kotlin.math.max(0f,bounce)*-10f*groove else 0f),Color(0xFFD6A0AE),12f)
   limb(pt(13f,160f+shift),pt(22f+swing*0.45f,207f+if(playing)kotlin.math.max(0f,-bounce)*-10f*groove else 0f),Color(0xFFD6A0AE),12f)
   drawCircle(Color(0x99FFFFFF),4f*scale,pt(-10f,112f+shift))
  }
  TextButton(onClick=onDismiss,modifier=Modifier.align(Alignment.TopEnd)) { Text("×",color=Color.White,fontSize=24.sp) }
 }
}
