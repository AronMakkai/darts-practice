package com.dartsapp.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.dartsapp.logic.TimingPresets

/**
 * MY THROWING PACE (Darts IRL menu): the heart of the app. Every game is played to a rhythm — how long
 * the aim ring takes and the beat between darts. Record your own from your real throw, or pick one of
 * the built-in paces; whichever is selected here is used by every game.
 */
@Composable
fun PaceScreen(navController: NavHostController) {
    val context = LocalContext.current
    val presets = remember { TimingPresets.load(context) }
    var paceName by remember { mutableStateOf(TimingPresets.selectedOrDefault(context, presets)) }
    Box(modifier = Modifier.fillMaxSize()) {
        VectorBackdrop(modifier = Modifier.fillMaxSize())
        Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            ScreenHeader("My throwing pace", navController)
            ChromeTitle("YOUR PACE", modifier = Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 24.dp))
            Text(
                "Every game in Metro Darts is played to a rhythm: the time your dart takes and the beat between darts. " +
                    "Set it from your own real throw and the games train exactly the rhythm you use at the oche.",
                fontSize = 14.sp, color = OffWhite, textAlign = TextAlign.Center, lineHeight = 19.sp,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 28.dp, vertical = 8.dp)
            )
            // The main event: record your own
            Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp), contentAlignment = Alignment.Center) {
                NeonSignButton("RECORD MY OWN PACE", onClick = { navController.navigate("metronome-learn") })
            }
            Text(
                "Stand at your real board. Walk through a few turns at your natural speed — tap as you finish each step " +
                    "(stance, aim, throw) — then save it. It appears below and every game uses it.",
                fontSize = 12.sp, color = Grey, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 28.dp, vertical = 4.dp)
            )

            Spacer(Modifier.height(14.dp))
            Text("PLAY EVERY GAME AT", fontSize = 14.sp, fontFamily = FontFamily.Monospace, letterSpacing = 4.sp, color = Grey,
                modifier = Modifier.padding(start = 28.dp, bottom = 6.dp))
            for (p in presets) {
                val selected = p.name == paceName
                OutlinedButton(
                    onClick = { paceName = p.name; TimingPresets.setSelected(context, p.name) },
                    shape = CutCornerShape(topStart = 10.dp, bottomEnd = 10.dp),
                    border = BorderStroke(2.dp, if (selected) Gold else Charcoal),
                    colors = ButtonDefaults.outlinedButtonColors(containerColor = if (selected) DarkRed else Night, contentColor = if (selected) OffWhite else Grey),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 28.dp, vertical = 3.dp)
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(p.name + (if (!p.builtIn) "  ·  recorded" else ""), fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        Text(p.summary(), fontSize = 11.sp)
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = { navController.navigate("metronome") }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                Text("Open the Metronome to practise your rhythm", color = Gold)
            }
            Spacer(Modifier.height(24.dp))
        }
        TvFilter(modifier = Modifier.fillMaxSize())
    }
}
