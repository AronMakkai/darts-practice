package com.dartsapp.logic

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlin.concurrent.thread
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/**
 * Small synthesised sound effects, generated on the fly as PCM so no asset files are needed.
 */
object Sounds {
    private const val RATE = 44100

    private val toneCache = HashMap<String, ShortArray>()

    /**
     * Short metronome tones, replacing the system ToneGenerator (which is quiet or silent on many
     * phones). Each is a sine with a sharp click transient so it cuts through.
     */
    fun tick() = tone(880f, 70, 0.9f)             // low tick: ring at the edge, approach done, board cleared
    fun beep() = tone(1760f, 130, 1.0f)           // high beep: the ideal release moment / dart done
    fun buzz() = tone(330f, 220, 0.9f)            // low buzz: start of a turn, opponent done

    fun tone(freqHz: Float, ms: Int, amp: Float) {
        val key = "$freqHz/$ms/$amp"
        val pcm = synchronized(toneCache) {
            toneCache.getOrPut(key) {
                val n = RATE * ms / 1000
                val buf = FloatArray(n)
                for (i in 0 until n) {
                    val t = i.toFloat() / RATE
                    val attack = (i / (RATE * 0.004f)).coerceAtMost(1f)
                    val release = ((n - i) / (RATE * 0.02f)).coerceAtMost(1f)
                    val click = exp(-t * 400f) * 0.5f
                    buf[i] = ((sin(2 * PI * freqHz * t) * 0.8 + sin(2 * PI * freqHz * 2 * t) * 0.2).toFloat() * amp + click) * attack * release
                }
                toPcm(buf)
            }
        }
        thread(name = "tone") { playPcm(pcm) }
    }

    private var thudPcm: ShortArray? = null

    /** A dart hitting the sisal: a short knock with a soft noise burst. */
    fun thud() {
        val pcm = thudPcm ?: run {
            val n = (RATE * 0.09f).toInt()
            val buf = FloatArray(n)
            val rnd = java.util.Random(5)
            var lp = 0f
            for (i in 0 until n) {
                val t = i.toFloat() / RATE
                val white = rnd.nextFloat() * 2f - 1f
                lp += 0.25f * (white - lp)
                val knock = sin(2 * PI * (140f - 60f * t / 0.09f) * t).toFloat() * exp(-t * 45f)
                buf[i] = (knock * 0.9f + lp * 0.5f * exp(-t * 70f)) * minOf(1f, i / (RATE * 0.001f))
            }
            toPcm(buf).also { thudPcm = it }
        }
        thread(name = "thud") { playPcm(pcm) }
    }

    /** Crowd roar: a wall of voices and applause for a ton-plus visit; [big] (180 or a finish) is longer and louder. */
    fun cheer(big: Boolean) {
        thread(name = "cheer") {
            val len = if (big) 3.2f else 2.0f
            val n = (RATE * len).toInt()
            val buf = FloatArray(n)
            val rnd = java.util.Random(if (big) 9 else 4)
            // Applause / roar bed: filtered noise with a fast swell and a long tail, crackle on top
            var lp = 0f
            for (i in 0 until n) {
                val t = i.toFloat() / RATE
                val p = t / len
                val white = rnd.nextFloat() * 2f - 1f
                lp += 0.18f * (white - lp)
                val env = minOf(p / 0.08f, 1f) * (1f - p).let { it * it * (3f - 2f * it) }
                val crackle = if (rnd.nextFloat() < 0.03f) (rnd.nextFloat() * 2f - 1f) * 0.7f else 0f
                buf[i] = (lp * 1.8f + crackle) * env * (if (big) 1.0f else 0.75f)
            }
            // Shouting voices: many rising-then-falling sweeps, each with its own start and pitch
            val voices = if (big) 28 else 14
            for (v in 0 until voices) {
                val f0 = 150f + rnd.nextFloat() * 220f
                val start = (rnd.nextFloat() * 0.35f * RATE).toInt()
                val dur = ((0.6f + rnd.nextFloat() * 0.9f) * RATE).toInt()
                var phase = 0.0
                for (i in 0 until dur) {
                    val idx = start + i; if (idx >= n) break
                    val p = i.toFloat() / dur
                    val f = f0 * (1f + 0.4f * sin(PI * p).toFloat())
                    phase += 2 * PI * f / RATE
                    val env = sin(PI * p).toFloat()
                    buf[idx] += ((sin(phase) + 0.5 * sin(2 * phase) + 0.25 * sin(3 * phase)) * env * 0.05f).toFloat()
                }
            }
            // Whistles for the big ones
            if (big) for (w in 0 until 3) {
                val start = ((0.2f + rnd.nextFloat() * 0.8f) * RATE).toInt()
                val dur = (0.5f * RATE).toInt()
                var phase = 0.0
                for (i in 0 until dur) {
                    val idx = start + i; if (idx >= n) break
                    val p = i.toFloat() / dur
                    phase += 2 * PI * (1800f + 600f * sin(PI * p)) / RATE
                    buf[idx] += (sin(phase) * sin(PI * p) * 0.08f).toFloat()
                }
            }
            // Soft clip so the roar is loud but never harsh
            for (i in buf.indices) { val x = buf[i]; buf[i] = x / (1f + kotlin.math.abs(x) * 0.5f) }
            play(buf)
        }
    }

