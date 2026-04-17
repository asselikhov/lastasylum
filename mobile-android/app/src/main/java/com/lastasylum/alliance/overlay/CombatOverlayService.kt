package com.lastasylum.alliance.overlay

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.app.NotificationCompat
import com.lastasylum.alliance.R
import com.lastasylum.alliance.di.AppContainer
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class CombatOverlayService : Service() {
    private var speechRecognizer: SpeechRecognizer? = null
    private var speechIntent: Intent? = null
    private var isRecording = false
    private var awaitingSpeechResult = false
    private var windowManager: WindowManager? = null
    private var overlayView: FrameLayout? = null
    private var overlayBubble: TextView? = null
    private var toggleView: TextView? = null
    private var toggleParams: WindowManager.LayoutParams? = null
    private val quickCommandViews = mutableListOf<TextView>()
    private var tickerView: TextView? = null
    private var tickerParams: WindowManager.LayoutParams? = null
    private val tickerHideRunnable = Runnable { hideTicker() }
    private val mainHandler = Handler(Looper.getMainLooper())
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var recordingStartRunnable: Runnable? = null
    private var overlayCollapsed = false

    override fun onCreate() {
        super.onCreate()
        initSpeechRecognizer()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification(getString(R.string.overlay_notif_combat_active)))
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(this)) {
            showOverlayControl()
        } else {
            updateNotification(getString(R.string.overlay_notif_permission_required))
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_RECORDING -> startRecording()
            ACTION_STOP_RECORDING -> stopRecording()
            ACTION_STOP_SERVICE -> stopSelf()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        stopRecording()
        speechRecognizer?.destroy()
        speechRecognizer = null
        hideTicker()
        removeOverlayControl()
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startRecording() {
        if (isRecording) return
        val recognizer = speechRecognizer
        val recognizerIntent = speechIntent
        if (recognizer == null || recognizerIntent == null) {
            updateNotification(getString(R.string.overlay_notif_speech_unavailable))
            pulseBubbleError()
            return
        }

        isRecording = true
        awaitingSpeechResult = true
        updateNotification(getString(R.string.overlay_notif_listening))
        setBubbleUi(OverlayBubbleUi.BubbleState.RECORDING, getString(R.string.overlay_bubble_recording))
        runCatching { recognizer.startListening(recognizerIntent) }.onFailure {
            isRecording = false
            awaitingSpeechResult = false
            updateNotification(getString(R.string.overlay_notif_start_recognition_failed))
            pulseBubbleError()
        }
    }

    private fun stopRecording() {
        if (!isRecording) return
        isRecording = false
        updateNotification(getString(R.string.overlay_notif_recognizing))
        setBubbleUi(OverlayBubbleUi.BubbleState.SENDING, getString(R.string.overlay_bubble_sending))
        runCatching { speechRecognizer?.stopListening() }.onFailure {
            awaitingSpeechResult = false
            updateNotification(getString(R.string.overlay_notif_recognition_failed))
            pulseBubbleError()
        }
    }

    private fun initSpeechRecognizer() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            return
        }
        val recognizer = SpeechRecognizer.createSpeechRecognizer(this)
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
                    updateNotification(getString(R.string.overlay_notif_no_speech))
                } else {
                    updateNotification(getString(R.string.overlay_notif_speech_error))
                    pulseBubbleError()
                }
                setBubbleUi(OverlayBubbleUi.BubbleState.IDLE, getString(R.string.overlay_bubble_idle))
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
                    updateNotification(getString(R.string.overlay_notif_no_speech))
                    setBubbleUi(OverlayBubbleUi.BubbleState.IDLE, getString(R.string.overlay_bubble_idle))
                    return
                }
                serviceScope.launch { publishRecognizedText(text) }
            }

            override fun onPartialResults(partialResults: android.os.Bundle?) = Unit
            override fun onEvent(eventType: Int, params: android.os.Bundle?) = Unit
        })
    }

    private suspend fun publishRecognizedText(text: String) {
        val container = AppContainer.from(this)
        container.chatRepository.sendSystemVoiceMessage(text)
            .onSuccess {
                updateNotification(getString(R.string.overlay_notif_voice_sent))
                setBubbleUi(OverlayBubbleUi.BubbleState.IDLE, getString(R.string.overlay_bubble_idle))
                showTicker(getString(R.string.overlay_ticker_voice, text))
            }
            .onFailure {
                updateNotification(getString(R.string.overlay_notif_voice_chat_failed))
                pulseBubbleError()
            }
    }

    private fun setBubbleUi(state: OverlayBubbleUi.BubbleState, label: String) {
        mainHandler.post {
            val bubble = overlayBubble ?: return@post
            OverlayBubbleUi.applyBubbleStyle(this, bubble, state)
            bubble.text = label
        }
    }

    private fun pulseBubbleError() {
        setBubbleUi(OverlayBubbleUi.BubbleState.ERROR, "!")
        mainHandler.postDelayed(
            {
                setBubbleUi(OverlayBubbleUi.BubbleState.IDLE, getString(R.string.overlay_bubble_idle))
            },
            1400L,
        )
    }

    private fun createNotificationChannel() {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.overlay_channel_name),
                NotificationManager.IMPORTANCE_LOW,
            )
            manager.createNotificationChannel(channel)
        }
    }

    private fun updateNotification(content: String) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, buildNotification(content))
    }

    private fun buildNotification(content: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle(getString(R.string.overlay_notif_title))
            .setContentText(content)
            .setOngoing(true)
            .build()
    }

    private fun showOverlayControl() {
        if (overlayView != null) return
        val manager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            android.graphics.PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = dp(18)
            y = dp(380)
        }

        var initialX = 0
        var initialY = 0
        var startTouchX = 0f
        var startTouchY = 0f
        var isDragging = false
        var startedRecording = false
        val dragThreshold = dp(14)
        val pressDelayMs = 150L

        val bubble = TextView(this).apply {
            text = getString(R.string.overlay_bubble_idle)
            gravity = Gravity.CENTER
            maxLines = 2
            OverlayBubbleUi.applyBubbleStyle(
                this@CombatOverlayService,
                this,
                OverlayBubbleUi.BubbleState.IDLE,
            )
            setOnTouchListener { view, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        hideQuickCommands()
                        initialX = layoutParams.x
                        initialY = layoutParams.y
                        startTouchX = event.rawX
                        startTouchY = event.rawY
                        isDragging = false
                        startedRecording = false
                        val delayedStart = Runnable {
                            startedRecording = true
                            startRecording()
                        }
                        recordingStartRunnable = delayedStart
                        mainHandler.postDelayed(delayedStart, pressDelayMs)
                        true
                    }

                    MotionEvent.ACTION_MOVE -> {
                        val deltaX = (event.rawX - startTouchX).toInt()
                        val deltaY = (event.rawY - startTouchY).toInt()
                        if (!isDragging &&
                            (kotlin.math.abs(deltaX) > dragThreshold ||
                                kotlin.math.abs(deltaY) > dragThreshold)
                        ) {
                            isDragging = true
                            recordingStartRunnable?.let { mainHandler.removeCallbacks(it) }
                            recordingStartRunnable = null
                            if (startedRecording) {
                                stopRecording()
                                startedRecording = false
                            }
                        }

                        if (isDragging) {
                            val screenWidth = resources.displayMetrics.widthPixels
                            val screenHeight = resources.displayMetrics.heightPixels
                            val nextX = (initialX + deltaX).coerceIn(dp(8), screenWidth - dp(72))
                            val nextY = (initialY + deltaY).coerceIn(dp(100), screenHeight - dp(180))
                            layoutParams.x = nextX
                            layoutParams.y = nextY
                            manager.updateViewLayout(view, layoutParams)
                            syncTickerPosition(nextX, nextY)
                        }
                        true
                    }

                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        recordingStartRunnable?.let { mainHandler.removeCallbacks(it) }
                        recordingStartRunnable = null
                        if (!isDragging && startedRecording) {
                            stopRecording()
                        } else if (!isDragging) {
                            toggleQuickCommands(layoutParams.x, layoutParams.y)
                        } else if (isDragging) {
                            snapBubbleToSide(manager, view, layoutParams)
                        }
                        startedRecording = false
                        true
                    }

                    else -> false
                }
            }
        }

        overlayBubble = bubble
        overlayView = FrameLayout(this).apply {
            addView(bubble)
        }
        manager.addView(overlayView, layoutParams)
        windowManager = manager
        ensureToggleButton()
        ensureTicker()
        syncTickerPosition(layoutParams.x, layoutParams.y)
    }

    private fun ensureToggleButton() {
        if (toggleView != null) return
        val manager = windowManager ?: return
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            android.graphics.PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = dp(10)
            y = dp(86)
        }

        val toggle = TextView(this).apply {
            text = getString(R.string.overlay_toggle_hide)
            OverlayTickerUi.applyToggleChipStyle(this@CombatOverlayService, this)
            setOnClickListener {
                overlayCollapsed = !overlayCollapsed
                applyOverlayVisibilityState()
            }
        }

        toggleView = toggle
        toggleParams = params
        manager.addView(toggle, params)
    }

    private fun applyOverlayVisibilityState() {
        val bubbleContainer = overlayView
        val toggle = toggleView
        if (overlayCollapsed) {
            hideQuickCommands()
            hideTicker()
            bubbleContainer?.animate()?.cancel()
            bubbleContainer?.visibility = View.VISIBLE
            bubbleContainer?.alpha = 0.5f
            bubbleContainer?.scaleX = 0.88f
            bubbleContainer?.scaleY = 0.88f
            toggle?.text = getString(R.string.overlay_toggle_show)
            toggle?.animate()?.alpha(1f)?.setDuration(120L)?.start()
        } else {
            bubbleContainer?.animate()?.cancel()
            bubbleContainer?.visibility = View.VISIBLE
            bubbleContainer?.alpha = 1f
            bubbleContainer?.scaleX = 1f
            bubbleContainer?.scaleY = 1f
            toggle?.text = getString(R.string.overlay_toggle_hide)
            toggle?.animate()?.alpha(0.92f)?.setDuration(120L)?.start()
        }
    }

    private fun toggleQuickCommands(bubbleX: Int, bubbleY: Int) {
        if (quickCommandViews.isNotEmpty()) {
            hideQuickCommands()
            return
        }
        showQuickCommands(bubbleX, bubbleY)
    }

    private fun showQuickCommands(bubbleX: Int, bubbleY: Int) {
        val manager = windowManager ?: return
        data class QuickCommand(
            val label: String,
            val text: String,
            val style: OverlayBubbleUi.BubbleState,
        )

        val commands = listOf(
            QuickCommand(
                getString(R.string.overlay_cmd_assembly_label),
                getString(R.string.overlay_cmd_assembly_text),
                OverlayBubbleUi.BubbleState.IDLE,
            ),
            QuickCommand(
                getString(R.string.overlay_cmd_focus_label),
                getString(R.string.overlay_cmd_focus_text),
                OverlayBubbleUi.BubbleState.RECORDING,
            ),
            QuickCommand(
                getString(R.string.overlay_cmd_help_label),
                getString(R.string.overlay_cmd_help_text),
                OverlayBubbleUi.BubbleState.ERROR,
            ),
            QuickCommand(
                getString(R.string.overlay_cmd_stand_down_label),
                getString(R.string.overlay_cmd_stand_down_text),
                OverlayBubbleUi.BubbleState.SENDING,
            ),
        )
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val screenWidth = resources.displayMetrics.widthPixels
        val sideIsLeft = bubbleX < screenWidth / 2
        val xDirection = if (sideIsLeft) 1 else -1
        val offsets = listOf(
            Pair(0, -dp(88)),
            Pair(xDirection * dp(72), -dp(34)),
            Pair(xDirection * dp(72), dp(34)),
            Pair(0, dp(88)),
        )

        commands.forEachIndexed { index, command ->
            val action = TextView(this).apply {
                text = command.label
                OverlayBubbleUi.applyQuickCommandStyle(
                    this@CombatOverlayService,
                    this,
                    command.style,
                )
                alpha = 0f
                scaleX = 0.84f
                scaleY = 0.84f
                setOnClickListener {
                    serviceScope.launch {
                        val result = AppContainer.from(this@CombatOverlayService)
                            .chatRepository
                            .sendSystemVoiceMessage(command.text)
                        mainHandler.post {
                            if (result.isSuccess) {
                                showTicker(command.text)
                            } else {
                                pulseBubbleError()
                            }
                        }
                    }
                    hideQuickCommands()
                }
            }
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                android.graphics.PixelFormat.TRANSLUCENT,
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                x = bubbleX + offsets[index].first
                y = bubbleY + offsets[index].second
            }
            manager.addView(action, params)
            action.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .setStartDelay(index * 22L)
                .setDuration(150L)
                .start()
            quickCommandViews.add(action)
        }
    }

    private fun hideQuickCommands() {
        val manager = windowManager ?: return
        if (quickCommandViews.isEmpty()) return
        quickCommandViews.forEach { view ->
            view.animate().cancel()
            runCatching { manager.removeView(view) }
        }
        quickCommandViews.clear()
    }

    private fun ensureTicker() {
        if (tickerView != null) return
        val manager = windowManager ?: return
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val screenWidth = resources.displayMetrics.widthPixels
        val horizontalMargin = dp(10)
        val params = WindowManager.LayoutParams(
            (screenWidth - horizontalMargin * 2).coerceAtLeast(dp(180)),
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            android.graphics.PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = horizontalMargin
            y = dp(110)
        }

        val ticker = TextView(this).apply {
            text = ""
            alpha = 0f
            OverlayTickerUi.applyTickerStyle(this@CombatOverlayService, this)
        }

        tickerView = ticker
        tickerParams = params
        manager.addView(ticker, params)
    }

    private fun showTicker(message: String) {
        mainHandler.post {
            ensureTicker()
            val ticker = tickerView ?: return@post
            ticker.text = message
            ticker.animate().alpha(1f).setDuration(160).start()
            mainHandler.removeCallbacks(tickerHideRunnable)
            mainHandler.postDelayed(tickerHideRunnable, 5000L)
        }
    }

    private fun hideTicker() {
        mainHandler.post {
            val manager = windowManager ?: return@post
            val ticker = tickerView ?: return@post
            runCatching { manager.removeView(ticker) }
            tickerView = null
            tickerParams = null
            mainHandler.removeCallbacks(tickerHideRunnable)
        }
    }

    private fun syncTickerPosition(bubbleX: Int, bubbleY: Int) {
        val manager = windowManager ?: return
        val params = tickerParams ?: return
        val ticker = tickerView ?: return
        params.x = dp(10)
        params.y = dp(110)
        runCatching { manager.updateViewLayout(ticker, params) }
    }

    private fun snapBubbleToSide(
        manager: WindowManager,
        view: android.view.View,
        params: WindowManager.LayoutParams,
    ) {
        val screenWidth = resources.displayMetrics.widthPixels
        val screenHeight = resources.displayMetrics.heightPixels
        val leftX = dp(10)
        val rightX = screenWidth - dp(72)
        val slotTop = dp(160)
        val slotMid = screenHeight / 2
        val slotBottom = (screenHeight - dp(230)).coerceAtLeast(slotTop + dp(120))
        val ySlots = listOf(slotTop, slotMid, slotBottom)
        val targetY = ySlots.minByOrNull { kotlin.math.abs(it - params.y) } ?: params.y
        params.x = if (params.x < screenWidth / 2) leftX else rightX
        params.y = targetY
        runCatching { manager.updateViewLayout(view, params) }
        hideQuickCommands()
        syncTickerPosition(params.x, params.y)
    }

    private fun dp(value: Int): Int {
        val density = resources.displayMetrics.density
        return (value * density).toInt()
    }

    private fun removeOverlayControl() {
        hideTicker()
        hideQuickCommands()
        removeToggleButton()
        val manager = windowManager ?: return
        val view = overlayView ?: return
        runCatching {
            manager.removeView(view)
        }
        overlayView = null
        overlayBubble = null
        windowManager = null
    }

    private fun removeToggleButton() {
        val manager = windowManager ?: return
        val toggle = toggleView ?: return
        runCatching { manager.removeView(toggle) }
        toggleView = null
        toggleParams = null
    }

    companion object {
        private const val CHANNEL_ID = "combat_overlay_channel"
        private const val NOTIFICATION_ID = 7001

        const val ACTION_START_RECORDING = "com.squadrelay.action.START_RECORDING"
        const val ACTION_STOP_RECORDING = "com.squadrelay.action.STOP_RECORDING"
        const val ACTION_STOP_SERVICE = "com.squadrelay.action.STOP_SERVICE"

        fun startService(context: Context) {
            val intent = Intent(context, CombatOverlayService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopService(context: Context) {
            val intent = Intent(context, CombatOverlayService::class.java).apply {
                action = ACTION_STOP_SERVICE
            }
            context.startService(intent)
        }

        fun startRecording(context: Context) {
            val intent = Intent(context, CombatOverlayService::class.java).apply {
                action = ACTION_START_RECORDING
            }
            context.startService(intent)
        }

        fun stopRecording(context: Context) {
            val intent = Intent(context, CombatOverlayService::class.java).apply {
                action = ACTION_STOP_RECORDING
            }
            context.startService(intent)
        }
    }
}
