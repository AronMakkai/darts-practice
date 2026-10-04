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

    private fun play(buf: FloatArray) {
        // Soft clip and convert to 16-bit
        val pcm = ShortArray(buf.size)
        for (i in buf.indices) {
            val v = buf[i].coerceIn(-1f, 1f)
            pcm[i] = (v * 32000).toInt().toShort()
        }
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
        track.write(pcm, 0, pcm.size)
        track.play()
        Thread.sleep((buf.size * 1000L / RATE) + 150)
        track.stop()
        track.release()
    }
}
