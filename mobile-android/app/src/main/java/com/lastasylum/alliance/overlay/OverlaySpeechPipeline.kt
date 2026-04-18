package com.lastasylum.alliance.overlay

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import com.lastasylum.alliance.R
import com.lastasylum.alliance.data.chat.ChatMessage
import com.lastasylum.alliance.di.AppContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * SpeechRecognizer lifecycle and routing recognized text to chat.
 */
class OverlaySpeechPipeline(
    private val context: Context,
    private val mainHandler: Handler,
    private val scope: CoroutineScope,
    private val applyBubbleState: (OverlayBubbleUi.BubbleState) -> Unit,
    private val notify: (String) -> Unit,
    private val pulseBubbleError: () -> Unit,
    private val onVoiceSent: (recognizedText: String, sent: ChatMessage) -> Unit,
    private val onVoiceSendFailed: (noRoom: Boolean) -> Unit,
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
                    notify(context.getString(R.string.overlay_notif_no_speech))
                } else {
                    notify(context.getString(R.string.overlay_notif_speech_error))
                    pulseBubbleError()
                }
                applyBubbleState(OverlayBubbleUi.BubbleState.IDLE)
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
                    notify(context.getString(R.string.overlay_notif_no_speech))
                    applyBubbleState(OverlayBubbleUi.BubbleState.IDLE)
                    return
                }
                scope.launch { publishRecognizedText(text) }
            }

            override fun onPartialResults(partialResults: android.os.Bundle?) = Unit
            override fun onEvent(eventType: Int, params: android.os.Bundle?) = Unit
        })
    }

    private suspend fun publishRecognizedText(text: String) {
        val container = AppContainer.from(context)
        container.chatRepository.sendSystemVoiceMessage(text)
            .onSuccess { sent ->
                mainHandler.post {
                    onVoiceSent(text, sent)
                }
            }
            .onFailure {
                val noRoom = it.message == "no_room"
                mainHandler.post {
                    onVoiceSendFailed(noRoom)
                }
            }
    }

    fun startRecording() {
        if (isRecording) return
        val recognizer = speechRecognizer
        val recognizerIntent = speechIntent
        if (recognizer == null || recognizerIntent == null) {
            notify(context.getString(R.string.overlay_notif_speech_unavailable))
            pulseBubbleError()
            return
        }

        isRecording = true
        awaitingSpeechResult = true
        notify(context.getString(R.string.overlay_notif_listening))
        applyBubbleState(OverlayBubbleUi.BubbleState.RECORDING)
        runCatching { recognizer.startListening(recognizerIntent) }.onFailure {
            isRecording = false
            awaitingSpeechResult = false
            notify(context.getString(R.string.overlay_notif_start_recognition_failed))
            pulseBubbleError()
        }
    }

    fun stopRecording() {
        if (!isRecording) return
        isRecording = false
        notify(context.getString(R.string.overlay_notif_recognizing))
        applyBubbleState(OverlayBubbleUi.BubbleState.SENDING)
        runCatching { speechRecognizer?.stopListening() }.onFailure {
            awaitingSpeechResult = false
            notify(context.getString(R.string.overlay_notif_recognition_failed))
            pulseBubbleError()
        }
    }

    fun destroy() {
        stopRecording()
        speechRecognizer?.destroy()
        speechRecognizer = null
        speechIntent = null
    }
}
