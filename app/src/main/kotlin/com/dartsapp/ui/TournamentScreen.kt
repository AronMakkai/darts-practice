package com.dartsapp.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.dartsapp.logic.Tournament

/**
 * Eight-player knockout: the draw as a bracket, your next match, and the result when it is done.
 */
@Composable
fun TournamentScreen(navController: NavHostController) {
    // Re-read the Tournament object whenever we come back to this screen
    var tick by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) { tick++ }
    // Winning the tournament: the Coach comes out to send you off to a real board (once per tournament)
    var champSplash by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (Tournament.youWon && !Tournament.congratulated) {
            Tournament.congratulated = true
            kotlinx.coroutines.delay(700)
            champSplash = true
        }
    }
    if (champSplash) {
        ChampionSplash(onIrl = { champSplash = false; navController.navigate("irl") }, onDismiss = { champSplash = false })
    }
    val started = Tournament.started
    val unlockContext = androidx.compose.ui.platform.LocalContext.current
    val coachUnlocked = com.dartsapp.logic.Settings.coachUnlocked(unlockContext)

    Box(modifier = Modifier.fillMaxSize()) {
    // The menu's sunset city behind it all, dimmed so the draw reads clearly
    VectorBackdrop(modifier = Modifier.fillMaxSize())
    Box(modifier = Modifier.fillMaxSize().background(Night.copy(alpha = 0.55f)))
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
        ScreenHeader("", navController) {
            // TEMP test button: show the champion splash without winning
            TextButton(onClick = { champSplash = true }) { Text("Test win", color = Red, fontSize = 11.sp) }
            if (started) TextButton(onClick = { Tournament.reset(); tick++ }) { Text("New", color = Gold) }
        }
        if (tick < 0) Text("")
        ChromeTitle("TOURNAMENT", modifier = Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 24.dp))

        if (!started) {
            Column(modifier = Modifier.fillMaxWidth().padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("EIGHT PLAYERS · ONE TROPHY", fontFamily = FontFamily.Monospace, letterSpacing = 3.sp, color = Gold, fontSize = 14.sp, fontWeight = FontWeight.Bold,
                    style = androidx.compose.ui.text.TextStyle(shadow = androidx.compose.ui.graphics.Shadow(color = NeonPink, blurRadius = 14f)))
                Spacer(Modifier.height(12.dp))
                Text(
                    (if (coachUnlocked) "You, the six regulars and the Coach. " else "You and the six regulars — the Coach sits this one out until you have won a tournament, so the top seed gets a bye. ") + "Quarter-final first to 2 legs, semi-final first to 3, final first to 4 — " +
                        "and the opposition throws sharper every round. Your matches are played in 501; the rest of the draw plays out on its own.",
                    fontSize = 14.sp, color = OffWhite, textAlign = TextAlign.Center, lineHeight = 20.sp
                )
                Spacer(Modifier.height(20.dp))
                // The field, big, in a neon-framed line-up: tap any of them for his bio
                for (row in Opponent.values().filter { it != Opponent.COACH || coachUnlocked }.chunked(3)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(vertical = 5.dp)) {
                        for (o in row) {
                            Column(
                                modifier = Modifier.width(96.dp).neonFrame(NeonPink).padding(8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                OpponentHeadWithBio(o, modifier = Modifier.size(72.dp))
                                Text(o.displayName.removePrefix("THE "), fontSize = 10.sp, fontFamily = FontFamily.Monospace, letterSpacing = 1.sp, color = PaleGold, maxLines = 1)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(18.dp))
                NeonSignButton("DRAW THE BRACKET", onClick = { Tournament.start(coachUnlocked); tick++ })
            }
        } else {
            Bracket(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 12.dp))

            Spacer(Modifier.height(8.dp))
            val opp = Tournament.currentOpponent()
            when {
                Tournament.youWon -> {
                    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("CHAMPION", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Black, letterSpacing = 6.sp, color = BrightGold, fontSize = 28.sp)
                        Text("You beat the lot of them. The Coach has seen enough — he's picking up his darts. He's in the draw from now on.", fontSize = 13.sp, color = PaleGold, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 6.dp))
                        Spacer(Modifier.height(14.dp))
                        NeonSignButton("RUN IT AGAIN", onClick = { Tournament.start(coachUnlocked); tick++ })
                    }
                }
                Tournament.eliminatedIn >= 0 -> {
                    val champ = Tournament.champion
                    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Knocked out in the ${Tournament.rounds[Tournament.eliminatedIn].lowercase()}", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Red)
                        if (champ != null) Text("${champ.name} takes the title.", fontSize = 13.sp, color = Grey, modifier = Modifier.padding(top = 4.dp))
                        Spacer(Modifier.height(14.dp))
                        NeonSignButton("NEW TOURNAMENT", onClick = { Tournament.start(coachUnlocked); tick++ })
                    }
                }
                opp != null -> {
                    val r = Tournament.round
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).neonFrame(NeonPink, corner = 14.dp).padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OpponentHeadWithBio(opp, modifier = Modifier.size(96.dp))
                        Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
                            Text(Tournament.rounds[r].uppercase(), fontFamily = FontFamily.Monospace, letterSpacing = 2.sp, color = Gold, fontSize = 12.sp)
                            Text("vs ${opp.displayName}", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = OffWhite)
                            Text("First to ${Tournament.legsPerSet(r) / 2 + 1} legs · ${opp.blurb}", fontSize = 11.sp, color = Grey, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        NeonSignButton("GAME ON", onClick = { navController.navigate("bot501tour") })
                    }
                }
            }
        }
    }
    }
}

