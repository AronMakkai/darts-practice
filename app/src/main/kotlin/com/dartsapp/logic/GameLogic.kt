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
    fun land(x: Float, y: Float, accuracy: Float, scatterScale: Float = 1f): Pair<Float, Float> {
        val a = accuracy.coerceIn(0f, 1f)
        if (a >= 0.999f) return x to y
        // Max spread of ~35 % of the board radius at 0 % accuracy (scaled by difficulty).
        val sigma = (1f - a) * 0.35f * scatterScale
        val dx = (random.nextGaussian() * sigma).toFloat()
        val dy = (random.nextGaussian() * sigma).toFloat()
        return (x + dx) to (y + dy)
    }
}

object CheckoutLogic {
    /** Totals from 2..170 that cannot be finished in three darts. */
    private val bogeyNumbers = setOf(169, 168, 166, 165, 163, 162, 159)

    fun isFinishable(n: Int): Boolean = n in 2..170 && n !in bogeyNumbers

    /**
     * Checkout range that gets harder as the perfect-rhythm streak grows:
     * 0–2: 2–60 · 3–5: 40–100 · 6–9: 80–140 · 10–19: 100–170 · 20+: 121–170 (big finishes only).
     */
    fun randomCheckoutForStreak(streak: Int, shift: Int = 0): Int {
        val (lo, hi) = when {
            streak < 3 -> 2 to 60
            streak < 6 -> 40 to 100
            streak < 10 -> 80 to 140
            streak < 20 -> 100 to 170
            else -> 121 to 170
        }
        val min = (lo + shift).coerceIn(2, 160)
        val max = (hi + shift).coerceIn(min + 5, 170)
        return randomCheckout(min, max)
    }

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

    // ---- Reaching a checkout (after dartscheckoutassistant.com, "A guide to reaching a checkout") ----

    /** Scores from which you need MORE darts than a slightly higher score. All end in 2, 3, 5, 6, 8 or 9. */
    private val bogeyUnder351 = setOf(349, 348, 346, 345, 343, 342, 339)
    private val bogeyUnder171 = bogeyNumbers   // 169, 168, 166, 165, 163, 162, 159

    /** Preferred checkout leaves: big finishes that end on the bull or D20. */
    private val preferredLeaves = listOf(170, 167, 164, 161, 160)

    data class SetupAdvice(val visit: Int, val route: String, val leaves: Int, val why: String, val warning: String?)

    private data class Visit(val score: Int, val route: String)

    private val setupVisits = listOf(
        Visit(180, "T20 T20 T20"), Visit(177, "T20 T20 T19"), Visit(174, "T20 T20 T18"), Visit(171, "T20 T19 T18"),
        Visit(140, "T20 T20 S20"), Visit(137, "T20 T19 S20"), Visit(134, "T20 T18 S20"),
        Visit(105, "T20 S20 25"), Visit(100, "T20 S20 S20"), Visit(99, "T20 S20 S19"), Visit(97, "T20 S20 S17"),
        Visit(65, "S20 S20 25"), Visit(60, "S20 S20 S20"), Visit(59, "S20 S20 S19"), Visit(58, "S20 S20 S18"), Visit(57, "S20 S20 S17"), Visit(55, "S20 S20 S15")
    )

    private fun isBogey(n: Int) = n in bogeyUnder351 || n in bogeyUnder171

    /**
     * What to throw from a score above 170 so that you LAND on a checkout — and never on a bogey.
     * Above 350 the aim is to get under 351 (keeps a nine-darter alive); from 350 down the aim is
     * a three-dart finish, ideally 170/167/164/161/160.
     */
    fun setupAdvice(remaining: Int): SetupAdvice? {
        if (remaining <= 170) return null
        val warnDigits = "Bogeys end in 2, 3, 5, 6, 8 or 9 — leave a score ending in 0, 1, 4 or 7."

        if (remaining > 350) {
            // Need three big trebles (or two plus bull) to get under 351. If a single 20 on the first
            // dart would strand you on a bogey, open on 19 (or 18) instead.
            for (bed in listOf(20, 19, 18)) {
                val ifSingle = remaining - bed - 120
                if (ifSingle in 2..350 && !isBogey(ifSingle)) {
                    val best = remaining - bed * 3 - 120
                    val route = "T$bed T20 T20"
                    val why = (if (bed == 20) "Three big trebles: $route leaves $best. "
                        else "Open on the $bed: a single 20 first would leave a bogey. $route leaves $best. ") +
                        "If the first dart is only a single, two T20s still leave $ifSingle — under 351 and not a bogey."
                    return SetupAdvice(bed * 3 + 120, route, best, why, if (remaining - 140 in bogeyUnder351) "Careful: 140 from here leaves ${remaining - 140}, a bogey." else null)
                }
            }
            return SetupAdvice(180, "T20 T20 T20", remaining - 180, "Score as big as you can and get under 351.", warnDigits)
        }

        if (remaining > 230 && remaining <= 350 && (remaining - 180) in 2..170 && !isBogey(remaining - 180)) {
            // Top of the range: a 180 is a checkout leave
            if (remaining - 180 in preferredLeaves || remaining >= 341) {
                return SetupAdvice(180, "T20 T20 T20", remaining - 180, "A 180 leaves ${remaining - 180} — a three-dart finish.", null)
            }
        }

        // Pick the biggest realistic visit that lands on a checkout, preferring the classic leaves.
        val candidates = setupVisits.filter { v ->
            val left = remaining - v.score
            left in 2..170 && !isBogey(left)
        }
        if (candidates.isEmpty()) {
            // Not reachable this visit: just avoid the bogeys and score
            val safe = (100 downTo 40).firstOrNull { !isBogey(remaining - it) && remaining - it >= 2 } ?: 60
            return SetupAdvice(safe, if (safe >= 100) "T20 S20 S20" else "S20 S20 S20", remaining - safe,
                "No checkout in reach this visit. Score and keep off the bogeys: $safe leaves ${remaining - safe}.", warnDigits)
        }
        val preferred = candidates.firstOrNull { remaining - it.score in preferredLeaves }
        val pick = preferred ?: candidates.first()
        val left = remaining - pick.score
        val sb = StringBuilder()
        sb.append("${pick.score} (${pick.route}) leaves $left")
        sb.append(if (left in preferredLeaves) " — one of the big finishes (bull or D20 at the end). " else " — a three-dart finish. ")
        // Switch advice: if three 20s would land on a bogey, say which bed to switch to
        if (isBogey(remaining - 60) && pick.score in 55..59) {
            sb.append("Three single 20s would leave ${remaining - 60}, a bogey, so after two 20s switch to the ${pick.route.takeLast(2).trimStart('S')}.")
        }
        if (pick.score == 60) sb.append("Don't throw a cover shot after two 20s — the third 20 is the one that matters.")
        val warning = when {
            isBogey(remaining - 100) && pick.score != 100 -> "A ton from here leaves ${remaining - 100}, a bogey."
            isBogey(remaining - 60) && pick.score != 60 -> "Three 20s from here leave ${remaining - 60}, a bogey."
            else -> null
        }
        return SetupAdvice(pick.score, pick.route, left, sb.toString().trim(), warning)
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
