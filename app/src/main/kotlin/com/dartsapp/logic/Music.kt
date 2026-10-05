package com.dartsapp.logic

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlin.concurrent.thread
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

/**
 * Looping background audio, synthesised once and cached: an 80s synth-pop track for the menus,
 * generated on a background thread the first time it is asked for and then looped with a static AudioTrack.
 */
object Music {
    private const val RATE = 22050

    private var menuTrack: AudioTrack? = null
    private var menuPcm: ShortArray? = null
    @Volatile private var menuWanted = false

    fun startMenu() { menuWanted = true; thread(name = "music") { syncTracks() } }
    fun stopAll() { menuWanted = false; thread(name = "music") { syncTracks() } }

    @Synchronized
    private fun syncTracks() {
        if (menuWanted) {
            if (menuTrack == null) {
                val pcm = menuPcm ?: renderMenuTrack().also { menuPcm = it }
                if (menuWanted) menuTrack = loop(pcm, 0.55f)
            }
        } else { menuTrack?.let { safeStop(it) }; menuTrack = null }
    }

    private fun safeStop(t: AudioTrack) { try { t.stop() } catch (e: Exception) {}; try { t.release() } catch (e: Exception) {} }

    private fun loop(pcm: ShortArray, volume: Float): AudioTrack? = try {
        val track = AudioTrack(
            AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build(),
            AudioFormat.Builder().setSampleRate(RATE).setEncoding(AudioFormat.ENCODING_PCM_16BIT).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build(),
            pcm.size * 2, AudioTrack.MODE_STATIC, android.media.AudioManager.AUDIO_SESSION_ID_GENERATE
        )
        track.write(pcm, 0, pcm.size)
        track.setLoopPoints(0, pcm.size, -1)
        track.setVolume(volume)
        track.play()
        track
    } catch (e: Exception) { null }

    // ---- synth track ---------------------------------------------------------------------------

    private fun hz(midi: Int): Float = 440f * 2f.pow((midi - 69) / 12f)

    /** 8 bars at 118 BPM: four-on-the-floor kick, gated snare, hats, octave bass, detuned pad, square lead. */
    private fun renderMenuTrack(): ShortArray {
        val bpm = 118f
        val beat = 60f / bpm
        val bars = 8
        val n = (RATE * beat * 4 * bars).toInt()
        val buf = FloatArray(n)
        val rnd = Random(7)

        // Chords: Am  F  C  G  (two bars each) as MIDI roots and triads
        val roots = intArrayOf(45, 41, 48, 43)                        // A2 F2 C3 G2
        val triads = arrayOf(intArrayOf(57, 60, 64), intArrayOf(53, 57, 60), intArrayOf(60, 64, 67), intArrayOf(55, 59, 62))

        // Drums
        val sixteenth = beat / 4f
        for (b in 0 until bars * 4) {
            val t0 = b * beat
            kick(buf, t0)
            if (b % 2 == 1) snare(buf, t0, rnd)
            for (s in 0 until 2) hat(buf, t0 + s * beat / 2f, open = (s == 1 && b % 4 == 3), rnd = rnd)
        }
        // Bass: 16th-note octave pattern
        for (bar in 0 until bars) {
            val root = roots[bar / 2]
            for (s in 0 until 16) {
                val midi = if (s % 4 == 2) root + 12 else if (s % 8 == 7) root + 7 else root
                pluck(buf, bar * beat * 4 + s * sixteenth, hz(midi), sixteenth * 0.9f, 0.32f, saw = true)
            }
        }
        // Pad: detuned saws, slow attack, held for two bars
        for (c in 0 until 4) {
            val start = c * beat * 8
            for (m in triads[c]) pad(buf, start, beat * 8, hz(m), 0.07f)
        }
        // Lead: pentatonic phrase on bars 3-4 and 7-8 (A minor pentatonic: A C D E G)
        val phrase = intArrayOf(76, 79, 81, 79, 76, 74, 72, 74, 76, 0, 79, 76, 74, 72, 69, 0)
        for (rep in 0 until 2) {
            val start = (if (rep == 0) 2 else 6) * beat * 4
            for ((i, m) in phrase.withIndex()) {
                if (m == 0) continue
                val dur = beat / 2f
                lead(buf, start + i * dur, hz(m), dur * 0.85f, 0.16f)
            }
        }
        // Gentle master compression by soft clipping
        for (i in buf.indices) { val v = buf[i]; buf[i] = (v / (1f + kotlin.math.abs(v) * 0.6f)) * 1.25f }
        return toPcm(buf)
    }

