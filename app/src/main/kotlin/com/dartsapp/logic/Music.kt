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
 * Looping background audio, synthesised once and cached: an 80s synth-pop track for the menus and a
 * soft room of people chatting for the game screens. Generated on a background thread the first time
 * they are asked for and then looped with a static AudioTrack.
 */
object Music {
    private const val RATE = 22050

    private var menuThread: Thread? = null
    private var menuPcm: ShortArray? = null
    private var crowdPcm: ShortArray? = null
    // The crowd loop is streamed by its own thread rather than played as one static buffer, so it
    // can be filtered live. IN THE ZONE fades in a steep low-pass, the room sounds under water.
    private var crowdThread: Thread? = null
    @Volatile var muffled = false
        private set
    private const val CROWD_VOL = 0.5f
    @Volatile private var menuWanted = false
    @Volatile private var crowdWanted = false

    fun startMenu() { menuWanted = true; crowdWanted = false; thread(name = "music") { syncTracks() } }
    fun startCrowd() {
        crowdWanted = true; menuWanted = false
        thread(name = "music") {
            syncTracks()
            // Pre-render the roars so the first ton-plus visit is not late
            synchronized(roarCache) { roarCache.getOrPut(false) { renderRoar(false) }; roarCache.getOrPut(true) { renderRoar(true) } }
        }
    }
    fun stopAll() { menuWanted = false; crowdWanted = false; thread(name = "music") { syncTracks() } }

    @Synchronized
    private fun syncTracks() {
        if (menuWanted) {
            if (menuThread?.isAlive != true) {
                val pcm = menuPcm ?: renderMenuTrack().also { menuPcm = it }
                if (menuWanted) menuThread = thread(name = "menuMusic") { streamMenu(pcm) }
            }
        }
        // (when not wanted, the menu thread sees menuWanted == false and stops itself)
        if (crowdWanted) {
            if (crowdThread?.isAlive != true) {
                val pcm = crowdPcm ?: renderCrowd().also { crowdPcm = it }
                if (crowdWanted) crowdThread = thread(name = "crowd") { streamCrowd(pcm) }
            }
        }
        // (when not wanted, the crowd thread sees crowdWanted == false and stops itself)
    }

    /** Plays the crowd loop for as long as it is wanted, through the live under-water filter. */
    private fun streamCrowd(pcm: ShortArray) {
        val minBuf = AudioTrack.getMinBufferSize(RATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT).coerceAtLeast(2048)
        val track = try {
            AudioTrack(
                AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build(),
                AudioFormat.Builder().setSampleRate(RATE).setEncoding(AudioFormat.ENCODING_PCM_16BIT).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build(),
                minBuf * 2, AudioTrack.MODE_STREAM, android.media.AudioManager.AUDIO_SESSION_ID_GENERATE
            )
        } catch (e: Exception) { return }
        try {
            track.setVolume(CROWD_VOL)
            track.play()
            val chunk = ShortArray(1024)
            val a = (1.0 - exp(-2.0 * PI * 380.0 / RATE)).toFloat()
            var s1 = 0f; var s2 = 0f; var s3 = 0f; var s4 = 0f
            var m = if (muffled) 1f else 0f            // 0 = clear, 1 = under water
            val step = 1f / (RATE * 0.45f)              // crossfade over ~half a second
            var pos = 0; var t = 0L
            while (crowdWanted) {
                val target = if (muffled) 1f else 0f
                for (i in chunk.indices) {
                    val x = pcm[pos] / 32768f
                    pos++; if (pos >= pcm.size) pos = 0
                    s1 += a * (x - s1); s2 += a * (s1 - s2); s3 += a * (s2 - s3); s4 += a * (s3 - s4)
                    if (m < target) m = (m + step).coerceAtMost(target) else if (m > target) m = (m - step).coerceAtLeast(target)
                    val wobble = 1f + 0.15f * sin(2.0 * PI * 0.6 * t / RATE).toFloat()
                    t++
                    val y = x * (1f - m) + s4 * 1.9f * wobble * m
                    chunk[i] = (y.coerceIn(-1f, 1f) * 32767f).toInt().toShort()
                }
                if (track.write(chunk, 0, chunk.size) < 0) break
            }
        } catch (e: Exception) {
        } finally { safeStop(track) }
    }

    // ---- the record on the main menu: the menu tune plays on as normal, and dragging the record
    // scratches a sample over the top of it, like a DJ cutting a vocal over the beat ----------------

    @Volatile private var scratching = false
    @Volatile private var scratchTarget = 0.0     // 0..1: how far through the sample the needle sits
    @Volatile private var faderOpen = true        // the CUT button closes it while held
    private var scratchPcm: FloatArray? = null

    /**
     * The sample is pressed onto a short arc of the record, starting at the dot on the label. The
     * screen tells us where the needle sits in it ([frac] 0..1, clamped: before the dot or past the
     * end of the arc is silent groove) whenever the record moves under a finger.
     */
    fun scratchStart(frac: Double) { scratchTarget = frac; scratching = true; cueTo = frac }
    fun scratchTo(frac: Double) { scratchTarget = frac }
    /** Finger up: the scratch stops. */
    fun scratchEnd() { scratching = false }
    /** The fader: closed while the CUT button is held, for chirps and transformer cuts. */
    fun setFader(open: Boolean) { faderOpen = open }
    @Volatile private var cueTo = -1.0

