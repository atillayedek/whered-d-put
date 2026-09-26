package com.wheredidiputit.presentation.remember

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext

enum class SpeechError { NO_MATCH, NO_PERMISSION, UNAVAILABLE }

/**
 * Thin wrapper around the platform [SpeechRecognizer]. Must be used from the
 * main thread (Compose callbacks already are). Any failure is reported through
 * [onFailure]; the form keeps working for manual entry.
 */
class SpeechCapture internal constructor(
    private val context: Context,
    private val onResult: (String) -> Unit,
    private val onFailure: (SpeechError) -> Unit,
) : RecognitionListener {

    val isAvailable: Boolean = SpeechRecognizer.isRecognitionAvailable(context)

    var isListening by mutableStateOf(false)
        private set

    var partialText by mutableStateOf("")
        private set

    private var recognizer: SpeechRecognizer? = null

    fun start() {
        if (!isAvailable) {
            onFailure(SpeechError.UNAVAILABLE)
            return
        }
        release()
        val created = try {
            SpeechRecognizer.createSpeechRecognizer(context)
        } catch (e: RuntimeException) {
            onFailure(SpeechError.UNAVAILABLE)
            return
        }
        recognizer = created
        created.setRecognitionListener(this)
        partialText = ""
        isListening = true
        created.startListening(
            Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, context.resources.configuration.locales[0].toLanguageTag())
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            },
        )
    }

    /** Stops listening; whatever was heard so far is delivered as the result. */
    fun stop() {
        recognizer?.stopListening()
    }

    fun release() {
        recognizer?.destroy()
        recognizer = null
        isListening = false
    }

    override fun onResults(results: Bundle?) {
        val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
        release()
        if (text.isNullOrBlank()) onFailure(SpeechError.NO_MATCH) else onResult(text)
    }

    override fun onPartialResults(partialResults: Bundle?) {
        partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
            ?.takeIf { it.isNotBlank() }
            ?.let { partialText = it }
    }

    override fun onError(error: Int) {
        release()
        onFailure(
            when (error) {
                SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> SpeechError.NO_MATCH
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> SpeechError.NO_PERMISSION
                else -> SpeechError.UNAVAILABLE
            },
        )
    }

    override fun onReadyForSpeech(params: Bundle?) = Unit
    override fun onBeginningOfSpeech() = Unit
    override fun onRmsChanged(rmsdB: Float) = Unit
    override fun onBufferReceived(buffer: ByteArray?) = Unit
    override fun onEndOfSpeech() = Unit
    override fun onEvent(eventType: Int, params: Bundle?) = Unit
}

@Composable
fun rememberSpeechCapture(
    onResult: (String) -> Unit,
    onError: (SpeechError) -> Unit,
): SpeechCapture {
    val context = LocalContext.current
    val currentOnResult by rememberUpdatedState(onResult)
    val currentOnError by rememberUpdatedState(onError)
    val capture = remember(context) {
        SpeechCapture(
            context = context,
            onResult = { currentOnResult(it) },
            onFailure = { currentOnError(it) },
        )
    }
    DisposableEffect(capture) {
        onDispose { capture.release() }
    }
    return capture
}
