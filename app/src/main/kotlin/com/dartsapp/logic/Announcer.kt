package com.dartsapp.logic

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale

/**
 * The caller. Uses the phone's text-to-speech engine, pitched down and slowed a touch for the
 * big-room referee sound. Anything said before the engine is ready is queued.
 */
object Announcer {
    private var tts: TextToSpeech? = null
    private var ready = false
    private val pending = ArrayList<String>()
    @Volatile private var enabled = true
    // IN THE ZONE: instead of speaking straight out, each line is rendered to a WAV file, run through
    // the same under-water filter as the crowd, and played back — works with any voice engine
    @Volatile private var muffled = false
    private var cacheDir: java.io.File? = null

    fun init(ctx: Context) {
        enabled = Settings.announcerOn(ctx)
        if (tts != null) return
        val app = ctx.applicationContext
        cacheDir = app.cacheDir
        tts = TextToSpeech(app) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val t = tts ?: return@TextToSpeech
                val res = t.setLanguage(Locale.UK)
                if (res == TextToSpeech.LANG_MISSING_DATA || res == TextToSpeech.LANG_NOT_SUPPORTED) t.setLanguage(Locale.getDefault())
                t.setPitch(0.72f)
                t.setSpeechRate(0.92f)
                t.setOnUtteranceProgressListener(object : android.speech.tts.UtteranceProgressListener() {
                    override fun onStart(id: String?) {}
                    @Deprecated("old API") override fun onError(id: String?) { id?.let { fileFor(it).delete() } }
                    override fun onDone(id: String?) {
                        if (id == null || !id.startsWith("zone-")) return
                        val f = fileFor(id)
                        kotlin.concurrent.thread(name = "zoneSay") { playMuffled(f) }
                    }
                })
                ready = true
                synchronized(pending) { for (s in pending) speak(s); pending.clear() }
            }
        }
    }

    fun setEnabled(on: Boolean) { enabled = on }

    /** Muffle the caller (IN THE ZONE) or bring him back clear. */
    fun setMuffled(on: Boolean) { muffled = on }

    private fun fileFor(id: String) = java.io.File(cacheDir, "$id.wav")

    /** Plays a rendered line through the under-water filter, then deletes the file. */
    private fun playMuffled(f: java.io.File) {
        try {
            val bytes = f.readBytes()
            f.delete()
            val bb = java.nio.ByteBuffer.wrap(bytes).order(java.nio.ByteOrder.LITTLE_ENDIAN)
            // walk the RIFF chunks for the format and the samples
            var rate = 22050; var channels = 1; var bits = 16
            var dataStart = -1; var dataLen = 0
            var i = 12
            while (i + 8 <= bytes.size) {
                val id = String(bytes, i, 4, Charsets.US_ASCII)
                val len = bb.getInt(i + 4)
                if (id == "fmt ") { channels = bb.getShort(i + 10).toInt(); rate = bb.getInt(i + 12); bits = bb.getShort(i + 22).toInt() }
                if (id == "data") { dataStart = i + 8; dataLen = minOf(len, bytes.size - dataStart); break }
                i += 8 + len + (len and 1)
            }
            if (dataStart < 0 || bits != 16 || channels < 1) return
            val frames = dataLen / (2 * channels)
            val buf = FloatArray(frames) { bb.getShort(dataStart + it * 2 * channels) / 32768f }
            Music.underwater(buf, rate)
            val pcm = ShortArray(frames) { (buf[it].coerceIn(-1f, 1f) * 32767f * 0.9f).toInt().toShort() }
            val track = android.media.AudioTrack(
                android.media.AudioAttributes.Builder().setUsage(android.media.AudioAttributes.USAGE_GAME).setContentType(android.media.AudioAttributes.CONTENT_TYPE_SPEECH).build(),
                android.media.AudioFormat.Builder().setSampleRate(rate).setEncoding(android.media.AudioFormat.ENCODING_PCM_16BIT).setChannelMask(android.media.AudioFormat.CHANNEL_OUT_MONO).build(),
                maxOf(pcm.size * 2, 4096), android.media.AudioTrack.MODE_STATIC, android.media.AudioManager.AUDIO_SESSION_ID_GENERATE
            )
            track.write(pcm, 0, pcm.size)
            track.play()
            Thread.sleep(frames * 1000L / rate + 150)
            track.stop(); track.release()
        } catch (e: Exception) { }
    }

    private fun speak(text: String) {
        if (!enabled) return
        val t = tts ?: return
        try {
            if (muffled && cacheDir != null) {
                // IN THE ZONE: render it, filter it, play it (see onDone)
                val id = "zone-${System.nanoTime()}"
                t.synthesizeToFile(text, android.os.Bundle(), fileFor(id), id)
            } else {
                t.speak(text, TextToSpeech.QUEUE_ADD, null, "say-${System.nanoTime()}")
            }
        } catch (e: Exception) { }
    }

    fun say(text: String) {
        if (!enabled) return
        if (ready) speak(text) else synchronized(pending) { pending.add(text) }
    }

    fun gameOn() = say("Game on!")

    fun gameShot() = say("Game shot!")

    /** Calls a visit score the way a referee would: tons and above, with the 180 drawn out. */
    fun score(visit: Int) {
        when {
            visit == 180 -> {
                // Drawn out, like the real call: slower rate and a touch lower just for this one
                if (ready && enabled) {
                    tts?.setSpeechRate(0.62f); tts?.setPitch(0.66f)
                    speak("One hundred and eighty!")
                    tts?.setSpeechRate(0.92f); tts?.setPitch(0.72f)
                } else say("One hundred and eighty!")
            }
            visit >= 100 -> say(words(visit))
        }
    }

    /** A checkout: "Game shot, and the leg" style call with the finish. */
    fun checkout(visit: Int) {
        if (visit >= 100) say("${words(visit)} checkout! Game shot!") else say("Game shot!")
    }

    private val ones = arrayOf("", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine", "ten",
        "eleven", "twelve", "thirteen", "fourteen", "fifteen", "sixteen", "seventeen", "eighteen", "nineteen")
    private val tens = arrayOf("", "", "twenty", "thirty", "forty", "fifty", "sixty", "seventy", "eighty", "ninety")

    private fun words(n: Int): String {
        if (n < 20) return ones[n]
        if (n < 100) return tens[n / 10] + (if (n % 10 != 0) " " + ones[n % 10] else "")
        val rest = n % 100
        return "one hundred" + (if (rest != 0) " and " + words(rest) else "")
    }
}
