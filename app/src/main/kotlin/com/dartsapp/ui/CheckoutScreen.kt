package com.dartsapp.ui

import androidx.compose.foundation.layout.*
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

/**
 * Checkout mode: a random checkout is shown; after each visit at the real board the player
 * types the score they threw on the number pad. Tips show the recommended route.
 */
@Composable
fun CheckoutScreen(navController: NavHostController) {
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
    }

    fun submit() {
        val score = input.toIntOrNull() ?: return
        input = ""
        if (score > 180) { message = "Max score per visit is 180"; return }
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

    val tip = remember(remaining) { if (remaining > 1) CheckoutLogic.tip(remaining) else "" }

    Column(modifier = Modifier.fillMaxSize().padding(bottom = 12.dp)) {
        ScreenHeader("Checkout", navController) {
            TextButton(onClick = { newCheckout() }) { Text("New", color = Gold) }
        }

        RemainingDisplay(start, remaining, tip, message)

        if (history.isNotEmpty()) {
            Text(
                "Visits: " + history.joinToString("  ") { it.first.toString() },
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp).align(Alignment.CenterHorizontally)
            )
        }

        Spacer(Modifier.weight(1f))

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
