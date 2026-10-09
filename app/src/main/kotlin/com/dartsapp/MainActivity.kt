package com.dartsapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
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
import com.dartsapp.ui.TournamentScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        goFullScreen()
        setContent {
            DartsTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    // Keep everything clear of the camera punch-hole; the bars themselves are hidden.
                    Box(modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.displayCutout)) {
                        DartsApp()
                    }
                }
            }
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) goFullScreen()
    }

    /** Immersive mode: hide the status and navigation bars; a swipe from the edge shows them briefly. */
    private fun goFullScreen() {
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.systemBars())
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
            "dartless", "bot501", "bot501tour", "x01", "x01bot", "valuechecker" -> if (Settings.crowdOn(context)) Music.startCrowd() else Music.stopAll()
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
        composable("x01bot") { FiveOhOneScreen(navController, vsBot = true) }
        composable("bot501") { Bot501Screen(navController) }
        composable("bot501tour") { Bot501Screen(navController, tournament = true) }
        composable("tournament") { TournamentScreen(navController) }
        composable("game") {
            SubMenuScreen(
                navController, "GAME",
                listOf(
                    "CHECKOUT GAME" to "dartless",
                    "1 PLAYER 501" to "bot501",
                    "TOURNAMENT" to "tournament",
                    "FAST 501" to "valuechecker"
                ),
                showPace = true
            )
        }
        composable("irl") {
            SubMenuScreen(
                navController, "IRL",
                listOf(
                    "CHECKOUT" to "checkout",
                    "1 PLAYER 501" to "x01bot",
                    "2 PLAYER 501" to "x01",
                    "METRONOME" to "metronome"
                )
            )
        }
    }
}
