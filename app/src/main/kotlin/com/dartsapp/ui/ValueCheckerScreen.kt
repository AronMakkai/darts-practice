package com.dartsapp.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.dartsapp.data.Board
import com.dartsapp.data.Hit
import com.dartsapp.data.Ring

/** Value checker: the board with every double and treble value written on it. Tap any sector for its value. */
@Composable
fun ValueCheckerScreen(navController: NavHostController) {
    var selected by remember { mutableStateOf<Hit?>(null) }
    var focus by remember { mutableStateOf<Offset?>(null) }

    Column(modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        ScreenHeader("Value Checker", navController)

        val s = selected
        Text(
            when {
                s == null -> "Touch or slide over the board"
                s.ring == Ring.MISS -> "Miss"
                else -> "${s.label}  =  ${s.score}"
            },
            fontSize = 36.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(vertical = 12.dp)
        )

        Dartboard(
            modifier = Modifier.padding(8.dp),
            showValues = true,
            focus = focus,
            onPointer = { p ->
                focus = p
                if (p != null) selected = Board.hitTest(p.x, p.y)
            }
        )

        Text(
            "Inner numbers = trebles, outer numbers = doubles. Slide your finger to magnify.",
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(16.dp)
        )
    }
}