    /** Crowd groan for a bust: a falling "ooh". */
    fun groan() {
        thread(name = "groan") {
            val len = 0.9f
            val n = (RATE * len).toInt()
            val buf = FloatArray(n)
            val rnd = java.util.Random(2)
            for (v in 0 until 5) {
                val f0 = 200f + rnd.nextFloat() * 120f
                var phase = 0.0
                for (i in 0 until n) {
                    val p = i.toFloat() / n
                    val f = f0 * (1.15f - 0.35f * p)
                    phase += 2 * PI * f / RATE
                    val env = sin(PI * p).toFloat()
                    buf[i] += ((sin(phase) + 0.5 * sin(2 * phase)) * env * 0.09f).toFloat()
                }
            }
            play(buf)
        }
    }

    /** A little "pop" followed by a cascade of coin dings and a final sparkle chord. */
    fun playCheckoutJingle() {
        thread(name = "jingle") {
            val lengthSec = 1.9f
            val n = (RATE * lengthSec).toInt()
            val buf = FloatArray(n)

            // Pop: fast downward sweep, very short
            addSweep(buf, startSec = 0f, durSec = 0.08f, fromHz = 620f, toHz = 90f, amp = 0.9f)

            // Coins: ascending dings, each a bright pair of partials with a quick decay
            val notes = floatArrayOf(1046.5f, 1318.5f, 1568f, 2093f, 1568f, 2093f, 2637f, 3136f)
            var t = 0.14f
            for ((i, f) in notes.withIndex()) {
                addDing(buf, startSec = t, freqHz = f, amp = 0.45f - i * 0.02f)
                t += if (i < 4) 0.11f else 0.085f
            }

            // Sparkle chord to finish
            for (f in floatArrayOf(2093f, 2637f, 3136f, 4186f)) {
                addDing(buf, startSec = 1.15f, freqHz = f, amp = 0.28f, decay = 5f)
            }

            play(buf)
        }
    }

    /** A dull thud with a crack: for a bust. */
    fun playBust() {
        thread(name = "bust") {
            val n = (RATE * 0.6f).toInt()
            val buf = FloatArray(n)
            addSweep(buf, startSec = 0f, durSec = 0.35f, fromHz = 180f, toHz = 35f, amp = 1.0f)
            // crack: short burst of noise
            val rnd = java.util.Random(3)
            val crackLen = (RATE * 0.06f).toInt()
            for (i in 0 until crackLen) {
                val env = 1f - i.toFloat() / crackLen
                buf[i] += (rnd.nextFloat() * 2f - 1f) * 0.6f * env * env
            }
            play(buf)
        }
    }

    private fun addDing(buf: FloatArray, startSec: Float, freqHz: Float, amp: Float, decay: Float = 14f) {
        val start = (startSec * RATE).toInt()
        val dur = (0.35f * RATE).toInt()
        for (i in 0 until dur) {
            val idx = start + i
            if (idx >= buf.size) break
            val t = i.toFloat() / RATE
            val env = exp(-decay * t)
            // fundamental + a bright inharmonic partial gives the "metal coin" timbre
            val v = sin(2 * PI * freqHz * t) * 0.7 + sin(2 * PI * freqHz * 2.76 * t) * 0.3
            buf[idx] += (v * env * amp).toFloat()
        }
    }

    private fun addSweep(buf: FloatArray, startSec: Float, durSec: Float, fromHz: Float, toHz: Float, amp: Float) {
        val start = (startSec * RATE).toInt()
        val dur = (durSec * RATE).toInt()
        var phase = 0.0
        for (i in 0 until dur) {
            val idx = start + i
            if (idx >= buf.size) break
            val p = i.toFloat() / dur
            val f = fromHz + (toHz - fromHz) * p
            phase += 2 * PI * f / RATE
            val env = (1f - p) * (1f - p)
            buf[idx] += (sin(phase) * env * amp).toFloat()
        }
    }

    private fun toPcm(buf: FloatArray): ShortArray {
        val pcm = ShortArray(buf.size)
        for (i in buf.indices) {
            val v = buf[i].coerceIn(-1f, 1f)
            pcm[i] = (v * 32000).toInt().toShort()
        }
        return pcm
    }

    private fun play(buf: FloatArray) = playPcm(toPcm(buf))

    private fun playPcm(pcm: ShortArray) {
        val track = AudioTrack(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build(),
            AudioFormat.Builder()
                .setSampleRate(RATE)
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .build(),
            pcm.size * 2,
            AudioTrack.MODE_STATIC,
            android.media.AudioManager.AUDIO_SESSION_ID_GENERATE
        )
        try {
            track.setVolume(1f)
            track.write(pcm, 0, pcm.size)
            track.play()
            Thread.sleep((pcm.size * 1000L / RATE) + 120)
            track.stop()
        } catch (e: Exception) {
            // never let a sound problem take the game down
        } finally {
            track.release()
        }
    }
}
