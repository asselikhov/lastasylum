package com.lastasylum.alliance.overlay

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
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
import android.util.Log
import android.widget.ImageView
import android.widget.Toast
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.lastasylum.alliance.R
import com.lastasylum.alliance.data.chat.ChatMessage
import com.lastasylum.alliance.data.chat.chatSenderDisplayWithTag
import com.lastasylum.alliance.di.AppContainer
import com.lastasylum.alliance.overlay.layout.OverlayLayoutDp
import com.lastasylum.alliance.ui.util.toUserMessageRu
import org.json.JSONObject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.Instant

class CombatOverlayService : Service() {
    private var windowManager: WindowManager? = null
    private var overlayView: FrameLayout? = null
    private var overlayBubble: FrameLayout? = null
    /** Корневое окно кнопки (расширенная зона касания); сам FAB внутри. */
    private var toggleHost: FrameLayout? = null
    private var toggleFab: FloatingActionButton? = null
    private var toggleParams: WindowManager.LayoutParams? = null
    private var lockHost: FrameLayout? = null
    private var lockFab: FloatingActionButton? = null
    private var lockParams: WindowManager.LayoutParams? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val overlayTicker by lazy {
        OverlayTickerWindow(
            context = this,
            windowManagerProvider = { windowManager },
            mainHandler = mainHandler,
            dp = { dp(it) },
            tickerYDp = {
                OverlayLayoutDp.forPreset(
                    AppContainer.from(this@CombatOverlayService).userSettingsPreferences.getOverlayLayoutPreset(),
                ).tickerY
            },
        )
    }
    private val speechPipeline by lazy {
        OverlaySpeechPipeline(
            context = this,
            mainHandler = mainHandler,
            scope = serviceScope,
            applyBubbleState = { state -> setBubbleUi(state) },
            notify = { updateNotification(it) },
            pulseBubbleError = { pulseBubbleError() },
            onVoiceSent = { text, sent ->
                applyLocalSentMessageToStrip(sent)
                updateNotification(getString(R.string.overlay_notif_voice_sent))
                setBubbleUi(OverlayBubbleUi.BubbleState.IDLE)
                overlayTicker.showTicker(getString(R.string.overlay_ticker_voice, text))
            },
            onVoiceSendFailed = { noRoom ->
                val msg = if (noRoom) {
                    getString(R.string.err_chat_no_room)
                } else {
                    getString(R.string.overlay_notif_voice_chat_failed)
                }
                updateNotification(msg)
                pulseBubbleError()
            },
        )
    }
    private val quickCommandsPopover by lazy {
        OverlayQuickCommandsPopover(
            context = this,
            windowManagerProvider = { windowManager },
            mainHandler = mainHandler,
            externalScope = serviceScope,
            dp = { dp(it) },
            sendChatText = { text ->
                AppContainer.from(this@CombatOverlayService).chatRepository.sendSystemVoiceMessage(text)
            },
            onSendSuccess = { sent, ticker ->
                applyLocalSentMessageToStrip(sent)
                overlayTicker.showTicker(ticker)
            },
            onSendFailure = {
                updateNotification(getString(R.string.overlay_notif_voice_chat_failed))
                pulseBubbleError()
            },
        )
    }
    private val presenceHeartbeat by lazy {
        OverlayPresenceHeartbeat(
            mainHandler = mainHandler,
            scope = serviceScope,
            intervalMs = 45_000L,
            ping = {
                AppContainer.from(this@CombatOverlayService).usersRepository.updatePresence(
                    OVERLAY_PRESENCE_INGAME,
                )
                Unit
            },
        )
    }
    private var recordingStartRunnable: Runnable? = null
    private var overlayCollapsed = false
    private var chatStripScroll: ScrollView? = null
    private var chatStripLines: LinearLayout? = null
    private var overlayMessageListener: ((ChatMessage) -> Unit)? = null

