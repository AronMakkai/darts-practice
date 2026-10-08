package com.dartsapp.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dartsapp.logic.TimingPreset
import kotlinx.coroutines.delay

private val turnSequence = listOf(Step.APPROACH, Step.DART1, Step.DART2, Step.DART3, Step.REMOVE)

/**
 * A compact metronome for one player's turn: a small stick figure, the current step, a preset
 * picker and a play/stop control. Each time [turnKey] changes (and is > 0) it runs one full turn —
 * approach, three darts, clear — with the preset's timings and the usual clicks, then rests.
 */
@Composable
fun MiniMetronome(
    presets: List<TimingPreset>,
    preset: TimingPreset?,
    onPresetChange: (TimingPreset) -> Unit,
    turnKey: Int,
    active: Boolean,
    modifier: Modifier = Modifier,
    label: String? = null
) {
    var step by remember { mutableStateOf(Step.READY) }
    var running by remember { mutableStateOf(false) }
    var runId by remember { mutableStateOf(0) }
    var menuOpen by remember { mutableStateOf(false) }


    // Start a turn whenever the host bumps turnKey
    LaunchedEffect(turnKey) {
        if (turnKey > 0 && active && preset != null) { runId++; running = true }
    }
    LaunchedEffect(runId, running) {
        if (!running) { step = Step.READY; return@LaunchedEffect }
        val p = preset ?: run { running = false; return@LaunchedEffect }
        for ((i, s) in turnSequence.withIndex()) {
            step = s
            playStepDoneTone(if (i == 0) Step.OPPONENT else turnSequence[i - 1])
            delay((stepSeconds(s.kind, p, 3f, 0f) * 1000).toLong())
        }
        playStepDoneTone(Step.REMOVE)
        step = Step.READY
        running = false
    }

    Row(
        modifier = modifier.background(if (active) Charcoal else Night).padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        StickFigure(step = step, modifier = Modifier.width(96.dp).height(56.dp))
        Column(modifier = Modifier.weight(1f).padding(start = 6.dp)) {
            if (label != null) Text(label, fontSize = 10.sp, fontFamily = FontFamily.Monospace, letterSpacing = 2.sp, color = Grey)
            Text(
                if (running) step.label.uppercase() else "READY",
                fontSize = 13.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace,
                color = if (running) Gold else Grey, maxLines = 1
            )
            Box {
                TextButton(onClick = { menuOpen = true }, contentPadding = PaddingValues(0.dp), modifier = Modifier.height(24.dp)) {
                    Text(preset?.name ?: "Choose preset", fontSize = 12.sp, color = if (preset != null) PaleGold else Gold, maxLines = 1)
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    for (p in presets) {
                        DropdownMenuItem(text = { Text(p.name) }, onClick = { onPresetChange(p); menuOpen = false })
                    }
                }
            }
        }
        OutlinedButton(
            onClick = { if (running) running = false else if (preset != null) { runId++; running = true } },
            enabled = preset != null,
            contentPadding = PaddingValues(horizontal = 10.dp),
            modifier = Modifier.height(34.dp)
        ) { Text(if (running) "■" else "▶", color = Gold) }
    }
}
