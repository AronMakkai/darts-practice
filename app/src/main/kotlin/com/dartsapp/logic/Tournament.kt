package com.dartsapp.logic

import com.dartsapp.ui.Opponent
import kotlin.random.Random

/**
 * An eight-player knockout: you, the six characters and the coach. Quarter-finals, semi-finals and
 * the final. You play your own matches in 501 vs bot; the rest of the draw is simulated from the
 * players' skill. Each round is longer (first to 2, 3, then 4 legs) and the opposition throws
 * sharper, so it gets harder as you go.
 */
object Tournament {
    /** A player in the draw. [opponent] is null for you. */
    class Entrant(val name: String, val opponent: Opponent?) {
        val skill: Float get() = opponent?.skill ?: 0.8f
        val isYou: Boolean get() = opponent == null
    }

    val rounds = listOf("Quarter-final", "Semi-final", "Final")

    /** Legs needed to win the match in each round (legsPerSet for Bot501: 3, 5, 7 → first to 2, 3, 4). */
    fun legsPerSet(round: Int) = intArrayOf(3, 5, 7)[round.coerceIn(0, 2)]

    /** How much sharper the bots throw in each round. */
    fun skillMultiplier(round: Int) = floatArrayOf(1.0f, 1.09f, 1.18f)[round.coerceIn(0, 2)]

    var entrants: List<Entrant> = emptyList(); private set
    /** slots[r] holds the entrant index in each slot of round r (8, 4, 2) and slots[3] the champion (1). -1 = undecided. */
    var slots: Array<IntArray> = emptyArray(); private set
    var started = false; private set
    var round = 0; private set              // the round you are currently in
    var eliminatedIn = -1; private set      // round index you lost in, or -1
    val champion: Entrant? get() = slots.getOrNull(3)?.getOrNull(0)?.takeIf { it >= 0 }?.let { entrants[it] }
    val youWon: Boolean get() = champion?.isYou == true
    val over: Boolean get() = eliminatedIn >= 0 || champion != null

    /** Draws the bracket. Seeded so your path gets harder: weakest first, strongest side kept away from you. */
    fun start() {
        val ops = Opponent.values().sortedBy { it.skill }       // weakest .. strongest
        val you = Entrant("YOU", null)
        val list = ops.map { Entrant(it.displayName, it) }
        // Your half: you vs weakest; then the next two. Far half: the four strongest.
        val order = listOf(you, list[0], list[1], list[2], list[3], list[5], list[4], list[6])
        entrants = order
        slots = arrayOf(IntArray(8) { it }, IntArray(4) { -1 }, IntArray(2) { -1 }, IntArray(1) { -1 })
        started = true
        round = 0
        eliminatedIn = -1
    }

    fun reset() { started = false; entrants = emptyList(); slots = emptyArray(); round = 0; eliminatedIn = -1 }

    private fun youIndex() = entrants.indexOfFirst { it.isYou }

    /** Your opponent in the current round, or null if the tournament is over. */
    fun currentOpponent(): Opponent? {
        if (!started || over || round > 2) return null
        val s = slots[round]
        val k = s.indexOf(youIndex())
        if (k < 0) return null
        val other = s[k xor 1]
        return if (other < 0) null else entrants[other].opponent
    }

    /** Records your match; then plays out the rest of the round (and the rest of the draw if you lost). */
    fun recordUserResult(won: Boolean) {
        if (!started || over) return
        val s = slots[round]
        val you = youIndex()
        val k = s.indexOf(you)
        val pair = k / 2
        val other = s[k xor 1]
        slots[round + 1][pair] = if (won) you else other
        simulateRound(round, exceptPair = pair)
        if (!won) {
            eliminatedIn = round
            // play the draw out so the bracket shows a champion
            for (r in round + 1..2) simulateRound(r, exceptPair = -1)
        } else {
            round++
            if (round > 2) { /* champion already written into slots[3] */ }
        }
    }

    private fun simulateRound(r: Int, exceptPair: Int) {
        val s = slots[r]
        for (pair in 0 until s.size / 2) {
            if (pair == exceptPair) continue
            if (slots[r + 1][pair] >= 0) continue
            val a = s[pair * 2]; val b = s[pair * 2 + 1]
            if (a < 0 || b < 0) continue
            val sa = entrants[a].skill; val sb = entrants[b].skill
            // Sharper edge than raw skill ratio so the better player usually goes through
            val pa = Math.pow((sa / (sa + sb)).toDouble(), 2.5).toFloat()
            val pb = Math.pow((sb / (sa + sb)).toDouble(), 2.5).toFloat()
            slots[r + 1][pair] = if (Random.nextFloat() < pa / (pa + pb)) a else b
        }
    }
}
