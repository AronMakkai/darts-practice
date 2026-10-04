package com.dartsapp.logic

import android.content.Context

/**
 * A named timing profile learned in the Metronome screen.
 *
 * The three darts are pooled into ONE dart time. The opponent's time is never part of a preset.
 */
data class TimingPreset(
    val name: String,
    val approach: Float,
    val dart: Float,
    val remove: Float,
    val rounds: Int
) {
    val builtIn: Boolean get() = rounds == 0

    fun summary(): String =
        if (builtIn) "approach %.1f s  ·  dart %.1f s  ·  clear %.1f s  ·  built-in".format(approach, dart, remove)
        else "approach %.1f s  ·  dart %.1f s  ·  clear %.1f s  ·  %d round%s"
            .format(approach, dart, remove, rounds, if (rounds == 1) "" else "s")
}

/** Simple persistent store for timing presets (SharedPreferences, one line per preset). */
object TimingPresets {
    private const val PREFS = "timing_presets"
    private const val KEY_LIST = "presets_v3"
    private const val KEY_SELECTED = "selected"

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Presets that ship with the app. They cannot be deleted. */
    val BUILT_IN = listOf(
        TimingPreset("Pike (slow)", approach = 4f, dart = 5f, remove = 6f, rounds = 0)
    )

    fun load(ctx: Context): List<TimingPreset> = BUILT_IN + loadStored(ctx)

    private fun loadStored(ctx: Context): List<TimingPreset> {
        val raw = prefs(ctx).getString(KEY_LIST, "") ?: ""
        if (raw.isBlank()) return emptyList()
        return raw.split('\n').mapNotNull { line ->
            val f = line.split('|')
            if (f.size < 5) return@mapNotNull null
            try {
                TimingPreset(f[0], f[1].toFloat(), f[2].toFloat(), f[3].toFloat(), f[4].toInt().coerceAtLeast(1))
            } catch (e: NumberFormatException) {
                null
            }
        }
    }

    private fun save(ctx: Context, list: List<TimingPreset>) {
        val raw = list.joinToString("\n") { p ->
            listOf(p.name, p.approach.toString(), p.dart.toString(), p.remove.toString(), p.rounds.toString()).joinToString("|")
        }
        prefs(ctx).edit().putString(KEY_LIST, raw).apply()
    }

    /** Adds [preset], replacing any preset with the same name. Returns the new list. */
    fun add(ctx: Context, preset: TimingPreset): List<TimingPreset> {
        var clean = preset.copy(name = sanitize(preset.name), rounds = preset.rounds.coerceAtLeast(1))
        if (BUILT_IN.any { it.name == clean.name }) clean = clean.copy(name = clean.name + " (mine)")
        val list = loadStored(ctx).filter { it.name != clean.name } + clean
        save(ctx, list)
        return BUILT_IN + list
    }

    fun delete(ctx: Context, name: String): List<TimingPreset> {
        if (BUILT_IN.any { it.name == name }) return load(ctx)
        val list = loadStored(ctx).filter { it.name != name }
        save(ctx, list)
        if (selectedName(ctx) == name) setSelected(ctx, null)
        return BUILT_IN + list
    }

    fun selectedName(ctx: Context): String? = prefs(ctx).getString(KEY_SELECTED, null)

    fun setSelected(ctx: Context, name: String?) {
        prefs(ctx).edit().apply { if (name == null) remove(KEY_SELECTED) else putString(KEY_SELECTED, name) }.apply()
    }

    fun sanitize(name: String): String = name.replace('|', ' ').replace('\n', ' ').trim().ifEmpty { "Preset" }
}
