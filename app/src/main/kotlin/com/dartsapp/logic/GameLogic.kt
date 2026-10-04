package com.dartsapp.logic

import com.dartsapp.data.Board
import com.dartsapp.data.Hit
import com.dartsapp.data.Ring
import kotlin.random.Random

/**
 * Simulates throwing inaccuracy. The tap point is nudged by a random (Gaussian) offset whose
 * spread grows as accuracy drops. At 100 % the dart lands exactly where you tapped; at 0 % it
 * can land several segments away or off the board entirely.
 */
class AccuracyModel(private val random: java.util.Random = java.util.Random()) {
    /** Returns the actual landing point (normalised board coords) for a tap at [x],[y]. */
    fun land(x: Float, y: Float, accuracy: Float): Pair<Float, Float> {
        val a = accuracy.coerceIn(0f, 1f)
        if (a >= 0.999f) return x to y
        // Max spread of ~35 % of the board radius at 0 % accuracy.
        val sigma = (1f - a) * 0.35f
        val dx = (random.nextGaussian() * sigma).toFloat()
        val dy = (random.nextGaussian() * sigma).toFloat()
        return (x + dx) to (y + dy)
    }
}

object CheckoutLogic {
    /** Totals from 2..170 that cannot be finished in three darts. */
    private val bogeyNumbers = setOf(169, 168, 166, 165, 163, 162, 159)

    fun isFinishable(n: Int): Boolean = n in 2..170 && n !in bogeyNumbers

    fun randomCheckout(min: Int = 2, max: Int = 170): Int {
        while (true) {
            val n = Random.nextInt(min, max + 1)
            if (isFinishable(n)) return n
        }
    }

    // Setup darts in order of preference (lower index = preferred).
    private val setupDarts: List<Hit> = buildList {
        for (n in 20 downTo 1) add(Hit(n, Ring.TREBLE))
        for (n in 20 downTo 1) add(Hit(n, Ring.SINGLE))
        add(Hit(25, Ring.BULL))
        add(Hit(25, Ring.OUTER_BULL))
        for (n in 20 downTo 1) add(Hit(n, Ring.DOUBLE))
    }

    // Finishing darts in order of preference (popular doubles first).
    private val finishers: List<Hit> = buildList {
        for (n in listOf(20, 16, 8, 18)) add(Hit(n, Ring.DOUBLE))
        add(Hit(25, Ring.BULL))
        for (n in listOf(12, 10, 4, 2, 14, 6, 19, 17, 15, 13, 11, 9, 7, 5, 3, 1)) add(Hit(n, Ring.DOUBLE))
    }

    /** Best route (fewest darts, then nicest doubles) to finish [remaining], or null if impossible. */
    fun bestFinish(remaining: Int): List<Hit>? {
        if (remaining < 2 || remaining > 170) return null
        var best: List<Hit>? = null
        var bestCost = Int.MAX_VALUE

        fun consider(seq: List<Hit>) {
            val cost = seq.size * 10_000 +
                finishers.indexOf(seq.last()) * 30 +
                seq.dropLast(1).sumOf { setupDarts.indexOf(it) }
            if (cost < bestCost) { bestCost = cost; best = seq }
        }

        for (f in finishers) if (f.score == remaining) consider(listOf(f))
        if (best != null) return best

        for (a in setupDarts) for (f in finishers) if (a.score + f.score == remaining) consider(listOf(a, f))
        if (best != null) return best

        for (a in setupDarts) for (b in setupDarts) {
            val rest = remaining - a.score - b.score
            if (rest < 2) continue
            for (f in finishers) if (f.score == rest) consider(listOf(a, b, f))
        }
        return best
    }

    fun tip(remaining: Int): String {
        if (remaining <= 1) return ""
        bestFinish(remaining)?.let { route ->
            return "Finish: " + route.joinToString("  ") { it.label }
        }
        // Not finishable in three darts: suggest a setup that leaves a finish.
        for (d in setupDarts) {
            val left = remaining - d.score
            if (left >= 2 && isFinishable(left)) {
                return "No 3-dart finish. ${d.label} leaves $left"
            }
        }
        return "Score heavily, aim T20"
    }
}
