package com.dartsapp.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.dartsapp.logic.AimOpacity
import com.dartsapp.logic.Difficulty
import com.dartsapp.logic.Settings
import com.dartsapp.logic.Sounds

@Composable
fun SettingsScreen(navController: NavHostController) {
    val context = LocalContext.current
    var difficulty by remember { mutableStateOf(Settings.difficulty(context)) }
    var aimOpacity by remember { mutableStateOf(Settings.aimOpacity(context)) }
    val dartlessPrefs = remember { context.getSharedPreferences("dartless", android.content.Context.MODE_PRIVATE) }
    var best by remember { mutableStateOf(dartlessPrefs.getInt("best", 0)) }

    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
        ScreenHeader("Settings", navController)

        Text("DIFFICULTY", fontSize = 14.sp, fontFamily = FontFamily.Monospace, letterSpacing = 4.sp, color = Grey,
            modifier = Modifier.padding(start = 28.dp, top = 20.dp, bottom = 6.dp))
        for (d in Difficulty.values()) {
            val selected = d == difficulty
            OutlinedButton(
                onClick = { difficulty = d; Settings.setDifficulty(context, d) },
                shape = CutCornerShape(topStart = 12.dp, bottomEnd = 12.dp),
                border = BorderStroke(2.dp, if (selected) Gold else Charcoal),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = if (selected) DarkRed else Black,
                    contentColor = if (selected) OffWhite else Grey
                ),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 28.dp, vertical = 6.dp).height(64.dp)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(d.label.uppercase(), fontSize = 18.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, letterSpacing = 3.sp)
                    Text(
                        when (d) {
                            Difficulty.EASY -> "Wide timing window, forgiving misses, lower checkouts"
                            Difficulty.NORMAL -> "The standard game"
                            Difficulty.HARD -> "Tight timing, wild misses, bigger checkouts"
                        },
                        fontSize = 11.sp
                    )
                }
            }
        }

        Text("Difficulty applies to Dartless Checkout: the pace window, how far a miss scatters, and how big the checkouts get.",
            fontSize = 12.sp, color = Grey, modifier = Modifier.padding(horizontal = 28.dp, vertical = 8.dp))

        Spacer(Modifier.height(16.dp))
        Text("AIM OPACITY", fontSize = 14.sp, fontFamily = FontFamily.Monospace, letterSpacing = 4.sp, color = Grey,
            modifier = Modifier.padding(start = 28.dp, bottom = 6.dp))
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 28.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (t in AimOpacity.values()) {
                val selected = t == aimOpacity
                OutlinedButton(
                    onClick = { aimOpacity = t; Settings.setAimOpacity(context, t) },
                    shape = CutCornerShape(topStart = 10.dp, bottomEnd = 10.dp),
                    border = BorderStroke(2.dp, if (selected) Gold else Charcoal),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = if (selected) DarkRed else Black,
                        contentColor = if (selected) OffWhite else Grey
                    ),
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp),
                    modifier = Modifier.weight(1f).height(56.dp)
                ) {
                    Text(t.label.uppercase(), fontSize = 16.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                }
            }
        }
        Text(aimOpacity.blurb + ". The aiming ring in Dartless Checkout and 501 vs bot.",
            fontSize = 12.sp, color = Grey, modifier = Modifier.padding(horizontal = 28.dp, vertical = 8.dp))

        Spacer(Modifier.height(16.dp))
        Text("SOUND", fontSize = 14.sp, fontFamily = FontFamily.Monospace, letterSpacing = 4.sp, color = Grey,
            modifier = Modifier.padding(start = 28.dp, bottom = 6.dp))
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 28.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { Sounds.tick() }, modifier = Modifier.weight(1f)) { Text("Tick", color = OffWhite) }
            OutlinedButton(onClick = { Sounds.beep() }, modifier = Modifier.weight(1f)) { Text("Beep", color = OffWhite) }
            OutlinedButton(onClick = { Sounds.playCheckoutJingle() }, modifier = Modifier.weight(1f)) { Text("Jingle", color = OffWhite) }
        }
        Text("Sounds play on the media volume — if these are silent, turn the media volume up (not the ring volume).",
            fontSize = 12.sp, color = Grey, modifier = Modifier.padding(horizontal = 28.dp, vertical = 8.dp))

        Spacer(Modifier.height(16.dp))
        Text("RECORDS", fontSize = 14.sp, fontFamily = FontFamily.Monospace, letterSpacing = 4.sp, color = Grey,
            modifier = Modifier.padding(start = 28.dp, bottom = 6.dp))
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 28.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Best perfect-rhythm streak: $best", fontSize = 16.sp, color = PaleGold, modifier = Modifier.weight(1f))
            TextButton(onClick = { best = 0; dartlessPrefs.edit().putInt("best", 0).putInt("streak", 0).apply() }, enabled = best > 0) {
                Text("Reset", color = Grey)
            }
        }
    }
}
