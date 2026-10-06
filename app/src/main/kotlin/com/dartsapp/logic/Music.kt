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

    private var menuTrack: AudioTrack? = null
    private var menuPcm: ShortArray? = null
    private var crowdTrack: AudioTrack? = null
    private var crowdPcm: ShortArray? = null
    @Volatile private var menuWanted = false
    @Volatile private var crowdWanted = false

    fun startMenu() { menuWanted = true; crowdWanted = false; thread(name = "music") { syncTracks() } }
    fun startCrowd() { crowdWanted = true; menuWanted = false; thread(name = "music") { syncTracks() } }
    fun stopAll() { menuWanted = false; crowdWanted = false; thread(name = "music") { syncTracks() } }

    @Synchronized
    private fun syncTracks() {
        if (menuWanted) {
            if (menuTrack == null) {
                val pcm = menuPcm ?: renderMenuTrack().also { menuPcm = it }
                if (menuWanted) menuTrack = loop(pcm, 0.55f)
            }
        } else { menuTrack?.let { safeStop(it) }; menuTrack = null }
        if (crowdWanted) {
            if (crowdTrack == null) {
                val pcm = crowdPcm ?: renderCrowd().also { crowdPcm = it }
                if (crowdWanted) crowdTrack = loop(pcm, 0.5f)
            }
        } else { crowdTrack?.let { safeStop(it) }; crowdTrack = null }
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

    // ---- crowd --------------------------------------------------------------------------------

    // Vowel formants (F1, F2) for a / e / i / o / u
    private val vowels = arrayOf(730f to 1090f, 530f to 1840f, 270f to 2290f, 570f to 840f, 300f to 870f)

    /**
     * 16 s of a busy pub: a handful of near voices you can almost follow — proper syllables with
     * consonant onsets, vowel colour and sentence intonation, taking turns — over a dozen distant,
     * duller voices, with the odd laugh, a whistled tune and a glass clink. No noise bed, so it
     * reads as people rather than a cabin hum.
     */
    private fun renderCrowd(): ShortArray {
        val len = 16f
        val n = (RATE * len).toInt()
        val buf = FloatArray(n)
        val rnd = Random(29)

        // Near voices: distinct pitches, loud enough to pick out, speaking in turns
        val near = arrayOf(105f, 125f, 150f, 195f, 230f)
        var turnEnd = 0f
        for (round in 0 until 9) {
            val v = rnd.nextInt(near.size)
            val start = turnEnd - rnd.nextFloat() * 0.3f               // slight overlap of turns
            val phraseLen = talk(buf, start.coerceAtLeast(0f), near[v], amp = 0.05f, bright = 1f, rnd = rnd, syllablesMin = 5, syllablesMax = 14)
            turnEnd = start + phraseLen + 0.2f + rnd.nextFloat() * 0.5f
            if (turnEnd > len - 1f) break
        }
        // Distant voices: quieter, duller, overlapping freely
        for (voice in 0 until 12) {
            val base = 95f + rnd.nextFloat() * 150f
            var t = rnd.nextFloat() * 2f
            while (t < len) {
                t += talk(buf, t, base, amp = 0.012f, bright = 0.35f, rnd = rnd, syllablesMin = 3, syllablesMax = 9)
                t += 0.6f + rnd.nextFloat() * 2.5f
            }
        }
        // Laughter: two bursts, "ha-ha-ha-ha" rising then tailing off
        for (k in 0 until 2) {
            val at = 2.5f + k * 7.5f + rnd.nextFloat()
            val f0 = if (k == 0) 170f else 240f
            for (i in 0 until 5 + rnd.nextInt(3)) {
                val f = f0 * (1.15f - 0.05f * i)
                syllable(buf, at + i * 0.17f, f, vowels[0], 0.13f, 0.045f * (1f - i * 0.1f), 1f, consonant = 0.5f, rnd = rnd)
            }
        }
        // Whistling: two short pentatonic snatches with vibrato
        val tune = intArrayOf(0, 2, 4, 7, 4, 2, 0, -3, 0)
        for (k in 0 until 2) {
            var t = 4.5f + k * 7f + rnd.nextFloat()
            val root = if (k == 0) 1480f else 1760f
            for ((i, step) in tune.withIndex()) {
                val dur = if (i == tune.size - 1) 0.5f else 0.22f + rnd.nextFloat() * 0.12f
                whistle(buf, t, root * 2f.pow(step / 12f), dur, 0.035f)
                t += dur * 1.05f
            }
        }
        // Glass clinks
        for (k in 0 until 3) {
            val at = 1f + rnd.nextFloat() * (len - 2f)
            val f = 2600f + rnd.nextFloat() * 2000f
            val start = (at * RATE).toInt(); val dur = (0.3f * RATE).toInt()
            for (i in 0 until dur) {
                val idx = start + i; if (idx >= n) break
                val tt = i.toFloat() / RATE
                buf[idx] += ((sin(2 * PI * f * tt) + 0.5 * sin(2 * PI * f * 1.42 * tt)) * exp(-tt * 16f) * 0.05f).toFloat()
            }
        }
        // Light smoothing only (keeps the consonants), then a seamless loop
        var y = 0f
        for (i in 0 until n) { y += 0.6f * (buf[i] - y); buf[i] = y }
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
        // Consonant: a brief noise burst coloured by the voice brightness
        val cLen = (RATE * (0.012f + 0.014f * consonant)).toInt()
        var lp = 0f
        for (i in 0 until cLen) {
            val idx = start + i; if (idx >= buf.size) break
            val white = rnd.nextFloat() * 2f - 1f
            lp += (0.2f + 0.5f * bright) * (white - lp)
            buf[idx] += lp * amp * 1.5f * consonant * (1f - i.toFloat() / cLen)
        }
        var phase = 0.0
        for (i in 0 until dur) {
            val idx = start + cLen + i; if (idx >= buf.size) break
            val p = i.toFloat() / dur
            val glide = f * (1f + 0.05f * (0.5f - p)) * (1f + 0.004f * sin(2 * PI * 5.0 * i / RATE).toFloat())
            phase += 2 * PI * glide / RATE
            val env = minOf(1f, p / 0.12f) * (if (p > 0.6f) (1f - p) / 0.4f else 1f)
            var v = 0.0
            for (h in 0 until harmonics) v += gains[h] * sin((h + 1) * phase)
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
            playOnce(pcm, 1f)
        }
    }

    private fun renderRoar(big: Boolean): ShortArray {
        val len = if (big) 3.4f else 2.2f
        val n = (RATE * len).toInt()
        val buf = FloatArray(n)
        val rnd = Random(if (big) 101 else 57)
        val shoutVowels = arrayOf(vowels[0], vowels[3], vowels[1])          // "yeah", "ohh", "eh"
        // Shouting voices: onset spread over the first part so the roar builds, each a sweep up then down
        val voices = if (big) 90 else 50
        for (v in 0 until voices) {
            val female = rnd.nextFloat() < 0.4f
            val f0 = (if (female) 220f else 130f) * (0.85f + rnd.nextFloat() * 0.35f)
            val onset = rnd.nextFloat() * rnd.nextFloat() * len * 0.45f          // most people shout early
            val dur = 0.5f + rnd.nextFloat() * (if (big) 1.3f else 0.8f)
            val vowel = shoutVowels[rnd.nextInt(shoutVowels.size)]
            shout(buf, onset, f0, vowel, dur, 0.022f * (0.6f + rnd.nextFloat() * 0.6f), rnd)
        }
        // Clapping: hundreds of individual claps, densest just after the shout peak
        val claps = if (big) 700 else 350
        for (c in 0 until claps) {
            val at = (0.15f + rnd.nextFloat() * (len - 0.3f)) * (0.7f + 0.3f * rnd.nextFloat())
            clap(buf, at, 0.05f + rnd.nextFloat() * 0.05f, rnd)
        }
        // Whistles on the big ones
        if (big) for (w in 0 until 4) {
            var t = 0.3f + rnd.nextFloat() * 1.2f
            val f = 1500f + rnd.nextFloat() * 700f
            whistle(buf, t, f, 0.35f, 0.05f); t += 0.3f
            whistle(buf, t, f * 1.25f, 0.5f, 0.05f)
        }
        // Overall shape: quick swell, long tail, then soft clip so it is loud but not harsh
        for (i in 0 until n) {
            val p = i.toFloat() / n
            val env = minOf(1f, p / 0.1f) * (if (p > 0.55f) ((1f - p) / 0.45f).let { it * it * (3f - 2f * it) } else 1f)
            val x = buf[i] * env * 2.2f
            buf[i] = x / (1f + kotlin.math.abs(x) * 0.7f)
        }
        return toPcm(buf)
    }

    /** One shouted vowel: pitch rises quickly then sags, formant-shaped harmonics, a little rasp. */
    private fun shout(buf: FloatArray, t0: Float, f0: Float, vowel: Pair<Float, Float>, durSec: Float, amp: Float, rnd: Random) {
        val start = (t0 * RATE).toInt(); val dur = (durSec * RATE).toInt()
        val (f1, f2) = vowel
        val harmonics = 10
        var phase = 0.0
        val rasp = rnd.nextFloat() * 0.3f
        for (i in 0 until dur) {
            val idx = start + i; if (idx >= buf.size) break
            val p = i.toFloat() / dur
            val f = f0 * (1f + 0.35f * sin(PI * minOf(1f, p * 1.4f)).toFloat()) * (1f + 0.01f * sin(2 * PI * 6.0 * i / RATE).toFloat())
            phase += 2 * PI * f / RATE
            val env = minOf(1f, p / 0.08f) * (if (p > 0.5f) (1f - p) / 0.5f else 1f)
            var v = 0.0
            for (h in 1..harmonics) {
                val fh = f * h
                val g1 = 1f / (1f + ((fh - f1) / 150f).let { it * it })
                val g2 = 0.8f / (1f + ((fh - f2) / 220f).let { it * it })
                v += (g1 + g2 + 0.1f) / h * sin(h * phase)
            }
            if (rasp > 0.15f) v += (rnd.nextFloat() * 2f - 1f) * rasp * 0.3f
            buf[idx] += (v * env * amp).toFloat()
        }
    }

    /** One hand clap: a very short burst of band-limited noise. */
    private fun clap(buf: FloatArray, t0: Float, amp: Float, rnd: Random) {
        val start = (t0 * RATE).toInt(); val dur = (0.012f * RATE).toInt()
        var lp = 0f; var prev = 0f
        for (i in 0 until dur) {
            val idx = start + i; if (idx >= buf.size) break
            val white = rnd.nextFloat() * 2f - 1f
            lp += 0.6f * (white - lp)
            val bp = lp - prev; prev = lp
            val p = i.toFloat() / dur
            buf[idx] += bp * (1f - p) * amp * 4f
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
