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
import android.view.ViewConfiguration
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.ViewGroup
import android.util.Base64
import android.widget.ImageView
import androidx.core.content.ContextCompat
import androidx.core.app.NotificationCompat
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.lastasylum.alliance.R
import com.lastasylum.alliance.data.chat.ChatMessage
import com.lastasylum.alliance.di.AppContainer
import com.lastasylum.alliance.ui.util.toUserMessageRu
import org.json.JSONObject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.temporal.ChronoUnit

class CombatOverlayService : Service() {
    private var speechRecognizer: SpeechRecognizer? = null
    private var speechIntent: Intent? = null
    private var isRecording = false
    private var awaitingSpeechResult = false
    private var windowManager: WindowManager? = null
    private var overlayView: FrameLayout? = null
    private var overlayBubble: FrameLayout? = null
    private var toggleView: FloatingActionButton? = null
    private var toggleParams: WindowManager.LayoutParams? = null
    private var lockView: FloatingActionButton? = null
    private var lockParams: WindowManager.LayoutParams? = null
    private val quickCommandViews = mutableListOf<TextView>()
    private var tickerView: TextView? = null
    private var tickerParams: WindowManager.LayoutParams? = null
    private val tickerHideRunnable = Runnable { hideTicker() }
    private val mainHandler = Handler(Looper.getMainLooper())
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var recordingStartRunnable: Runnable? = null
    private var overlayCollapsed = false
    private var chatStripScroll: ScrollView? = null
    private var chatStripLines: LinearLayout? = null
    private var overlayMessageListener: ((ChatMessage) -> Unit)? = null

    /** Буфер для ленты: до [STRIP_BUFFER_CAP], фильтр по времени в [refreshOverlayChatStrip]. */
    private val stripMessageBuffer = mutableListOf<ChatMessage>()
    private val messageReceivedAt = mutableMapOf<String, Instant>()
    private var overlayHistoryRoot: FrameLayout? = null
    private var overlayHistoryScroll: ScrollView? = null
    private var overlayHistoryLines: LinearLayout? = null
    private var overlayHistoryParams: WindowManager.LayoutParams? = null
    private var overlayHistoryFab: FloatingActionButton? = null
    private var overlayHistoryVisible = false
    private var overlayHistoryInput: com.google.android.material.textfield.TextInputEditText? = null
    private var overlayHistorySend: FloatingActionButton? = null
    private var overlayHistoryStatus: TextView? = null
    private val overlayHistoryDedupeIds = mutableSetOf<String>()

    private val stripTickRunnable = Runnable {
        pruneStripBuffer()
        refreshOverlayChatStrip()
        scheduleStripTick()
    }

