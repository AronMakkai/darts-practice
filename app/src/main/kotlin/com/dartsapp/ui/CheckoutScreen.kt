package com.dartsapp.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.dartsapp.logic.CheckoutLogic
import com.dartsapp.logic.TimingPresets
import androidx.compose.ui.platform.LocalContext

/**
 * Checkout mode: a random checkout is shown; after each visit at the real board the player
 * types the score they threw on the number pad. Tips show the recommended route.
 */
@Composable
fun CheckoutScreen(navController: NavHostController) {
    val context = LocalContext.current
    val presets = remember { TimingPresets.load(context) }
    var metronomeOn by remember { mutableStateOf(false) }
    var presetName by remember { mutableStateOf(TimingPresets.selectedOrDefault(context, presets)) }
    val preset = presets.firstOrNull { it.name == presetName }
    var turnKey by remember { mutableStateOf(0) }

    var start by remember { mutableStateOf(CheckoutLogic.randomCheckout()) }
    var remaining by remember { mutableStateOf(start) }
    var input by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    var finished by remember { mutableStateOf(false) }
    val history = remember { mutableStateListOf<Pair<Int, Int>>() } // (score entered, remaining before)

    fun newCheckout() {
        start = CheckoutLogic.randomCheckout()
        remaining = start
        input = ""
        message = ""
        finished = false
        history.clear()
        if (metronomeOn) turnKey++
    }

    fun submit() {
        val score = input.toIntOrNull() ?: return
        input = ""
        if (score > 180) { message = "Max score per visit is 180"; return }
        if (metronomeOn) turnKey++
        val newRem = remaining - score
        history.add(score to remaining)
        when {
            newRem == 0 -> { remaining = 0; finished = true; message = "Checked out in ${history.size} visit${if (history.size == 1) "" else "s"}!" }
            newRem < 0 || newRem == 1 -> { message = "Bust! Still on $remaining" }
            else -> { remaining = newRem; message = "" }
        }
    }

    fun undo() {
        val last = history.removeLastOrNull() ?: return
        remaining = last.second
        finished = false
        message = ""
    }

    var showSuggestion by remember { mutableStateOf(false) }
    val suggestion = remember(remaining) { if (remaining > 1) CheckoutLogic.suggest(remaining) else null }
    // Hide the suggestion again whenever the total changes.
    LaunchedEffect(remaining) { showSuggestion = false }

    Column(modifier = Modifier.fillMaxSize().padding(bottom = 12.dp)) {
        ScreenHeader("Checkout", navController) {
            TextButton(onClick = { metronomeOn = !metronomeOn; if (metronomeOn) turnKey++ }) {
                Text("Metronome", color = if (metronomeOn) Gold else Grey)
            }
            TextButton(onClick = { newCheckout() }) { Text("New", color = Gold) }
        }
        if (metronomeOn) {
            MiniMetronome(
                presets = presets, preset = preset,
                onPresetChange = { presetName = it.name; TimingPresets.setSelected(context, it.name) },
                turnKey = turnKey, active = !finished,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)
            )
        }

        RemainingDisplay(start, remaining, "", message)

        if (history.isNotEmpty()) {
            Text(
                "Visits: " + history.joinToString("  ") { it.first.toString() },
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp).align(Alignment.CenterHorizontally)
            )
        }

        // Suggestion: hidden until asked for. Lives in the flexible space above the number pad.
        Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
            if (!finished && remaining > 1) {
                if (!showSuggestion) {
                    OutlinedButton(
                        onClick = { showSuggestion = true },
                        modifier = Modifier.padding(top = 12.dp)
                    ) { Text("Show checkout suggestion", color = Gold) }
                } else if (suggestion != null) {
                    SuggestionCard(suggestion, onHide = { showSuggestion = false })
                } else {
                    Text(
                        CheckoutLogic.tip(remaining),
                        color = PaleGold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp)
                    )
                }
            }
        }

        // Score entry
        Text(
            if (input.isEmpty()) "Enter visit score" else input,
            fontSize = 40.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            color = if (input.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.fillMaxWidth().padding(8.dp)
        )

        NumberPad(
            enabled = !finished,
            onDigit = { d -> if (input.length < 3) input += d },
            onBackspace = { input = input.dropLast(1) },
            onEnter = { submit() }
        )

        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            OutlinedButton(onClick = { undo() }, enabled = history.isNotEmpty()) { Text("Undo visit") }
            if (finished) Button(onClick = { newCheckout() }) { Text("Next checkout") }
        }
    }
}

@Composable
fun NumberPad(enabled: Boolean, onDigit: (String) -> Unit, onBackspace: () -> Unit, onEnter: () -> Unit) {
    val rows = listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"), listOf("DEL", "0", "OK"))
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        for (row in rows) {
            Row(modifier = Modifier.fillMaxWidth()) {
                for (key in row) {
                    val modifier = Modifier.weight(1f).padding(4.dp).height(56.dp)
                    when (key) {
                        "DEL" -> OutlinedButton(onClick = onBackspace, enabled = enabled, modifier = modifier) { Text("DEL", fontSize = 18.sp) }
                        "OK" -> Button(onClick = onEnter, enabled = enabled, modifier = modifier) { Text("OK", fontSize = 20.sp, fontWeight = FontWeight.Bold) }
                        else -> FilledTonalButton(onClick = { onDigit(key) }, enabled = enabled, modifier = modifier) { Text(key, fontSize = 24.sp) }
                    }
                }
            }
        }
    }
}

@Composable
private fun SuggestionCard(s: CheckoutLogic.Suggestion, onHide: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = Charcoal)
    ) {
        Column(modifier = Modifier.padding(14.dp).verticalScroll(rememberScrollState())) {
            Text("Suggested", fontSize = 12.sp, color = Grey)
            Text(CheckoutLogic.routeLabel(s.best), fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Gold)
            Text(s.bestWhy, fontSize = 14.sp, color = OffWhite, modifier = Modifier.padding(top = 4.dp))
            if (s.alt != null) {
                Spacer(Modifier.height(10.dp))
                Text("Alternative", fontSize = 12.sp, color = Grey)
                Text(CheckoutLogic.routeLabel(s.alt), fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = PaleGold)
                Text(s.altWhy, fontSize = 14.sp, color = Grey, modifier = Modifier.padding(top = 4.dp))
            }
            TextButton(onClick = onHide, modifier = Modifier.align(Alignment.End)) { Text("Hide", color = Gold) }
        }
    }
}