    /**
     * The scratch sample: the classic "AHHH... YEAHHH!" vocal. A big shouted open "ahhh" with the pitch
     * falling away, a breath, then "yeahhh" sliding from "ee" through "eh" to an open "ah", lifting
     * and then dropping off. A raspy glottal buzz with lots of harmonics and a bit of breath in it, so
     * it chirps and squeals when it is cut back and forth.
     */
    private fun renderScratchSample(): FloatArray {
        val len = 1.75f
        val n = (RATE * len).toInt()
        val buf = FloatArray(n)
        val rnd = Random(77)
        // one sung syllable with formants and pitch moving over it: fn(p) gives (f0, F1, F2, F3) at 0..1
        fun voice(t0: Float, durSec: Float, amp: Float, shape: (Float) -> FloatArray, attack: Float, release: Float) {
            val start = (t0 * RATE).toInt(); val dur = (durSec * RATE).toInt()
            var phase = 0.0
            var lp = 0f
            for (i in 0 until dur) {
                val idx = start + i; if (idx >= n) break
                val p = i.toFloat() / dur
                val (f0, f1, f2, f3) = shape(p).let { arrayOf(it[0], it[1], it[2], it[3]) }
                // a little rough vibrato in the shout
                val wob = 1f + 0.012f * sin(2 * PI * 6.0 * i / RATE).toFloat() + 0.006f * (rnd.nextFloat() - 0.5f)
                phase += 2 * PI * f0 * wob / RATE
                var v = 0.0
                var h = 1
                while (h <= 30) {
                    val fh = f0 * h
                    if (fh > RATE / 2.2f) break
                    val g = 1f / (1f + ((fh - f1) / 110f).let { it * it }) + 0.8f / (1f + ((fh - f2) / 150f).let { it * it }) +
                        0.4f / (1f + ((fh - f3) / 250f).let { it * it }) + 0.03f
                    v += g / kotlin.math.sqrt(h.toFloat()) * sin(h * phase)
                    h++
                }
                // breath through the formants: noise, roughly band-limited
                val white = rnd.nextFloat() * 2f - 1f
                lp += 0.35f * (white - lp)
                val env = minOf(1f, p / attack) * (if (p > 1f - release) ((1f - p) / release).coerceAtLeast(0f) else 1f)
                buf[idx] += ((v * 0.2 + lp * 0.05) * env * amp).toFloat()
            }
        }
        // "AHHH": a breathy "h" onset then the open vowel, pitch falling from 190 to 130
        voice(0.02f, 0.62f, 1f, { p ->
            floatArrayOf(190f - 60f * p, 780f, 1200f - 80f * p, 2500f)
        }, attack = 0.06f, release = 0.3f)
        // "YEAHHH": ee -> eh -> ah, the pitch lifting then falling away
        voice(0.78f, 0.92f, 1.05f, { p ->
            val y = (p / 0.22f).coerceIn(0f, 1f)                  // the "y" glide
            val e = ((p - 0.22f) / 0.35f).coerceIn(0f, 1f)        // "eh" opening to "ah"
            val f1 = 290f + (560f - 290f) * y + (760f - 560f) * e
            val f2 = 2250f + (1850f - 2250f) * y + (1300f - 1850f) * e
            val f0 = if (p < 0.35f) 150f + 70f * (p / 0.35f) else 220f - 85f * ((p - 0.35f) / 0.65f)
            floatArrayOf(f0, f1, f2, 2600f)
        }, attack = 0.04f, release = 0.35f)
        return buf
    }

