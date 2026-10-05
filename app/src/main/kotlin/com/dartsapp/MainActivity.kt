package com.dartsapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.navigation.compose.currentBackStackEntryAsState
import com.dartsapp.logic.Announcer
import com.dartsapp.logic.Music
import com.dartsapp.logic.Settings
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
import com.dartsapp.ui.Bot501Screen
import com.dartsapp.ui.SubMenuScreen

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
    val context = LocalContext.current
    LaunchedEffect(Unit) { Announcer.init(context) }

    // Background audio follows the screen: synth track on the menus, a quiet chatting crowd in the game modes.
    val entry by navController.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    val lifecycleOwner = LocalLifecycleOwner.current
    fun applyAudio(r: String?, foreground: Boolean) {
        if (!foreground) { Music.stopAll(); return }
        when (r) {
            "menu", "game", "irl", "settings" -> if (Settings.musicOn(context)) Music.startMenu() else Music.stopAll()
            "dartless", "bot501", "x01", "valuechecker" -> if (Settings.crowdOn(context)) Music.startCrowd() else Music.stopAll()
            else -> Music.stopAll()
        }
    }
    LaunchedEffect(route) { applyAudio(route, true) }
    DisposableEffect(lifecycleOwner, route) {
        val obs = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> applyAudio(route, false)
                Lifecycle.Event.ON_START -> applyAudio(route, true)
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
    }

    NavHost(navController = navController, startDestination = "menu") {
        composable("menu") { MainMenuScreen(navController) }
        composable("checkout") { CheckoutScreen(navController) }
        composable("dartless") { DartlessScreen(navController) }
        composable("valuechecker") { ValueCheckerScreen(navController) }
        composable("metronome") { MetronomeScreen(navController) }
        composable("settings") { SettingsScreen(navController) }
        composable("x01") { FiveOhOneScreen(navController) }
        composable("bot501") { Bot501Screen(navController) }
        composable("game") {
            SubMenuScreen(
                navController, "GAME",
                listOf(
                    "CHECKOUT GAME" to "dartless",
                    "501  ·  1 PLAYER" to "bot501",
                    "FAST 501  ·  SOLO" to "valuechecker"
                )
            )
        }
        composable("irl") {
            SubMenuScreen(
                navController, "IRL",
                listOf(
                    "CHECKOUT" to "checkout",
                    "501  ·  2 PLAYER" to "x01",
                    "METRONOME" to "metronome"
                )
            )
        }
    }
}
