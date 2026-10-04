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

    /** All finishing routes for [remaining] (1–3 darts), best first. */
    fun allFinishes(remaining: Int): List<List<Hit>> {
        if (remaining < 2 || remaining > 170) return emptyList()
        val routes = mutableListOf<List<Hit>>()
        for (f in finishers) if (f.score == remaining) routes.add(listOf(f))
        for (a in setupDarts) for (f in finishers) if (a.score + f.score == remaining) routes.add(listOf(a, f))
        for (a in setupDarts) for (b in setupDarts) {
            val rest = remaining - a.score - b.score
            if (rest < 2) continue
            for (f in finishers) if (f.score == rest) routes.add(listOf(a, b, f))
        }
        return routes.sortedBy { cost(it) }
    }

    private fun cost(seq: List<Hit>): Int =
        seq.size * 10_000 +
            finishers.indexOf(seq.last()) * 30 +
            seq.dropLast(1).sumOf { setupDarts.indexOf(it) }

    /** Best route (fewest darts, then nicest doubles) to finish [remaining], or null if impossible. */
    fun bestFinish(remaining: Int): List<Hit>? = allFinishes(remaining).firstOrNull()

    data class Suggestion(val best: List<Hit>, val bestWhy: String, val alt: List<Hit>?, val altWhy: String)

    /** Best route with an explanation, plus an alternative that finishes on a different double. */
    fun suggest(remaining: Int): Suggestion? {
        val routes = allFinishes(remaining)
        val best = routes.firstOrNull() ?: return null
        val alt = routes.firstOrNull { it.last() != best.last() }
            ?: routes.firstOrNull { it != best && it.first() != best.first() }
        return Suggestion(best, explain(best, remaining, true), alt, alt?.let { explain(it, remaining, false) } ?: "")
    }

    fun routeLabel(route: List<Hit>): String = route.joinToString("  ") { it.label }

    private fun explain(route: List<Hit>, remaining: Int, isBest: Boolean): String {
        val sb = StringBuilder()
        val finisher = route.last()
        if (route.size == 1) {
            sb.append("Straight at ${finisher.label}. ")
        } else {
            val setup = route.dropLast(1).joinToString(" then ") { describeSetup(it) }
            val left = finisher.score
            sb.append("Hit $setup to leave $left, then ${finisher.label}. ")
        }
        sb.append(describeFinisher(finisher))
        sb.append(
            when (route.size) {
                1 -> " One dart does it, with two spare for another go."
                2 -> " Two darts, so you have one in hand if the first goes astray."
                else -> " Needs all three darts, so there is no room for a miss."
            }
        )
        if (isBest && route.size > 1 && route.dropLast(1).any { it.ring == Ring.SINGLE }) {
            sb.append(" Taking a single here is deliberate: it sets up the double without risking a bust.")
        }
        return sb.toString().trim()
    }

    private fun describeSetup(h: Hit): String = when (h.ring) {
        Ring.TREBLE -> if (h.number >= 19) "T${h.number} (${h.score}, the big scoring bed)" else "T${h.number} (${h.score})"
        Ring.SINGLE -> "single ${h.number}"
        Ring.DOUBLE -> "D${h.number} (${h.score})"
        Ring.BULL -> "the bull (50)"
        Ring.OUTER_BULL -> "the 25"
        Ring.MISS -> "a miss"
    }

    private fun describeFinisher(h: Hit): String {
        if (h.ring == Ring.BULL) return "The bull is a small target, and a miss into the 25 leaves 25, which needs a single before another double."
        val n = h.number
        return when (n) {
            16 -> "D16 is the players' favourite: drop into single 16 and you are left on D8, then D4 — the halving chain keeps you on a double."
            20 -> "D20 is the biggest double on the board; a single 20 leaves D10."
            8 -> "D8 is a comfortable double, and single 8 still leaves D4."
            else -> if (n % 2 == 0) {
                val half = n / 2
                if (half % 2 == 0) "D$n is an even double: a single $n leaves D$half, still a clean double."
                else "A single $n leaves D$half — still a double, though an odd one, so the next miss costs you a setup dart."
            } else {
                "D$n is an odd double: a single leaves $n, which is not a finish, so it needs to be hit clean."
            }
        }
    }

    fun tip(remaining: Int): String {
        if (remaining <= 1) return ""
        bestFinish(remaining)?.let { route ->
            return "Finish: " + routeLabel(route)
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