    private fun kick(buf: FloatArray, t0: Float) {
        val start = (t0 * RATE).toInt(); val dur = (0.16f * RATE).toInt()
        var phase = 0.0
        for (i in 0 until dur) {
            val idx = start + i; if (idx >= buf.size) break
            val p = i.toFloat() / dur
            val f = 160f * (1f - p) * (1f - p) + 45f
            phase += 2 * PI * f / RATE
            buf[idx] += (sin(phase) * (1f - p) * 0.9f).toFloat()
        }
    }

    private fun snare(buf: FloatArray, t0: Float, rnd: Random) {
        val start = (t0 * RATE).toInt(); val dur = (0.28f * RATE).toInt()
        var lp = 0f
        for (i in 0 until dur) {
            val idx = start + i; if (idx >= buf.size) break
            val p = i.toFloat() / dur
            val white = rnd.nextFloat() * 2f - 1f
            lp += 0.35f * (white - lp)
            // gated-reverb feel: flat-ish body then a hard cut
            val env = if (p < 0.7f) (1f - p * 0.5f) else (1f - (p - 0.7f) / 0.3f)
            val body = sin(2 * PI * 190 * i / RATE).toFloat() * exp(-p * 12f)
            buf[idx] += (lp * 0.55f + body * 0.4f) * env * 0.7f
        }
    }

    private fun hat(buf: FloatArray, t0: Float, open: Boolean, rnd: Random) {
        val start = (t0 * RATE).toInt(); val dur = ((if (open) 0.16f else 0.04f) * RATE).toInt()
        var hp = 0f; var prev = 0f
        for (i in 0 until dur) {
            val idx = start + i; if (idx >= buf.size) break
            val white = rnd.nextFloat() * 2f - 1f
            hp = white - prev; prev = white            // crude high-pass
            val p = i.toFloat() / dur
            buf[idx] += hp * (1f - p) * (1f - p) * (if (open) 0.16f else 0.14f)
        }
    }

    private fun pluck(buf: FloatArray, t0: Float, f: Float, durSec: Float, amp: Float, saw: Boolean) {
        val start = (t0 * RATE).toInt(); val dur = (durSec * RATE).toInt()
        for (i in 0 until dur) {
            val idx = start + i; if (idx >= buf.size) break
            val t = i.toFloat() / RATE
            val p = i.toFloat() / dur
            // saw via first few harmonics, brightness decays with the envelope (filter sweep)
            val bright = exp(-p * 4f)
            var v = 0f
            for (h in 1..5) v += (sin(2 * PI * f * h * t) / h).toFloat() * (if (h == 1) 1f else bright)
            val env = (1f - p).coerceAtLeast(0f) * minOf(1f, i / (RATE * 0.004f))
            buf[idx] += v * env * amp
        }
    }

    private fun pad(buf: FloatArray, t0: Float, durSec: Float, f: Float, amp: Float) {
        val start = (t0 * RATE).toInt(); val dur = (durSec * RATE).toInt()
        for (i in 0 until dur) {
            val idx = start + i; if (idx >= buf.size) break
            val t = i.toFloat() / RATE
            val p = i.toFloat() / dur
            val env = minOf(p / 0.15f, 1f) * minOf((1f - p) / 0.1f, 1f)
            val v = sin(2 * PI * f * 0.997 * t) + sin(2 * PI * f * 1.003 * t) + 0.5 * sin(2 * PI * f * 2.0 * t)
            buf[idx] += (v * env * amp).toFloat()
        }
    }

    private fun lead(buf: FloatArray, t0: Float, f: Float, durSec: Float, amp: Float) {
        val start = (t0 * RATE).toInt(); val dur = (durSec * RATE).toInt()
        for (i in 0 until dur) {
            val idx = start + i; if (idx >= buf.size) break
            val t = i.toFloat() / RATE
            val p = i.toFloat() / dur
            val vib = 1f + 0.006f * sin(2 * PI * 5.5 * t).toFloat() * minOf(1f, p * 3f)
            // square-ish: odd harmonics
            var v = 0f
            for (h in intArrayOf(1, 3, 5)) v += (sin(2 * PI * f * vib * h * t) / h).toFloat()
            val env = minOf(1f, i / (RATE * 0.01f)) * (if (p > 0.8f) (1f - p) / 0.2f else 1f)
            buf[idx] += v * env * amp
        }
    }

    private fun toPcm(buf: FloatArray): ShortArray {
        val pcm = ShortArray(buf.size)
        for (i in buf.indices) pcm[i] = (buf[i].coerceIn(-1f, 1f) * 30000).toInt().toShort()
        return pcm
    }
}