    private val stripBuffer = OverlayChatStripBuffer()
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
        stripBuffer.prune()
        refreshOverlayChatStrip()
        scheduleStripTick()
    }

    private var gateNotifyKey: String = ""
    private var cachedAccessTokenSub: Pair<String?, String?> = null to null
    private var lastStripRenderSignature: String? = null
    @Volatile
    private var gateCheckInFlight = false
    private var lastGateDiagLogMs: Long = 0L

    private val gameGateRunnable = object : Runnable {
        override fun run() {
            runCatching { tickGameGate() }
            mainHandler.postDelayed(this, GAME_GATE_POLL_MS)
        }
    }

    override fun onCreate() {
        super.onCreate()
        speechPipeline.initIfAvailable()
        OverlayForegroundNotifications.ensureChannel(this)
        val notification = OverlayForegroundNotifications.build(
            this,
            getString(R.string.overlay_notif_combat_active),
            AppContainer.from(this).userSettingsPreferences.isQuietMode(),
        )
        // Тип FGS в манифесте — dataSync (см. AndroidManifest); иначе microphone + API 34 ломают onCreate без RECORD_AUDIO / eligible state.
        ServiceCompat.startForeground(
            this,
            OverlayForegroundNotifications.NOTIFICATION_ID,
            notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
        )
        isServiceInstanceActive = true
        _serviceRunning.value = true
        _overlayVisible.value = false
        mainHandler.post(gameGateRunnable)
    }

    /** Показать оверлей при наличии разрешения «поверх окон» (после проверки режима «только в игре»). */
    private fun ensureOverlayIfPermitted() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            Log.w(TAG, "ensureOverlayIfPermitted: SYSTEM_ALERT_WINDOW not granted for ${packageName}")
            updateNotification(getString(R.string.overlay_notif_permission_required))
            return
        }
        if (overlayView == null) {
            showOverlayControl()
        }
    }

    private fun notifyGateThrottled(content: String) {
        if (content == gateNotifyKey) return
        gateNotifyKey = content
        updateNotification(content)
    }

    private fun tickGameGate() {
        val prefs = AppContainer.from(this).userSettingsPreferences
        if (!prefs.isOverlayPanelEnabled()) {
            gateNotifyKey = ""
            if (overlayView != null) {
                removeOverlayControl()
            }
            return
        }
        if (!prefs.isOverlayGameGateEnabled()) {
            applyGameGateState(
                gameGateEnabled = false,
                hasUsageAccess = true,
                shouldShow = true,
            )
            return
        }
        if (gateCheckInFlight) return
        gateCheckInFlight = true
        val targets = prefs.getOverlayTargetGamePackages()
        serviceScope.launch {
            try {
                val hasUsageAccess = GameForegroundGate.hasUsageStatsAccess(this@CombatOverlayService)
                val shouldShow = if (hasUsageAccess) {
                    GameForegroundGate.shouldShowOverlay(this@CombatOverlayService, targets)
                } else {
                    false
                }
                mainHandler.post {
                    val nowMs = System.currentTimeMillis()
                    if (prefs.isOverlayGameGateEnabled() && nowMs - lastGateDiagLogMs >= 25_000L) {
                        lastGateDiagLogMs = nowMs
                        val draw = canDrawOverlaysNow()
                        if (!hasUsageAccess || !shouldShow || !draw || overlayView == null) {
                            Log.i(
                                TAG,
                                "overlayGate usage=$hasUsageAccess inGame=$shouldShow " +
                                    "drawOverlays=$draw overlayAttached=${overlayView != null} targets=${targets.joinToString()}",
                            )
                        }
                    }
                    applyGameGateState(
                        gameGateEnabled = true,
                        hasUsageAccess = hasUsageAccess,
                        shouldShow = shouldShow,
                    )
                }
            } finally {
                mainHandler.post {
                    gateCheckInFlight = false
                }
            }
        }
    }

    private fun applyGameGateState(
        gameGateEnabled: Boolean,
        hasUsageAccess: Boolean,
        shouldShow: Boolean,
    ) {
        if (!gameGateEnabled) {
            gateNotifyKey = ""
            if (!canDrawOverlaysNow()) {
                updateNotification(getString(R.string.overlay_notif_permission_required))
                return
            }
            ensureOverlayIfPermitted()
            return
        }
        if (!hasUsageAccess) {
            notifyGateThrottled(getString(R.string.overlay_notif_waiting_for_game))
            if (overlayView != null) {
                removeOverlayControl()
            }
            return
        }
        if (!shouldShow) {
            notifyGateThrottled(getString(R.string.overlay_notif_waiting_for_game))
            if (overlayView != null) {
                removeOverlayControl()
            }
            return
        }
        gateNotifyKey = ""
        if (!canDrawOverlaysNow()) {
            updateNotification(getString(R.string.overlay_notif_permission_required))
            if (overlayView != null) {
                removeOverlayControl()
            }
            return
        }
        if (overlayView == null) {
            ensureOverlayIfPermitted()
        }
        updateNotification(getString(R.string.overlay_notif_combat_active))
    }

    private fun canDrawOverlaysNow(): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val prefs = AppContainer.from(this).userSettingsPreferences
        when (intent?.action) {
            ACTION_SET_ENABLED -> {
                val enabled = intent.getBooleanExtra(EXTRA_ENABLED, true)
                prefs.setOverlayPanelEnabled(enabled)
                if (!enabled) {
                    // Stop must be reliable: immediately remove overlay windows and prevent sticky restart.
                    gateCheckInFlight = false
                    mainHandler.removeCallbacks(gameGateRunnable)
                    runCatching { removeOverlayControl() }
                    runCatching { overlayTicker.hideTicker() }
                    runCatching { quickCommandsPopover.hide() }
                    runCatching { stopForeground(STOP_FOREGROUND_REMOVE) }
                    isServiceInstanceActive = false
                    _serviceRunning.value = false
                    stopSelfResult(startId)
                    return START_NOT_STICKY
                }
                tickGameGate()
                return START_STICKY
            }
            ACTION_STOP_SERVICE -> {
                // Stop must be reliable: immediately remove overlay windows and prevent sticky restart.
                gateCheckInFlight = false
                mainHandler.removeCallbacks(gameGateRunnable)
                runCatching { removeOverlayControl() }
                runCatching { overlayTicker.hideTicker() }
                runCatching { quickCommandsPopover.hide() }
                runCatching { stopForeground(STOP_FOREGROUND_REMOVE) }
                isServiceInstanceActive = false
                _serviceRunning.value = false
                prefs.setOverlayPanelEnabled(false)
                stopSelfResult(startId)
                return START_NOT_STICKY
            }
            ACTION_REBUILD_OVERLAY -> {
                if (overlayView != null) {
                    removeOverlayControl()
                }
                tickGameGate()
                return START_STICKY
            }
            ACTION_REFRESH_NOTIFICATION -> {
                tickGameGate()
                return START_STICKY
            }
            ACTION_TICK_GAME_GATE -> {
                tickGameGate()
                return START_STICKY
            }
            else -> {
                if (!prefs.isOverlayPanelEnabled()) {
                    runCatching { removeOverlayControl() }
                    runCatching { stopForeground(STOP_FOREGROUND_REMOVE) }
                    isServiceInstanceActive = false
                    _serviceRunning.value = false
                    stopSelfResult(startId)
                    return START_NOT_STICKY
                }
                tickGameGate()
                when (intent?.action) {
                    ACTION_START_RECORDING -> speechPipeline.startRecording()
                    ACTION_STOP_RECORDING -> speechPipeline.stopRecording()
                }
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        isServiceInstanceActive = false
        _serviceRunning.value = false
        gateCheckInFlight = false
        mainHandler.removeCallbacks(gameGateRunnable)
        speechPipeline.destroy()
        overlayTicker.hideTicker()
        removeOverlayControl()
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

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

    private fun updateNotification(content: String) {
        OverlayForegroundNotifications.notify(
            this,
            content,
            AppContainer.from(this).userSettingsPreferences.isQuietMode(),
        )
    }

    private fun scheduleStripTick() {
        mainHandler.removeCallbacks(stripTickRunnable)
        mainHandler.postDelayed(stripTickRunnable, STRIP_TICK_MS)
    }

    private fun cancelStripTick() {
        mainHandler.removeCallbacks(stripTickRunnable)
    }

    private fun refreshOverlayChatStrip() {
        val lines = chatStripLines ?: return
        stripBuffer.prune()
        val preview = stripBuffer.visibleForPreview()
        val signature = preview.joinToString(separator = "|") { msg ->
            val key = msg._id?.takeIf { it.isNotBlank() } ?: msg.stableKey()
            "$key:${msg.text.hashCode()}:${msg.senderRole}:${msg.senderId}:${msg.senderTeamTag ?: ""}"
        }
        if (signature == lastStripRenderSignature) return
        val selfId = jwtSubFromAccessToken()
        OverlayChatStripUi.clearLines(lines)
        for (msg in preview) {
            OverlayChatStripUi.addLine(
                this,
                lines,
                chatSenderDisplayWithTag(msg.senderTeamTag, msg.senderUsername),
                msg.text,
                msg.senderId,
                msg.senderRole,
                selfId,
                showDismiss = false,
            )
        }
        lastStripRenderSignature = signature
        scrollChatStripToEnd()
    }

    /**
     * Исходящее сообщение по HTTP (текст из истории, голос, быстрые команды): сразу обновить ленту оверлея,
     * как только приходит ответ API (не ждать сокет). Вызывать с главного потока.
     */
    private fun applyLocalSentMessageToStrip(sent: ChatMessage) {
        stripBuffer.markClientSend(sent)
        processOverlayChatMessage(sent)
    }

    private fun processOverlayChatMessage(msg: ChatMessage) {
        stripBuffer.upsert(msg)
        stripBuffer.mergeReceiveTimeline(msg, jwtSubFromAccessToken())
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
                stripBuffer.receivedAtMap(),
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
        stripBuffer.clear()
        val lines = chatStripLines ?: return
        val signature = "notice:$message"
        if (signature == lastStripRenderSignature) return
        OverlayChatStripUi.clearLines(lines)
        OverlayChatStripUi.addNoticeLine(this, lines, message)
        lastStripRenderSignature = signature
    }

    private fun jwtSubFromAccessToken(): String? {
        val token = runCatching { AppContainer.from(this).tokenStore.getAccessToken() }.getOrNull()
        val cached = cachedAccessTokenSub
        if (cached.first == token) return cached.second
        if (token == null) {
            cachedAccessTokenSub = null to null
            return null
        }
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
        }.getOrNull().also { parsed ->
            cachedAccessTokenSub = token to parsed
        }
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
            presenceHeartbeat.start()
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
                        stripBuffer.seedFromHistory(loaded)
                        lastStripRenderSignature = null
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
        presenceHeartbeat.stop()
        serviceScope.launch {
            runCatching {
                AppContainer.from(this@CombatOverlayService).usersRepository.updatePresence(
                    OVERLAY_PRESENCE_AWAY,
                )
            }
        }
        cancelStripTick()
        hideOverlayHistoryPanel()
        stripBuffer.clear()
        lastStripRenderSignature = null
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
            OverlayWindowLayout.popupWindowFlags(),
            android.graphics.PixelFormat.TRANSLUCENT,
        ).apply {
            OverlayWindowLayout.applyPopupLayoutCompat(this)
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
        val dragThreshold = OverlayWindowDragHelper.dragSlopPx(this)
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
                        (bubbleView.parent as? ViewGroup)?.requestDisallowInterceptTouchEvent(true)
                        quickCommandsPopover.hide()
                        initialX = overlayWindowLayoutParams.x
                        initialY = overlayWindowLayoutParams.y
                        startTouchX = event.rawX
                        startTouchY = event.rawY
                        isDragging = false
                        startedRecording = false
                        val delayedStart = Runnable {
                            startedRecording = true
                            speechPipeline.startRecording()
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
                                speechPipeline.stopRecording()
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
                            overlayTicker.syncTickerPosition()
                        }
                        true
                    }

                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        recordingStartRunnable?.let { mainHandler.removeCallbacks(it) }
                        recordingStartRunnable = null
                        if (!isDragging && startedRecording) {
                            speechPipeline.stopRecording()
                        } else if (!isDragging && event.action != MotionEvent.ACTION_CANCEL) {
                            val loc = IntArray(2)
                            bubbleView.getLocationOnScreen(loc)
                            quickCommandsPopover.toggle(loc[0], loc[1])
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

        val attach = runCatching { manager.addView(overlayView, overlayWindowLayoutParams) }
        if (attach.isFailure) {
            Log.e(TAG, "WindowManager.addView(overlay) failed", attach.exceptionOrNull())
            overlayView = null
            overlayBubble = null
            chatStripScroll = null
            chatStripLines = null
            overlayHistoryFab = null
            return
        }
        _overlayVisible.value = true
        windowManager = manager
        ensureToggleButton()
        ensureLockButton()
        overlayTicker.ensureTicker()
        overlayTicker.syncTickerPosition()
        beginOverlayChatSubscription()
    }

    private fun ensureToggleButton() {
        if (toggleHost != null) return
        val manager = windowManager ?: return
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val touchSide = dp(64)
        val fabSide = dp(48)
        val params = WindowManager.LayoutParams(
            touchSide,
            touchSide,
            type,
            OverlayWindowLayout.popupWindowFlags(),
            android.graphics.PixelFormat.TRANSLUCENT,
        ).apply {
            OverlayWindowLayout.applyPopupLayoutCompat(this)
            gravity = Gravity.TOP or Gravity.START
            val layout = OverlayLayoutDp.forPreset(
                AppContainer.from(this@CombatOverlayService).userSettingsPreferences.getOverlayLayoutPreset(),
            )
            x = dp(layout.toggleX)
            y = dp(layout.toggleY)
        }

        val toggleCtx = OverlayTickerUi.themedFabContext(this)
        val fab = FloatingActionButton(toggleCtx).apply {
            OverlayTickerUi.styleOverlayFab(toggleCtx, this, 48f)
            setImageResource(R.drawable.ic_overlay_ui_collapse)
            contentDescription = getString(R.string.overlay_cd_toggle_hide_ui)
        }
        val host = FrameLayout(this).apply {
            isClickable = true
            addView(
                fab,
                FrameLayout.LayoutParams(fabSide, fabSide, Gravity.CENTER),
            )
        }
        val wmFab = windowManager ?: return
        OverlayWindowDragHelper.attachDraggableFab(
            this,
            wmFab,
            host,
            params,
            isDragLocked = {
                AppContainer.from(this@CombatOverlayService).userSettingsPreferences.isOverlayDragLocked()
            },
            onTap = {
                overlayCollapsed = !overlayCollapsed
                applyOverlayVisibilityState()
            },
        )

        toggleHost = host
        toggleFab = fab
        toggleParams = params
        manager.addView(host, params)
    }

    private fun ensureLockButton() {
        if (lockHost != null) return
        val manager = windowManager ?: return
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val touchSide = dp(64)
        val fabSide = dp(48)
        val params = WindowManager.LayoutParams(
            touchSide,
            touchSide,
            type,
            OverlayWindowLayout.popupWindowFlags(),
            android.graphics.PixelFormat.TRANSLUCENT,
        ).apply {
            OverlayWindowLayout.applyPopupLayoutCompat(this)
            gravity = Gravity.TOP or Gravity.START
            val layout = OverlayLayoutDp.forPreset(
                AppContainer.from(this@CombatOverlayService).userSettingsPreferences.getOverlayLayoutPreset(),
            )
            x = dp(layout.lockX)
            y = dp(layout.lockY)
        }

        val lockCtx = OverlayTickerUi.themedFabContext(this)
        val fab = FloatingActionButton(lockCtx).apply {
            OverlayTickerUi.styleOverlayFab(lockCtx, this, 48f)
            refreshLockFabIcon(this)
        }
        val host = FrameLayout(this).apply {
            isClickable = true
            addView(
                fab,
                FrameLayout.LayoutParams(fabSide, fabSide, Gravity.CENTER),
            )
        }
        val wmLock = windowManager ?: return
        OverlayWindowDragHelper.attachDraggableFab(
            this,
            wmLock,
            host,
            params,
            isDragLocked = {
                AppContainer.from(this@CombatOverlayService).userSettingsPreferences.isOverlayDragLocked()
            },
            onTap = {
                val prefs = AppContainer.from(this@CombatOverlayService).userSettingsPreferences
                prefs.setOverlayDragLocked(!prefs.isOverlayDragLocked())
                refreshLockFabIcon(fab)
            },
        )

        lockHost = host
        lockFab = fab
        lockParams = params
        manager.addView(host, params)
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

    private fun applyOverlayVisibilityState() {
        val bubbleContainer = overlayView
        val toggle = toggleFab
        val lock = lockFab
        if (overlayCollapsed) {
            hideOverlayHistoryPanel()
            quickCommandsPopover.hide()
            overlayTicker.hideTicker()
            chatStripScroll?.visibility = View.GONE
            bubbleContainer?.animate()?.cancel()
            bubbleContainer?.visibility = View.GONE
            lockHost?.visibility = View.GONE
            toggleHost?.visibility = View.VISIBLE
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
            lockHost?.visibility = View.VISIBLE
            lock?.let { refreshLockFabIcon(it) }
            toggleHost?.visibility = View.VISIBLE
            toggle?.setImageResource(R.drawable.ic_overlay_ui_collapse)
            toggle?.contentDescription = getString(R.string.overlay_cd_toggle_hide_ui)
            overlayTicker.ensureTicker()
        }
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
            val result = AppContainer.from(this@CombatOverlayService).chatRepository.sendMessageWithRetries(text, roomId)
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
            OverlayWindowLayout.historyPanelWindowFlags(),
            android.graphics.PixelFormat.TRANSLUCENT,
        ).apply {
            OverlayWindowLayout.applyHistoryLayoutCompat(this)
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = 0
            // ADJUST_RESIZE is deprecated on newer APIs; pan keeps the focused field visible over IME.
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN
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
                        stripBuffer.receivedAtMap(),
                        overlayHistoryDedupeIds,
                    )
                    overlayHistoryScroll?.post {
                        overlayHistoryScroll?.fullScroll(View.FOCUS_DOWN)
                    }
                }.onFailure {
                    showOverlayHistoryStatus(getString(R.string.overlay_strip_history_failed))
                }
            }
        }
    }

    private fun dp(value: Int): Int {
        val density = resources.displayMetrics.density
        return (value * density).toInt()
    }

    private fun removeOverlayControl() {
        recordingStartRunnable?.let { mainHandler.removeCallbacks(it) }
        recordingStartRunnable = null
        speechPipeline.cancelActiveSession()
        endOverlayChatSubscription()
        overlayTicker.hideTicker()
        quickCommandsPopover.hide()
        removeLockButton()
        removeToggleButton()
        val manager = windowManager ?: return
        val view = overlayView ?: return
        runCatching {
            manager.removeView(view)
        }
        overlayView = null
        _overlayVisible.value = false
        overlayBubble = null
        overlayHistoryFab = null
        chatStripScroll = null
        chatStripLines = null
        windowManager = null
    }

    private fun removeToggleButton() {
        val manager = windowManager ?: return
        val host = toggleHost ?: return
        runCatching { manager.removeView(host) }
        toggleHost = null
        toggleFab = null
        toggleParams = null
    }

    private fun removeLockButton() {
        val manager = windowManager ?: return
        val host = lockHost ?: return
        runCatching { manager.removeView(host) }
        lockHost = null
        lockFab = null
        lockParams = null
    }

    companion object {
        private const val STRIP_TICK_MS = 45_000L
        private const val GAME_GATE_POLL_MS = 2_000L
        private const val OVERLAY_HISTORY_LOAD = 150
        /** Matches backend / user schema: ingame | online | away */
        private const val OVERLAY_PRESENCE_INGAME = "ingame"
        private const val OVERLAY_PRESENCE_AWAY = "away"
        private const val TAG = "CombatOverlayService"

        const val ACTION_START_RECORDING = "com.squadrelay.action.START_RECORDING"
        const val ACTION_STOP_RECORDING = "com.squadrelay.action.STOP_RECORDING"
        const val ACTION_STOP_SERVICE = "com.squadrelay.action.STOP_SERVICE"
        const val ACTION_SET_ENABLED = "com.squadrelay.action.SET_ENABLED"
        const val ACTION_REBUILD_OVERLAY = "com.squadrelay.action.REBUILD_OVERLAY"
        const val ACTION_REFRESH_NOTIFICATION = "com.squadrelay.action.REFRESH_NOTIFICATION"
        const val ACTION_TICK_GAME_GATE = "com.squadrelay.action.TICK_GAME_GATE"
        private const val EXTRA_ENABLED = "enabled"

        @Volatile
        var isServiceInstanceActive: Boolean = false

        private val _serviceRunning = MutableStateFlow(false)
        private val _overlayVisible = MutableStateFlow(false)

        /** Для UI вкладки «Оверлей»: синхронно с жизненным циклом сервиса (без гонки с [isServiceInstanceActive]). */
        val serviceRunning: StateFlow<Boolean> = _serviceRunning.asStateFlow()
        /** True when the overlay windows are attached (panel visible on screen). */
        val overlayVisible: StateFlow<Boolean> = _overlayVisible.asStateFlow()

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

        /** После смены настроек «только в игре» / пакета — пересчитать показ оверлея. */
        fun requestGateRecheckIfRunning(context: Context) {
            if (!isServiceInstanceActive) return
            val intent = Intent(context, CombatOverlayService::class.java).apply {
                action = ACTION_TICK_GAME_GATE
            }
            context.startService(intent)
        }

        /**
         * Запуск боевого сервиса и оверлея. Микрофон не обязателен: панель может работать без голоса;
         * запись голоса запросит разрешение при использовании.
         */
        fun setEnabled(context: Context, enabled: Boolean): Boolean {
            val app = context.applicationContext
            return try {
                val intent = Intent(context, CombatOverlayService::class.java).apply {
                    action = ACTION_SET_ENABLED
                    putExtra(EXTRA_ENABLED, enabled)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    app.startForegroundService(intent)
                } else {
                    app.startService(intent)
                }
                true
            } catch (t: Throwable) {
                Log.e(TAG, "setEnabled failed", t)
                Toast.makeText(
                    app,
                    app.getString(R.string.overlay_start_service_failed),
                    Toast.LENGTH_LONG,
                ).show()
                false
            }
        }

        fun startService(context: Context): Boolean = setEnabled(context, true)

        fun stopService(context: Context) {
            setEnabled(context, false)
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
