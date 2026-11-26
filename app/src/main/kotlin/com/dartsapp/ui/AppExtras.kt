package com.dartsapp.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Where players can reach us: Play Store listing, and (when set) a feedback email address. */
object AppLinks {
    const val PACKAGE = "com.aronmakkai.metrodarts"
    /** Feedback address shown in Settings; leave blank to hide the "Send feedback" button. */
    const val FEEDBACK_EMAIL = ""

    /** Open the Play Store page for Metro Darts (the Play app if installed, else the web page). */
    fun openStorePage(ctx: Context) {
        val market = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$PACKAGE")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try { ctx.startActivity(market) } catch (e: Exception) {
            try {
                ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$PACKAGE")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            } catch (_: Exception) { }
        }
    }

    /** Start an email to the developer with the app version filled in. */
    fun sendFeedback(ctx: Context) {
        val version = try { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName } catch (e: Exception) { "?" }
        val i = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:")).apply {
            putExtra(Intent.EXTRA_EMAIL, arrayOf(FEEDBACK_EMAIL))
            putExtra(Intent.EXTRA_SUBJECT, "Metro Darts feedback (v$version)")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try { ctx.startActivity(i) } catch (_: Exception) { }
    }
}

private data class TourPage(val title: String, val body: String)

private val TOUR = listOf(
    TourPage("WELCOME TO METRO DARTS",
        "Darts is rhythm. Every game here is played to a beat, like a real throw: stance, aim, release."),
    TourPage("HOW TO THROW",
        "Swipe up from the bottom-left to pick up a dart. The aiming ring runs out from the bull and back — tap your target as it returns to the centre. On the beat, the dart flies true; early or late, it drifts."),
    TourPage("CHECKOUT GAME",
        "Random finishes from 2 to 170. Check out in one visit to build your streak. Hit perfect darts in a row to light up HOT STREAK — fatter trebles and doubles for four darts. The Coach rates every checkout."),
    TourPage("501, CHARACTERS & TOURNAMENT",
        "Play 501 against six characters, each with their own game — and a dirty super power when they're losing. Tap any portrait for his bio. Win the Tournament and someone special joins the next draw."),
    TourPage("DARTS IRL",
        "Got a real board? Score real games here: 2-player 501, 501 against a character, a checkout helper, and the Metronome to learn and record your own throwing rhythm."),
)

/** First-launch walkthrough: a few short pages, with Skip on every page. */
@Composable
fun WelcomeTour(onDone: () -> Unit) {
    var page by remember { mutableStateOf(0) }
    val p = TOUR[page]
    androidx.compose.ui.window.Dialog(onDismissRequest = onDone) {
        Column(
            modifier = Modifier.fillMaxWidth()
                .background(BubblePurple, CutCornerShape(topStart = 16.dp, bottomEnd = 16.dp))
                .border(BorderStroke(2.dp, NeonPink), CutCornerShape(topStart = 16.dp, bottomEnd = 16.dp))
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(p.title, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Black, letterSpacing = 2.sp,
                color = Gold, fontSize = 18.sp, textAlign = TextAlign.Center)
            Spacer(Modifier.height(12.dp))
            Text(p.body, color = BubbleText, fontSize = 15.sp, lineHeight = 21.sp, textAlign = TextAlign.Center)
            Spacer(Modifier.height(14.dp))
            // page dots
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for (i in TOUR.indices) Box(Modifier.size(8.dp).background(if (i == page) NeonPink else Charcoal, CutCornerShape(2.dp)))
            }
            Spacer(Modifier.height(10.dp))
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                if (page < TOUR.lastIndex) TextButton(onClick = onDone) { Text("Skip", color = Grey) }
                Spacer(Modifier.weight(1f))
                Button(onClick = { if (page < TOUR.lastIndex) page++ else onDone() }) {
                    Text(if (page < TOUR.lastIndex) "Next" else "Let's play")
                }
            }
        }
    }
}

/** Gentle, one-off rating prompt after a milestone. "Never" and "Rate" both stop it for good. */
@Composable
fun RatePrompt(onRate: () -> Unit, onLater: () -> Unit, onNever: () -> Unit) {
    AlertDialog(
        onDismissRequest = onLater,
        title = { Text("Enjoying Metro Darts?") },
        text = { Text("If you have a moment, a rating on Google Play helps other darts players find the game. Thanks for playing!") },
        confirmButton = { Button(onClick = onRate) { Text("Rate it") } },
        dismissButton = {
            Row {
                TextButton(onClick = onNever) { Text("No thanks", color = Grey) }
                TextButton(onClick = onLater) { Text("Later", color = Grey) }
            }
        }
    )
}
