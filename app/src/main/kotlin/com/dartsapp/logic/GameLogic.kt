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

    /**
     * The checkout book (William Hill's published table), 170 down to 41, best route first with the
     * table's alternatives. Below 41 a single plus a double is obvious and is computed instead.
     * Tokens: T/D/S + number, BULL (50), OB (outer bull, 25).
     */
    private val book: Map<Int, List<String>> = mapOf(
        170 to listOf("T20 T20 BULL"),
        167 to listOf("T20 T19 BULL"),
        164 to listOf("T20 T18 BULL"),
        161 to listOf("T20 T17 BULL"),
        160 to listOf("T20 T20 D20"),
        158 to listOf("T20 T20 D19"),
        157 to listOf("T20 T19 D20"),
        156 to listOf("T20 T20 D18"),
        155 to listOf("T20 T19 D19"),
        154 to listOf("T20 T18 D20"),
        153 to listOf("T20 T19 D18"),
        152 to listOf("T20 T20 D16"),
        151 to listOf("T20 T17 D20", "T19 T18 D20"),
        150 to listOf("T20 T18 D18", "T19 T19 D18"),
        149 to listOf("T20 T19 D16"),
        148 to listOf("T20 T20 D14", "T20 T16 D20"),
        147 to listOf("T20 T17 D18", "T19 T18 D18"),
        146 to listOf("T20 T18 D16", "T19 T19 D16"),
        145 to listOf("T20 T15 D20"),
        144 to listOf("T20 T20 D12"),
        143 to listOf("T20 T17 D16"),
        142 to listOf("T20 T14 D20"),
        141 to listOf("T20 T19 D12"),
        140 to listOf("T20 T20 D10", "T20 D20 D20"),
        139 to listOf("T19 T14 D20", "T20 T19 D11"),
        138 to listOf("T20 T18 D12"),
        137 to listOf("T20 T19 D10", "T19 D20 D20"),
        136 to listOf("T20 T20 D8"),
        135 to listOf("BULL T15 D20", "OB T20 BULL"),
        134 to listOf("T20 T14 D16", "T18 D20 D20"),
        133 to listOf("T20 T19 D8", "T19 D18 D20"),
        132 to listOf("BULL BULL D16", "OB T19 BULL"),
        131 to listOf("T20 T13 D16", "T17 D20 D20"),
        130 to listOf("T20 T20 D5", "S20 T20 BULL"),
        129 to listOf("T19 T16 D12", "S19 T20 BULL"),
        128 to listOf("T18 T14 D16", "S18 T20 BULL"),
        127 to listOf("T20 T17 D8", "S20 T19 BULL"),
        126 to listOf("T19 T19 D6", "S19 T19 BULL"),
        125 to listOf("BULL T17 D12", "OB T20 D20"),
        124 to listOf("T20 S14 BULL", "S20 T18 BULL"),
        123 to listOf("T19 S16 BULL", "S19 T18 BULL"),
        122 to listOf("T18 T18 D7", "S18 T18 BULL"),
        121 to listOf("T20 S11 BULL", "S20 T17 BULL"),
        120 to listOf("T20 S20 D20", "S20 T20 D20"),
        119 to listOf("T19 S12 BULL", "S19 T20 D20"),
        118 to listOf("T20 S18 D20", "S20 T20 D19"),
        117 to listOf("T20 S17 D20", "S20 T19 D20"),
        116 to listOf("T19 S19 D20", "S19 T19 D20"),
        115 to listOf("T19 S18 D20", "S19 T20 D18"),
        114 to listOf("T20 S14 D20", "S20 T18 D20"),
        113 to listOf("T19 S16 D20", "S19 T18 D20"),
        112 to listOf("T20 S12 D20", "S20 T20 D16"),
        111 to listOf("T20 S11 D20", "S20 T17 D20"),
        110 to listOf("T20 S10 D20", "S20 T18 D18"),
        109 to listOf("T19 S12 D20", "S19 T18 D18"),
        108 to listOf("T19 S19 D16", "S19 T19 D16"),
        107 to listOf("T20 S15 D16", "S20 T17 D18"),
        106 to listOf("T20 S6 D20", "S20 T18 D16"),
        105 to listOf("T20 S5 D20", "S20 T15 D20"),
        104 to listOf("T18 S10 D20", "S18 T18 D16"),
        103 to listOf("T20 S11 D16", "S20 T17 D16"),
        102 to listOf("T20 S10 D16"),
        101 to listOf("T20 S9 D16", "S20 T19 D12"),
        100 to listOf("T20 D20", "S20 T20 D10"),
        99 to listOf("T19 S10 D16", "S19 T20 D10"),
        98 to listOf("T20 D19", "S20 T18 D12"),
        97 to listOf("T19 D20", "S19 T18 D12"),
        96 to listOf("T20 D18", "S20 T20 D8"),
        95 to listOf("T19 D19", "BULL S5 D20", "OB S20 BULL"),
        94 to listOf("T18 D20", "S18 D18 D20"),
        93 to listOf("T19 D18", "S19 T14 D16"),
        92 to listOf("T20 D16", "S20 D18 D18"),
        91 to listOf("T17 D20", "S17 T14 D16"),
        90 to listOf("T20 D15", "T18 D18", "S20 S20 BULL"),
        89 to listOf("T19 D16", "S19 T20 D5"),
        88 to listOf("T20 D14", "T16 D20", "S20 T18 D7"),
        87 to listOf("T17 D18", "S17 T20 D5"),
        86 to listOf("T18 D16", "S18 T18 D7"),
        85 to listOf("T15 D20", "T19 D14", "S15 T20 D5"),
        84 to listOf("T20 D12", "T16 D18", "S20 T14 D11"),
        83 to listOf("T17 D16", "S17 T16 D9"),
        82 to listOf("BULL D16", "T14 D20", "OB S17 D20"),
        81 to listOf("T19 D12", "T15 D18", "S19 T12 D13"),
        80 to listOf("T20 D10", "T16 D16", "S20 S20 D20"),
        79 to listOf("T19 D11", "T13 D20", "S19 S20 D20"),
        78 to listOf("T18 D12", "S18 S20 D20"),
        77 to listOf("T19 D10", "S19 S18 D20"),
        76 to listOf("T20 D8", "S20 S16 D20"),
        75 to listOf("T17 D12", "OB BULL", "S17 S18 D20"),
        74 to listOf("T14 D16", "T18 D10", "S14 S20 D20"),
        73 to listOf("T19 D8", "T11 D20", "S19 S14 D20"),
        72 to listOf("T16 D12", "T12 D18", "S16 S16 D20"),
        71 to listOf("T13 D16", "T17 D10", "S13 S18 D20"),
        70 to listOf("T10 D20", "T18 D8", "S10 S20 D20"),
        69 to listOf("T19 D6", "T15 D12", "S19 S10 D20"),
        68 to listOf("T20 D4", "S20 S16 D16"),
        67 to listOf("T17 D8", "S17 S10 D20"),
        66 to listOf("T10 D18", "S10 S16 D20"),
        65 to listOf("OB D20", "T19 D4"),
        64 to listOf("T16 D8", "T8 D20", "S16 S16 D16"),
        63 to listOf("T9 D18", "T13 D12"),
        62 to listOf("T10 D16", "S10 S12 D20"),
        61 to listOf("OB D18", "T7 D20"),
        60 to listOf("S20 D20"),
        59 to listOf("S19 D20"),
        58 to listOf("S18 D20"),
        57 to listOf("S17 D20"),
        56 to listOf("S16 D20"),
        55 to listOf("S15 D20"),
        54 to listOf("S14 D20"),
        53 to listOf("S13 D20"),
        52 to listOf("S12 D20", "S20 D16"),
        51 to listOf("S11 D20", "S19 D16"),
        50 to listOf("S10 D20", "S18 D16"),
        49 to listOf("S9 D20", "S17 D16"),
        48 to listOf("S8 D20", "S16 D16"),
        47 to listOf("S7 D20", "S15 D16"),
        46 to listOf("S6 D20", "S14 D16"),
        45 to listOf("S5 D20", "S13 D16"),
        44 to listOf("S4 D20", "S12 D16"),
        43 to listOf("S3 D20", "S11 D16"),
        42 to listOf("S2 D20", "S10 D16"),
        41 to listOf("S1 D20", "S9 D16"),
    )

    /** Parses a book token into a [Hit]. */
    fun parseToken(tok: String): Hit = when {
        tok == "BULL" || tok == "Bull" -> Hit(25, Ring.BULL)
        tok == "OB" || tok == "25" -> Hit(25, Ring.OUTER_BULL)
        tok.startsWith("T") -> Hit(tok.drop(1).toIntOrNull() ?: 20, Ring.TREBLE)
        tok.startsWith("D") -> Hit(tok.drop(1).toIntOrNull() ?: 20, Ring.DOUBLE)
        tok.startsWith("S") -> Hit(tok.drop(1).toIntOrNull() ?: 20, Ring.SINGLE)
        else -> Hit(tok.toIntOrNull() ?: 20, Ring.SINGLE)
    }

    private fun parseRoute(route: String): List<Hit> = route.split(' ').filter { it.isNotBlank() }.map { parseToken(it) }

    /** Routes from the book for [remaining], best first; empty if the book has no entry. */
    fun bookRoutes(remaining: Int): List<List<Hit>> = book[remaining]?.map { parseRoute(it) } ?: emptyList()

    /** All finishing routes for [remaining] (1–3 darts): book routes first, then computed ones. */
    fun allFinishes(remaining: Int): List<List<Hit>> {
        if (remaining < 2 || remaining > 170) return emptyList()
        val fromBook = bookRoutes(remaining)
        val computed = computedFinishes(remaining)
        return fromBook + computed.filter { c -> fromBook.none { it == c } }
    }

    private fun computedFinishes(remaining: Int): List<List<Hit>> {
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
        val fromBook = bookRoutes(remaining)
        // Prefer the book's own alternative; otherwise a computed route on a different double
        val alt = fromBook.getOrNull(1)
            ?: routes.firstOrNull { it.last() != best.last() }
            ?: routes.firstOrNull { it != best && it.first() != best.first() }
        val bestWhy = (if (fromBook.isNotEmpty()) "The book route. " else "") + explain(best, remaining, true)
        val altWhy = alt?.let { (if (fromBook.size > 1) "The book's second option. " else "") + explain(it, remaining, false) } ?: ""
        return Suggestion(best, bestWhy, alt, altWhy)
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
