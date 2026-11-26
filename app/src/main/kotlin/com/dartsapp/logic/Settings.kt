package com.dartsapp.logic

import android.content.Context

/**
 * Difficulty for Checkout Game. Scales the timing windows, the hesitation allowance and the
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

    private fun flag(ctx: Context, key: String, def: Boolean) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(key, def)
    private fun setFlag(ctx: Context, key: String, v: Boolean) { ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(key, v).apply() }

    /** Ask "Are you sure?" before the back button leaves the app from the main menu. */
    fun exitConfirm(ctx: Context) = flag(ctx, "exitConfirm", true)
    fun setExitConfirm(ctx: Context, v: Boolean) = setFlag(ctx, "exitConfirm", v)
    /** The first-launch welcome tour has been seen (or skipped). */
    fun welcomeSeen(ctx: Context) = flag(ctx, "welcomeSeen", false)
    fun setWelcomeSeen(ctx: Context, v: Boolean) = setFlag(ctx, "welcomeSeen", v)
    /** Matches won against the characters, and whether we have already asked for a rating. */
    fun matchesWon(ctx: Context): Int = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt("matchesWon", 0)
    fun addMatchWon(ctx: Context) { ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putInt("matchesWon", matchesWon(ctx) + 1).apply() }
    fun rateAsked(ctx: Context) = flag(ctx, "rateAsked", false)
    fun setRateAsked(ctx: Context, v: Boolean) = setFlag(ctx, "rateAsked", v)

    /** Where the swipe-to-throw arrow sits: 0 = bottom-left corner, 1 = in by the centre of the screen. */
    fun throwButtonPos(ctx: Context): Float = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getFloat("throwButtonPos", 0f)
    fun setThrowButtonPos(ctx: Context, v: Float) { ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putFloat("throwButtonPos", v).apply() }

    fun musicOn(ctx: Context) = flag(ctx, "music", true)
    fun setMusicOn(ctx: Context, v: Boolean) = setFlag(ctx, "music", v)
    fun crowdOn(ctx: Context) = flag(ctx, "crowd", true)
    fun setCrowdOn(ctx: Context, v: Boolean) = setFlag(ctx, "crowd", v)
    fun announcerOn(ctx: Context) = flag(ctx, "announcer", true)
    fun setAnnouncerOn(ctx: Context, v: Boolean) = setFlag(ctx, "announcer", v)

    /** The Coach only plays in tournaments, and only joins the draw once you have won one. */
    fun coachUnlocked(ctx: Context): Boolean = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean("coachUnlocked", false)
    fun setCoachUnlocked(ctx: Context, on: Boolean) { ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean("coachUnlocked", on).apply() }
    /** The tournament prize: the record player on the main menu (anyone who has already won a tournament has it). */
    fun turntableUnlocked(ctx: Context): Boolean =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean("turntableUnlocked", false) || coachUnlocked(ctx)
    fun setTurntableUnlocked(ctx: Context, on: Boolean) { ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean("turntableUnlocked", on).apply() }

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
