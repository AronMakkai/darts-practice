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

@Composable
fun MainMenuScreen(navController: NavHostController) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Darts Practice", fontSize = 40.sp, fontWeight = FontWeight.Bold, color = Gold, modifier = Modifier.padding(bottom = 40.dp))
        MenuButton("Checkout") { navController.navigate("checkout") }
        MenuButton("Dartless Checkout") { navController.navigate("dartless") }
        MenuButton("Value Checker") { navController.navigate("valuechecker") }
        MenuButton("Metronome") { navController.navigate("metronome") }
    }
}

@Composable
private fun MenuButton(label: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp).height(60.dp)
    ) { Text(label, fontSize = 20.sp) }
}

/** Shared top bar with a back button and title. */
@Composable
fun ScreenHeader(title: String, navController: NavHostController, trailing: @Composable () -> Unit = {}) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TextButton(onClick = { navController.popBackStack() }) { Text("< Back", color = Gold) }
        Text(
            title,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = Gold,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f)
        )
        trailing()
    }
}

/** Big "remaining" display used by both checkout modes. */
@Composable
fun RemainingDisplay(start: Int, remaining: Int, tip: String, message: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Text("Checkout $start", fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(remaining.toString(), fontSize = 72.sp, fontWeight = FontWeight.Bold, color = Gold)
        if (tip.isNotEmpty()) Text(tip, fontSize = 18.sp, color = PaleGold, textAlign = TextAlign.Center)
        if (message.isNotEmpty()) Text(message, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 4.dp), textAlign = TextAlign.Center)
    }
}
