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
    // IN THE ZONE: the caller's voice goes through its own audio session with an equaliser that cuts
    // the highs, so he sounds muffled, as if under water; a touch lower and quieter too in case the
    // phone's voice engine ignores the session
    private var sessionId = 0
    private var eq: android.media.audiofx.Equalizer? = null
    @Volatile private var muffled = false

    fun init(ctx: Context) {
        enabled = Settings.announcerOn(ctx)
        if (tts != null) return
        val app = ctx.applicationContext
        try {
            val am = app.getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
            sessionId = am.generateAudioSessionId()
            eq = android.media.audiofx.Equalizer(0, sessionId).apply { enabled = false }
        } catch (e: Exception) { eq = null }
        tts = TextToSpeech(app) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val t = tts ?: return@TextToSpeech
                val res = t.setLanguage(Locale.UK)
                if (res == TextToSpeech.LANG_MISSING_DATA || res == TextToSpeech.LANG_NOT_SUPPORTED) t.setLanguage(Locale.getDefault())
                t.setPitch(0.72f)
                t.setSpeechRate(0.92f)
                ready = true
                synchronized(pending) { for (s in pending) speak(s); pending.clear() }
            }
        }
    }

    fun setEnabled(on: Boolean) { enabled = on }

    /** Muffle the caller (IN THE ZONE) or bring him back clear. */
    fun setMuffled(on: Boolean) {
        if (muffled == on) return
        muffled = on
        val e = eq ?: return
        try {
            if (on) {
                // keep the low bands, sink everything above ~600 Hz to the floor
                val floor = e.bandLevelRange[0]
                for (b in 0 until e.numberOfBands) {
                    val centreHz = e.getCenterFreq(b.toShort()) / 1000
                    e.setBandLevel(b.toShort(), if (centreHz < 600) 0 else floor)
                }
                e.enabled = true
            } else e.enabled = false
        } catch (ex: Exception) { }
    }

    private fun speak(text: String) {
        if (!enabled) return
        val t = tts ?: return
        try {
            val params = android.os.Bundle()
            if (sessionId != 0) params.putInt(TextToSpeech.Engine.KEY_PARAM_SESSION_ID, sessionId)
            if (muffled) params.putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 0.7f)
            if (muffled) t.setPitch(0.62f)
            t.speak(text, TextToSpeech.QUEUE_ADD, params, "say-${System.nanoTime()}")
            if (muffled) t.setPitch(0.72f)
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
