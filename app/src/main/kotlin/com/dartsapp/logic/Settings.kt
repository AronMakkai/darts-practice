package com.dartsapp.logic

import android.content.Context

/**
 * Difficulty for Dartless Checkout. Scales the timing windows, the hesitation allowance and the
 * scatter of a missed throw, and shifts the checkout ranges.
 */
enum class Difficulty(
    val label: String,
    val windowScale: Float,     // on-pace window around the ideal moment
    val lateScale: Float,       // how fast a late throw plummets (bigger = more forgiving)
    val pauseScale: Float,      // free hesitation between throws
    val scatterScale: Float,    // spread of a miss at low accuracy
    val checkoutShift: Int      // added to the streak-based range (negative = easier)
) {
    EASY("Easy", 1.8f, 2.0f, 2.0f, 0.6f, -40),
    NORMAL("Normal", 1.0f, 1.0f, 1.0f, 1.0f, 0),
    HARD("Hard", 0.7f, 0.7f, 0.6f, 1.25f, 30)
}

/** How strongly the aiming ring is drawn. [opacity] scales the ring's alpha: High = bold, Low = faint. */
enum class AimOpacity(val label: String, val opacity: Float, val blurb: String) {
    LO("Lo", 0.35f, "Faint ring, the board stays clear"),
    MID("Mid", 0.65f, "Half-strength ring"),
    HI("Hi", 1.0f, "Bold ring, lots of aiming room")
}

object Settings {
    private const val PREFS = "settings"

    fun aimOpacity(ctx: Context): AimOpacity {
        val name = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("aimOpacity", null)
        return AimOpacity.values().firstOrNull { it.name == name } ?: AimOpacity.HI
    }

    fun setAimOpacity(ctx: Context, t: AimOpacity) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString("aimOpacity", t.name).apply()
    }

    fun opponentIndex(ctx: Context): Int = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt("opponent", 0)
    fun setOpponentIndex(ctx: Context, i: Int) { ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putInt("opponent", i).apply() }

    fun difficulty(ctx: Context): Difficulty {
        val name = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("difficulty", null)
        return Difficulty.values().firstOrNull { it.name == name } ?: Difficulty.NORMAL
    }

    fun setDifficulty(ctx: Context, d: Difficulty) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString("difficulty", d.name).apply()
    }
}