/** The draw: quarter-finals, semi-finals, final and champion as four columns. */
@Composable
private fun Bracket(modifier: Modifier = Modifier) {
    val slots = Tournament.slots
    val entrants = Tournament.entrants
    val titles = listOf("QF", "SF", "FINAL", "")
    Row(modifier = modifier.height(370.dp)) {
        for (r in 0..3) {
            Column(modifier = Modifier.weight(if (r == 3) 0.8f else 1f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(titles[r], fontFamily = FontFamily.Monospace, letterSpacing = 2.sp, color = BubbleText, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                    style = androidx.compose.ui.text.TextStyle(shadow = androidx.compose.ui.graphics.Shadow(color = NeonPink, blurRadius = 12f)),
                    modifier = Modifier.height(20.dp))
                val s = slots[r]
                if (r == 3) {
                    Box(modifier = Modifier.fillMaxHeight(), contentAlignment = Alignment.Center) {
                        val idx = s[0]
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("🏆", fontSize = 40.sp)
                            PlayerCell(idx, winner = idx >= 0, loser = false, pending = idx < 0)
                        }
                    }
                } else {
                    Column(modifier = Modifier.fillMaxHeight(), verticalArrangement = Arrangement.SpaceEvenly) {
                        for (pair in 0 until s.size / 2) {
                            val a = s[pair * 2]; val b = s[pair * 2 + 1]
                            val next = slots[r + 1][pair]
                            // the two players side by side; the match still to play glows, finished ones are switched-off tubes
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp).neonFrame(if (next >= 0) NeonPink else Gold, lit = next < 0).padding(horizontal = 4.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                PlayerCell(a, winner = next >= 0 && next == a, loser = next >= 0 && next != a, pending = a < 0, modifier = Modifier.weight(1f))
                                PlayerCell(b, winner = next >= 0 && next == b, loser = next >= 0 && next != b, pending = b < 0, modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PlayerCell(idx: Int, winner: Boolean, loser: Boolean, pending: Boolean, modifier: Modifier = Modifier.fillMaxWidth()) {
    val e = if (idx >= 0) Tournament.entrants[idx] else null
    val color = when { winner -> BrightGold; loser -> Grey.copy(alpha = 0.5f); else -> OffWhite }
    val head = 32.dp
    Column(modifier = modifier.padding(vertical = 1.dp).alpha(if (loser) 0.55f else 1f), horizontalAlignment = Alignment.CenterHorizontally) {
        if (e == null) {
            Box(modifier = Modifier.size(head).background(Charcoal), contentAlignment = Alignment.Center) { Text("?", color = Grey, fontSize = 14.sp) }
        } else if (e.bye) {
            // The Coach, locked: a dimmed portrait with a padlock until you have won a tournament
            Box(modifier = Modifier.size(head), contentAlignment = Alignment.Center) {
                CoachHead(modifier = Modifier.size(head).alpha(0.35f))
                Text("🔒", fontSize = 14.sp)
            }
        } else if (e.isYou) {
            Box(modifier = Modifier.size(head).background(if (loser) Charcoal else DarkRed), contentAlignment = Alignment.Center) {
                Text("YOU", color = if (loser) Grey else BrightGold, fontSize = 11.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace)
            }
        } else {
            OpponentHeadWithBio(e.opponent!!, modifier = Modifier.size(head))
        }
        Text(
            e?.name?.removePrefix("THE ") ?: "—",
            fontSize = 8.sp, fontFamily = FontFamily.Monospace, color = color, maxLines = 1, overflow = TextOverflow.Clip,
            fontWeight = if (winner) FontWeight.Black else FontWeight.Normal,
            style = if (winner) androidx.compose.ui.text.TextStyle(shadow = androidx.compose.ui.graphics.Shadow(color = Gold, blurRadius = 10f)) else androidx.compose.ui.text.TextStyle.Default
        )
    }
}


/**
 * Shown when you win the tournament: the Coach congratulates you and sends you out to play for real,
 * with a "dartboard near me" search and a shortcut to the Darts IRL menu.
 */
@Composable
internal fun ChampionSplash(onIrl: () -> Unit, onDismiss: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var page by remember { mutableStateOf(0) }     // 0 = congratulations, 1 = "for real" + the links
    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Column(
            modifier = Modifier.fillMaxSize().background(Color(0xF5101010)).verticalScroll(rememberScrollState()).padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center
        ) {
            Text("🏆", fontSize = 44.sp)
            Text("CHAMPION", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Black, letterSpacing = 6.sp, color = BrightGold, fontSize = 34.sp)
            CoachHead(modifier = Modifier.padding(vertical = 10.dp).size(230.dp))
            Box(modifier = Modifier.background(Charcoal).border(2.dp, Gold).padding(horizontal = 18.dp, vertical = 6.dp)) {
                Text("THE COACH", fontFamily = FontFamily.Monospace, letterSpacing = 3.sp, fontWeight = FontWeight.Black, color = Gold, fontSize = 16.sp)
            }
            Box(
                modifier = Modifier.fillMaxWidth().padding(top = 14.dp)
                    .background(BubblePurple, androidx.compose.foundation.shape.CutCornerShape(topStart = 14.dp, bottomEnd = 14.dp))
                    .border(androidx.compose.foundation.BorderStroke(3.dp, NeonPink), androidx.compose.foundation.shape.CutCornerShape(topStart = 14.dp, bottomEnd = 14.dp))
                    .clickable(indication = null, interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }) { page = 1 }
                    .padding(16.dp)
            ) {
                Text(
                    if (page == 0) "Congratulations, champion! You beat the lot of them — and you now have the throwing rhythm of a professional darts player."
                    else "For real, the swipe and tap timing is based on real darts, and there is a metronome function in the Darts IRL menu to explore this further. GAME ON!",
                    color = BubbleText, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, lineHeight = 22.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()
                )
            }
            if (page == 0) {
                TextButton(onClick = { page = 1 }, modifier = Modifier.padding(top = 10.dp)) { Text("tap to continue", color = Grey) }
            } else {
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = {
                        try {
                            context.startActivity(
                                android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://www.google.com/search?q=dartboard+near+me"))
                                    .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                            )
                        } catch (e: Exception) { /* no browser on the device */ }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Find a dartboard near me") }
                OutlinedButton(onClick = onIrl, modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) { Text("Open the Darts IRL menu", color = Gold) }
            }
            TextButton(onClick = onDismiss, modifier = Modifier.padding(top = 4.dp)) { Text("Back to the bracket", color = Grey) }
        }
    }
}
