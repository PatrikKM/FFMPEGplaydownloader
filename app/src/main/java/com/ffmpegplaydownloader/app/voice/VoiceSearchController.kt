package com.ffmpegplaydownloader.app.voice

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.core.content.ContextCompat

/**
 * Safe on-device Android speech controller wrapping [SpeechRecognizer] for inline voice input
 * with [RecognizerIntent.ACTION_RECOGNIZE_SPEECH] helpers as system fallback.
 */
class VoiceSearchController(
    context: Context,
    private val onReady: () -> Unit = {},
    private val onRmsChanged: (Float) -> Unit = {},
    private val onPartialResult: (String) -> Unit = {},
    private val onFinalResult: (String) -> Unit = {},
    private val onError: (message: String, shouldFallbackToSystemIntent: Boolean) -> Unit = { _, _ -> }
) {
    private val appContext: Context = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())

    private var speechRecognizer: SpeechRecognizer? = null
    private var isDestroyed = false
    private var isListening = false
    private var deliveredFinalOrError = false

    fun startListening(languageTag: String = "cs-CZ") {
        runOnMain {
            if (isDestroyed) return@runOnMain

            if (!hasMicPermission(appContext)) {
                isListening = false
                deliveredFinalOrError = true
                onError("Chybí oprávnění k použití mikrofonu.", false)
                return@runOnMain
            }

            if (!isDirectSpeechAvailable(appContext)) {
                isListening = false
                deliveredFinalOrError = true
                onError("Přímé hlasové rozpoznávání není na tomto zařízení dostupné.", true)
                return@runOnMain
            }

            releaseRecognizerInternal()

            val recognizer = try {
                SpeechRecognizer.createSpeechRecognizer(appContext)
            } catch (_: Throwable) {
                null
            }

            if (recognizer == null) {
                isListening = false
                deliveredFinalOrError = true
                onError("Nelze připojit službu rozpoznávání řeči.", true)
                return@runOnMain
            }

            speechRecognizer = recognizer
            deliveredFinalOrError = false
            isListening = true

            recognizer.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    if (!isDestroyed && isListening) {
                        onReady()
                    }
                }

                override fun onBeginningOfSpeech() = Unit

                override fun onRmsChanged(rmsdB: Float) {
                    if (!isDestroyed && isListening) {
                        this@VoiceSearchController.onRmsChanged(rmsdB)
                    }
                }

                override fun onBufferReceived(buffer: ByteArray?) = Unit

                override fun onEndOfSpeech() = Unit

                override fun onError(error: Int) {
                    if (isDestroyed || !isListening || deliveredFinalOrError) return
                    deliveredFinalOrError = true
                    isListening = false
                    val (message, shouldFallback) = mapError(error)
                    this@VoiceSearchController.onError(message, shouldFallback)
                }

                override fun onResults(results: Bundle?) {
                    if (isDestroyed || deliveredFinalOrError) return
                    deliveredFinalOrError = true
                    isListening = false
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val best = matches?.firstOrNull { !it.isNullOrBlank() }?.trim()
                    if (best.isNullOrBlank()) {
                        onError("Nepodařilo se rozpoznat řeč. Zkuste to prosím znovu.", false)
                    } else {
                        onFinalResult(best)
                    }
                }

                override fun onPartialResults(partialResults: Bundle?) {
                    if (isDestroyed || !isListening || deliveredFinalOrError) return
                    val partial = partialResults
                        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull { !it.isNullOrBlank() }
                        ?.trim()
                    if (!partial.isNullOrBlank()) {
                        onPartialResult(partial)
                    }
                }

                override fun onEvent(eventType: Int, params: Bundle?) = Unit
            })

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                )
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageTag)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, languageTag)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, appContext.packageName)
            }

            try {
                recognizer.startListening(intent)
            } catch (_: Throwable) {
                isListening = false
                deliveredFinalOrError = true
                releaseRecognizerInternal()
                onError("Nepodařilo se spustit přímý mikrofon.", true)
            }
        }
    }

    fun stopListening() {
        runOnMain {
            if (!isDestroyed && isListening) {
                try {
                    speechRecognizer?.stopListening()
                } catch (_: Throwable) {
                }
            }
        }
    }

    fun cancel() {
        runOnMain {
            isListening = false
            deliveredFinalOrError = true
            try {
                speechRecognizer?.cancel()
            } catch (_: Throwable) {
            }
        }
    }

    fun destroy() {
        runOnMain {
            isDestroyed = true
            isListening = false
            deliveredFinalOrError = true
            releaseRecognizerInternal()
        }
    }

    private fun releaseRecognizerInternal() {
        val current = speechRecognizer ?: return
        speechRecognizer = null
        try {
            current.cancel()
        } catch (_: Throwable) {
        }
        try {
            current.destroy()
        } catch (_: Throwable) {
        }
    }

    private fun runOnMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            block()
        } else {
            mainHandler.post(block)
        }
    }

    private fun mapError(errorCode: Int): Pair<String, Boolean> = when (errorCode) {
        SpeechRecognizer.ERROR_NETWORK_TIMEOUT ->
            "Vypršel časový limit sítě pro rozpoznávání hlasu." to false
        SpeechRecognizer.ERROR_NETWORK ->
            "Chyba sítě při rozpoznávání hlasu." to false
        SpeechRecognizer.ERROR_AUDIO ->
            "Chyba záznamu zvuku z mikrofonu." to false
        SpeechRecognizer.ERROR_SERVER ->
            "Chyba serveru rozpoznávání řeči." to true
        SpeechRecognizer.ERROR_CLIENT ->
            "Přímé rozpoznávání hlasu není připraveno." to true
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT ->
            "Nebyla zachycena žádná řeč. Zkuste to znovu." to false
        SpeechRecognizer.ERROR_NO_MATCH ->
            "Nepodařilo se rozpoznat řeč. Zkuste mluvit zřetelněji." to false
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY ->
            "Hlasová služba je momentálně zaneprázdněna." to true
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS ->
            "Chybí oprávnění k použití mikrofonu." to false
        10 -> // SpeechRecognizer.ERROR_TOO_MANY_REQUESTS (API 31+)
            "Příliš mnoho požadavků na rozpoznávání hlasu." to true
        11 -> // SpeechRecognizer.ERROR_SERVER_DISCONNECTED (API 31+)
            "Služba rozpoznávání řeči byla odpojena." to true
        12 -> // SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED (API 31+)
            "Požadovaný jazyk není v přímém rozpoznávání podporován." to true
        13 -> // SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE (API 31+)
            "Jazykový balíček pro rozpoznávání není dostupný." to true
        14 -> // SpeechRecognizer.ERROR_CANNOT_CHECK_SUPPORT (API 33+)
            "Nelze ověřit podporu hlasového rozpoznávání." to true
        15 -> // SpeechRecognizer.ERROR_CANNOT_LISTEN_TO_DOWNLOAD_EVENTS (API 34+)
            "Hlasová služba je dočasně nedostupná." to true
        else ->
            "Hlasové rozpoznávání selhalo (kód $errorCode)." to true
    }

    companion object {
        fun hasMicPermission(context: Context): Boolean =
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED

        fun isDirectSpeechAvailable(context: Context): Boolean =
            try {
                SpeechRecognizer.isRecognitionAvailable(context)
            } catch (_: Throwable) {
                false
            }

        fun createFallbackRecognizerIntent(
            prompt: String = "Řekněte přirozeným jazykem, co chcete najít nebo přehrát...",
            languageTag: String = "cs-CZ"
        ): Intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageTag)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, languageTag)
            putExtra(RecognizerIntent.EXTRA_PROMPT, prompt)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
        }

        fun extractFallbackResult(data: Intent?): String? =
            data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                ?.firstOrNull { !it.isNullOrBlank() }
                ?.trim()
    }
}
