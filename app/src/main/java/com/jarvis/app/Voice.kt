package com.jarvis.app

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale

/** Sunna (SpeechRecognizer) aur bolna (TextToSpeech). Sab main thread par chalta hai. */
class Voice(
    private val ctx: Context,
    private val lang: () -> String,
    private val rate: () -> Float
) {
    private val main = Handler(Looper.getMainLooper())
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var rec: SpeechRecognizer? = null

    init {
        tts = TextToSpeech(ctx.applicationContext) { ttsReady = (it == TextToSpeech.SUCCESS) }
    }

    fun listen(onState: (String) -> Unit, onResult: (String) -> Unit) {
        main.post {
            tts?.stop()
            if (!SpeechRecognizer.isRecognitionAvailable(ctx)) {
                onState("error")
                onResult("")
                return@post
            }
            rec?.destroy()
            val r = SpeechRecognizer.createSpeechRecognizer(ctx)
            rec = r
            r.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) { onState("listening") }
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {}
                override fun onPartialResults(partialResults: Bundle?) {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
                override fun onError(error: Int) { onResult("") }
                override fun onResults(results: Bundle?) {
                    val t = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull() ?: ""
                    onResult(t)
                }
            })
            val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, lang())
            }
            r.startListening(i)
        }
    }

    fun stopListening() { main.post { rec?.stopListening() } }

    fun speak(text: String, finished: () -> Unit) {
        main.post {
            val t = tts
            if (t == null || !ttsReady) {
                finished()
                return@post
            }
            t.setLanguage(Locale.forLanguageTag(lang()))
            t.setSpeechRate(rate())
            t.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {}
                override fun onDone(utteranceId: String?) { main.post { finished() } }
                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) { main.post { finished() } }
            })
            t.speak(text, TextToSpeech.QUEUE_FLUSH, null, "jarvis")
        }
    }

    fun stopSpeaking() { main.post { tts?.stop() } }

    fun destroy() {
        main.post {
            rec?.destroy()
            tts?.shutdown()
        }
    }
}