    private fun streamMenu(pcm: ShortArray) {
        val minBuf = AudioTrack.getMinBufferSize(RATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT).coerceAtLeast(1024)
        val track = try {
            AudioTrack(
                AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build(),
                AudioFormat.Builder().setSampleRate(RATE).setEncoding(AudioFormat.ENCODING_PCM_16BIT).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build(),
                minBuf, AudioTrack.MODE_STREAM, android.media.AudioManager.AUDIO_SESSION_ID_GENERATE
            )
        } catch (e: Exception) { return }
        try {
            val smp = scratchPcm ?: renderScratchSample().also { scratchPcm = it }
            track.setVolume(0.55f)
            track.play()
            val n = pcm.size
            val chunk = ShortArray(256)                  // small chunks: the scratch follows the finger closely
            var pos = 0                                  // the tune: always straight through
            var sPos = 0.0                               // the scratch sample: wherever the hand puts it
            var v = 0.0                                  // scratch speed (samples per sample, negative = back)
            var gate = 0f                                // scratch is only heard while the record moves
            var fader = 1f
            val last = (smp.size - 2).toDouble()
            while (menuWanted) {
                val sc = scratching
                // finger just went down: the needle is wherever the record already is
                val cue = cueTo
                if (cue >= 0.0) { sPos = cue * last; v = 0.0; cueTo = -1.0 }
                val fTarget = if (faderOpen) 1f else 0f
                for (i in chunk.indices) {
                    var out = pcm[pos] / 32768f
                    pos++; if (pos >= n) pos = 0
                    if (sc) {
                        val want = ((scratchTarget * last - sPos) / (RATE * 0.03)).coerceIn(-5.0, 5.0)
                        v += (want - v) * 0.02
                    } else v *= 0.995
                    val np = sPos + v
                    // past either end of the arc the needle is in silent groove
                    val inGroove = np > 0.0 && np < last
                    sPos = np.coerceIn(0.0, last)
                    val g = if (!inGroove) 0f else (kotlin.math.abs(v) * 4.0).coerceAtMost(1.0).toFloat() * (if (sc) 1f else 0.6f)
                    gate += (g - gate) * 0.01f
                    fader += (fTarget - fader) * 0.05f          // a crisp cut, not a click
                    if (gate > 0.001f) {
                        val i0 = sPos.toInt(); val f = (sPos - i0).toFloat()
                        out += (smp[i0] + (smp[i0 + 1] - smp[i0]) * f) * gate * fader * 1.4f
                    }
                    chunk[i] = (out.coerceIn(-1f, 1f) * 32767f).toInt().toShort()
                }
                if (track.write(chunk, 0, chunk.size) < 0) break
            }
        } catch (e: Exception) {
        } finally { safeStop(track) }
    }

    private const val WET_GAIN = 1.25f     // the filtered one-shots are quieter; bring them back up a bit

    /** Turn the under-water sound on or off (the crowd stream crossfades by itself). */
    fun setMuffled(on: Boolean) { muffled = on }

    /**
     * The under-water filter: a steep low-pass (four one-pole stages at ~380 Hz) that keeps the
     * body of the voices and drops the consonants, glass and whistles, with a slow wobble in level.
     */
    fun underwater(buf: FloatArray, rate: Int) {
        val a = (1.0 - exp(-2.0 * PI * 380.0 / rate)).toFloat()
        var s1 = 0f; var s2 = 0f; var s3 = 0f; var s4 = 0f
        for (i in buf.indices) {
            s1 += a * (buf[i] - s1); s2 += a * (s1 - s2); s3 += a * (s2 - s3); s4 += a * (s3 - s4)
            val wobble = 1f + 0.15f * sin(2.0 * PI * 0.6 * i / rate).toFloat()
            buf[i] = s4 * 1.9f * wobble
        }
    }

    private fun underwater(pcm: ShortArray): ShortArray {
        val f = FloatArray(pcm.size) { pcm[it] / 30000f }
        underwater(f, RATE)
        return toPcm(f)
    }

    private fun safeStop(t: AudioTrack) { try { t.stop() } catch (e: Exception) {}; try { t.release() } catch (e: Exception) {} }

    private fun loop(pcm: ShortArray, volume: Float, start: Boolean = true): AudioTrack? = try {
        val track = AudioTrack(
            AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build(),
            AudioFormat.Builder().setSampleRate(RATE).setEncoding(AudioFormat.ENCODING_PCM_16BIT).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build(),
            pcm.size * 2, AudioTrack.MODE_STATIC, android.media.AudioManager.AUDIO_SESSION_ID_GENERATE
        )
        track.write(pcm, 0, pcm.size)
        track.setLoopPoints(0, pcm.size, -1)
        track.setVolume(volume)
        if (start) track.play()
        track
    } catch (e: Exception) { null }

    // ---- synth track ---------------------------------------------------------------------------

    private fun hz(midi: Int): Float = 440f * 2f.pow((midi - 69) / 12f)