    override fun onCreate() {
        super.onCreate()
        initSpeechRecognizer()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification(getString(R.string.overlay_notif_combat_active)))
        ensureOverlayIfPermitted()
        isServiceInstanceActive = true
    }

    /** Показать оверлей при наличии разрешения (в том числе поверх других приложений и игр). */
    private fun ensureOverlayIfPermitted() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            updateNotification(getString(R.string.overlay_notif_permission_required))
            return
        }
        if (overlayView == null) {
            showOverlayControl()
        }
    }

    /** Порог «это перетаскивание, не тап»: чуть ниже системного slop, чтобы FAB не «залипали». */
    private fun dragSlopPx(): Int {
        val slop = ViewConfiguration.get(this).scaledTouchSlop
        return (slop * 2 / 3).coerceIn(dp(3), dp(12))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP_SERVICE -> {
                stopSelf()
                return START_STICKY
            }
            ACTION_REBUILD_OVERLAY -> {
                if (overlayView != null) {
                    removeOverlayControl()
                }
                ensureOverlayIfPermitted()
                return START_STICKY
            }
            ACTION_REFRESH_NOTIFICATION -> {
                updateNotification(getString(R.string.overlay_notif_combat_active))
                return START_STICKY
            }
            else -> {
                ensureOverlayIfPermitted()
                when (intent?.action) {
                    ACTION_START_RECORDING -> startRecording()
                    ACTION_STOP_RECORDING -> stopRecording()
                }
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        isServiceInstanceActive = false
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
        setBubbleUi(OverlayBubbleUi.BubbleState.RECORDING)
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
        setBubbleUi(OverlayBubbleUi.BubbleState.SENDING)
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
                setBubbleUi(OverlayBubbleUi.BubbleState.IDLE)
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
                    setBubbleUi(OverlayBubbleUi.BubbleState.IDLE)
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
            .onSuccess { sent ->
                mainHandler.post {
                    applyLocalSentMessageToStrip(sent)
                    updateNotification(getString(R.string.overlay_notif_voice_sent))
                    setBubbleUi(OverlayBubbleUi.BubbleState.IDLE)
                    showTicker(getString(R.string.overlay_ticker_voice, text))
                }
            }
            .onFailure {
                val msg = if (it.message == "no_room") {
                    getString(R.string.err_chat_no_room)
                } else {
                    getString(R.string.overlay_notif_voice_chat_failed)
                }
                updateNotification(msg)
                pulseBubbleError()
            }
    }

    private fun setBubbleUi(state: OverlayBubbleUi.BubbleState) {
        mainHandler.post {
            val bubble = overlayBubble ?: return@post
            val compact = AppContainer.from(this).userSettingsPreferences.isCompactOverlay()
            OverlayBubbleUi.applyBubbleStyle(this, bubble, state, compact, iconOnly = true)
        }
    }

    private fun pulseBubbleError() {
        setBubbleUi(OverlayBubbleUi.BubbleState.ERROR)
        mainHandler.postDelayed(
            {
                setBubbleUi(OverlayBubbleUi.BubbleState.IDLE)
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
        val quiet = AppContainer.from(this).userSettingsPreferences.isQuietMode()
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle(getString(R.string.overlay_notif_title))
            .setContentText(content)
            .setOngoing(true)
            .setPriority(
                if (quiet) {
                    NotificationCompat.PRIORITY_MIN
                } else {
                    NotificationCompat.PRIORITY_LOW
                },
            )
        builder.setSilent(quiet)
        return builder.build()
    }

    /**
     * Флаги для TYPE_APPLICATION_OVERLAY: полноэкранные игры и вырезы.
     * [FLAG_NOT_TOUCH_MODAL] — касания мимо пузырька уходят в игру (прозрачные области не «крадут» ввод).
     * [FLAG_LAYOUT_NO_LIMITS] — окно не обрезается по insets полноэкранного приложения.
     */
    private fun overlayWindowFlags(): Int =
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
            WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED

    /**
     * Окно полноэкранного чата: без [FLAG_NOT_FOCUSABLE], чтобы работали поле ввода и клавиатура.
     */
    private fun overlayHistoryWindowFlags(): Int =
        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
            WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED

    private fun applyOverlayHistoryLayoutCompat(params: WindowManager.LayoutParams) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            params.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
    }

    private fun applyOverlayLayoutCompat(params: WindowManager.LayoutParams) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            params.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            params.setFitInsetsTypes(0)
        }
    }

    private fun scheduleStripTick() {
        mainHandler.removeCallbacks(stripTickRunnable)
        mainHandler.postDelayed(stripTickRunnable, STRIP_TICK_MS)
    }

    private fun cancelStripTick() {
        mainHandler.removeCallbacks(stripTickRunnable)
    }

    private fun upsertStripBuffer(msg: ChatMessage) {
        val id = msg._id?.takeIf { it.isNotBlank() }
        if (id != null) {
            val i = stripMessageBuffer.indexOfFirst { it._id == id }
            if (i >= 0) {
                stripMessageBuffer[i] = msg
            } else {
                stripMessageBuffer.add(msg)
            }
        } else {
            stripMessageBuffer.add(msg)
        }
    }

    private fun pruneStripBuffer() {
        if (stripMessageBuffer.isEmpty()) return
        val cutoff = Instant.now().minus(STRIP_MAX_AGE_MINUTES, ChronoUnit.MINUTES)
        stripMessageBuffer.removeAll {
            OverlayChatTime.effectiveInstant(it, messageReceivedAt).isBefore(cutoff)
        }
        stripMessageBuffer.sortBy { OverlayChatTime.effectiveInstant(it, messageReceivedAt) }
        while (stripMessageBuffer.size > STRIP_BUFFER_CAP) {
            stripMessageBuffer.removeAt(0)
        }
    }

    private fun stripVisibleForPreview(): List<ChatMessage> {
        val cutoff = Instant.now().minus(STRIP_MAX_AGE_MINUTES, ChronoUnit.MINUTES)
        return stripMessageBuffer
            .filter { !OverlayChatTime.effectiveInstant(it, messageReceivedAt).isBefore(cutoff) }
            .sortedBy { OverlayChatTime.effectiveInstant(it, messageReceivedAt) }
            .takeLast(STRIP_MAX_MESSAGES)
    }

    private fun refreshOverlayChatStrip() {
        val lines = chatStripLines ?: return
        pruneStripBuffer()
        val selfId = jwtSubFromAccessToken()
        OverlayChatStripUi.clearLines(lines)
        for (msg in stripVisibleForPreview()) {
            OverlayChatStripUi.addLine(
                this,
                lines,
                msg.senderUsername,
                msg.text,
                msg.senderId,
                msg.senderRole,
                selfId,
                showDismiss = false,
            )
        }
        scrollChatStripToEnd()
    }

    /**
     * Исходящее сообщение по HTTP (текст из истории, голос, быстрые команды): сразу обновить ленту оверлея,
     * как только приходит ответ API (не ждать сокет). Вызывать с главного потока.
     */
    private fun applyLocalSentMessageToStrip(sent: ChatMessage) {
        messageReceivedAt[sent.stableKey()] = Instant.now()
        processOverlayChatMessage(sent)
    }

    private fun processOverlayChatMessage(msg: ChatMessage) {
        upsertStripBuffer(msg)
        val key = msg.stableKey()
        val parsed = OverlayChatTime.parseInstant(msg.createdAt)
        val cutoff = Instant.now().minus(STRIP_MAX_AGE_MINUTES, ChronoUnit.MINUTES)
        val selfId = jwtSubFromAccessToken()
        when {
            parsed == null -> messageReceivedAt.putIfAbsent(key, Instant.now())
            parsed.isBefore(cutoff) &&
                selfId != null &&
                msg.senderId == selfId -> {
                // Свои новые сообщения: если дата с сервера «старше окна», не выкидывать из превью-ленты.
                messageReceivedAt[key] = Instant.now()
            }
            else -> messageReceivedAt.putIfAbsent(key, parsed ?: Instant.now())
        }
        refreshOverlayChatStrip()
        if (overlayHistoryVisible) {
            mainHandler.post { appendOverlayHistoryIfVisible(msg) }
        }
    }

    private fun appendOverlayHistoryIfVisible(msg: ChatMessage) {
        val lines = overlayHistoryLines ?: return
        val selfId = jwtSubFromAccessToken()
        if (!OverlayChatHistoryPanel.appendIncomingMessage(
                this,
                lines,
                msg,
                selfId,
                messageReceivedAt,
                overlayHistoryDedupeIds,
            )
        ) {
            return
        }
        overlayHistoryScroll?.post {
            overlayHistoryScroll?.fullScroll(View.FOCUS_DOWN)
        }
    }

    private fun setStripPlainMessage(message: String) {
        stripMessageBuffer.clear()
        messageReceivedAt.clear()
        val lines = chatStripLines ?: return
        OverlayChatStripUi.clearLines(lines)
        OverlayChatStripUi.addNoticeLine(this, lines, message)
    }

    private fun jwtSubFromAccessToken(): String? {
        val token = runCatching { AppContainer.from(this).tokenStore.getAccessToken() }.getOrNull()
            ?: return null
        val parts = token.split('.')
        if (parts.size < 2) return null
        var payload = parts[1].replace('-', '+').replace('_', '/')
        when (payload.length % 4) {
            2 -> payload += "=="
            3 -> payload += "="
            else -> {}
        }
        return runCatching {
            val json = String(Base64.decode(payload, Base64.DEFAULT), Charsets.UTF_8)
            JSONObject(json).optString("sub", "").takeIf { it.isNotBlank() }
        }.getOrNull()
    }

    private fun scrollChatStripToEnd() {
        chatStripScroll?.post {
            chatStripScroll?.fullScroll(View.FOCUS_DOWN)
        }
    }

    private fun beginOverlayChatSubscription() {
        if (overlayMessageListener != null) return
        cancelStripTick()
        val listener: (ChatMessage) -> Unit = { msg ->
            mainHandler.post {
                val roomId = AppContainer.from(this).chatRoomPreferences.getSelectedRoomId()
                if (roomId != null && msg.roomId.isNotBlank() && msg.roomId != roomId) {
                    return@post
                }
                processOverlayChatMessage(msg)
            }
        }
        overlayMessageListener = listener
        mainHandler.post {
            AppContainer.from(this).chatRepository.addOverlayMessageListener(listener)
            scheduleStripTick()
        }
        serviceScope.launch {
            val container = AppContainer.from(this@CombatOverlayService)
            val roomId = container.chatRoomPreferences.getSelectedRoomId()
            if (roomId == null) {
                mainHandler.post {
                    setStripPlainMessage(getString(R.string.overlay_strip_no_room))
                }
                return@launch
            }
            container.chatRepository.loadRecentMessages(roomId, null, OVERLAY_HISTORY_LOAD)
                .onSuccess { loaded ->
                    mainHandler.post {
                        stripMessageBuffer.clear()
                        messageReceivedAt.clear()
                        loaded.forEach { m ->
                            upsertStripBuffer(m)
                            when (val p = OverlayChatTime.parseInstant(m.createdAt)) {
                                null -> messageReceivedAt.putIfAbsent(m.stableKey(), Instant.now())
                                else -> messageReceivedAt[m.stableKey()] = p
                            }
                        }
                        refreshOverlayChatStrip()
                    }
                }
                .onFailure {
                    mainHandler.post {
                        setStripPlainMessage(getString(R.string.overlay_strip_history_failed))
                    }
                }
        }
    }

    private fun endOverlayChatSubscription() {
        cancelStripTick()
        hideOverlayHistoryPanel()
        stripMessageBuffer.clear()
        messageReceivedAt.clear()
        overlayMessageListener?.let { listener ->
            runCatching {
                AppContainer.from(applicationContext).chatRepository.removeOverlayMessageListener(listener)
            }
        }
        overlayMessageListener = null
        mainHandler.post {
            chatStripLines?.let { OverlayChatStripUi.clearLines(it) }
        }
    }

    private fun showOverlayControl() {
        if (overlayView != null) return
        val compact = AppContainer.from(this).userSettingsPreferences.isCompactOverlay()
        val manager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val overlayWindowLayoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            overlayWindowFlags(),
            android.graphics.PixelFormat.TRANSLUCENT,
        ).apply {
            applyOverlayLayoutCompat(this)
            gravity = Gravity.TOP or Gravity.START
            x = dp(18)
            y = dp(280)
        }

        var initialX = 0
        var initialY = 0
        var startTouchX = 0f
        var startTouchY = 0f
        var isDragging = false
        var startedRecording = false
        val dragThreshold = dragSlopPx()
        val pressDelayMs = 100L

        val lines = OverlayChatStripUi.createLinesContainer(this@CombatOverlayService)
        val stripScroll = ScrollView(this).apply {
            OverlayChatStripUi.styleStripScroll(this@CombatOverlayService, this)
            addView(
                lines,
                ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ),
            )
        }
        val stripLines = lines
        if (compact) {
            stripScroll.visibility = View.GONE
        }

        lateinit var windowRoot: FrameLayout

        val micSize = if (compact) dp(26) else dp(30)
        val mic = ImageView(this).apply {
            setImageDrawable(
                ContextCompat.getDrawable(
                    this@CombatOverlayService,
                    R.drawable.ic_overlay_mic,
                ),
            )
            scaleType = ImageView.ScaleType.FIT_CENTER
        }
        val bubble = FrameLayout(this).apply {
            OverlayBubbleUi.applyBubbleStyle(
                this@CombatOverlayService,
                this,
                OverlayBubbleUi.BubbleState.IDLE,
                compact,
                iconOnly = true,
            )
            addView(
                mic,
                FrameLayout.LayoutParams(micSize, micSize).apply {
                    gravity = Gravity.CENTER
                },
            )
            setOnTouchListener { bubbleView, event ->
                val dragLocked = AppContainer.from(this@CombatOverlayService)
                    .userSettingsPreferences
                    .isOverlayDragLocked()
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        hideQuickCommands()
                        initialX = overlayWindowLayoutParams.x
                        initialY = overlayWindowLayoutParams.y
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

                        if (isDragging && !dragLocked) {
                            val screenWidth = resources.displayMetrics.widthPixels
                            val screenHeight = resources.displayMetrics.heightPixels
                            val rootW = windowRoot.width.takeIf { it > 0 }?.coerceAtLeast(dp(48)) ?: dp(260)
                            val rootH = windowRoot.height.takeIf { it > 0 }?.coerceAtLeast(dp(48)) ?: dp(120)
                            val nextX = (initialX + deltaX).coerceIn(0, screenWidth - rootW)
                            val nextY = (initialY + deltaY).coerceIn(0, screenHeight - rootH)
                            overlayWindowLayoutParams.x = nextX
                            overlayWindowLayoutParams.y = nextY
                            manager.updateViewLayout(windowRoot, overlayWindowLayoutParams)
                            syncTickerPosition()
                        }
                        true
                    }

                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        recordingStartRunnable?.let { mainHandler.removeCallbacks(it) }
                        recordingStartRunnable = null
                        if (!isDragging && startedRecording) {
                            stopRecording()
                        } else if (!isDragging && event.action != MotionEvent.ACTION_CANCEL) {
                            val loc = IntArray(2)
                            bubbleView.getLocationOnScreen(loc)
                            toggleQuickCommands(loc[0], loc[1])
                        }
                        startedRecording = false
                        isDragging = false
                        true
                    }

                    else -> false
                }
            }
        }

        val stripLp = LinearLayout.LayoutParams(dp(280), dp(200)).apply {
            gravity = Gravity.CENTER_VERTICAL
            marginEnd = dp(8)
        }
        val bubbleLp = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT,
        ).apply {
            gravity = Gravity.CENTER_VERTICAL
        }

        val fabCtx = OverlayTickerUi.themedFabContext(this@CombatOverlayService)
        val historyFab = FloatingActionButton(fabCtx).apply {
            OverlayTickerUi.styleOverlayFab(fabCtx, this, 40f)
            setImageResource(R.drawable.ic_overlay_history)
            contentDescription = getString(R.string.overlay_cd_history)
            isClickable = false
            isFocusable = false
        }
        val historyHit = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(54), dp(54)).apply {
                gravity = Gravity.CENTER_VERTICAL
                marginEnd = dp(4)
            }
            isClickable = true
            isFocusable = true
            setOnClickListener { toggleOverlayHistoryPanel() }
            addView(
                historyFab,
                FrameLayout.LayoutParams(dp(40), dp(40), Gravity.CENTER),
            )
        }

        val stripLpActual = if (compact) {
            LinearLayout.LayoutParams(0, 0).apply {
                gravity = Gravity.CENTER_VERTICAL
            }
        } else {
            stripLp
        }
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, 0)
            addView(stripScroll, stripLpActual)
            addView(historyHit)
            addView(bubble, bubbleLp)
        }
        overlayHistoryFab = historyFab

        windowRoot = FrameLayout(this).apply {
            elevation = 22f
            setBackgroundColor(Color.TRANSPARENT)
            @Suppress("DEPRECATION")
            fitsSystemWindows = false
            addView(row)
        }

        overlayBubble = bubble
        overlayView = windowRoot
        chatStripScroll = stripScroll
        chatStripLines = stripLines

        manager.addView(overlayView, overlayWindowLayoutParams)
        windowManager = manager
        ensureToggleButton()
        ensureLockButton()
        ensureTicker()
        syncTickerPosition()
        beginOverlayChatSubscription()
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
            overlayWindowFlags(),
            android.graphics.PixelFormat.TRANSLUCENT,
        ).apply {
            applyOverlayLayoutCompat(this)
            gravity = Gravity.TOP or Gravity.START
            x = dp(10)
            y = dp(86)
        }

        val toggleCtx = OverlayTickerUi.themedFabContext(this)
        val toggle = FloatingActionButton(toggleCtx).apply {
            OverlayTickerUi.styleOverlayFab(toggleCtx, this)
            setImageResource(R.drawable.ic_overlay_ui_collapse)
            contentDescription = getString(R.string.overlay_cd_toggle_hide_ui)
        }
        attachDraggableFab(toggle, params) {
            overlayCollapsed = !overlayCollapsed
            applyOverlayVisibilityState()
        }

        toggleView = toggle
        toggleParams = params
        manager.addView(toggle, params)
    }

    private fun ensureLockButton() {
        if (lockView != null) return
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
            overlayWindowFlags(),
            android.graphics.PixelFormat.TRANSLUCENT,
        ).apply {
            applyOverlayLayoutCompat(this)
            gravity = Gravity.TOP or Gravity.START
            x = dp(10)
            y = dp(138)
        }

        val lockCtx = OverlayTickerUi.themedFabContext(this)
        val lock = FloatingActionButton(lockCtx).apply {
            OverlayTickerUi.styleOverlayFab(lockCtx, this)
            refreshLockFabIcon(this)
        }
        attachDraggableFab(lock, params) {
            val prefs = AppContainer.from(this).userSettingsPreferences
            prefs.setOverlayDragLocked(!prefs.isOverlayDragLocked())
            refreshLockFabIcon(lock)
        }

        lockView = lock
        lockParams = params
        manager.addView(lock, params)
    }

    private fun refreshLockFabIcon(button: FloatingActionButton) {
        val locked = AppContainer.from(this).userSettingsPreferences.isOverlayDragLocked()
        button.setImageResource(
            if (locked) R.drawable.ic_overlay_lock_locked else R.drawable.ic_overlay_lock_open,
        )
        button.contentDescription = getString(
            if (locked) R.string.overlay_cd_unlock_positions else R.string.overlay_cd_lock_positions,
        )
    }

    /**
     * Перетаскивание круглой кнопки; короткое касание — [onTap].
     * При заблокированных позициях перетаскивание отключено, касание выполняет [onTap].
     */
    private fun attachDraggableFab(
        view: View,
        params: WindowManager.LayoutParams,
        onTap: () -> Unit,
    ) {
        val manager = windowManager ?: return
        var initialX = 0
        var initialY = 0
        var startRawX = 0f
        var startRawY = 0f
        var dragging = false
        val threshold = dragSlopPx()
        view.setOnTouchListener { _, event ->
            val locked = AppContainer.from(this).userSettingsPreferences.isOverlayDragLocked()
            if (locked) {
                when (event.action) {
                    MotionEvent.ACTION_UP -> onTap()
                    else -> Unit
                }
                return@setOnTouchListener true
            }
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    startRawX = event.rawX
                    startRawY = event.rawY
                    dragging = false
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - startRawX).toInt()
                    val dy = (event.rawY - startRawY).toInt()
                    if (!dragging &&
                        (kotlin.math.abs(dx) > threshold || kotlin.math.abs(dy) > threshold)
                    ) {
                        dragging = true
                    }
                    if (dragging) {
                        val sw = resources.displayMetrics.widthPixels
                        val sh = resources.displayMetrics.heightPixels
                        val w = view.width.coerceAtLeast(dp(48))
                        val h = view.height.coerceAtLeast(dp(48))
                        params.x = (initialX + dx).coerceIn(0, (sw - w).coerceAtLeast(0))
                        params.y = (initialY + dy).coerceIn(0, (sh - h).coerceAtLeast(0))
                        runCatching { manager.updateViewLayout(view, params) }
                    }
                    true
                }

                MotionEvent.ACTION_UP -> {
                    if (!dragging) onTap()
                    dragging = false
                    true
                }

                MotionEvent.ACTION_CANCEL -> {
                    dragging = false
                    true
                }

                else -> false
            }
        }
    }

    private fun applyOverlayVisibilityState() {
        val bubbleContainer = overlayView
        val toggle = toggleView
        val lock = lockView
        if (overlayCollapsed) {
            hideOverlayHistoryPanel()
            hideQuickCommands()
            hideTicker()
            chatStripScroll?.visibility = View.GONE
            bubbleContainer?.animate()?.cancel()
            bubbleContainer?.visibility = View.GONE
            lock?.visibility = View.GONE
            toggle?.visibility = View.VISIBLE
            toggle?.setImageResource(R.drawable.ic_overlay_ui_expand)
            toggle?.contentDescription = getString(R.string.overlay_cd_toggle_show_ui)
        } else {
            val compactStrip = AppContainer.from(this).userSettingsPreferences.isCompactOverlay()
            chatStripScroll?.visibility = if (compactStrip) View.GONE else View.VISIBLE
            bubbleContainer?.animate()?.cancel()
            bubbleContainer?.visibility = View.VISIBLE
            bubbleContainer?.alpha = 1f
            bubbleContainer?.scaleX = 1f
            bubbleContainer?.scaleY = 1f
            lock?.visibility = View.VISIBLE
            lock?.let { refreshLockFabIcon(it) }
            toggle?.visibility = View.VISIBLE
            toggle?.setImageResource(R.drawable.ic_overlay_ui_collapse)
            toggle?.contentDescription = getString(R.string.overlay_cd_toggle_hide_ui)
            ensureTicker()
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
                            result.onSuccess { sent ->
                                applyLocalSentMessageToStrip(sent)
                                showTicker(command.text)
                            }.onFailure {
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
                overlayWindowFlags(),
                android.graphics.PixelFormat.TRANSLUCENT,
            ).apply {
                applyOverlayLayoutCompat(this)
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
            overlayWindowFlags(),
            android.graphics.PixelFormat.TRANSLUCENT,
        ).apply {
            applyOverlayLayoutCompat(this)
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

    private fun syncTickerPosition() {
        val manager = windowManager ?: return
        val params = tickerParams ?: return
        val ticker = tickerView ?: return
        params.x = dp(10)
        params.y = dp(110)
        runCatching { manager.updateViewLayout(ticker, params) }
    }

    private fun toggleOverlayHistoryPanel() {
        if (overlayHistoryVisible) {
            hideOverlayHistoryPanel()
        } else {
            showOverlayHistoryPanel()
        }
    }

    private fun hideOverlayHistoryPanel() {
        val root = overlayHistoryRoot ?: return
        overlayHistoryVisible = false
        overlayHistoryInput?.let { hideOverlayIme(it) }
        val manager = windowManager ?: return
        runCatching { manager.removeView(root) }
        overlayHistoryRoot = null
        overlayHistoryScroll = null
        overlayHistoryLines = null
        overlayHistoryParams = null
        overlayHistoryInput = null
        overlayHistorySend = null
        overlayHistoryStatus = null
        overlayHistoryDedupeIds.clear()
    }

    private fun hideOverlayIme(view: View) {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager ?: return
        imm.hideSoftInputFromWindow(view.windowToken, 0)
    }

    private fun showOverlayHistoryStatus(message: String?) {
        val tv = overlayHistoryStatus ?: return
        if (message.isNullOrBlank()) {
            tv.visibility = View.GONE
            tv.text = ""
        } else {
            tv.text = message
            tv.visibility = View.VISIBLE
        }
    }

    private fun attemptSendOverlayHistoryMessage() {
        val input = overlayHistoryInput ?: return
        val text = input.text?.toString()?.trim().orEmpty()
        if (text.isEmpty()) {
            showOverlayHistoryStatus(getString(R.string.overlay_history_empty_send))
            return
        }
        val roomId = runCatching { AppContainer.from(this).chatRoomPreferences.getSelectedRoomId() }.getOrNull()
        if (roomId.isNullOrBlank()) {
            showOverlayHistoryStatus(getString(R.string.overlay_strip_no_room))
            return
        }
        showOverlayHistoryStatus(getString(R.string.overlay_history_sending))
        overlayHistorySend?.isEnabled = false
        hideOverlayIme(input)
        serviceScope.launch {
            val result = AppContainer.from(this@CombatOverlayService).chatRepository.sendMessage(text, roomId)
            mainHandler.post {
                overlayHistorySend?.isEnabled = true
                result.onSuccess { sent ->
                    input.setText("")
                    showOverlayHistoryStatus(null)
                    applyLocalSentMessageToStrip(sent)
                }.onFailure { e ->
                    showOverlayHistoryStatus(
                        getString(R.string.overlay_history_send_failed, e.toUserMessageRu(resources)),
                    )
                }
            }
        }
    }

    private fun showOverlayHistoryPanel() {
        if (overlayHistoryVisible) return
        val manager = windowManager ?: return
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val panel = OverlayChatHistoryPanel.create(this) {
            hideOverlayHistoryPanel()
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            type,
            overlayHistoryWindowFlags(),
            android.graphics.PixelFormat.TRANSLUCENT,
        ).apply {
            applyOverlayHistoryLayoutCompat(this)
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = 0
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        }

        overlayHistoryRoot = panel.root
        overlayHistoryScroll = panel.scroll
        overlayHistoryLines = panel.lines
        overlayHistoryInput = panel.input
        overlayHistorySend = panel.sendButton
        overlayHistoryStatus = panel.statusView
        overlayHistoryParams = params
        overlayHistoryVisible = true
        runCatching { manager.addView(panel.root, params) }

        panel.sendButton.setOnClickListener { attemptSendOverlayHistoryMessage() }
        panel.input.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                attemptSendOverlayHistoryMessage()
                true
            } else {
                false
            }
        }

        serviceScope.launch {
            val container = AppContainer.from(this@CombatOverlayService)
            val roomId = container.chatRoomPreferences.getSelectedRoomId() ?: return@launch
            val result = container.chatRepository.loadRecentMessages(roomId, null, OVERLAY_HISTORY_LOAD)
            mainHandler.post {
                val targetLines = overlayHistoryLines ?: return@post
                result.onSuccess { list ->
                    OverlayChatHistoryPanel.populate(
                        this@CombatOverlayService,
                        targetLines,
                        list,
                        jwtSubFromAccessToken(),
                        messageReceivedAt,
                        overlayHistoryDedupeIds,
                    )
                    overlayHistoryScroll?.post {
                        overlayHistoryScroll?.fullScroll(View.FOCUS_DOWN)
                    }
                }.onFailure {
                    mainHandler.post {
                        showOverlayHistoryStatus(getString(R.string.overlay_strip_history_failed))
                    }
                }
            }
        }
    }

    private fun dp(value: Int): Int {
        val density = resources.displayMetrics.density
        return (value * density).toInt()
    }

    private fun removeOverlayControl() {
        endOverlayChatSubscription()
        hideTicker()
        hideQuickCommands()
        removeLockButton()
        removeToggleButton()
        val manager = windowManager ?: return
        val view = overlayView ?: return
        runCatching {
            manager.removeView(view)
        }
        overlayView = null
        overlayBubble = null
        overlayHistoryFab = null
        chatStripScroll = null
        chatStripLines = null
        windowManager = null
    }

    private fun removeToggleButton() {
        val manager = windowManager ?: return
        val toggle = toggleView ?: return
        runCatching { manager.removeView(toggle) }
        toggleView = null
        toggleParams = null
    }

    private fun removeLockButton() {
        val manager = windowManager ?: return
        val lock = lockView ?: return
        runCatching { manager.removeView(lock) }
        lockView = null
        lockParams = null
    }

    companion object {
        private const val CHANNEL_ID = "combat_overlay_channel"
        private const val NOTIFICATION_ID = 7001
        private const val STRIP_MAX_MESSAGES = 3
        private const val STRIP_MAX_AGE_MINUTES = 5L
        private const val STRIP_BUFFER_CAP = 80
        private const val STRIP_TICK_MS = 45_000L
        private const val OVERLAY_HISTORY_LOAD = 150

        const val ACTION_START_RECORDING = "com.squadrelay.action.START_RECORDING"
        const val ACTION_STOP_RECORDING = "com.squadrelay.action.STOP_RECORDING"
        const val ACTION_STOP_SERVICE = "com.squadrelay.action.STOP_SERVICE"
        const val ACTION_REBUILD_OVERLAY = "com.squadrelay.action.REBUILD_OVERLAY"
        const val ACTION_REFRESH_NOTIFICATION = "com.squadrelay.action.REFRESH_NOTIFICATION"

        @Volatile
        var isServiceInstanceActive: Boolean = false

        /** Re-layout overlay (e.g. compact mode) while combat service is running. */
        fun requestRebuildOverlayIfRunning(context: Context) {
            if (!isServiceInstanceActive) return
            val intent = Intent(context, CombatOverlayService::class.java).apply {
                action = ACTION_REBUILD_OVERLAY
            }
            context.startService(intent)
        }

        /** Re-post foreground notification (quiet mode) while service is running. */
        fun refreshQuietNotificationIfRunning(context: Context) {
            if (!isServiceInstanceActive) return
            val intent = Intent(context, CombatOverlayService::class.java).apply {
                action = ACTION_REFRESH_NOTIFICATION
            }
            context.startService(intent)
        }

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
