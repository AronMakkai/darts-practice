package com.dartsapp.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.dartsapp.data.BoardGeometry
import kotlinx.coroutines.delay

private class TutorialPage(val title: String, val body: String)

private val pages = listOf(
    TutorialPage(
        "Pick your pace",
        "Choose a timing preset top-right — a pro's rhythm, or one you learned in the Metronome screen. " +
            "Every dart gets that many seconds."
    ),
    TutorialPage(
        "Pick up a dart",
        "Swipe diagonally up from the arrow in the bottom-left corner. That is you taking a dart from your other hand. " +
            "A ring starts growing out from the bull."
    ),
    TutorialPage(
        "Throw on the beat",
        "The ring grows to the edge of the board and shrinks back. Tap your target the moment it is back at the centre — " +
            "that is the ideal release. Early is forgiven a little; late is punished fast."
    ),
    TutorialPage(
        "Keep the rhythm",
        "Swipe, tap, swipe, tap, swipe, tap. Don't stop to think between darts — a long pause before the next swipe " +
            "breaks the pace and the accuracy bar at the top drops, so the dart drifts off your target."
    ),
    TutorialPage(
        "Clean checkouts pay",
        "Hit the checkout in one visit, on the beat, no hesitation: stars burst from the winning sector and a gold bar " +
            "joins your pile. Ten bars become a big one. The coach reviews every checkout against the book route."
    )
)

/** Step-by-step tutorial for Checkout Game, with live illustrations. */
@Composable
fun DartlessTutorial(onClose: () -> Unit) {
    var page by remember { mutableStateOf(0) }
    Dialog(onDismissRequest = onClose) {
        Card(
            colors = CardDefaults.cardColors(containerColor = NearBlack),
            border = BorderStroke(2.dp, Gold),
            shape = CutCornerShape(topStart = 16.dp, bottomEnd = 16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("HOW TO THROW", fontFamily = FontFamily.Monospace, letterSpacing = 3.sp, color = Gold, fontSize = 13.sp)
                Text(
                    "${page + 1} / ${pages.size}  ·  ${pages[page].title}",
                    fontSize = 17.sp, fontWeight = FontWeight.Bold, color = OffWhite, modifier = Modifier.padding(top = 4.dp)
                )

                Box(modifier = Modifier.fillMaxWidth().height(150.dp).padding(vertical = 10.dp), contentAlignment = Alignment.Center) {
                    when (page) {
                        0 -> PresetIllustration()
                        1 -> SwipeToThrowZone(armed = false, enabled = true, onSwipe = {}, modifier = Modifier.size(130.dp))
                        2 -> RingIllustration()
                        3 -> RhythmIllustration()
                        else -> Row(verticalAlignment = Alignment.CenterVertically) {
                            GoldBarStack(count = 23, modifier = Modifier.width(44.dp).height(130.dp))
                            Spacer(Modifier.width(18.dp))
                            CoachHead(modifier = Modifier.size(110.dp))
                        }
                    }
                }

                Text(pages[page].body, fontSize = 14.sp, color = OffWhite, textAlign = TextAlign.Center, lineHeight = 20.sp)

                Spacer(Modifier.height(14.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { if (page > 0) page-- else onClose() }) { Text(if (page > 0) "Back" else "Skip", color = Grey) }
                    Row {
                        for (i in pages.indices) {
                            Text(if (i == page) "●" else "○", color = if (i == page) Gold else Grey, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 2.dp))
                        }
                    }
                    Button(onClick = { if (page < pages.size - 1) page++ else onClose() }) { Text(if (page < pages.size - 1) "Next" else "Play") }
                }
            }
        }
    }
}

@Composable
private fun PresetIllustration() {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Preset", fontSize = 12.sp, color = Grey)
        OutlinedButton(onClick = {}, contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp)) {
            Text("Littler (fast)", color = OffWhite)
        }
        Text("approach 1.5 s · dart 1.9 s · clear 3.5 s", fontSize = 11.sp, color = PaleGold, modifier = Modifier.padding(top = 6.dp))
    }
}

@Composable
private fun RingIllustration() {
    // A small board with the ring cycling every 2.4 s
    var startMs by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) { delay(3000); startMs = System.currentTimeMillis() }
    }
    Box(modifier = Modifier.size(140.dp)) {
        Dartboard(geometry = BoardGeometry.PRACTICE)
        ThrowRing(startMs = startMs, periodSec = 2.4f, modifier = Modifier.fillMaxWidth().aspectRatio(1f))
    }
}

@Composable
private fun RhythmIllustration() {
    // Darts in hand counting down with a swipe / tap caption
    var phase by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) { delay(700); phase = (phase + 1) % 6 }
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        DartsInHand(inHand = 3 - phase / 2, modifier = Modifier.width(150.dp).height(80.dp))
        Text(
            if (phase % 2 == 0) "SWIPE" else "TAP",
            fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, letterSpacing = 4.sp,
            color = if (phase % 2 == 0) Gold else BrightGold, fontSize = 16.sp
        )
    }
}