    /** 8 bars at 118 BPM: four-on-the-floor kick, gated snare, hats, octave bass, detuned pad, square lead. */
    private fun renderMenuTrack(): ShortArray {
        val bpm = 112f
        val beat = 60f / bpm
        val bars = 16   // four rounds of: tom break (2 bars) · melody (2 bars)
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
            val root = roots[(bar / 2) % 4]
            for (s in 0 until 16) {
                val midi = if (s % 4 == 2) root + 12 else if (s % 8 == 7) root + 7 else root
                pluck(buf, bar * beat * 4 + s * sixteenth, hz(midi), sixteenth * 0.9f, 0.32f, saw = true)
            }
        }
        // Pad: detuned saws, slow attack, held for two bars
        for (c in 0 until 8) {
            val start = c * beat * 8
            for (m in triads[c % 4]) pad(buf, start, beat * 8, hz(m), 0.07f)
        }
        // Lead: the tune the crowd whistles (root, 2, 4, 7, 4, 2, root, -3, root), elaborated into a
        // staccato 16th-note line: each tune note stated, then echoed an octave up or down with a
        // passing note, over bars 3-4 and 7-8. Root = A (81).
        // Tune (semitones from A): 0 2 3 7 3 2 0 -2 0 — A B C E C B A G A, all inside the Am/F/C/G chords.
        // Bar 1: the tune straight, as 8ths. Bar 2: the tune again with octave-up echoes on the long notes.
        // Played an octave down (root A4 = 69) with the echoes only one octave up, so it sits in the mid-range
        val riff = intArrayOf(
            69, 0, 71, 0,   72, 0, 76, 0,   72, 0, 71, 0,   69, 0, 67, 69,
            69, 81, 71, 0,  72, 84, 76, 0,  72, 84, 71, 0,  69, 81, 67, 69
        )
        for (rep in 0 until 4) {
            val start = (2 + rep * 4) * beat * 4
            for ((i, m) in riff.withIndex()) {
                if (m == 0) continue
                // Staccato: each note is short, with a tiny ring on the lowest and highest ones
                val hold = if (riff.getOrNull(i + 1) == 0) sixteenth * 1.6f else sixteenth * 0.6f
                lead(buf, start + i * sixteenth, hz(m), hold, 0.2f)
            }
        }
        // Tom breaks: between the melody phrases a snappy FM tom part answers it, a different
        // pattern every time (h/m/l/f = high, mid, low, floor tom, tuned to the key; '.' = rest).
        // Each break is two bars of 16ths; the last one is the big fill that brings the tune back round.
        val tomPatterns = arrayOf(
            "h..m..l.h..m..l.h..m..l.hhmmllff",
            "l.l.m.h.l.l.m.h.l.l.m.h.hmlfhmlf",
            "h.h.....m.m.....l.l.....hmhmlflf",
            "hmlfhmlf..h...h.mmll..ff..hhmlf."
        )
        val tomPitch = mapOf('h' to hz(60), 'm' to hz(57), 'l' to hz(55), 'f' to hz(52))   // C4 A3 G3 E3
        for (k in 0 until 4) {
            // the break after melody k (the loop wraps, so the first break follows the last melody)
            val bar0 = ((4 * k + 4) % bars)
            val pattern = tomPatterns[k]
            for ((i, ch) in pattern.withIndex()) {
                val f = tomPitch[ch] ?: continue
                val vel = if (i % 4 == 0) 0.38f else 0.28f
                fmTom(buf, bar0 * beat * 4 + i * sixteenth, f, vel)
            }
        }
        // Gentle master compression by soft clipping
        for (i in buf.indices) { val v = buf[i]; buf[i] = (v / (1f + kotlin.math.abs(v) * 0.6f)) * 1.25f }
        return toPcm(buf)
    }

    /**
     * Snappy FM tom: a sine carrier whose pitch drops fast onto [f], modulated by an inharmonic
     * partner (1.41x) whose index dies away in a few milliseconds — the "pew" of an 80s synth tom.
     */
    private fun fmTom(buf: FloatArray, t0: Float, f: Float, amp: Float) {
        val start = (t0 * RATE).toInt(); val dur = (0.32f * RATE).toInt()
        var pc = 0.0; var pm = 0.0
        for (i in 0 until dur) {
            val idx = start + i; if (idx >= buf.size) break
            val t = i.toFloat() / RATE
            val fc = f * (1f + 0.9f * exp(-t * 38f))
            pm += 2 * PI * fc * 1.41 / RATE
            pc += 2 * PI * fc / RATE
            val index = 3.2f * exp(-t * 55f)
            val env = exp(-t * 11f) * minOf(1f, i / (RATE * 0.0015f))
            buf[idx] += (sin(pc + index * sin(pm)) * env * amp).toFloat()
        }
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
        val start = (t0 * RATE).toInt(); val dur = (0.15f * RATE).toInt()
        var lp = 0f; var prev = 0f
        for (i in 0 until dur) {
            val idx = start + i; if (idx >= buf.size) break
            val p = i.toFloat() / dur
            val t = i.toFloat() / RATE
            val white = rnd.nextFloat() * 2f - 1f
            lp += 0.7f * (white - lp)
            val hp = lp - prev; prev = lp                      // bright, crisp noise
            // Snap: a hard transient in the first 6 ms, a fast-decaying 200 Hz body, then a short gated tail
            val snap = exp(-t * 350f)
            val body = sin(2 * PI * 200 * t).toFloat() * exp(-t * 40f)
            val tail = if (p < 0.75f) (1f - p * 0.6f) else (1f - (p - 0.75f) / 0.25f)
            buf[idx] += (hp * 1.4f * (0.5f + snap) + lp * 0.3f + body * 0.9f) * tail * 0.75f
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

    /**
     * 80s FM "string" lead: a two-operator FM stack (modulator at 3x with its own feedback for a reedy
     * edge) on a 1x carrier, plus a second carrier modulated at 2x, three slightly detuned voices
     * with a slow ensemble chorus, bowed attack and a bright-to-warm modulation sweep.
     */
    private fun lead(buf: FloatArray, t0: Float, f: Float, durSec: Float, amp: Float) {
        val start = (t0 * RATE).toInt(); val dur = (durSec * RATE).toInt()
        val detunes = floatArrayOf(0.996f, 1f, 1.005f)
        val ph = DoubleArray(3); val m3 = DoubleArray(3); val m2 = DoubleArray(3); var fb = 0.0
        for (i in 0 until dur) {
            val idx = start + i; if (idx >= buf.size) break
            val t = i.toFloat() / RATE
            val p = i.toFloat() / dur
            val idx3 = 0.7 + 2.4 * exp(-t * 14f)                  // reedy 3x modulator: bright attack, settles
            val idx2 = 0.6 + 1.2 * exp(-t * 8f)                   // softer 2x modulator for body
            val vib = 1f + 0.006f * sin(2 * PI * 5.0 * t).toFloat() * minOf(1f, p * 2.5f)
            val chorus = 1f + 0.0025f * sin(2 * PI * 0.8 * t + i * 0.0).toFloat()
            var v = 0.0
            for (k in 0 until 3) {
                val fk = f * detunes[k] * vib * (if (k == 2) chorus else 1f)
                m3[k] += 2 * PI * fk * 3.0 / RATE
                m2[k] += 2 * PI * fk * 2.0 / RATE
                ph[k] += 2 * PI * fk / RATE
                val modA = sin(m3[k] + 0.35 * fb)                 // feedback gives the sawtooth-ish string rasp
                fb = modA
                val carrierA = sin(ph[k] + idx3 * modA)
                val carrierB = sin(ph[k] * 1.0 + idx2 * sin(m2[k]))
                v += (carrierA * 0.5 + carrierB * 0.5) / 3.0
            }
            // Bowed attack, sustained, then a quick release
            val env = minOf(1f, i / (RATE * 0.012f)).let { it * it } * (if (p > 0.7f) ((1f - p) / 0.3f) else 1f)
            buf[idx] += (v * env * amp).toFloat()
        }
    }

    // ---- crowd --------------------------------------------------------------------------------

    // Vowel formants (F1, F2) for a / e / i / o / u
    private val vowels = arrayOf(730f to 1090f, 530f to 1840f, 270f to 2290f, 570f to 840f, 300f to 870f)

    /**
     * 64 s of a busy pub: a handful of near voices you can almost follow — proper syllables with
     * consonant onsets, vowel colour and sentence intonation, taking turns — over a dozen distant,
     * duller voices, with the odd laugh, a whistled tune and a glass clink. No noise bed, so it
     * reads as people rather than a cabin hum.
     */
    private fun renderCrowd(): ShortArray {
        val len = 64f
        val n = (RATE * len).toInt()
        val buf = FloatArray(n)
        val rnd = Random(29)

        // Near voices: distinct pitches, loud enough to pick out. Three conversations run side by side,
        // each with its own speakers taking turns.
        val tables = arrayOf(floatArrayOf(105f, 150f, 230f), floatArrayOf(125f, 195f, 260f), floatArrayOf(112f, 170f, 215f), floatArrayOf(98f, 140f, 205f))
        for ((ti, table) in tables.withIndex()) {
            var turnEnd = ti * 0.7f
            val amp = if (ti == 0) 0.05f else 0.038f                       // one table is closest
            while (turnEnd < len - 1f) {
                val v = rnd.nextInt(table.size)
                val start = (turnEnd - rnd.nextFloat() * 0.3f).coerceAtLeast(0f)   // slight overlap of turns
                val phraseLen = talk(buf, start, table[v], amp = amp, bright = 1f, rnd = rnd, syllablesMin = 5, syllablesMax = 14)
                turnEnd = start + phraseLen + 0.2f + rnd.nextFloat() * (if (ti == 0) 0.5f else 1.4f)
            }
        }
        // Distant voices: quieter, duller, overlapping freely
        for (voice in 0 until 26) {
            val base = 95f + rnd.nextFloat() * 170f
            var t = rnd.nextFloat() * 2f
            while (t < len) {
                t += talk(buf, t, base, amp = 0.012f, bright = 0.35f, rnd = rnd, syllablesMin = 3, syllablesMax = 9)
                t += 0.6f + rnd.nextFloat() * 2.5f
            }
        }
        // Laughter: two bursts, "ha-ha-ha-ha" rising then tailing off
        for (k in 0 until 8) {
            val at = 2.5f + k * 7.5f + rnd.nextFloat() * 2f
            val f0 = if (k % 2 == 0) 170f + rnd.nextFloat() * 20f else 240f + rnd.nextFloat() * 30f
            for (i in 0 until 5 + rnd.nextInt(3)) {
                val f = f0 * (1.15f - 0.05f * i)
                syllable(buf, at + i * 0.17f, f, vowels[0], 0.13f, 0.045f * (1f - i * 0.1f), 1f, consonant = 0.5f, rnd = rnd)
            }
        }
        // Whistling: a quiet snatch of the house tune, far across the room — only now and then (about
        // every half minute), and each time by someone else, in a different octave
        val tune = intArrayOf(0, 2, 3, 7, 3, 2, 0, -2, 0)
        for ((w, root) in floatArrayOf(1760f, 880f).withIndex()) {       // A6, then A5 — the menu track's key
            var t = 9f + w * 32f + rnd.nextFloat() * 4f
            for ((i, step) in tune.withIndex()) {
                val dur = if (i == tune.size - 1) 0.5f else 0.22f + rnd.nextFloat() * 0.12f
                whistle(buf, t, root * 2f.pow(step / 12f), dur, if (root < 1000f) 0.024f else 0.018f)
                t += dur * 1.05f
            }
        }
        // Glasses clinking: pint glasses (low ring) and wine glasses (high ring). Roughly one every
        // three seconds, unevenly spaced, each glass a little sharp or flat of the last.
        run {
            var at = 0.4f
            while (at < len - 0.6f) {
                val pint = rnd.nextFloat() < 0.55f
                val f = (if (pint) 1150f else 3100f) * (0.94f + rnd.nextFloat() * 0.12f)
                val loud = 0.02f + rnd.nextFloat() * 0.012f
                // sometimes a proper "cheers": two glasses a moment apart
                val hits = if (rnd.nextFloat() < 0.25f) 2 else 1
                for (hNo in 0 until hits) {
                    val ff = f * (1f + hNo * 0.035f)
                    val start = ((at + hNo * 0.09f) * RATE).toInt(); val dur = (0.5f * RATE).toInt()
                    for (i in 0 until dur) {
                        val idx = start + i; if (idx >= n) break
                        val tt = i.toFloat() / RATE
                        val attack = minOf(1f, i / (RATE * 0.004f))
                        buf[idx] += ((sin(2 * PI * ff * tt) + 0.4 * sin(2 * PI * ff * 1.5 * tt) + 0.2 * sin(2 * PI * ff * 2.76 * tt)) * exp(-tt * 9f) * loud * attack).toFloat()
                    }
                }
                at += 2.0f + rnd.nextFloat() * 2.6f        // about one every three seconds
            }
        }
        // Now and then (twice a loop, rarer than the menu's breaks) a few tables drum one of the tom
        // breaks on the tabletops, softly and dull, at the menu track's tempo
        run {
            val sixteenth = 60f / 112f / 4f
            val pats = arrayOf("h..m..l.h..m..l.h..m..l.hhmmllff", "hmlfhmlf..h...h.mmll..ff..hhmlf.")
            val pitch = mapOf('h' to 196f, 'm' to 147f, 'l' to 123f, 'f' to 98f)
            for ((k, pat) in pats.withIndex()) {
                val t0 = 18f + k * 30f
                for ((i, ch) in pat.withIndex()) {
                    val f = pitch[ch] ?: continue
                    fmTom(buf, t0 + i * sixteenth + rnd.nextFloat() * 0.012f, f, if (i % 4 == 0) 0.07f else 0.05f)
                }
            }
        }
        // Someone singing along in the corner: a slow pentatonic tune, lots of vibrato, far away
        run {
            val notes = intArrayOf(0, 2, 3, 7, 3, 2, 0, -2, 0, 2, 3, 2, 0)
            val root = 220f   // A3 — the house tune, same key as the menu
            for (verse in 0 until 2) {
                var t = 1.5f + verse * 37f
                for ((i, st) in notes.withIndex()) {
                    val dur = if (i % 4 == 3) 0.9f else 0.45f
                    sing(buf, t, root * 2f.pow(st / 12f), dur, 0.03f, rnd)
                    t += dur * 1.02f
                    if (t > len - 0.5f) break
                }
            }
        }
        // A distant table cheering something: a soft, far-off "heyyy" from a few voices
        for (k in 0 until 8) {
            val at = 3f + k * 7.6f + rnd.nextFloat() * 1.5f
            for (v in 0 until 5) {
                val f0 = 140f + rnd.nextFloat() * 160f
                val start = ((at + rnd.nextFloat() * 0.12f) * RATE).toInt(); val dur = ((0.6f + rnd.nextFloat() * 0.4f) * RATE).toInt()
                var phase = 0.0
                for (i in 0 until dur) {
                    val idx = start + i; if (idx >= n) break
                    val p = i.toFloat() / dur
                    phase += 2 * PI * f0 * (1f + 0.25f * sin(PI * minOf(1f, p * 1.3f))) / RATE
                    val env = sin(PI * p).toFloat().let { it * it }
                    buf[idx] += ((sin(phase) + 0.4 * sin(2 * phase)) * env * 0.012f).toFloat()
                }
            }
        }
        // Gentle smoothing (takes any remaining edge off the onsets), then a seamless loop
        var y = 0f
        for (i in 0 until n) { y += 0.45f * (buf[i] - y); buf[i] = y }
        val x = (0.8f * RATE).toInt()
        for (i in 0 until x) { val k = i.toFloat() / x; buf[i] = buf[i] * k + buf[n - x + i] * (1f - k) }
        return toPcm(buf.copyOf(n - x))
    }

    /** One spoken phrase: a run of syllables with a wandering pitch that falls at the end. Returns its length in seconds. */
    private fun talk(buf: FloatArray, t0: Float, base: Float, amp: Float, bright: Float, rnd: Random, syllablesMin: Int, syllablesMax: Int): Float {
        val count = syllablesMin + rnd.nextInt(syllablesMax - syllablesMin + 1)
        var t = t0
        var pitch = base * (0.95f + rnd.nextFloat() * 0.15f)
        for (i in 0 until count) {
            val p = i.toFloat() / count
            // intonation: random walk, lifted mid-phrase, dropping on the last two syllables
            pitch *= 0.96f + rnd.nextFloat() * 0.09f
            val contour = if (p > 0.75f) 0.88f else if (p in 0.3f..0.6f) 1.06f else 1f
            val stressed = rnd.nextFloat() < 0.3f
            val dur = (if (stressed) 0.16f else 0.09f) + rnd.nextFloat() * 0.08f
            val vowel = vowels[rnd.nextInt(vowels.size)]
            syllable(buf, t, pitch * contour, vowel, dur, amp * (if (stressed) 1.3f else 1f), bright, consonant = 0.3f + rnd.nextFloat() * 0.7f, rnd = rnd)
            t += dur + 0.02f + (if (rnd.nextFloat() < 0.15f) 0.12f else 0f)   // occasional word gap
        }
        return t - t0
    }

    /** One syllable: a short consonant burst, then a vowel built from harmonics shaped by two formants. */
    private fun syllable(buf: FloatArray, t0: Float, f: Float, vowel: Pair<Float, Float>, durSec: Float, amp: Float, bright: Float, consonant: Float, rnd: Random) {
        val start = (t0 * RATE).toInt(); val dur = (durSec * RATE).toInt()
        val (f1, f2) = vowel
        // Harmonic gains from the formants (bright voices keep more of F2)
        val harmonics = 10
        val gains = FloatArray(harmonics) { h ->
            val fh = f * (h + 1)
            val g1 = 1f / (1f + ((fh - f1) / 130f).let { it * it })
            val g2 = bright * 0.7f / (1f + ((fh - f2) / 200f).let { it * it })
            (g1 + g2 + 0.08f) / (h + 1)
        }
        // Consonant: a soft breathy onset (no click) — low-passed noise that swells and fades
        val cLen = (RATE * (0.02f + 0.02f * consonant)).toInt()
        var lp = 0f; var lp2 = 0f
        for (i in 0 until cLen) {
            val idx = start + i; if (idx >= buf.size) break
            val white = rnd.nextFloat() * 2f - 1f
            lp += 0.12f * (white - lp); lp2 += 0.12f * (lp - lp2)
            val p = i.toFloat() / cLen
            buf[idx] += lp2 * amp * 0.9f * consonant * sin(PI * p).toFloat()
        }
        var phase = 0.0
        for (i in 0 until dur) {
            val idx = start + cLen + i; if (idx >= buf.size) break
            val p = i.toFloat() / dur
            val glide = f * (1f + 0.05f * (0.5f - p)) * (1f + 0.004f * sin(2 * PI * 5.0 * i / RATE).toFloat())
            phase += 2 * PI * glide / RATE
            val env = minOf(1f, p / 0.25f).let { it * it } * (if (p > 0.6f) (1f - p) / 0.4f else 1f)
            var v = 0.0
            for (h in 0 until harmonics) v += gains[h] * sin((h + 1) * phase)
            buf[idx] += (v * env * amp).toFloat()
        }
    }

    /** A sung note: warm "ooh"-ish harmonics with slow deep vibrato and a soft swell, as if across the room. */
    private fun sing(buf: FloatArray, t0: Float, f: Float, durSec: Float, amp: Float, rnd: Random) {
        val start = (t0 * RATE).toInt(); val dur = (durSec * RATE).toInt()
        var phase = 0.0
        for (i in 0 until dur) {
            val idx = start + i; if (idx >= buf.size) break
            val p = i.toFloat() / dur
            val t = i.toFloat() / RATE
            val vib = 1f + 0.018f * sin(2 * PI * 5.2 * t).toFloat() * minOf(1f, p * 3f)
            phase += 2 * PI * f * vib / RATE
            val env = (0.5f - 0.5f * kotlin.math.cos(PI.toFloat() * p)).let { it * it }
            val v = sin(phase) + 0.5 * sin(2 * phase) + 0.2 * sin(3 * phase) + 0.1 * sin(4 * phase)
            buf[idx] += (v * env * amp).toFloat()
        }
    }

    private fun whistle(buf: FloatArray, t0: Float, f: Float, durSec: Float, amp: Float) {
        val start = (t0 * RATE).toInt(); val dur = (durSec * RATE).toInt()
        var phase = 0.0
        for (i in 0 until dur) {
            val idx = start + i; if (idx >= buf.size) break
            val t = i.toFloat() / RATE
            val p = i.toFloat() / dur
            val vib = 1f + 0.012f * sin(2 * PI * 6.0 * t).toFloat() * minOf(1f, p * 4f)
            phase += 2 * PI * f * vib / RATE
            val env = minOf(1f, p / 0.1f) * (if (p > 0.7f) (1f - p) / 0.3f else 1f)
            buf[idx] += (sin(phase) * env * amp).toFloat()
        }
    }

    // ---- roar ---------------------------------------------------------------------------------

    private val roarCache = HashMap<Boolean, ShortArray>()

    /**
     * A crowd roar for a ton-plus visit, built from voices rather than noise: dozens of people
     * shouting vowels with sweeping pitch, staggered so the wall builds and decays, individual claps
     * from many pairs of hands, and whistles. [big] (180 or a finish) is longer, louder and denser.
     */
    fun roar(big: Boolean) {
        thread(name = "roar") {
            val pcm = synchronized(roarCache) { roarCache.getOrPut(big) { renderRoar(big) } }
            if (muffled) playOnce(underwater(pcm), WET_GAIN) else playOnce(pcm, 1f)
        }
    }

    private fun renderRoar(big: Boolean): ShortArray {
        val len = if (big) 3.6f else 2.4f
        val n = (RATE * len).toInt()
        val buf = FloatArray(n)
        val rnd = Random(if (big) 101 else 57)
        // One big collective "Waaayyy": everyone starts on a "w/o" and opens into "ay", the pitch
        // of the whole crowd lifts together then settles, and every voice is long and smooth.
        val voices = if (big) 120 else 70
        for (v in 0 until voices) {
            val female = rnd.nextFloat() < 0.45f
            val f0 = (if (female) 230f else 125f) * (0.88f + rnd.nextFloat() * 0.3f)
            val onset = rnd.nextFloat() * rnd.nextFloat() * len * 0.25f
            val dur = len * (0.55f + rnd.nextFloat() * 0.4f) - onset
            shout(buf, onset, f0, dur.coerceAtLeast(0.8f), 0.016f * (0.7f + rnd.nextFloat() * 0.6f), rnd)
        }
        // Whistles on the big ones
        if (big) for (w in 0 until 3) {
            var t = 0.5f + rnd.nextFloat() * 1.2f
            val f = 1500f + rnd.nextFloat() * 600f
            whistle(buf, t, f, 0.35f, 0.04f); t += 0.3f
            whistle(buf, t, f * 1.25f, 0.6f, 0.04f)
        }
        // Smooth it: a one-pole low-pass takes any edge off, then a soft limiter for loudness
        var y = 0f
        for (i in 0 until n) { y += 0.35f * (buf[i] - y); buf[i] = y }
        for (i in 0 until n) {
            val p = i.toFloat() / n
            val env = minOf(1f, p / 0.12f).let { it * it * (3f - 2f * it) } * (if (p > 0.6f) ((1f - p) / 0.4f).let { it * it * (3f - 2f * it) } else 1f)
            val x = buf[i] * env * 2.6f
            buf[i] = x / (1f + kotlin.math.abs(x) * 0.6f)
        }
        return toPcm(buf)
    }

    /**
     * One long shouted "waaay": rounded onset ("w"/"o" formants) opening to an "ay" vowel, pitch that
     * rises over the first third and sags gently after, slow vibrato, raised-cosine envelope. No noise.
     */
    private fun shout(buf: FloatArray, t0: Float, f0: Float, durSec: Float, amp: Float, rnd: Random) {
        val start = (t0 * RATE).toInt(); val dur = (durSec * RATE).toInt()
        val harmonics = 8
        var phase = 0.0
        val vibRate = 4.5 + rnd.nextFloat() * 1.5
        val lift = 0.25f + rnd.nextFloat() * 0.15f
        for (i in 0 until dur) {
            val idx = start + i; if (idx >= buf.size) break
            val p = i.toFloat() / dur
            // pitch: up over the first 30%, then a slow sag
            val contour = if (p < 0.3f) 1f + lift * (p / 0.3f) else 1f + lift * (1f - 0.5f * (p - 0.3f) / 0.7f)
            val f = f0 * contour * (1f + 0.012f * sin(2 * PI * vibRate * i / RATE).toFloat() * minOf(1f, p * 3f))
            phase += 2 * PI * f / RATE
            // vowel morph: "o" (570/840) -> "ay" (660/1700) over the first 40%
            val m = minOf(1f, p / 0.4f)
            val f1 = 570f + (660f - 570f) * m
            val f2 = 840f + (1700f - 840f) * m
            val env = (0.5f - 0.5f * kotlin.math.cos(PI.toFloat() * p)).let { it * it }   // smooth in and out
            var v = 0.0
            for (h in 1..harmonics) {
                val fh = f * h
                val g1 = 1f / (1f + ((fh - f1) / 170f).let { it * it })
                val g2 = 0.6f / (1f + ((fh - f2) / 260f).let { it * it })
                v += (g1 + g2 + 0.08f) / h * sin(h * phase)
            }
            buf[idx] += (v * env * amp).toFloat()
        }
    }

    private fun playOnce(pcm: ShortArray, volume: Float) {
        try {
            val track = AudioTrack(
                AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build(),
                AudioFormat.Builder().setSampleRate(RATE).setEncoding(AudioFormat.ENCODING_PCM_16BIT).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build(),
                pcm.size * 2, AudioTrack.MODE_STATIC, android.media.AudioManager.AUDIO_SESSION_ID_GENERATE
            )
            track.setVolume(volume)
            track.write(pcm, 0, pcm.size)
            track.play()
            Thread.sleep(pcm.size * 1000L / RATE + 120)
            track.stop(); track.release()
        } catch (e: Exception) { }
    }

    private fun toPcm(buf: FloatArray): ShortArray {
        val pcm = ShortArray(buf.size)
        for (i in buf.indices) pcm[i] = (buf[i].coerceIn(-1f, 1f) * 30000).toInt().toShort()
        return pcm
    }
}
