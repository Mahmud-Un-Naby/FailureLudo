package com.failureludo.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import com.failureludo.ui.theme.gardenBackground
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.failureludo.ui.tabletop.*
import com.failureludo.ui.theme.*

@Composable
fun HomeScreen(onNewGame: () -> Unit, onResume: () -> Unit, onHistory: () -> Unit,
    hasActiveGame: Boolean, onRules: () -> Unit, onSettings: () -> Unit, isSessionRestored: Boolean,
    resumeSummary: String = "", onPlayOnline: (() -> Unit)? = null) {
    Box(Modifier.fillMaxSize().gardenBackground(dark = true)
        .safeDrawingPadding(), contentAlignment=Alignment.Center) {
        Column(Modifier.widthIn(max=420.dp).fillMaxWidth().verticalScroll(rememberScrollState()).padding(32.dp),
            horizontalAlignment=Alignment.CenterHorizontally, verticalArrangement=Arrangement.spacedBy(18.dp)) {
            TabletopDice(5,0L,false,false,false,{ },Modifier.size(100.dp))
            Text("LUDO",color=TabletopStyle.Paper,fontSize=64.sp,fontWeight=FontWeight.Black,letterSpacing=8.sp)
            Text("Failure Edition",color=TabletopStyle.Muted,fontSize=18.sp)
            Spacer(Modifier.height(24.dp))
            if(!isSessionRestored) CircularProgressIndicator(color=TabletopStyle.Gold)
            else {
                if(hasActiveGame) Button(onClick=onResume,modifier=Modifier.fillMaxWidth().heightIn(min = 56.dp),
                    shape=RoundedCornerShape(16.dp),colors=ButtonDefaults.buttonColors(containerColor=TabletopStyle.Gold,contentColor=TabletopStyle.Ink)) {
                    Text("Resume",fontWeight=FontWeight.Bold)
                }
                if (hasActiveGame && resumeSummary.isNotBlank()) Text(resumeSummary, color = TabletopStyle.Muted,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center, style = MaterialTheme.typography.bodyMedium)
                Button(onClick=onNewGame,modifier=Modifier.fillMaxWidth().heightIn(min = 56.dp),shape=RoundedCornerShape(16.dp),
                    colors=ButtonDefaults.buttonColors(containerColor=TabletopStyle.Paper,contentColor=TabletopStyle.Ink)) {
                    Text("New game",fontWeight=FontWeight.Bold)
                }
                OutlinedButton(onClick=onHistory,
                    modifier=Modifier.fillMaxWidth().heightIn(min = 52.dp), shape=RoundedCornerShape(16.dp),
                    colors=ButtonDefaults.outlinedButtonColors(contentColor=TabletopStyle.Paper,
                        disabledContentColor=TabletopStyle.Muted.copy(alpha=.45f))) {
                    Text("Saved games")
                }
                onPlayOnline?.let { playOnline ->
                    OutlinedButton(onClick = playOnline,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = TabletopStyle.Paper)) {
                        Text("Play online")
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    TextButton(onClick = onRules) { Text("Rules", color = TabletopStyle.Paper) }
                    TextButton(onClick = onSettings) { Text("Settings", color = TabletopStyle.Paper) }
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement=Arrangement.spacedBy(18.dp)) {
                listOf(LudoRed,LudoBlue,LudoYellow,LudoGreen).forEachIndexed { index,tint ->
                    Canvas(Modifier.size(18.dp)) { drawIdentity(center,size.width*.32f,tint,index) }
                }
            }
            Text("Play on one phone",color=TabletopStyle.Muted,fontSize=12.sp)
        }
    }
}
