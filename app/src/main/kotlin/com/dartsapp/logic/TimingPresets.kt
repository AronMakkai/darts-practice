package com.dartsapp.logic

import android.content.Context

/**
 * A named timing profile learned in the Metronome screen.
 *
 * Every dart is Grab -> Aim -> Throw. The three darts are pooled, so there is one grab time,
 * one aim time and one throw time shared by all darts. The opponent's time is never part of a preset.
 */
data class TimingPreset(
    val name: String,
    val approach: Float,
    val grab: Float,
    val aim: Float,
    val throwTime: Float,
    val remove: Float,
    val rounds: Int
) {
    /** Full length of one dart: grab + aim + throw. */
    val dart: Float get() = grab + aim + throwTime

    fun summary(): String =
        "approach %.1f s  ·  grab %.1f s  ·  aim %.1f s  ·  throw %.1f s  ·  clear %.1f s  ·  %d round%s"
            .format(approach, grab, aim, throwTime, remove, rounds, if (rounds == 1) "" else "s")
}

/** Simple persistent store for timing presets (SharedPreferences, one line per preset). */
object TimingPresets {
    private const val PREFS = "timing_presets"
    private const val KEY_LIST = "presets_v2"
    private const val KEY_SELECTED = "selected"

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun load(ctx: Context): List<TimingPreset> {
        val raw = prefs(ctx).getString(KEY_LIST, "") ?: ""
        if (raw.isBlank()) return emptyList()
        return raw.split('\n').mapNotNull { line ->
            val f = line.split('|')
            if (f.size < 7) return@mapNotNull null
            try {
                TimingPreset(f[0], f[1].toFloat(), f[2].toFloat(), f[3].toFloat(), f[4].toFloat(), f[5].toFloat(), f[6].toInt())
            } catch (e: NumberFormatException) {
                null
            }
        }
    }

    private fun save(ctx: Context, list: List<TimingPreset>) {
        val raw = list.joinToString("\n") { p ->
            listOf(
                p.name, p.approach.toString(), p.grab.toString(), p.aim.toString(),
                p.throwTime.toString(), p.remove.toString(), p.rounds.toString()
            ).joinToString("|")
        }
        prefs(ctx).edit().putString(KEY_LIST, raw).apply()
    }

    /** Adds [preset], replacing any preset with the same name. Returns the new list. */
    fun add(ctx: Context, preset: TimingPreset): List<TimingPreset> {
        val clean = preset.copy(name = sanitize(preset.name))
        val list = load(ctx).filter { it.name != clean.name } + clean
        save(ctx, list)
        return list
    }

    fun delete(ctx: Context, name: String): List<TimingPreset> {
        val list = load(ctx).filter { it.name != name }
        save(ctx, list)
        if (selectedName(ctx) == name) setSelected(ctx, null)
        return list
    }

    fun selectedName(ctx: Context): String? = prefs(ctx).getString(KEY_SELECTED, null)

    fun setSelected(ctx: Context, name: String?) {
        prefs(ctx).edit().apply { if (name == null) remove(KEY_SELECTED) else putString(KEY_SELECTED, name) }.apply()
    }

    fun sanitize(name: String): String = name.replace('|', ' ').replace('\n', ' ').trim().ifEmpty { "Preset" }
}
