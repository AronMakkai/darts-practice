package com.dartsapp.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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

    Column(modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        ScreenHeader("Value Checker", navController)

        val s = selected
        Text(
            when {
                s == null -> "Tap a sector"
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
            onTap = { selected = Board.hitTest(it.x, it.y) }
        )

        Text(
            "Inner numbers = trebles, outer numbers = doubles",
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(16.dp)
        )
    }
}
