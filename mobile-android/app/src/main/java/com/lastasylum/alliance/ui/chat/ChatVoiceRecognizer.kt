package com.lastasylum.alliance.ui.chat

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import com.lastasylum.alliance.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Распознавание речи на устройстве (как в оверлее): [startRecording] / [stopListening] → текст в чат.
 */
class ChatVoiceRecognizer(
    private val context: Context,
    private val mainHandler: Handler,
    private val scope: CoroutineScope,
    private val setPhase: (ChatVoicePhase) -> Unit,
    private val onRecognizedText: (String) -> Unit,
    private val onNotify: (String) -> Unit,
) {
    private var speechRecognizer: SpeechRecognizer? = null
    private var speechIntent: Intent? = null
    private var isRecording = false
    private var awaitingSpeechResult = false

    fun initIfAvailable() {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            return
        }
        val recognizer = SpeechRecognizer.createSpeechRecognizer(context)
        speechRecognizer = recognizer
        speechIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ru-RU")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
        }
        recognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: android.os.Bundle?) = Unit
            override fun onBeginningOfSpeech() = Unit
            override fun onRmsChanged(rmsdB: Float) = Unit
            override fun onBufferReceived(buffer: ByteArray?) = Unit
            override fun onEndOfSpeech() = Unit

            override fun onError(error: Int) {
                if (!awaitingSpeechResult) return
                awaitingSpeechResult = false
                val benign = error == SpeechRecognizer.ERROR_NO_MATCH ||
                    error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT ||
                    error == SpeechRecognizer.ERROR_CLIENT
                if (benign) {
                    onNotify(context.getString(R.string.overlay_notif_no_speech))
                } else {
                    onNotify(context.getString(R.string.overlay_notif_speech_error))
                }
                mainHandler.post { setPhase(ChatVoicePhase.Idle) }
            }

            override fun onResults(results: android.os.Bundle?) {
                if (!awaitingSpeechResult) return
                awaitingSpeechResult = false
                val text = results
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()
                    ?.trim()
                    .orEmpty()
                if (text.isBlank()) {
                    onNotify(context.getString(R.string.overlay_notif_no_speech))
                    mainHandler.post { setPhase(ChatVoicePhase.Idle) }
                    return
                }
                scope.launch {
                    withContext(Dispatchers.Main) {
                        onRecognizedText(text)
                        setPhase(ChatVoicePhase.Idle)
                    }
                }
            }

            override fun onPartialResults(partialResults: android.os.Bundle?) = Unit
            override fun onEvent(eventType: Int, params: android.os.Bundle?) = Unit
        })
    }

    fun startRecording() {
        if (isRecording) return
        val recognizer = speechRecognizer
        val recognizerIntent = speechIntent
        if (recognizer == null || recognizerIntent == null) {
            onNotify(context.getString(R.string.overlay_notif_speech_unavailable))
            mainHandler.post { setPhase(ChatVoicePhase.Idle) }
            return
        }

        isRecording = true
        awaitingSpeechResult = true
        onNotify(context.getString(R.string.overlay_notif_listening))
        mainHandler.post { setPhase(ChatVoicePhase.Listening) }
        runCatching { recognizer.startListening(recognizerIntent) }.onFailure {
            isRecording = false
            awaitingSpeechResult = false
            onNotify(context.getString(R.string.overlay_notif_start_recognition_failed))
            mainHandler.post { setPhase(ChatVoicePhase.Idle) }
        }
    }

    fun stopRecording() {
        if (!isRecording) return
        isRecording = false
        onNotify(context.getString(R.string.overlay_notif_recognizing))
        mainHandler.post { setPhase(ChatVoicePhase.Sending) }
        runCatching { speechRecognizer?.stopListening() }.onFailure {
            awaitingSpeechResult = false
            onNotify(context.getString(R.string.overlay_notif_recognition_failed))
            mainHandler.post { setPhase(ChatVoicePhase.Idle) }
        }
    }

    fun destroy() {
        stopRecording()
        speechRecognizer?.destroy()
        speechRecognizer = null
        speechIntent = null
        mainHandler.post { setPhase(ChatVoicePhase.Idle) }
    }
}
