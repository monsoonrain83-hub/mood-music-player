package com.musicsquare.player

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*

private val shades = listOf(Color(0xFF294B85),Color(0xFF246D91),Color(0xFF288D88),Color(0xFF8C689F),Color(0xFFBB755C))
class MainActivity: ComponentActivity() {
 override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); setContent { MusicSquareScreen() } }
}
@Composable fun MusicSquareScreen() {
 var selected by remember { mutableIntStateOf(-1) }
 MaterialTheme(colorScheme=darkColorScheme(background=Color(0xFF0C0C18),surface=Color(0xFF20203A),primary=Color(0xFFC6A4FF))) {
  Column(Modifier.fillMaxSize().background(Color(0xFF0C0C18)).padding(22.dp),verticalArrangement=Arrangement.spacedBy(18.dp)) {
   Spacer(Modifier.height(24.dp))
   Text("MUSIC SQUARE",color=Color(0xFFB4ACD7),fontSize=13.sp,fontWeight=FontWeight.Bold)
   Text("Find your mood",color=Color.White,fontSize=32.sp,fontWeight=FontWeight.Bold)
   Text("Calm to energetic • Sad to happy",color=Color.LightGray)
   Column(verticalArrangement=Arrangement.spacedBy(6.dp)) {
    repeat(5){ row -> Row(horizontalArrangement=Arrangement.spacedBy(6.dp)) {
     repeat(5){ col -> val index=row*5+col
      Box(Modifier.weight(1f).aspectRatio(1f).background(shades[(col+row)%5].copy(alpha=if(selected==index)1f else .7f),RoundedCornerShape(12.dp)).clickable { selected=index },contentAlignment=Alignment.Center){if(selected==index)Text("♪",color=Color.White,fontSize=24.sp)}
     }
    }}
   }
   Text(if(selected<0)"Tap a square to explore" else "Selected mood cell: ${selected+1}",color=Color.White)
   Card(colors=CardDefaults.cardColors(containerColor=Color(0xFF20203A)),modifier=Modifier.fillMaxWidth()) { Column(Modifier.padding(20.dp)) { Text("Your mood mix",color=Color.White,fontSize=20.sp,fontWeight=FontWeight.Bold); Text("Local library integration coming next",color=Color.LightGray) } }
  }
 }
}
