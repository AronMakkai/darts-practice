package com.dartsapp.data

import kotlin.math.atan2
import kotlin.math.sqrt

enum class Ring { MISS, SINGLE, DOUBLE, TREBLE, OUTER_BULL, BULL }

data class Hit(val number: Int, val ring: Ring) {
    val score: Int
        get() = when (ring) {
            Ring.MISS -> 0
            Ring.SINGLE -> number
            Ring.DOUBLE -> number * 2
            Ring.TREBLE -> number * 3
            Ring.OUTER_BULL -> 25
            Ring.BULL -> 50
        }

    val label: String
        get() = when (ring) {
            Ring.MISS -> "Miss"
            Ring.SINGLE -> "S$number"
            Ring.DOUBLE -> "D$number"
            Ring.TREBLE -> "T$number"
            Ring.OUTER_BULL -> "25"
            Ring.BULL -> "Bull"
        }

    /** True if this dart is a valid finishing dart (double ring or inner bull). */
    val isDoubleOut: Boolean get() = ring == Ring.DOUBLE || ring == Ring.BULL
}

/**
 * Dartboard geometry. All radii are fractions of the outer radius of the double ring,
 * taken from the standard board (170 mm radius).
 */
/** Ring radii as fractions of the outer radius of the double ring. */
data class BoardGeometry(
    val bullR: Float,
    val outerBullR: Float,
    val trebleIn: Float,
    val trebleOut: Float,
    val doubleIn: Float
) {
    val doubleOut: Float get() = 1.0f

    companion object {
        /** Proportions of a real board (170 mm radius). */
        val STANDARD = BoardGeometry(
            bullR = 0.037f, outerBullR = 0.094f,
            trebleIn = 0.582f, trebleOut = 0.629f,
            doubleIn = 0.953f
        )

        /** Fat doubles, trebles and bulls so they are easy to tap on a phone. */
        val WIDE = BoardGeometry(
            bullR = 0.085f, outerBullR = 0.17f,
            trebleIn = 0.50f, trebleOut = 0.66f,
            doubleIn = 0.84f
        )

        /** Halfway between a real board and WIDE — used by Checkout Game. */
        val PRACTICE = BoardGeometry(
            bullR = 0.061f, outerBullR = 0.132f,
            trebleIn = 0.541f, trebleOut = 0.645f,
            doubleIn = 0.897f
        )

        /** HOT STREAK board: trebles, doubles and bull roughly twice as fat as PRACTICE. */
        val HOT = BoardGeometry(
            bullR = 0.11f, outerBullR = 0.21f,
            trebleIn = 0.47f, trebleOut = 0.69f,
            doubleIn = 0.80f
        )
    }
}

object Board {
    val segments = listOf(20, 1, 18, 4, 13, 6, 10, 15, 2, 17, 3, 19, 7, 16, 8, 11, 14, 9, 12, 5)

    /** Centre angle (degrees, clockwise from 3 o'clock) of segment at [index]. 20 is at the top. */
    fun segmentAngle(index: Int): Float = -90f + index * 18f

    /** Hit-test a point given in normalised board coordinates (x, y in units of the board radius). */
    fun hitTest(x: Float, y: Float, g: BoardGeometry = BoardGeometry.STANDARD): Hit {
        val r = sqrt(x * x + y * y)
        if (r > g.doubleOut) return Hit(0, Ring.MISS)
        if (r <= g.bullR) return Hit(25, Ring.BULL)
        if (r <= g.outerBullR) return Hit(25, Ring.OUTER_BULL)
        val angle = Math.toDegrees(atan2(y.toDouble(), x.toDouble())).toFloat()
        val idx = (((angle + 99f + 720f) % 360f) / 18f).toInt() % 20
        val number = segments[idx]
        val ring = when {
            r <= g.trebleIn -> Ring.SINGLE
            r <= g.trebleOut -> Ring.TREBLE
            r <= g.doubleIn -> Ring.SINGLE
            else -> Ring.DOUBLE
        }
        return Hit(number, ring)
    }
}
