package com.dartsapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.dartsapp.ui.DartsTheme
import com.dartsapp.ui.MainMenuScreen
import com.dartsapp.ui.CheckoutScreen
import com.dartsapp.ui.DartlessScreen
import com.dartsapp.ui.ValueCheckerScreen
import com.dartsapp.ui.MetronomeScreen
import com.dartsapp.ui.SettingsScreen
import com.dartsapp.ui.FiveOhOneScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            DartsTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    DartsApp()
                }
            }
        }
    }
}

@Composable
fun DartsApp() {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = "menu") {
        composable("menu") { MainMenuScreen(navController) }
        composable("checkout") { CheckoutScreen(navController) }
        composable("dartless") { DartlessScreen(navController) }
        composable("valuechecker") { ValueCheckerScreen(navController) }
        composable("metronome") { MetronomeScreen(navController) }
        composable("settings") { SettingsScreen(navController) }
        composable("x01") { FiveOhOneScreen(navController) }
    }
}
