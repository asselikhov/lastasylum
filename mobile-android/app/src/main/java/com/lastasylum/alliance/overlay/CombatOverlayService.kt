package com.lastasylum.alliance.overlay

import android.app.Service
import android.content.Context
import android.content.IntentFilter
import android.content.BroadcastReceiver
import android.content.Intent
import android.content.pm.ServiceInfo
import android.content.ClipData
import android.net.Uri
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
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.view.ViewGroup
import android.util.Base64
import android.util.Log
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.OnBackPressedDispatcher
import androidx.activity.OnBackPressedDispatcherOwner
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.setViewTreeOnBackPressedDispatcherOwner
import androidx.core.app.ActivityOptionsCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import com.google.android.material.color.MaterialColors
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.lastasylum.alliance.R
import com.lastasylum.alliance.data.chat.ChatMessage
import com.lastasylum.alliance.data.chat.chatSenderDisplayWithTag
import com.lastasylum.alliance.data.settings.UserSettingsPreferences
import com.lastasylum.alliance.di.AppContainer
import com.lastasylum.alliance.overlay.layout.OverlayLayoutDp
import com.lastasylum.alliance.ui.screens.ChatScreen
import com.lastasylum.alliance.ui.chat.ChatViewModel
import com.lastasylum.alliance.ui.theme.SquadRelayTheme
import com.lastasylum.alliance.ui.util.toUserMessageRu
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalSavedStateRegistryOwner
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
            onTickerWindowAttached = { rebalanceOverlayChatWindowZOrder() },
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
    /** True: только кнопка разворота; по нажатию — остальные кнопки панели. */
    private var panelCollapsed = false
    private var messageExpanded = false
    private var chatStripClipRoot: FrameLayout? = null
    private var chatStripLines: LinearLayout? = null
    private var chatStripCompose: ComposeView? = null
    private var chatStripHost: FrameLayout? = null
    private var chatStripParams: WindowManager.LayoutParams? = null
    private val chatStripPreviewFlow = MutableStateFlow<List<ChatMessage>>(emptyList())
    private var overlayMessageListener: ((ChatMessage) -> Unit)? = null
    /** Параметры главного окна оверлея (перетаскивание + определение «у правого края»). */
    private var overlayMainWindowParams: WindowManager.LayoutParams? = null
    /** Горизонтальный ряд: лента чата + колонка кнопок (порядок меняется у правого края). */
    private var overlayOuterRow: LinearLayout? = null
    private var overlayControlsStack: LinearLayout? = null
    private var overlayMessageRow: LinearLayout? = null
    private var overlayMessageFabColumn: LinearLayout? = null
    private var overlaySubRow: LinearLayout? = null
    private var overlayBtnMessageFab: FloatingActionButton? = null
    private var overlayPanelAnchoredEnd: Boolean = false
    /**
     * Ширина главного окна оверлея в развёрнутом виде. Нужна для [syncOverlayPanelEdgeLayout]:
     * при сворачивании [overlayView] становится узким — без этого «центр» окна смещается и якорь
     * левый/правый переключается, из‑за чего панель визуально «уезжает».
     */
    private var overlayPanelStableAnchorWidthPx: Int = 0

    /** Лента: короткий TTL и мало строк превью — компактная полоса у края. */
    private val stripBuffer = OverlayChatStripBuffer(
        messageTtlSeconds = OverlayChatStripBuffer.DEFAULT_MESSAGE_TTL_SECONDS,
        maxPreviewMessages = 5,
    )
    private var overlayHistoryRoot: FrameLayout? = null
    private var overlayHistoryScroll: ScrollView? = null
    private var overlayHistoryLines: LinearLayout? = null
    private var overlayHistoryParams: WindowManager.LayoutParams? = null
    private var overlayHistoryFab: FloatingActionButton? = null
    @Volatile
    private var overlayHistoryVisible = false
    private var overlayHistoryInput: com.google.android.material.textfield.TextInputEditText? = null
    private var overlayHistorySend: FloatingActionButton? = null
    private var overlayHistoryStatus: TextView? = null
    private val overlayHistoryDedupeIds = mutableSetOf<String>()
    private var overlayChatViewModel: ChatViewModel? = null
    private var overlayChatOwner: OverlayChatComposeOwner? = null

    private data class OverlayWindowFlagSnap(
        val view: View,
        val params: WindowManager.LayoutParams,
        val prevFlags: Int,
    )

    /** Снимок флагов окон overlay на время системного пикера/permission (иначе они выше Activity и блокируют ввод). */
    private val overlayTouchPassthroughSnaps = mutableListOf<OverlayWindowFlagSnap>()

    private fun suspendOverlayWindowsForSystemActivity() {
        if (overlayTouchPassthroughSnaps.isNotEmpty()) return
        val mgr = windowManager ?: return
        fun snap(params: WindowManager.LayoutParams?, view: View?) {
            if (params == null || view == null || !view.isAttachedToWindow) return
            overlayTouchPassthroughSnaps.add(OverlayWindowFlagSnap(view, params, params.flags))
            val with = params.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
            if (params.flags != with) {
                params.flags = with
                runCatching { mgr.updateViewLayout(view, params) }
            }
        }
        snap(overlayMainWindowParams, overlayView)
        snap(overlayHistoryParams, overlayHistoryRoot)
        snap(chatStripParams, chatStripHost)
        overlayTicker.applyTouchPassthrough(true)
        quickCommandsPopover.hide()
    }

    private fun resumeOverlayWindowsAfterSystemActivity() {
        val mgr = windowManager
        if (overlayTouchPassthroughSnaps.isNotEmpty() && mgr != null) {
            for (snap in overlayTouchPassthroughSnaps) {
                snap.params.flags = snap.prevFlags
                if (snap.view.isAttachedToWindow) {
                    runCatching { mgr.updateViewLayout(snap.view, snap.params) }
                }
            }
            overlayTouchPassthroughSnaps.clear()
        }
        overlayTicker.applyTouchPassthrough(false)
    }

    private val overlaySystemResultReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val i = intent ?: return
            val requestCode = i.getIntExtra(OverlaySystemDialogActivity.EXTRA_REQUEST_CODE, -1)
            if (requestCode < 0) return
            mainHandler.post {
                resumeOverlayWindowsAfterSystemActivity()
                val owner = overlayChatOwner ?: return@post
                when (i.action) {
                    OverlaySystemDialogActivity.ACTION_OVERLAY_PICK_IMAGES_RESULT -> {
                        val uris = i.getParcelableArrayListExtra<Uri>(OverlaySystemDialogActivity.EXTRA_URIS).orEmpty()
                        // Build intent payload compatible with PickMultipleVisualMedia contract parsing:
                        // ActivityResultContracts.PickMultipleVisualMedia -> GetMultipleContents.getClipDataUris(intent)
                        val data = Intent().apply {
                            if (uris.isNotEmpty()) {
                                val clip = ClipData.newRawUri("images", uris.first())
                                uris.drop(1).forEach { clip.addItem(ClipData.Item(it)) }
                                clipData = clip
                            }
                        }
                        owner.activityResultRegistry.dispatchResult(requestCode, android.app.Activity.RESULT_OK, data)
                    }
                    OverlaySystemDialogActivity.ACTION_OVERLAY_MIC_PERMISSION_RESULT -> {
                        val granted = i.getBooleanExtra(OverlaySystemDialogActivity.EXTRA_GRANTED, false)
                        val data = Intent().apply {
                            putExtra(
                                "androidx.activity.result.contract.extra.PERMISSION_GRANT_RESULTS",
                                intArrayOf(if (granted) 0 else -1),
                            )
                        }
                        owner.activityResultRegistry.dispatchResult(requestCode, android.app.Activity.RESULT_OK, data)
                    }
                }
            }
        }
    }

    private inner class OverlayChatComposeOwner :
        LifecycleOwner,
        ViewModelStoreOwner,
        SavedStateRegistryOwner,
        OnBackPressedDispatcherOwner,
        ActivityResultRegistryOwner {
        private val lifecycleRegistry = LifecycleRegistry(this)
        private val store = ViewModelStore()
        private val savedStateController = SavedStateRegistryController.create(this)
        private val backDispatcher = OnBackPressedDispatcher()

        private val registry = object : ActivityResultRegistry() {
            override fun <I, O> onLaunch(
                requestCode: Int,
                contract: androidx.activity.result.contract.ActivityResultContract<I, O>,
                input: I,
                options: ActivityOptionsCompat?,
            ) {
                val kind = when (contract) {
                    is androidx.activity.result.contract.ActivityResultContracts.PickMultipleVisualMedia ->
                        OverlaySystemDialogActivity.KIND_PICK_IMAGES
                    is androidx.activity.result.contract.ActivityResultContracts.RequestPermission ->
                        OverlaySystemDialogActivity.KIND_REQUEST_MIC
                    else -> null
                }
                if (kind == null) {
                    Toast.makeText(
                        this@CombatOverlayService,
                        "Действие недоступно в оверлее",
                        Toast.LENGTH_SHORT,
                    ).show()
                    return
                }
                val i = Intent(this@CombatOverlayService, OverlaySystemDialogActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    putExtra(OverlaySystemDialogActivity.EXTRA_KIND, kind)
                    putExtra(OverlaySystemDialogActivity.EXTRA_REQUEST_CODE, requestCode)
                }
                suspendOverlayWindowsForSystemActivity()
                try {
                    startActivity(i)
                } catch (e: Exception) {
                    Log.e(TAG, "Overlay system dialog start failed", e)
                    resumeOverlayWindowsAfterSystemActivity()
                    Toast.makeText(
                        this@CombatOverlayService,
                        e.toUserMessageRu(resources),
                        Toast.LENGTH_SHORT,
                    ).show()
                }
            }
        }

        init {
            savedStateController.performAttach()
            // Required before composition uses SaveableState / bottom sheets tied to saved state.
            savedStateController.performRestore(null)
            lifecycleRegistry.currentState = Lifecycle.State.RESUMED
        }

        override val lifecycle: Lifecycle get() = lifecycleRegistry
        override val viewModelStore: ViewModelStore get() = store
        override val savedStateRegistry: SavedStateRegistry get() = savedStateController.savedStateRegistry
        override val onBackPressedDispatcher: OnBackPressedDispatcher get() = backDispatcher
        override val activityResultRegistry: ActivityResultRegistry get() = registry

        fun destroy() {
            lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
            store.clear()
        }
    }

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
        // If the user has disabled the panel, do not spin up the FGS at all.
        // This avoids slow enable/disable cycles on OEM ROMs (HyperOS/MIUI) caused by starting an FGS
        // just to immediately stop it.
        if (!AppContainer.from(this).userSettingsPreferences.isOverlayPanelEnabled()) {
            isServiceInstanceActive = false
            _serviceRunning.value = false
            _overlayVisible.value = false
            stopSelf()
            return
        }
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

    /**
     * Система (OEM / нехватка памяти) может снять overlay с экрана, оставив ссылки на View.
     * Тогда [showOverlayControl] выходит по `overlayView != null` и панель не появляется, пока
     * пользователь не перезапустит сервис тумблером.
     */
    private fun repairDetachedOverlayShellIfNeeded() {
        val v = overlayView ?: return
        val wm = windowManager
        if (wm != null && !v.isAttachedToWindow) {
            Log.w(TAG, "repairDetachedOverlayShellIfNeeded: overlay shell was detached, rebuilding")
            removeOverlayControl()
        }
    }

    private fun systemWindowManager(): WindowManager? =
        runCatching { getSystemService(Context.WINDOW_SERVICE) as WindowManager }.getOrNull()

    /** Показать оверлей при наличии разрешения «поверх окон» (после проверки режима «только в игре»). */
    private fun ensureOverlayIfPermitted() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            Log.w(TAG, "ensureOverlayIfPermitted: SYSTEM_ALERT_WINDOW not granted for ${packageName}")
            updateNotification(getString(R.string.overlay_notif_permission_required))
            return
        }
        repairDetachedOverlayShellIfNeeded()
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
                    // While the user is interacting with the overlay chat UI, do not hide the overlay.
                    // Some OEM ROMs may report SquadRelay as "resumed" when touching overlay windows.
                    // [overlayHistoryVisible] is @Volatile so IO reads see main-thread updates; full-screen image
                    // dialogs set [OverlayChatInteractionHold] while open (Compose Dialog window timing).
                    if (overlayHistoryVisible || OverlayChatInteractionHold.suppressGameForegroundGate) {
                        true
                    } else {
                        GameForegroundGate.shouldShowOverlay(this@CombatOverlayService, targets)
                    }
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
        ensureOverlayIfPermitted()
        updateNotification(getString(R.string.overlay_notif_combat_active))
    }

    private fun canDrawOverlaysNow(): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(this)
    }

    /**
     * Tear down overlay windows + FGS without touching [UserSettingsPreferences.isOverlayPanelEnabled].
     * Used for app-policy stops (hidden overlay tab, logout) — must not flip the user's "Показывать панель" toggle.
     */
    private fun shutdownRuntimeOnly(startId: Int): Int {
        gateCheckInFlight = false
        mainHandler.removeCallbacks(gameGateRunnable)
        runCatching { hideOverlayHistoryPanel() }
        runCatching { overlayTicker.hideTicker() }
        runCatching { quickCommandsPopover.hide() }
        runCatching { removeOverlayControl() }
        runCatching { stopForeground(STOP_FOREGROUND_REMOVE) }
        isServiceInstanceActive = false
        _serviceRunning.value = false
        _overlayVisible.value = false
        stopSelfResult(startId)
        return START_NOT_STICKY
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val prefs = AppContainer.from(this).userSettingsPreferences
        when (intent?.action) {
            ACTION_SET_ENABLED -> {
                val enabled = intent.getBooleanExtra(EXTRA_ENABLED, true)
                prefs.setOverlayPanelEnabled(enabled)
                if (!enabled) {
                    return shutdownRuntimeOnly(startId)
                }
                tickGameGate()
                return START_STICKY
            }
            ACTION_STOP_SERVICE -> {
                // Legacy stop intent: tear down runtime only. Do NOT clear the user's "show panel" preference.
                return shutdownRuntimeOnly(startId)
            }
            ACTION_RUNTIME_SHUTDOWN -> {
                // Used when the app UI policy requires stopping the FGS (e.g. overlay tab hidden / logout),
                // but the user did NOT explicitly disable "Показывать панель".
                return shutdownRuntimeOnly(startId)
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
        stripBuffer.prune()
        val preview = stripBuffer.visibleForPreview()
        val signature = preview.joinToString(separator = "|") { msg ->
            val key = msg._id?.takeIf { it.isNotBlank() } ?: msg.stableKey()
            val att = msg.attachments.joinToString(";") { a -> "${a.kind}:${a.url}:${a.mimeType ?: ""}" }
            "$key:${msg.text.hashCode()}:${msg.senderRole}:${msg.senderId}:" +
                "${msg.senderTeamTag ?: ""}:${msg.senderTelegramUsername ?: ""}:$att"
        }
        if (signature == lastStripRenderSignature) return
        chatStripPreviewFlow.value = preview
        lastStripRenderSignature = signature
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
        val signature = "notice:$message"
        if (signature == lastStripRenderSignature) return
        chatStripPreviewFlow.value = listOf(
            ChatMessage(
                _id = "notice",
                allianceId = "",
                roomId = "",
                senderId = "",
                senderUsername = getString(R.string.app_name),
                senderRole = "",
                senderTeamTag = null,
                senderTelegramUsername = null,
                text = message.trimEnd(),
                attachments = emptyList(),
                createdAt = null,
                updatedAt = null,
                replyToMessageId = null,
                replyTo = null,
                deletedAt = null,
                deletedByUserId = null,
            ),
        )
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

    private fun removeChatStripWindow(forManager: WindowManager? = null) {
        val mgr = forManager ?: windowManager ?: systemWindowManager() ?: return
        val host = chatStripHost ?: return
        runCatching { mgr.removeView(host) }
        chatStripHost = null
        chatStripParams = null
        chatStripClipRoot = null
        chatStripLines = null
        chatStripCompose = null
    }

    private fun ensureChatStripWindow(manager: WindowManager) {
        if (chatStripHost != null) return
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val stripMaxWidth =
            (resources.displayMetrics.widthPixels - dp(20)).coerceAtLeast(dp(220))
        // Strip is display-only: touches must reach the game under the overlay.
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            OverlayWindowLayout.popupWindowFlags() or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            android.graphics.PixelFormat.TRANSLUCENT,
        ).apply {
            OverlayWindowLayout.applyPopupLayoutCompat(this)
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            x = 0
            // Slight top margin from the game screen edge.
            y = dp(10)
        }

        val clipRoot = FrameLayout(this).apply {
            clipChildren = true
            OverlayChatStripUi.styleStripContainer(this@CombatOverlayService, this)
            val compose = ComposeView(this@CombatOverlayService).apply {
                setContent {
                    val preview by chatStripPreviewFlow.collectAsState()
                    val selfId = jwtSubFromAccessToken()
                    SquadRelayTheme {
                        OverlayChatStrip(
                            messages = preview,
                            selfUserId = selfId,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 2.dp, vertical = 2.dp),
                        )
                    }
                }
            }
            chatStripCompose = compose
            addView(
                compose,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    Gravity.BOTTOM,
                ),
            )
        }

        val host = OverlayPassthroughMultitouchFrameLayout(this).apply {
            elevation = 18f
            setPadding(dp(10), 0, dp(10), 0)
            setBackgroundColor(Color.TRANSPARENT)
            addView(
                clipRoot,
                FrameLayout.LayoutParams(
                    stripMaxWidth,
                    dp(210),
                ),
            )
        }

        val attach = runCatching { manager.addView(host, params) }
        if (attach.isFailure) {
            Log.e(TAG, "WindowManager.addView(chatStrip) failed", attach.exceptionOrNull())
            return
        }
        chatStripHost = host
        chatStripParams = params
        chatStripClipRoot = clipRoot
        chatStripLines = null
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

    /**
     * У правого края экрана: выпадающий ряд (атака/защита) — слева от колонки истории (история + чат + мик + замок),
     * у левого края — справа. Верхняя чат-лента теперь в отдельном overlay-окне и здесь не участвует.
     */
    private fun syncOverlayPanelEdgeLayout() {
        val params = overlayMainWindowParams ?: return
        val root = overlayView ?: return
        val controls = overlayControlsStack ?: return
        val msgRow = overlayMessageRow ?: return
        val sub = overlaySubRow ?: return
        val fabCol = overlayMessageFabColumn ?: return
        val w = root.width
        if (w <= 0) {
            root.post { syncOverlayPanelEdgeLayout() }
            return
        }
        val screenW = resources.displayMetrics.widthPixels
        val wAnchor = maxOf(w, maxOf(overlayPanelStableAnchorWidthPx, dp(160)))
        val anchoredEnd = params.x + wAnchor / 2 >= screenW / 2
        val msgOk = msgRow.childCount == 2 &&
            (
                (anchoredEnd && msgRow.getChildAt(0) === sub && msgRow.getChildAt(1) === fabCol) ||
                    (!anchoredEnd && msgRow.getChildAt(0) === fabCol && msgRow.getChildAt(1) === sub)
                )
        if (msgOk && anchoredEnd == overlayPanelAnchoredEnd) return
        overlayPanelAnchoredEnd = anchoredEnd

        // Controls stack itself is always inside the main overlay window; only re-order inside message row.
        msgRow.removeAllViews()
        if (anchoredEnd) {
            sub.setPadding(0, 0, dp(8), 0)
            msgRow.addView(sub, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT))
            msgRow.addView(
                fabCol,
                LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                    marginStart = dp(8)
                },
            )
        } else {
            sub.setPadding(dp(8), 0, 0, 0)
            msgRow.addView(
                fabCol,
                LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT),
            )
            msgRow.addView(sub, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        }
    }

    private fun showOverlayControl() {
        repairDetachedOverlayShellIfNeeded()
        if (overlayView != null && windowManager == null) {
            Log.w(TAG, "showOverlayControl: clearing orphan overlayView (no WindowManager)")
            overlayView = null
            chatStripClipRoot = null
            chatStripLines = null
            overlayBubble = null
            overlayHistoryFab = null
            overlayMainWindowParams = null
            overlayOuterRow = null
            overlayControlsStack = null
            overlayMessageRow = null
            overlayMessageFabColumn = null
            overlaySubRow = null
            overlayBtnMessageFab = null
            overlayPanelAnchoredEnd = false
            overlayPanelStableAnchorWidthPx = 0
        }
        if (overlayView != null) return
        val manager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        overlayMainWindowParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            OverlayWindowLayout.popupWindowFlags(),
            android.graphics.PixelFormat.TRANSLUCENT,
        ).apply {
            OverlayWindowLayout.applyPopupLayoutCompat(this)
            gravity = Gravity.BOTTOM or Gravity.START
            val prefs = AppContainer.from(this@CombatOverlayService).userSettingsPreferences
            val preset = OverlayLayoutDp.forPreset(prefs.getOverlayLayoutPreset())
            // Default position from preset, then override by saved drag position (px) if present.
            x = prefs.getOverlayPanelPosXPx() ?: dp(preset.toggleX)
            y = prefs.getOverlayPanelPosYPx() ?: dp(preset.toggleY)
        }

        var initialX = 0
        var initialY = 0
        var startTouchX = 0f
        var startTouchY = 0f
        var isDragging = false
        var dragArmed = false
        var dragArmRunnable: Runnable? = null
        var startedRecording = false
        val dragThreshold = OverlayWindowDragHelper.dragSlopPx(this)
        val fabCtx = OverlayTickerUi.themedFabContext(this@CombatOverlayService)
        fun makeMiniFab(iconRes: Int, cd: String): FloatingActionButton =
            FloatingActionButton(fabCtx).apply {
                // Smaller than before; consistent circular buttons
                OverlayTickerUi.styleOverlayFab(fabCtx, this, 42f)
                setImageResource(iconRes)
                contentDescription = cd
            }

        val btnCollapse = ImageView(this).apply {
            setImageResource(R.drawable.ic_overlay_ui_collapse)
            contentDescription = getString(R.string.overlay_cd_toggle_hide_ui)
            isClickable = true
            isFocusable = true
        }
        OverlayTickerUi.styleOverlayIconButton(fabCtx, btnCollapse, sideDp = 42f)
        val btnMessage = makeMiniFab(
            iconRes = R.drawable.ic_overlay_history,
            cd = getString(R.string.overlay_cd_history),
        )
        val btnChat = makeMiniFab(
            iconRes = R.drawable.ic_overlay_chat,
            cd = "Чат",
        )
        val btnMic = makeMiniFab(
            iconRes = R.drawable.ic_overlay_mic,
            cd = getString(R.string.overlay_ptt_start),
        )

        val lockIcon = ImageView(this).apply {
            setImageResource(R.drawable.ic_overlay_lock_open)
            contentDescription = getString(R.string.overlay_cd_lock_positions)
            isClickable = true
            isFocusable = true
        }

        val subAttack = makeMiniFab(iconRes = R.drawable.ic_overlay_send, cd = "Атака").apply {
            // Placeholder
            setOnClickListener { Toast.makeText(this@CombatOverlayService, "Атака (заглушка)", Toast.LENGTH_SHORT).show() }
        }
        val subDefense = makeMiniFab(iconRes = R.drawable.ic_overlay_send, cd = "Защита").apply {
            setOnClickListener { Toast.makeText(this@CombatOverlayService, "Защита (заглушка)", Toast.LENGTH_SHORT).show() }
        }
        btnChat.setOnClickListener { showOverlayHistoryPanel() }

        val subRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            visibility = View.GONE
            setPadding(dp(8), 0, 0, 0)
            addView(subAttack, LinearLayout.LayoutParams(dp(44), dp(44)).apply { marginEnd = dp(6) })
            addView(subDefense, LinearLayout.LayoutParams(dp(44), dp(44)).apply { marginEnd = dp(6) })
        }

        OverlayTickerUi.styleOverlayIconButton(fabCtx, lockIcon, sideDp = 42f)
        val messageFabColumn = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            addView(btnMessage, LinearLayout.LayoutParams(dp(44), dp(44)))
            addView(
                btnChat,
                LinearLayout.LayoutParams(dp(44), dp(44)).apply {
                    topMargin = dp(6)
                },
            )
            addView(
                btnMic,
                LinearLayout.LayoutParams(dp(44), dp(44)).apply {
                    topMargin = dp(6)
                },
            )
            addView(
                lockIcon,
                LinearLayout.LayoutParams(dp(44), dp(44)).apply {
                    topMargin = dp(6)
                    gravity = Gravity.CENTER_HORIZONTAL
                },
            )
        }

        val messageRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            // TOP: «Атака/Защита» в одной линии с верхней кнопкой колонки (история), а не по центру всей колонки.
            gravity = Gravity.TOP
            addView(
                messageFabColumn,
                LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT),
            )
            addView(subRow, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        }

        val buttonStack = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.START
            addView(btnCollapse, LinearLayout.LayoutParams(dp(44), dp(44)).apply { bottomMargin = dp(8) })
            addView(
                messageRow,
                LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                    bottomMargin = dp(8)
                },
            )
        }

        val outerRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.BOTTOM
            addView(buttonStack, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        }

        lateinit var windowRoot: FrameLayout
        windowRoot = OverlayPassthroughMultitouchFrameLayout(this).apply {
            elevation = 22f
            setBackgroundColor(Color.TRANSPARENT)
            @Suppress("DEPRECATION")
            fitsSystemWindows = false
            addView(
                outerRow,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                ),
            )
        }

        overlayOuterRow = outerRow
        overlayControlsStack = buttonStack
        overlayMessageRow = messageRow
        overlayMessageFabColumn = messageFabColumn
        overlaySubRow = subRow
        overlayBtnMessageFab = btnMessage

        fun refreshLockIcon() {
            val locked = AppContainer.from(this@CombatOverlayService).userSettingsPreferences.isOverlayDragLocked()
            lockIcon.setImageResource(if (locked) R.drawable.ic_overlay_lock_locked else R.drawable.ic_overlay_lock_open)
            lockIcon.contentDescription = getString(if (locked) R.string.overlay_cd_unlock_positions else R.string.overlay_cd_lock_positions)
        }

        fun applyControlsVisibility() {
            // GONE (не INVISIBLE): иначе ряд с FAB остаётся в разметке по ширине и окно перехватывает карту справа от кнопок.
            if (panelCollapsed) {
                messageExpanded = false
                messageRow.visibility = View.GONE
                subRow.visibility = View.GONE
            } else {
                messageRow.visibility = View.VISIBLE
                btnMic.visibility = View.VISIBLE
                lockIcon.visibility = View.VISIBLE
                subRow.visibility = if (messageExpanded) View.VISIBLE else View.GONE
            }
            btnCollapse.setImageResource(if (panelCollapsed) R.drawable.ic_overlay_ui_expand else R.drawable.ic_overlay_ui_collapse)
            btnCollapse.contentDescription = getString(
                if (panelCollapsed) R.string.overlay_cd_toggle_show_ui else R.string.overlay_cd_toggle_hide_ui,
            )
            windowRoot.post {
                if (!windowRoot.isAttachedToWindow) return@post
                val p = overlayMainWindowParams ?: return@post
                runCatching { manager.updateViewLayout(windowRoot, p) }
                // После смены размеров измеряем ширину, чтобы якорь края не «прыгал» при следующем сворачивании.
                windowRoot.post {
                    if (!panelCollapsed && windowRoot.width > 0) {
                        overlayPanelStableAnchorWidthPx = kotlin.math.max(
                            overlayPanelStableAnchorWidthPx,
                            windowRoot.width,
                        )
                    }
                    syncOverlayPanelEdgeLayout()
                }
            }
        }

        refreshLockIcon()
        panelCollapsed = true
        messageExpanded = false
        applyControlsVisibility()

        btnCollapse.setOnClickListener {
            // Keep the toggle button anchored on screen when window content expands/collapses.
            val desired = IntArray(2)
            btnCollapse.getLocationOnScreen(desired)
            panelCollapsed = !panelCollapsed
            applyControlsVisibility()
            windowRoot.post {
                if (!windowRoot.isAttachedToWindow) return@post
                val p = overlayMainWindowParams ?: return@post
                val current = IntArray(2)
                btnCollapse.getLocationOnScreen(current)
                val dx = current[0] - desired[0]
                val dy = current[1] - desired[1]
                if (dx == 0 && dy == 0) return@post

                val screenW = resources.displayMetrics.widthPixels
                val screenH = resources.displayMetrics.heightPixels
                val w = windowRoot.width.takeIf { it > 0 } ?: dp(120)
                val h = windowRoot.height.takeIf { it > 0 } ?: dp(180)

                // gravity = BOTTOM|START: x is from left; y is from bottom.
                p.x = (p.x - dx).coerceIn(0, (screenW - w).coerceAtLeast(0))
                p.y = (p.y + dy).coerceIn(0, (screenH - h).coerceAtLeast(0))
                runCatching { manager.updateViewLayout(windowRoot, p) }
                // Persist corrected position (toggle anchored).
                AppContainer.from(this@CombatOverlayService).userSettingsPreferences.setOverlayPanelPosPx(
                    x = p.x,
                    y = p.y,
                )
                overlayTicker.syncTickerPosition()
                syncOverlayPanelEdgeLayout()
            }
        }

        btnMessage.setOnClickListener {
            if (panelCollapsed) return@setOnClickListener
            messageExpanded = !messageExpanded
            applyControlsVisibility()
        }

        lockIcon.setOnClickListener {
            val prefs = AppContainer.from(this@CombatOverlayService).userSettingsPreferences
            prefs.setOverlayDragLocked(!prefs.isOverlayDragLocked())
            refreshLockIcon()
        }

        btnMic.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    startedRecording = false
                    val delayedStart = Runnable {
                        startedRecording = true
                        speechPipeline.startRecording()
                    }
                    recordingStartRunnable = delayedStart
                    mainHandler.postDelayed(delayedStart, 100L)
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    recordingStartRunnable?.let { mainHandler.removeCallbacks(it) }
                    recordingStartRunnable = null
                    if (startedRecording) {
                        speechPipeline.stopRecording()
                    }
                    startedRecording = false
                    true
                }
                else -> false
            }
        }

        btnCollapse.setOnTouchListener { v, event ->
            val dragLocked = AppContainer.from(this@CombatOverlayService).userSettingsPreferences.isOverlayDragLocked()
            if (dragLocked) return@setOnTouchListener false
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    (v.parent as? ViewGroup)?.requestDisallowInterceptTouchEvent(true)
                    initialX = overlayMainWindowParams!!.x
                    initialY = overlayMainWindowParams!!.y
                    startTouchX = event.rawX
                    startTouchY = event.rawY
                    isDragging = false
                    dragArmed = false
                    dragArmRunnable?.let { mainHandler.removeCallbacks(it) }
                    val arm = Runnable { dragArmed = true }
                    dragArmRunnable = arm
                    mainHandler.postDelayed(arm, 180L)
                    false
                }
                MotionEvent.ACTION_MOVE -> {
                    val deltaX = (event.rawX - startTouchX).toInt()
                    val deltaY = (event.rawY - startTouchY).toInt()
                    if (!isDragging && dragArmed &&
                        (kotlin.math.abs(deltaX) > dragThreshold || kotlin.math.abs(deltaY) > dragThreshold)
                    ) {
                        isDragging = true
                    }
                    if (isDragging) {
                        val screenWidth = resources.displayMetrics.widthPixels
                        val screenHeight = resources.displayMetrics.heightPixels
                        val rootW = windowRoot.width.takeIf { it > 0 }?.coerceAtLeast(dp(48)) ?: dp(120)
                        val rootH = windowRoot.height.takeIf { it > 0 }?.coerceAtLeast(dp(48)) ?: dp(180)
                        val nextX = (initialX + deltaX).coerceIn(0, screenWidth - rootW)
                        val nextY = (initialY - deltaY).coerceIn(0, screenHeight - rootH) // gravity=BOTTOM
                        overlayMainWindowParams!!.x = nextX
                        overlayMainWindowParams!!.y = nextY
                        manager.updateViewLayout(windowRoot, overlayMainWindowParams)
                        overlayTicker.syncTickerPosition()
                        syncOverlayPanelEdgeLayout()
                        true
                    } else {
                        false
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    dragArmRunnable?.let { mainHandler.removeCallbacks(it) }
                    dragArmRunnable = null
                    if (isDragging) {
                        syncOverlayPanelEdgeLayout()
                        // Persist last drag position so the panel doesn't jump after rebuild/restart.
                        overlayMainWindowParams?.let { p ->
                            AppContainer.from(this@CombatOverlayService).userSettingsPreferences.setOverlayPanelPosPx(
                                x = p.x,
                                y = p.y,
                            )
                        }
                    }
                    val consumed = isDragging
                    isDragging = false
                    dragArmed = false
                    consumed
                }
                else -> false
            }
        }

        overlayBubble = null
        overlayView = windowRoot
        overlayHistoryFab = null

        ensureChatStripWindow(manager)

        val attach = runCatching { manager.addView(overlayView, overlayMainWindowParams) }
        if (attach.isFailure) {
            Log.e(TAG, "WindowManager.addView(overlay) failed", attach.exceptionOrNull())
            overlayView = null
            removeChatStripWindow(manager)
            overlayMainWindowParams = null
            overlayOuterRow = null
            overlayControlsStack = null
            overlayMessageRow = null
            overlayMessageFabColumn = null
            overlaySubRow = null
            overlayBtnMessageFab = null
            return
        }
        _overlayVisible.value = true
        windowManager = manager
        overlayTicker.syncTickerPosition()
        rebalanceOverlayChatWindowZOrder()
        applyOverlayVisibilityState()
        windowRoot.post {
            syncOverlayPanelEdgeLayout()
            beginOverlayChatSubscription()
        }
    }

    private fun applyOverlayVisibilityState() {
        val bubbleContainer = overlayView
        chatStripHost?.visibility = View.VISIBLE
        bubbleContainer?.animate()?.cancel()
        bubbleContainer?.visibility = View.VISIBLE
        bubbleContainer?.alpha = 1f
        bubbleContainer?.scaleX = 1f
        bubbleContainer?.scaleY = 1f
        rebalanceOverlayChatWindowZOrder()
    }

    private fun toggleOverlayHistoryPanel() {
        if (overlayHistoryVisible) {
            hideOverlayHistoryPanel()
        } else {
            showOverlayHistoryPanel()
        }
    }

    private fun hideOverlayHistoryPanel() {
        resumeOverlayWindowsAfterSystemActivity()
        val root = overlayHistoryRoot
        overlayHistoryVisible = false
        overlayHistoryInput?.let { hideOverlayIme(it) }
        val manager = windowManager ?: systemWindowManager()
        if (root != null && manager != null) {
            runCatching { manager.removeView(root) }
        }
        overlayHistoryRoot = null
        overlayHistoryScroll = null
        overlayHistoryLines = null
        overlayHistoryParams = null
        overlayHistoryInput = null
        overlayHistorySend = null
        overlayHistoryStatus = null
        overlayHistoryDedupeIds.clear()
        overlayChatViewModel = null
        runCatching { unregisterReceiver(overlaySystemResultReceiver) }
        overlayChatOwner?.destroy()
        overlayChatOwner = null
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

    /**
     * У нескольких [TYPE_APPLICATION_OVERLAY] окон порядок «кто выше» на OEM может не совпадать с порядком
     * addView; remove+add поднимает окно чата поверх панели и тикера.
     */
    private fun rebalanceOverlayChatWindowZOrder() {
        if (!overlayHistoryVisible) return
        val root = overlayHistoryRoot ?: return
        val mgr = windowManager ?: return
        val p = overlayHistoryParams ?: return
        if (!root.isAttachedToWindow) return
        runCatching {
            mgr.removeView(root)
            mgr.addView(root, p)
        }.onFailure { e ->
            Log.w(TAG, "rebalanceOverlayChatWindowZOrder failed", e)
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

        // Full chat UI (same as in-app ChatScreen) rendered inside overlay window.
        val owner = overlayChatOwner ?: OverlayChatComposeOwner().also { overlayChatOwner = it }
        runCatching { unregisterReceiver(overlaySystemResultReceiver) }
        runCatching {
            ContextCompat.registerReceiver(
                this@CombatOverlayService,
                overlaySystemResultReceiver,
                IntentFilter().apply {
                    addAction(OverlaySystemDialogActivity.ACTION_OVERLAY_PICK_IMAGES_RESULT)
                    addAction(OverlaySystemDialogActivity.ACTION_OVERLAY_MIC_PERMISSION_RESULT)
                },
                ContextCompat.RECEIVER_NOT_EXPORTED,
            )
        }
        val compose = ComposeView(this).apply {
            setContent {
                val container = remember { AppContainer.from(this@CombatOverlayService) }
                val app = this@CombatOverlayService.applicationContext as android.app.Application
                val userId = remember { jwtSubFromAccessToken().orEmpty() }
                val vm = remember {
                    overlayChatViewModel ?: ChatViewModel(
                        application = app,
                        repository = container.chatRepository,
                        chatRoomPreferences = container.chatRoomPreferences,
                        usersRepository = container.usersRepository,
                        currentUserId = userId,
                        currentUserRole = "",
                    ).also {
                        overlayChatViewModel = it
                        it.refreshChat()
                    }
                }
                val chatState by vm.state.collectAsState()
                val draftMessage by vm.draftMessage.collectAsState()
                val pickedImageUris by vm.pickedImageUris.collectAsState()
                val typingPeers by vm.typingPeers.collectAsState()

                CompositionLocalProvider(
                    LocalActivityResultRegistryOwner provides owner,
                    LocalOnBackPressedDispatcherOwner provides owner,
                    LocalLifecycleOwner provides owner,
                    LocalSavedStateRegistryOwner provides owner,
                ) {
                    SquadRelayTheme {
                        Surface(
                            modifier = Modifier.fillMaxSize(),
                            color = MaterialTheme.colorScheme.surface,
                        ) {
                            Box(Modifier.fillMaxSize()) {
                                ChatScreen(
                                    state = chatState,
                                    typingPeers = typingPeers,
                                    draftMessage = draftMessage,
                                    pickedImageUris = pickedImageUris,
                                    onSelectRoom = vm::selectRoom,
                                    onClearError = vm::clearError,
                                    onLoadOlder = vm::loadOlderMessages,
                                    onDraftChange = vm::setDraftMessage,
                                    onSendDraft = vm::sendDraftMessage,
                                    onSendStickerPayload = { body -> vm.sendMessage(body) },
                                    onPickImages = vm::onImagesPicked,
                                    onRemovePickedImage = vm::removePickedImage,
                                    onClearPickedImages = vm::clearPickedImages,
                                    onReplyToMessage = vm::beginReplyToMessage,
                                    onClearReply = vm::clearReplyToMessage,
                                    onOpenMessageActions = vm::openMessageActions,
                                    onDismissMessageActions = vm::dismissMessageActions,
                                    onRequestDeleteMessage = vm::requestDeleteMessage,
                                    onDismissDeleteMessage = vm::dismissDeleteMessage,
                                    onConfirmDeleteMessage = vm::confirmDeleteMessage,
                                    onBeginMessageSelection = vm::beginMessageSelection,
                                    onToggleMessageSelection = vm::toggleMessageSelection,
                                    onClearMessageSelection = vm::clearMessageSelection,
                                    onRequestBulkDelete = vm::requestBulkDelete,
                                    onDismissBulkDeleteConfirm = vm::dismissBulkDeleteConfirm,
                                    onConfirmDeleteSelectedMessages = vm::confirmDeleteSelectedMessages,
                                    onRetrySendFailure = vm::retrySendFailure,
                                    onDismissSendFailure = vm::dismissSendFailure,
                                    onChatVoiceHoldStart = vm::startChatVoiceInput,
                                    onChatVoiceHoldEnd = vm::stopChatVoiceInput,
                                    onEditMessage = vm::editMessage,
                                    onForwardMessage = vm::forwardMessage,
                                    onToggleReaction = vm::toggleReaction,
                                )
                                IconButton(
                                    onClick = { hideOverlayHistoryPanel() },
                                    modifier = Modifier.align(Alignment.TopEnd),
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Close,
                                        contentDescription = getString(R.string.overlay_history_close_cd),
                                        tint = MaterialTheme.colorScheme.onSurface,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            type,
            OverlayWindowLayout.historyPanelWindowFlags(),
            PixelFormat.OPAQUE,
        ).apply {
            OverlayWindowLayout.applyHistoryLayoutCompat(this)
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = 0
            // Let Compose handle IME insets (ChatScreen uses imePadding); avoids double-adjust jank.
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING
        }

        val overlayUiContext = OverlayTickerUi.themedFabContext(this)
        val surfaceArgb = MaterialColors.getColor(
            overlayUiContext,
            com.google.android.material.R.attr.colorSurface,
            Color.parseColor("#10141E"),
        )
        // Compose resolves WindowRecomposer from the View tree before composition runs; locals alone are not enough.
        val root = FrameLayout(this).apply {
            setBackgroundColor(surfaceArgb)
            elevation = 48f
            setViewTreeLifecycleOwner(owner)
            setViewTreeViewModelStoreOwner(owner)
            setViewTreeSavedStateRegistryOwner(owner)
            setViewTreeOnBackPressedDispatcherOwner(owner)
            addView(
                compose,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT,
                ),
            )
        }

        val attachResult = runCatching { manager.addView(root, params) }
        if (attachResult.isFailure) {
            Log.e(TAG, "showOverlayHistoryPanel: failed to attach overlay chat", attachResult.exceptionOrNull())
            Toast.makeText(
                this,
                "Не удалось открыть чат в оверлее (ошибка окна). Проверьте разрешение «Поверх других приложений».",
                Toast.LENGTH_LONG,
            ).show()
            // Do not leave the service in a "chat visible" state if we failed to attach the window.
            overlayHistoryRoot = null
            overlayHistoryParams = null
            overlayHistoryVisible = false
            runCatching { unregisterReceiver(overlaySystemResultReceiver) }
            // Owner/VM are safe to keep; they will be recreated next time if needed.
            // Some ROMs leave the main overlay in a bad state after a failed second window; rebuild shell.
            mainHandler.post {
                requestRebuildOverlayIfRunning(this@CombatOverlayService)
            }
            return
        }

        overlayHistoryRoot = root
        overlayHistoryParams = params
        overlayHistoryVisible = true
        rebalanceOverlayChatWindowZOrder()
    }

    private fun dp(value: Int): Int {
        val density = resources.displayMetrics.density
        return (value * density).toInt()
    }

    private fun removeOverlayControl() {
        resumeOverlayWindowsAfterSystemActivity()
        recordingStartRunnable?.let { mainHandler.removeCallbacks(it) }
        recordingStartRunnable = null
        speechPipeline.cancelActiveSession()
        endOverlayChatSubscription()
        overlayTicker.hideTicker()
        quickCommandsPopover.hide()
        val wm = windowManager ?: systemWindowManager()
        removeChatStripWindow(wm)
        val view = overlayView
        if (view != null && wm != null) {
            runCatching { wm.removeView(view) }
        }
        overlayView = null
        _overlayVisible.value = false
        overlayBubble = null
        overlayHistoryFab = null
        chatStripClipRoot = null
        chatStripLines = null
        overlayMainWindowParams = null
        overlayOuterRow = null
        overlayControlsStack = null
        overlayMessageRow = null
        overlayMessageFabColumn = null
        overlaySubRow = null
        overlayBtnMessageFab = null
        overlayPanelAnchoredEnd = false
        overlayPanelStableAnchorWidthPx = 0
        windowManager = null
    }

    companion object {
        /** Частый prune ленты относительно TTL ~10 с. */
        private const val STRIP_TICK_MS = 2_500L
        /** Реже дергать UsageStats — меньше нагрузки на CPU/binder рядом с игрой; скрытие оверлея после выхода из игры — до ~4s. */
        private const val GAME_GATE_POLL_MS = 4_000L
        private const val OVERLAY_HISTORY_LOAD = 150
        /** Matches backend / user schema: ingame | online | away */
        private const val OVERLAY_PRESENCE_INGAME = "ingame"
        private const val OVERLAY_PRESENCE_AWAY = "away"
        private const val TAG = "CombatOverlayService"

        const val ACTION_START_RECORDING = "com.squadrelay.action.START_RECORDING"
        const val ACTION_STOP_RECORDING = "com.squadrelay.action.STOP_RECORDING"
        const val ACTION_STOP_SERVICE = "com.squadrelay.action.STOP_SERVICE"
        /** Stops overlay runtime without clearing the user's "show panel" preference. */
        const val ACTION_RUNTIME_SHUTDOWN = "com.squadrelay.action.RUNTIME_SHUTDOWN"
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
            // Persist desired state first (single source of truth).
            runCatching { UserSettingsPreferences(app).setOverlayPanelEnabled(enabled) }
            // If we are disabling and the service is not running, do not start it just to stop it.
            if (!enabled && !isServiceInstanceActive) {
                // Also try a hard stop in case the process is alive but state is stale.
                runCatching { app.stopService(Intent(app, CombatOverlayService::class.java)) }
                return true
            }
            return try {
                val intent = Intent(context, CombatOverlayService::class.java).apply {
                    action = ACTION_SET_ENABLED
                    putExtra(EXTRA_ENABLED, enabled)
                }
                if (!enabled) {
                    // First, ask the running service to detach overlay windows immediately.
                    // Then force-stop the service so we don't wait on OEM delayed teardown.
                    runCatching { app.startService(intent) }
                    runCatching { app.stopService(Intent(app, CombatOverlayService::class.java)) }
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
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

        /**
         * Stops the foreground overlay service without flipping [UserSettingsPreferences.isOverlayPanelEnabled].
         * Use this for app navigation / logout flows. User-facing disable must use [setEnabled](..., false).
         */
        fun stopRuntime(context: Context) {
            val app = context.applicationContext
            if (!isServiceInstanceActive) {
                runCatching { app.stopService(Intent(app, CombatOverlayService::class.java)) }
                return
            }
            val intent = Intent(app, CombatOverlayService::class.java).apply {
                action = ACTION_RUNTIME_SHUTDOWN
            }
            runCatching { app.startService(intent) }
        }

        fun stopService(context: Context) {
            stopRuntime(context)
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
