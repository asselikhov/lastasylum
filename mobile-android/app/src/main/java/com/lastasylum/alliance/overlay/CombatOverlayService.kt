package com.lastasylum.alliance.overlay

import android.app.Service
import android.content.Context
import android.content.IntentFilter
import android.content.BroadcastReceiver
import android.content.Intent
import android.Manifest
import android.content.pm.PackageManager
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
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.view.ViewGroup
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
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.google.android.material.color.MaterialColors
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.lastasylum.alliance.R
import com.lastasylum.alliance.data.chat.ChatMessage
import com.lastasylum.alliance.data.chat.chatSenderDisplayWithTag
import com.lastasylum.alliance.data.auth.JwtAccessTokenClaims
import com.lastasylum.alliance.data.voice.VoiceChatSession
import com.lastasylum.alliance.data.settings.UserSettingsPreferences
import com.lastasylum.alliance.di.AppContainer
import com.lastasylum.alliance.overlay.layout.OverlayLayoutDp
import com.lastasylum.alliance.ui.screens.ChatScreen
import com.lastasylum.alliance.ui.screens.TeamMainSection
import com.lastasylum.alliance.ui.screens.TeamScreen
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
            onTickerWindowAttached = { rebalanceOverlayFullscreenZOrder() },
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
    private val overlayAllianceOnlinePopover by lazy {
        OverlayAllianceOnlinePopover(
            context = this,
            mainHandler = mainHandler,
            scope = serviceScope,
            dp = { dp(it) },
        )
    }
    private val overlayCommandsPopover by lazy {
        OverlayCommandsPopover(
            context = this,
            mainHandler = mainHandler,
            scope = serviceScope,
            dp = { dp(it) },
            sendCoords = { label, x, y, excavation ->
                val roomId = AppContainer.from(this@CombatOverlayService).chatRoomPreferences.getRaidRoomId()
                    ?: return@OverlayCommandsPopover Result.failure(IllegalStateException("no_raid"))
                val repo = AppContainer.from(this@CombatOverlayService).chatRepository
                if (excavation) {
                    val text = getString(R.string.overlay_excavation_message, x, y)
                    repo.sendExcavationAlertWithRetries(text, roomId)
                } else {
                    val text = "$label X:${x} Y:${y}"
                    repo.sendMessageWithRetries(text, roomId)
                }
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
    private var overlayVoiceControls: OverlayVoiceControls? = null
    private var overlayVoiceAnchor: FrameLayout? = null
    /** Высота окна панели до relayout (компенсация y при BOTTOM|gravity). */
    private var overlayPanelLastHeightPx: Int = 0
    /** Экранная Y (raw) верхнего края кнопки свернуть/развернуть — якорь при toggle. */
    private var voiceSession: VoiceChatSession? = null
    private var pendingVoiceMicEnable = false
    private var voicePermissionReceiverRegistered = false
    /** True: только кнопка разворота; по нажатию — остальные кнопки панели. */
    private var panelCollapsed = false
    private var chatStripClipRoot: FrameLayout? = null
    private var chatStripLines: LinearLayout? = null
    private var chatStripCompose: ComposeView? = null
    private var chatStripHost: View? = null
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
        maxPreviewMessages = OverlayChatStripBuffer.DEFAULT_MAX_PREVIEW,
    )
    /** Полноэкранное окно чата + команды (вкладки внизу); уничтожается в [hideOverlayChatTeamPanel]. */
    private var overlayChatTeamRoot: FrameLayout? = null
    private var overlayChatTeamParams: WindowManager.LayoutParams? = null
    @Volatile
    private var overlayChatTeamPanelVisible = false
    private var overlayChatTeamComposeOwner: OverlayChatComposeOwner? = null
    private var overlayHistoryScroll: ScrollView? = null
    private var overlayHistoryLines: LinearLayout? = null
    private var overlayHistoryFab: FloatingActionButton? = null
    private var overlayHistoryInput: com.google.android.material.textfield.TextInputEditText? = null
    private var overlayHistorySend: FloatingActionButton? = null
    private var overlayHistoryStatus: TextView? = null
    private val overlayHistoryDedupeIds = mutableSetOf<String>()
    private var overlayChatViewModel: ChatViewModel? = null
    /** Владелец Compose для ленты сообщений (отдельное окно). */
    private var overlayStripComposeOwner: OverlayChatComposeOwner? = null
    private var overlayCollapseButton: ImageView? = null
    /** Unread messages counter for the overlay Chat button badge. */
    @Volatile
    private var overlayUnreadChatCount: Int = 0
    private var overlayChatBadgeText: TextView? = null

    private data class OverlayWindowFlagSnap(
        val view: View,
        val params: WindowManager.LayoutParams,
        val prevFlags: Int,
        val prevVisibility: Int,
        /** Окно снято с [WindowManager], чтобы системный пикер (обычная Activity) был поверх. */
        val detachedForPicker: Boolean = false,
    )

    /** Снимок окон overlay на время системного пикера (TYPE_APPLICATION_OVERLAY выше Activity — иначе галерея «под» чатом). */
    private val overlayTouchPassthroughSnaps = mutableListOf<OverlayWindowFlagSnap>()

    private fun suspendOverlayWindowsForSystemActivity() {
        OverlayChatInteractionHold.beginOverlaySystemPickerSession()
        val mgr = windowManager
        if (mgr == null) {
            OverlayChatInteractionHold.endOverlaySystemPickerSession()
            return
        }
        fun snap(
            params: WindowManager.LayoutParams?,
            view: View?,
            hideFromScreen: Boolean,
        ) {
            if (params == null || view == null || !view.isAttachedToWindow) return
            overlayTouchPassthroughSnaps.add(
                OverlayWindowFlagSnap(
                    view = view,
                    params = params,
                    prevFlags = params.flags,
                    prevVisibility = view.visibility,
                ),
            )
            if (hideFromScreen && view.visibility != View.GONE) {
                view.visibility = View.GONE
            }
            val with = params.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
            if (params.flags != with) {
                params.flags = with
                runCatching { mgr.updateViewLayout(view, params) }
            }
        }
        // Панель и ленту прячем. Полноэкранный чат снимаем с WM: TYPE_APPLICATION_OVERLAY всегда выше
        // обычной Activity — Photo Picker иначе остаётся под чатом и не кликается.
        detachOverlayChatPanelForPicker(mgr)
        snap(overlayMainWindowParams, overlayView, hideFromScreen = true)
        snap(chatStripParams, chatStripHost, hideFromScreen = true)
        overlayTicker.applyTouchPassthrough(true)
        overlayCommandsPopover.hide()
        overlayAllianceOnlinePopover.hide()
        quickCommandsPopover.hide()
    }

    private fun detachOverlayChatPanelForPicker(mgr: WindowManager) {
        val root = overlayChatTeamRoot ?: return
        val params = overlayChatTeamParams ?: return
        if (!root.isAttachedToWindow) return
        overlayTouchPassthroughSnaps.add(
            OverlayWindowFlagSnap(
                view = root,
                params = params,
                prevFlags = params.flags,
                prevVisibility = root.visibility,
                detachedForPicker = true,
            ),
        )
        runCatching { mgr.removeView(root) }
            .onFailure { e ->
                Log.w(TAG, "detachOverlayChatPanelForPicker: removeView failed", e)
            }
    }

    private fun resumeOverlayWindowsAfterSystemActivity() {
        val mgr = windowManager
        val hadSuspendedWindows = overlayTouchPassthroughSnaps.isNotEmpty()
        if (hadSuspendedWindows && mgr != null) {
            for (snap in overlayTouchPassthroughSnaps) {
                if (snap.detachedForPicker) {
                    if (!snap.view.isAttachedToWindow) {
                        runCatching { mgr.addView(snap.view, snap.params) }
                            .onFailure { e ->
                                Log.e(TAG, "resumeOverlayWindows: re-attach chat panel failed", e)
                            }
                    }
                } else {
                    snap.params.flags = snap.prevFlags
                    snap.view.visibility = snap.prevVisibility
                    if (snap.view.isAttachedToWindow) {
                        runCatching { mgr.updateViewLayout(snap.view, snap.params) }
                    }
                }
            }
            overlayTouchPassthroughSnaps.clear()
        }
        overlayTicker.applyTouchPassthrough(false)
        if (OverlayChatInteractionHold.isOverlaySystemPickerSessionActive()) {
            OverlayChatInteractionHold.endOverlaySystemPickerSession()
        }
        rebalanceOverlayFullscreenZOrder()
        repairDetachedOverlayChatTeamPanelIfNeeded()
        repairDetachedOverlayShellIfNeeded()
    }

    private val overlaySystemResultReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val i = intent ?: return
            val requestCode = i.getIntExtra(OverlaySystemDialogActivity.EXTRA_REQUEST_CODE, -1)
            if (requestCode < 0) return
            mainHandler.post {
                resumeOverlayWindowsAfterSystemActivity()
                if (i.action == OverlaySystemDialogActivity.ACTION_OVERLAY_SYSTEM_UI_FINISHED) {
                    return@post
                }
                val owner = overlayChatTeamComposeOwner ?: return@post
                when (i.action) {
                    OverlaySystemDialogActivity.ACTION_OVERLAY_PICK_IMAGES_RESULT -> {
                        val uris = if (android.os.Build.VERSION.SDK_INT >= 33) {
                            i.getParcelableArrayListExtra(
                                OverlaySystemDialogActivity.EXTRA_URIS,
                                Uri::class.java,
                            ).orEmpty()
                        } else {
                            @Suppress("DEPRECATION")
                            i.getParcelableArrayListExtra<Uri>(OverlaySystemDialogActivity.EXTRA_URIS).orEmpty()
                        }
                        val data = Intent().apply {
                            if (uris.isNotEmpty()) {
                                val clip = ClipData.newRawUri("images", uris.first())
                                uris.drop(1).forEach { clip.addItem(ClipData.Item(it)) }
                                clipData = clip
                            }
                        }
                        owner.activityResultRegistry.dispatchResult(
                            requestCode,
                            android.app.Activity.RESULT_OK,
                            data,
                        )
                    }
                    OverlaySystemDialogActivity.ACTION_OVERLAY_GET_CONTENT_RESULT -> {
                        val uri = if (android.os.Build.VERSION.SDK_INT >= 33) {
                            i.getParcelableExtra(OverlaySystemDialogActivity.EXTRA_URI, Uri::class.java)
                        } else {
                            @Suppress("DEPRECATION")
                            i.getParcelableExtra(OverlaySystemDialogActivity.EXTRA_URI)
                        }
                        val data = if (uri != null) Intent().setData(uri) else Intent()
                        owner.activityResultRegistry.dispatchResult(
                            requestCode,
                            android.app.Activity.RESULT_OK,
                            data,
                        )
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
                    is androidx.activity.result.contract.ActivityResultContracts.GetContent ->
                        OverlaySystemDialogActivity.KIND_GET_CONTENT
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
                    activityResultRegistry.dispatchResult(
                        requestCode,
                        android.app.Activity.RESULT_CANCELED,
                        Intent(),
                    )
                    return
                }
                val i = Intent(this@CombatOverlayService, OverlaySystemDialogActivity::class.java).apply {
                    addFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_ACTIVITY_CLEAR_TOP or
                            Intent.FLAG_ACTIVITY_SINGLE_TOP or
                            Intent.FLAG_ACTIVITY_NO_ANIMATION,
                    )
                    putExtra(OverlaySystemDialogActivity.EXTRA_KIND, kind)
                    putExtra(OverlaySystemDialogActivity.EXTRA_REQUEST_CODE, requestCode)
                    if (kind == OverlaySystemDialogActivity.KIND_GET_CONTENT) {
                        val mime = (input as? String)?.takeIf { it.isNotBlank() } ?: "image/*"
                        putExtra(OverlaySystemDialogActivity.EXTRA_CONTENT_MIME, mime)
                    }
                }
                suspendOverlayWindowsForSystemActivity()
                mainHandler.post {
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
                        activityResultRegistry.dispatchResult(
                            requestCode,
                            android.app.Activity.RESULT_CANCELED,
                            Intent(),
                        )
                    }
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

    /** Throttle для Log при «панель скрыта» — не дёргаем FGS-текст при каждом тике гейта. */
    private var gateNotifyKey: String = ""
    private var lastStripRenderSignature: String? = null
    @Volatile
    private var gateCheckInFlight = false
    private var lastGateDiagLogMs: Long = 0L
    private var lastForegroundHintPkg: String? = null
    @Volatile
    private var lastOverlayInGameAtMs: Long = 0L
    /** Не вызывать [NotificationManager.notify] с тем же текстом подряд (лишние всплытия на части OEM). */
    private var lastForegroundNotificationText: String? = null
    private var lastForegroundMicActive: Boolean = false

    private val gameGateRunnable = Runnable {
        runCatching { tickGameGate() }
    }

    private val overlayVoiceConnectRunnable = Runnable {
        startOverlayVoiceIfRaidAvailable()
    }

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "onCreate: starting overlay service")
        // If the user has disabled the panel, do not spin up the FGS at all.
        // This avoids slow enable/disable cycles on OEM ROMs (HyperOS/MIUI) caused by starting an FGS
        // just to immediately stop it.
        if (!AppContainer.from(this).userSettingsPreferences.isOverlayPanelEnabled()) {
            Log.w(TAG, "onCreate: overlay disabled in prefs, stopping self")
            isServiceInstanceActive = false
            _serviceRunning.value = false
            _overlayVisible.value = false
            stopSelf()
            return
        }
        OverlayForegroundNotifications.ensureChannel(this)
        val notification = OverlayForegroundNotifications.build(
            this,
            foregroundNotificationIdleText(),
            AppContainer.from(this).userSettingsPreferences.isQuietMode(),
        )
        lastForegroundNotificationText = foregroundNotificationIdleText()
        // Тип FGS в манифесте — dataSync (см. AndroidManifest); иначе microphone + API 34 ломают onCreate без RECORD_AUDIO / eligible state.
        ServiceCompat.startForeground(
            this,
            OverlayForegroundNotifications.NOTIFICATION_ID,
            notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
        )
        Log.i(TAG, "onCreate: startForeground OK")
        isServiceInstanceActive = true
        _serviceRunning.value = true
        _overlayVisible.value = false
        mainHandler.post { tickGameGate() }
    }

    /**
     * Система (OEM / нехватка памяти) может снять overlay с экрана, оставив ссылки на View.
     * Тогда [showOverlayControl] выходит по `overlayView != null` и панель не появляется, пока
     * пользователь не перезапустит сервис тумблером.
     */
    private fun repairDetachedOverlayShellIfNeeded() {
        val v = overlayView ?: return
        val wm = windowManager
        if (wm == null || v.isAttachedToWindow) return
        val params = overlayMainWindowParams
        if (params != null) {
            Log.w(TAG, "repairDetachedOverlayShellIfNeeded: re-attaching overlay shell")
            runCatching { wm.addView(v, params) }
                .onSuccess { return }
                .onFailure { e ->
                    Log.w(TAG, "repairDetachedOverlayShellIfNeeded: re-attach failed, rebuilding", e)
                }
        }
        Log.w(
            TAG,
            "repairDetachedOverlayShellIfNeeded: overlay shell detached (chatPanel=$overlayChatTeamPanelVisible), rebuilding shell only",
        )
        // Не вызывать removeOverlayControl(): на части ROM addView полноэкранного чата временно отцепляет
        // пузырь панели — полный teardown закрывал бы чат и требовал переключать «Показывать панель».
        overlayView = null
        overlayBubble = null
        overlayHistoryFab = null
        overlayMainWindowParams = null
        overlayOuterRow = null
        overlayControlsStack = null
        overlayMessageRow = null
        overlayMessageFabColumn = null
        overlayBtnMessageFab = null
        overlayCollapseButton = null
        overlayPanelAnchoredEnd = false
        overlayPanelStableAnchorWidthPx = 0
        _overlayVisible.value = false
        runCatching { showOverlayControl() }
        repairDetachedOverlayChatTeamPanelIfNeeded()
    }

    /**
     * На части ROM addView AlertDialog/системного окна временно отцепляет полноэкранный чат/команду.
     * Восстанавливаем то же [overlayChatTeamRoot], не пересоздавая Compose.
     */
    private fun repairDetachedOverlayChatTeamPanelIfNeeded() {
        if (OverlayChatInteractionHold.isOverlaySystemPickerSessionActive()) {
            return
        }
        if (overlayTouchPassthroughSnaps.any { it.detachedForPicker }) {
            return
        }
        if (!overlayChatTeamPanelVisible && !OverlayChatInteractionHold.isFullscreenChatTeamPanelVisible) {
            return
        }
        val root = overlayChatTeamRoot ?: return
        val params = overlayChatTeamParams ?: return
        val mgr = windowManager ?: systemWindowManager() ?: return
        if (root.isAttachedToWindow) return
        Log.w(TAG, "repairDetachedOverlayChatTeamPanelIfNeeded: fullscreen chat/team detached, re-attaching")
        runCatching { mgr.addView(root, params) }
            .onSuccess { rebalanceOverlayFullscreenZOrder() }
            .onFailure { e ->
                Log.e(TAG, "repairDetachedOverlayChatTeamPanelIfNeeded: re-attach failed", e)
            }
    }

    private fun shouldKeepOverlayWindows(): Boolean =
        overlayTouchPassthroughSnaps.isNotEmpty() ||
            OverlayChatInteractionHold.isOverlaySystemPickerSessionActive() ||
            overlayChatTeamPanelVisible ||
            OverlayChatInteractionHold.isFullscreenChatTeamPanelVisible ||
            overlayCommandsPopover.isShowing() ||
            OverlayChatInteractionHold.isGameForegroundGateSuppressed()

    private fun systemWindowManager(): WindowManager? =
        runCatching { getSystemService(Context.WINDOW_SERVICE) as WindowManager }.getOrNull()

    /** Показать оверлей при наличии разрешения «поверх окон» (после проверки режима «только в игре»). */
    private fun ensureOverlayIfPermitted() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            Log.w(TAG, "ensureOverlayIfPermitted: SYSTEM_ALERT_WINDOW not granted for ${packageName}")
            logGateStateThrottled("overlayGate: нет разрешения «поверх других приложений»")
            return
        }
        repairDetachedOverlayShellIfNeeded()
        repairDetachedOverlayChatTeamPanelIfNeeded()
        if (overlayView == null) {
            val result = runCatching { showOverlayControl() }
            if (result.isFailure) {
                Log.e(TAG, "ensureOverlayIfPermitted: showOverlayControl crashed", result.exceptionOrNull())
                // Avoid a crash-loop with a half-initialized state.
                runCatching { removeOverlayControl() }
                _overlayVisible.value = false
                Log.w(TAG, "ensureOverlayIfPermitted: showOverlayControl failed, strip overlay")
            }
        }
    }

    private fun logGateStateThrottled(content: String) {
        if (content == gateNotifyKey) return
        gateNotifyKey = content
        Log.i(TAG, content)
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
        if (gateCheckInFlight) return
        gateCheckInFlight = true
        val targets = prefs.getOverlayTargetGamePackages()
        val activityTokens = prefs.getOverlayTargetGameActivityTokens()
        serviceScope.launch {
            try {
                val hasUsageAccess = GameForegroundGate.hasUsageStatsAccess(this@CombatOverlayService)
                val hintedComp = runCatching {
                    GameForegroundGate.lastResumedComponent(this@CombatOverlayService)
                }.getOrNull()
                val hintedPkg = hintedComp?.packageName
                lastForegroundHintPkg = hintedPkg
                if (activityTokens.isNotEmpty()) {
                    Log.d(
                        OVERLAY_DIAG_TAG,
                        "activityGate hint pkg=${hintedComp?.packageName ?: "-"} cls=${hintedComp?.className ?: "-"} tokens=${activityTokens.joinToString()}",
                    )
                }
                val targetSet = targets.toSet()
                val inGame = if (!hasUsageAccess || targets.isEmpty()) {
                    false
                } else {
                    val probe = GameForegroundGate.quickTargetForegroundProbe(
                        context = this@CombatOverlayService,
                        targetGamePackages = targets,
                        allowedActivitySubstrings = activityTokens,
                    )
                    when (probe) {
                        GameForegroundGate.QuickForegroundProbe.IN_TARGET -> true
                        GameForegroundGate.QuickForegroundProbe.NOT_IN_TARGET -> false
                        GameForegroundGate.QuickForegroundProbe.NEED_FULL_HEURISTICS ->
                            GameForegroundGate.shouldShowOverlay(
                                context = this@CombatOverlayService,
                                targetGamePackages = targets,
                                allowedActivitySubstrings = activityTokens,
                            )
                    }
                }
                val nowMs = System.currentTimeMillis()
                if (inGame) {
                    lastOverlayInGameAtMs = nowMs
                }
                val shouldShow = when {
                    inGame -> true
                    OverlayChatInteractionHold.isOverlaySystemPickerSessionActive() -> true
                    shouldKeepOverlayWindows() &&
                        nowMs - lastOverlayInGameAtMs < OVERLAY_INGAME_GRACE_MS &&
                        !GameForegroundGate.isConflictingForegroundHint(
                            hintedPkg,
                            targetSet,
                            packageName,
                        ) -> true
                    else -> false
                }
                mainHandler.post {
                    val diagNowMs = System.currentTimeMillis()
                    if (diagNowMs - lastGateDiagLogMs >= 25_000L) {
                        lastGateDiagLogMs = diagNowMs
                        val draw = canDrawOverlaysNow()
                        if (!hasUsageAccess || !shouldShow || !draw || overlayView == null) {
                            Log.i(
                                TAG,
                                "overlayGate usage=$hasUsageAccess show=$shouldShow " +
                                    "hint=${hintedPkg ?: "-"} drawOverlays=$draw overlayAttached=${overlayView != null} " +
                                    "targets=${targets.joinToString()}",
                            )
                        }
                    }
                    applyGameGateState(
                        hasUsageAccess = hasUsageAccess,
                        shouldShow = shouldShow,
                    )
                    gateCheckInFlight = false
                    scheduleGameGateTick(nextGameGateDelayMs())
                }
            } catch (_: Throwable) {
                mainHandler.post {
                    gateCheckInFlight = false
                    scheduleGameGateTick(nextGameGateDelayMs())
                }
            }
        }
    }

    private fun scheduleGameGateTick(delayMs: Long = nextGameGateDelayMs()) {
        mainHandler.removeCallbacks(gameGateRunnable)
        mainHandler.postDelayed(gameGateRunnable, delayMs)
    }

    private fun nextGameGateDelayMs(): Long {
        if (overlayView != null) return GAME_GATE_POLL_ACTIVE_MS
        if (overlayChatTeamPanelVisible || OverlayChatInteractionHold.isFullscreenChatTeamPanelVisible) {
            return GAME_GATE_POLL_ACTIVE_MS
        }
        if (shouldKeepOverlayWindows()) return GAME_GATE_POLL_WARM_MS
        val now = System.currentTimeMillis()
        if (now - lastOverlayInGameAtMs < OVERLAY_INGAME_GRACE_MS) return GAME_GATE_POLL_WARM_MS
        return GAME_GATE_POLL_IDLE_MS
    }

    private fun scheduleOverlayVoiceConnect() {
        cancelOverlayVoiceConnectScheduled()
        val prefs = AppContainer.from(this).userSettingsPreferences
        if (!prefs.isOverlayVoiceMicEnabled() && !prefs.isOverlayVoiceSoundEnabled()) {
            return
        }
        mainHandler.postDelayed(overlayVoiceConnectRunnable, OVERLAY_VOICE_CONNECT_DELAY_MS)
    }

    private fun cancelOverlayVoiceConnectScheduled() {
        mainHandler.removeCallbacks(overlayVoiceConnectRunnable)
    }

    private fun ensureOverlayVoiceStarted() {
        cancelOverlayVoiceConnectScheduled()
        startOverlayVoiceIfRaidAvailable()
    }

    private fun applyGameGateState(
        hasUsageAccess: Boolean,
        shouldShow: Boolean,
    ) {
        if (!shouldShow) {
            dismissOverlayUiBecauseNotInGame(
                logWaitingForGame = true,
            )
            return
        }
        // Пикер/хост SquadRelay в usage-stats — не закрывать оверлей-чат (см. [OverlayChatInteractionHold]).
        if (lastForegroundHintPkg == packageName &&
            !OverlayChatInteractionHold.isOverlaySystemPickerSessionActive() &&
            !overlayChatTeamPanelVisible &&
            !OverlayChatInteractionHold.isFullscreenChatTeamPanelVisible &&
            !shouldKeepOverlayWindows() &&
            overlayTouchPassthroughSnaps.isEmpty()
        ) {
            dismissOverlayUiBecauseNotInGame(logWaitingForGame = false)
            return
        }
        if (!hasUsageAccess) {
            logGateStateThrottled(
                "overlayGate: нет доступа к статистике использования — панель скрыта",
            )
            if (overlayView != null) {
                removeOverlayControl(force = true)
            }
            return
        }
        gateNotifyKey = ""
        if (!canDrawOverlaysNow()) {
            logGateStateThrottled("overlayGate: нет разрешения «поверх других приложений»")
            if (overlayView != null) {
                removeOverlayControl(force = true)
            }
            return
        }
        ensureOverlayIfPermitted()
    }

    /**
     * Снимает панель/чат при уходе из игры. Не опирается на [shouldKeepOverlayWindows]: suppress/пикер
     * иначе оставляют оверлей «висеть» после сворачивания или закрытия игры.
     */
    private fun dismissOverlayUiBecauseNotInGame(logWaitingForGame: Boolean) {
        if (OverlayChatInteractionHold.isOverlaySystemPickerSessionActive()) {
            return
        }
        overlayCommandsPopover.hide()
        overlayAllianceOnlinePopover.hide()
        quickCommandsPopover.hide()
        OverlayChatInteractionHold.clearStaleSuppressForGameBackground(
            chatTeamPanelVisible = false,
            commandsPopoverShowing = false,
        )
        resumeOverlayWindowsAfterSystemActivity()
        if (overlayChatTeamPanelVisible || OverlayChatInteractionHold.isFullscreenChatTeamPanelVisible) {
            hideOverlayChatTeamPanel()
        }
        if (logWaitingForGame) {
            val hint = lastForegroundHintPkg?.takeIf { it.isNotBlank() }
            val content = if (hint != null) {
                "overlayGate: ${getString(R.string.overlay_notif_waiting_for_game)} ($hint)"
            } else {
                "overlayGate: ${getString(R.string.overlay_notif_waiting_for_game)}"
            }
            logGateStateThrottled(content)
        }
        if (overlayView != null) {
            removeOverlayControl(force = true)
        }
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
        cancelOverlayVoiceConnectScheduled()
        runCatching { hideOverlayChatTeamPanel() }
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
        Log.i(TAG, "onStartCommand: action=${intent?.action} enabled=${prefs.isOverlayPanelEnabled()}")
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
                    ACTION_START_RECORDING -> ensureVoiceSession().setMicEnabled(true)
                    ACTION_STOP_RECORDING -> ensureVoiceSession().setMicEnabled(false)
                }
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        Log.w(TAG, "onDestroy: overlay service is being destroyed")
        isServiceInstanceActive = false
        _serviceRunning.value = false
        gateCheckInFlight = false
        mainHandler.removeCallbacks(gameGateRunnable)
        cancelOverlayVoiceConnectScheduled()
        lastForegroundNotificationText = null
        lastForegroundMicActive = false
        speechPipeline.destroy()
        stopOverlayVoice()
        overlayTicker.hideTicker()
        runCatching { hideOverlayChatTeamPanel() }
        removeOverlayControl(force = true)
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

    private fun foregroundNotificationIdleText(): String =
        getString(R.string.overlay_notif_fgs_idle)

    /**
     * Обновляет FGS-уведомление только для микрофона в эфире.
     * Статусы гейта/распознавания/«боевой режим» не трогаем — смена текста даёт всплывашку при входе в игру.
     */
    private fun updateNotification(content: String) {
        val micActiveText = getString(R.string.overlay_notif_voice_active)
        if (content != micActiveText) return
        if (content == lastForegroundNotificationText) return
        lastForegroundNotificationText = content
        OverlayForegroundNotifications.notify(
            this,
            content,
            AppContainer.from(this).userSettingsPreferences.isQuietMode(),
        )
    }

    private fun scheduleStripTick() {
        mainHandler.removeCallbacks(stripTickRunnable)
        val delay = if (panelCollapsed) STRIP_TICK_COLLAPSED_MS else STRIP_TICK_MS
        mainHandler.postDelayed(stripTickRunnable, delay)
    }

    private fun cancelStripTick() {
        mainHandler.removeCallbacks(stripTickRunnable)
    }

    private fun dismissStripMessage(msg: ChatMessage) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { dismissStripMessage(msg) }
            return
        }
        val key = msg._id?.takeIf { it.isNotBlank() } ?: msg.stableKey()
        stripBuffer.removeMessageWithKey(key)
        lastStripRenderSignature = null
        // Сразу снимаем зоны крестика, пока Compose не пересчитал ленту — иначе один кадр с «призрачными» rect блокирует игру.
        updateStripDismissScreenRects(emptyList())
        refreshOverlayChatStrip()
    }

    private fun updateStripDismissScreenRects(rects: List<Rect>) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { updateStripDismissScreenRects(rects) }
            return
        }
        val nonEmpty = rects.filterNot { it.isEmpty }
        (chatStripHost as? OverlayStripPassthroughFrameLayout)?.dismissRectsInCompose = nonEmpty
        syncChatStripWindowTouchPassthrough()
    }

    /**
     * Когда зон крестика нет, на окно ленты ставим [FLAG_NOT_TOUCHABLE]: иначе на части OEM
     * касания по «пустому» месту после закрытия карточек остаются в оверлее и блокируют игру,
     * даже если корень ленты не забирает жест в [OverlayStripPassthroughFrameLayout].
     */
    private fun syncChatStripWindowTouchPassthrough() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { syncChatStripWindowTouchPassthrough() }
            return
        }
        val host = chatStripHost as? OverlayStripPassthroughFrameLayout ?: return
        val params = chatStripParams ?: return
        val mgr = windowManager ?: systemWindowManager() ?: return
        if (!host.isAttachedToWindow) return
        val hasDismissZones = host.dismissRectsInCompose.isNotEmpty()
        val mask = WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        val newFlags = if (hasDismissZones) {
            params.flags and mask.inv()
        } else {
            params.flags or mask
        }
        if (params.flags == newFlags) return
        params.flags = newFlags
        runCatching { mgr.updateViewLayout(host, params) }.onFailure { e ->
            Log.w(TAG, "syncChatStripWindowTouchPassthrough updateViewLayout failed", e)
        }
    }

    private fun refreshOverlayChatStrip() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { refreshOverlayChatStrip() }
            return
        }
        stripBuffer.prune()
        val preview = stripBuffer.visibleForPreview()
        val signature = preview.joinToString(separator = "|") { msg ->
            val key = msg._id?.takeIf { it.isNotBlank() } ?: msg.stableKey()
            val att = msg.attachments.joinToString(";") { a -> "${a.kind}:${a.url}:${a.mimeType ?: ""}" }
            "$key:${msg.text.hashCode()}:${msg.senderRole}:${msg.senderId}:" +
                "${msg.senderTeamTag ?: ""}:${msg.senderTelegramUsername ?: ""}:$att"
        }
        if (signature == lastStripRenderSignature) return
        Log.d(
            OVERLAY_DIAG_TAG,
            "stripRefresh apply preview=${preview.size} sigLen=${signature.length} " +
                preview.joinToString { m ->
                    "id=${m._id} room=${m.roomId} len=${m.text.length}"
                },
        )
        chatStripPreviewFlow.value = preview
        lastStripRenderSignature = signature
        // Ensure the top strip window exists and is visible whenever preview changes.
        mainHandler.post {
            val wm = windowManager ?: systemWindowManager() ?: return@post
            runCatching { ensureChatStripWindow(wm) }
            chatStripHost?.visibility =
                if (overlayChatTeamPanelVisible) View.GONE else View.VISIBLE
            rebalanceOverlayChatStripZOrder()
        }
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
        val raidId = AppContainer.from(this).chatRoomPreferences.getRaidRoomId()
        if (raidId == null || (msg.roomId.isNotBlank() && msg.roomId != raidId)) {
            return
        }
        Log.d(
            OVERLAY_DIAG_TAG,
            "processMsg id=${msg._id} room=${msg.roomId} sender=${msg.senderId} textLen=${msg.text.length}",
        )
        stripBuffer.upsert(msg)
        stripBuffer.mergeReceiveTimeline(msg, jwtSubFromAccessToken())
        refreshOverlayChatStrip()
        val selfId = jwtSubFromAccessToken()
        if (!overlayChatTeamPanelVisible && !selfId.isNullOrBlank() && msg.senderId != selfId) {
            overlayUnreadChatCount = (overlayUnreadChatCount + 1).coerceAtMost(99)
            mainHandler.post { updateOverlayChatBadge() }
        }
        if (overlayChatTeamPanelVisible) {
            mainHandler.post { appendOverlayHistoryIfVisible(msg) }
        }
    }

    private fun updateOverlayChatBadge() {
        val badge = overlayChatBadgeText ?: return
        val count = overlayUnreadChatCount.coerceAtLeast(0)
        if (count <= 0) {
            badge.visibility = View.GONE
            badge.text = ""
            return
        }
        badge.visibility = View.VISIBLE
        badge.text = if (count > 99) "99+" else count.toString()
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
        Log.d(OVERLAY_DIAG_TAG, "stripNotice textLen=${message.length}")
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

    private fun accessTokenOrNull(): String? =
        runCatching { AppContainer.from(this).tokenStore.getAccessToken() }.getOrNull()

    private fun jwtSubFromAccessToken(): String? = JwtAccessTokenClaims.sub(accessTokenOrNull())

    private fun jwtRoleFromAccessToken(): String = JwtAccessTokenClaims.role(accessTokenOrNull()).orEmpty()

    private fun removeChatStripWindow(forManager: WindowManager? = null) {
        val mgr = forManager ?: windowManager ?: systemWindowManager() ?: return
        val host = chatStripHost ?: return
        runCatching { mgr.removeView(host) }
        chatStripHost = null
        chatStripParams = null
        chatStripClipRoot = null
        chatStripLines = null
        chatStripCompose = null
        overlayStripComposeOwner?.destroy()
        overlayStripComposeOwner = null
    }

    private fun repairDetachedChatStripIfNeeded(manager: WindowManager) {
        val host = chatStripHost ?: return
        if (!host.isAttachedToWindow) {
            Log.w(TAG, "repairDetachedChatStripIfNeeded: strip host detached, rebuilding")
            removeChatStripWindow(manager)
        }
    }

    private fun ensureChatStripWindow(manager: WindowManager) {
        repairDetachedChatStripIfNeeded(manager)
        if (chatStripHost != null) return
        // Compose in overlay windows requires ViewTree owners (Lifecycle/VM/SavedState), otherwise it crashes
        // with "ViewTreeLifecycleOwner not found" on some devices/Compose versions.
        val owner = overlayStripComposeOwner ?: OverlayChatComposeOwner().also { overlayStripComposeOwner = it }
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val stripMaxWidth =
            (resources.displayMetrics.widthPixels - dp(20)).coerceAtLeast(dp(220))
        // Touches pass through except dismiss rects handled by [OverlayStripPassthroughFrameLayout].
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            OverlayWindowLayout.popupWindowFlags(),
            android.graphics.PixelFormat.TRANSLUCENT,
        ).apply {
            OverlayWindowLayout.applyPopupLayoutCompat(this)
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            x = 0
            // Slight top margin from the game screen edge.
            y = dp(10)
        }

        val compose = ComposeView(this).apply {
            setContent {
                val preview by chatStripPreviewFlow.collectAsState()
                val selfId = jwtSubFromAccessToken()
                SquadRelayTheme {
                    OverlayChatStrip(
                        messages = preview,
                        selfUserId = selfId,
                        onDismissMessage = { m -> dismissStripMessage(m) },
                        onDismissRegionsChanged = { updateStripDismissScreenRects(it) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 2.dp, vertical = 2.dp),
                    )
                }
            }
        }
        chatStripCompose = compose

        val clipRoot = FrameLayout(this).apply {
            clipChildren = false
            OverlayChatStripUi.styleStripContainer(this@CombatOverlayService, this)
            addView(
                compose,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    Gravity.TOP,
                ),
            )
        }

        val host = OverlayStripPassthroughFrameLayout(this).apply {
            elevation = 18f
            setPadding(dp(10), 0, dp(10), 0)
            setBackgroundColor(Color.TRANSPARENT)
            composeLocatorView = compose
            setViewTreeLifecycleOwner(owner)
            setViewTreeViewModelStoreOwner(owner)
            setViewTreeSavedStateRegistryOwner(owner)
            setViewTreeOnBackPressedDispatcherOwner(owner)
            addView(
                clipRoot,
                FrameLayout.LayoutParams(
                    stripMaxWidth,
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                ),
            )
        }

        val attach = runCatching { manager.addView(host, params) }
        if (attach.isFailure) {
            Log.e(TAG, "WindowManager.addView(chatStrip) failed", attach.exceptionOrNull())
            return
        }
        Log.d(OVERLAY_DIAG_TAG, "chatStripWindow attached stripOwner=${overlayStripComposeOwner != null}")
        chatStripHost = host
        chatStripParams = params
        chatStripClipRoot = clipRoot
        chatStripLines = null
        syncChatStripWindowTouchPassthrough()
    }

    private val voiceMicPermissionReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val i = intent ?: return
            if (i.action != OverlaySystemDialogActivity.ACTION_OVERLAY_MIC_PERMISSION_RESULT) return
            if (i.getIntExtra(OverlaySystemDialogActivity.EXTRA_REQUEST_CODE, -1) != VOICE_MIC_PERMISSION_REQUEST) {
                return
            }
            mainHandler.post {
                val granted = i.getBooleanExtra(OverlaySystemDialogActivity.EXTRA_GRANTED, false)
                if (granted && pendingVoiceMicEnable) {
                    ensureVoiceSession().setMicEnabled(true)
                    overlayVoiceControls?.applyState(
                        ensureVoiceSession().micOn,
                        ensureVoiceSession().soundOn,
                    )
                } else if (!granted) {
                    Toast.makeText(
                        this@CombatOverlayService,
                        R.string.overlay_voice_mic_permission_needed,
                        Toast.LENGTH_SHORT,
                    ).show()
                }
                pendingVoiceMicEnable = false
            }
        }
    }

    private fun beginOverlayChatSubscription() {
        if (overlayMessageListener != null) return
        registerVoiceMicPermissionReceiver()
        cancelStripTick()
        val listener: (ChatMessage) -> Unit = { msg ->
            mainHandler.post {
                val raidId = AppContainer.from(this).chatRoomPreferences.getRaidRoomId()
                if (raidId == null) return@post
                if (msg.roomId.isNotBlank() && msg.roomId != raidId) {
                    Log.d(
                        OVERLAY_DIAG_TAG,
                        "overlayListener skipRoom msgRoom=${msg.roomId} raid=$raidId id=${msg._id}",
                    )
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
            scheduleOverlayVoiceConnect()
        }
        serviceScope.launch {
            val container = AppContainer.from(this@CombatOverlayService)
            val raidId = container.chatRoomPreferences.getRaidRoomId()
            if (raidId == null) {
                mainHandler.post {
                    setStripPlainMessage(getString(R.string.overlay_strip_no_raid))
                }
                return@launch
            }
            container.chatRepository.loadRecentMessages(raidId, null, OVERLAY_HISTORY_LOAD)
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
        cancelOverlayVoiceConnectScheduled()
        stopOverlayVoice()
        unregisterVoiceMicPermissionReceiver()
        presenceHeartbeat.stop()
        serviceScope.launch {
            runCatching {
                AppContainer.from(this@CombatOverlayService).usersRepository.updatePresence(
                    OVERLAY_PRESENCE_AWAY,
                )
            }
        }
        cancelStripTick()
        if (overlayChatTeamPanelVisible || OverlayChatInteractionHold.isFullscreenChatTeamPanelVisible) {
            hideOverlayChatTeamPanel()
        }
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
     * Определяет, у левого или правого края экрана закреплена панель (для поповеров «Онлайн» / «Команды»).
     * Раньше здесь переставлялся подряд «атака/защита» — теперь только якорь без перестановки детей.
     */
    private fun syncOverlayPanelEdgeLayout() {
        val params = overlayMainWindowParams ?: return
        val root = overlayView ?: return
        val w = root.width
        if (w <= 0) {
            root.post { syncOverlayPanelEdgeLayout() }
            return
        }
        val screenW = resources.displayMetrics.widthPixels
        val wAnchor = maxOf(w, maxOf(overlayPanelStableAnchorWidthPx, dp(160)))
        overlayPanelAnchoredEnd = params.x + wAnchor / 2 >= screenW / 2
        syncOverlayVoiceExpandLayout()
    }

    private fun syncOverlayVoiceExpandLayout() {
        overlayVoiceControls?.setExpandTowardStart(overlayPanelAnchoredEnd)
        overlayMessageFabColumn?.gravity =
            if (overlayPanelAnchoredEnd) Gravity.END else Gravity.START
    }

    private fun showOverlayControl() {
        repairDetachedOverlayShellIfNeeded()
        repairDetachedOverlayChatTeamPanelIfNeeded()
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
            overlayBtnMessageFab = null
            overlayCollapseButton = null
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
            gravity = Gravity.TOP or Gravity.START
            val prefs = AppContainer.from(this@CombatOverlayService).userSettingsPreferences
            val preset = OverlayLayoutDp.forPreset(prefs.getOverlayLayoutPreset())
            // x/y — от левого и верхнего края; при раскрытии панель растёт вниз, кнопка collapse не смещается.
            val screenW = resources.displayMetrics.widthPixels
            val screenH = resources.displayMetrics.heightPixels
            // Until view is measured, clamp using conservative minimum size so the panel can't spawn off-screen.
            val minW = dp(120)
            val minH = dp(52)
            val rawX = prefs.getOverlayPanelPosXPx() ?: dp(preset.toggleX)
            val savedY = prefs.getOverlayPanelPosYPx()
            x = rawX.coerceIn(0, (screenW - minW).coerceAtLeast(0))
            y = prefs.resolveOverlayPanelTopYPx(
                screenHeightPx = screenH,
                savedYPx = savedY,
                defaultTopYPx = dp(preset.toggleY),
                fallbackPanelHeightPx = minH,
            )
            if (rawX != x || savedY != y) {
                Log.w(TAG, "overlay pos clamped from ($rawX,$savedY) to ($x,$y)")
                runCatching { prefs.setOverlayPanelPosPx(x = x, y = y) }
            }
        }

        var initialX = 0
        var initialY = 0
        var startTouchX = 0f
        var startTouchY = 0f
        var isDragging = false
        var dragScreenW = 0
        var dragScreenH = 0
        var dragClampW = 0
        var dragClampH = 0
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
        overlayCollapseButton = btnCollapse
        val btnMessage = makeMiniFab(
            iconRes = R.drawable.ic_overlay_history,
            cd = getString(R.string.overlay_cd_commands),
        )
        val btnChatTeam = makeMiniFab(
            iconRes = R.drawable.ic_overlay_chat,
            cd = getString(R.string.overlay_cd_chat_and_team),
        )
        val voiceControls = OverlayVoiceControls(
            context = this,
            fabCtx = fabCtx,
            dp = { dp(it) },
            makeMiniFab = ::makeMiniFab,
        ).also { controls ->
            controls.onSoundToggle = {
                if (!panelCollapsed) {
                    ensureOverlayVoiceStarted()
                    ensureVoiceSession().toggleSound()
                }
            }
            controls.onMicToggle = {
                if (!panelCollapsed) {
                    ensureOverlayVoiceStarted()
                    val session = ensureVoiceSession()
                    if (!session.micOn && !session.hasRecordAudioPermission()) {
                        pendingVoiceMicEnable = true
                        requestOverlayVoiceMicPermission()
                    } else {
                        session.toggleMic()
                    }
                }
            }
            controls.onExpansionChanged = voiceExpansion@{
                if (controls.isExpanded()) {
                    ensureOverlayVoiceStarted()
                }
                val mgr = windowManager
                val wr = overlayView
                val p = overlayMainWindowParams
                if (mgr == null || wr == null || p == null) return@voiceExpansion
                val widthBefore = wr.width.coerceAtLeast(0)
                wr.post {
                    if (!wr.isAttachedToWindow) return@post
                    syncOverlayPanelEdgeLayout()
                    val wAfter = wr.width
                    if (overlayPanelAnchoredEnd && widthBefore > 0 && wAfter != widthBefore) {
                        p.x += widthBefore - wAfter
                        runCatching { mgr.updateViewLayout(wr, p) }
                    }
                }
            }
        }
        overlayVoiceControls = voiceControls

        val lockIcon = ImageView(this).apply {
            setImageResource(R.drawable.ic_overlay_lock_open)
            contentDescription = getString(R.string.overlay_cd_lock_positions)
            isClickable = true
            isFocusable = true
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
        }

        btnChatTeam.setOnClickListener {
            if (panelCollapsed) return@setOnClickListener
            showOverlayChatTeamPanel(0)
        }

        // Chat unread badge (overlay only).
        val chatBadge = TextView(this).apply {
            visibility = View.GONE
            text = ""
            setTextColor(Color.WHITE)
            setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 10f)
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setPadding(dp(6), dp(2), dp(6), dp(2))
            background = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.RECTANGLE
                cornerRadius = dp(10).toFloat()
                setColor(Color.parseColor("#E53935"))
            }
        }
        overlayChatBadgeText = chatBadge
        updateOverlayChatBadge()
        val chatTeamHost = FrameLayout(this).apply {
            addView(btnChatTeam, FrameLayout.LayoutParams(dp(44), dp(44)))
            addView(
                chatBadge,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    Gravity.TOP or Gravity.END,
                ).apply {
                    setMargins(0, dp(2), dp(2), 0)
                },
            )
        }

        val collapseHost = OverlayPanelCollapseHost.build(
            context = this,
            fabCtx = fabCtx,
            dp = { dp(it) },
            collapseButton = btnCollapse,
            lockButton = lockIcon,
        )
        val fabColW = dp(44)
        val messageFabColumn = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            // START: при раскрытии голоса вправо столбец растёт только вправо, FAB не смещаются.
            gravity = Gravity.START
            clipChildren = false
            clipToPadding = false
            val gap = dp(6)
            addView(
                btnMessage,
                LinearLayout.LayoutParams(fabColW, dp(44)),
            )
            addView(
                chatTeamHost,
                LinearLayout.LayoutParams(fabColW, dp(44)).apply {
                    topMargin = gap
                },
            )
            val voiceAnchor = FrameLayout(this@CombatOverlayService).apply {
                clipChildren = false
                clipToPadding = false
                addView(
                    voiceControls.root,
                    FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.WRAP_CONTENT,
                        FrameLayout.LayoutParams.WRAP_CONTENT,
                    ),
                )
            }
            overlayVoiceAnchor = voiceAnchor
            addView(
                voiceAnchor,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ).apply {
                    topMargin = gap
                },
            )
        }

        val buttonStack = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.START or Gravity.TOP
            clipChildren = false
            clipToPadding = false
            addView(
                collapseHost,
                LinearLayout.LayoutParams(fabColW, dp(44)),
            )
            addView(
                messageFabColumn,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ).apply {
                    topMargin = dp(6)
                },
            )
        }

        val outerRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.TOP or Gravity.START
            clipChildren = false
            clipToPadding = false
            addView(buttonStack, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        }

        lateinit var windowRoot: FrameLayout
        windowRoot = OverlayPassthroughMultitouchFrameLayout(this).apply {
            clipChildren = false
            clipToPadding = false
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

        syncOverlayVoiceExpandLayout()

        overlayOuterRow = outerRow
        overlayControlsStack = buttonStack
        overlayMessageRow = messageFabColumn
        overlayMessageFabColumn = messageFabColumn
        overlayBtnMessageFab = btnMessage

        fun refreshLockIcon() {
            val locked = AppContainer.from(this@CombatOverlayService).userSettingsPreferences.isOverlayDragLocked()
            lockIcon.contentDescription = getString(if (locked) R.string.overlay_cd_unlock_positions else R.string.overlay_cd_lock_positions)
            OverlayPanelCollapseHost.applyLockVisual(lockIcon, locked)
        }

        fun applyControlsVisibility() {
            Log.d(OVERLAY_DIAG_TAG, "applyControls collapsed=$panelCollapsed")
            val widthBefore = windowRoot.width.coerceAtLeast(0)
            // GONE (not INVISIBLE): скрытые FAB не участвуют в layout, окно WM сжимается.
            // Кнопка collapse вне messageFabColumn — при сворачивании ряд FAB скрыт целиком, якорь не двигается.
            if (panelCollapsed) {
                overlayAllianceOnlinePopover.hide()
                overlayCommandsPopover.hide()
                messageFabColumn.visibility = View.GONE
                voiceControls.collapse()
            } else {
                messageFabColumn.visibility = View.VISIBLE
                btnMessage.visibility = View.VISIBLE
                chatTeamHost.visibility = View.VISIBLE
                voiceControls.root.visibility = View.VISIBLE
            }
            btnCollapse.setImageResource(if (panelCollapsed) R.drawable.ic_overlay_ui_expand else R.drawable.ic_overlay_ui_collapse)
            btnCollapse.contentDescription = getString(
                if (panelCollapsed) R.string.overlay_cd_toggle_show_ui else R.string.overlay_cd_toggle_hide_ui,
            )
            windowRoot.requestLayout()
            windowRoot.post {
                if (!windowRoot.isAttachedToWindow) return@post
                if (!panelCollapsed && windowRoot.width > 0) {
                    overlayPanelStableAnchorWidthPx = kotlin.math.max(
                        overlayPanelStableAnchorWidthPx,
                        windowRoot.width,
                    )
                }
                val p = overlayMainWindowParams ?: return@post
                syncOverlayPanelEdgeLayout()
                val wAfter = windowRoot.width.coerceAtLeast(0)
                if (overlayPanelAnchoredEnd && widthBefore > 0 && wAfter != widthBefore) {
                    p.x += widthBefore - wAfter
                    runCatching { manager.updateViewLayout(windowRoot, p) }
                }
                overlayPanelLastHeightPx = windowRoot.height
            }
        }

        refreshLockIcon()
        voiceControls.btnHub.setOnClickListener {
            if (!panelCollapsed) {
                val willExpand = !voiceControls.isExpanded()
                voiceControls.toggleExpanded()
                if (willExpand) {
                    ensureOverlayVoiceStarted()
                }
            }
        }
        panelCollapsed = true
        applyControlsVisibility()

        btnCollapse.setOnClickListener {
            panelCollapsed = !panelCollapsed
            applyControlsVisibility()
        }

        btnMessage.setOnClickListener {
            if (panelCollapsed) return@setOnClickListener
            val mgr = windowManager ?: return@setOnClickListener
            overlayAllianceOnlinePopover.hide()
            OverlayChatInteractionHold.prepareOverlayModalInteraction(true)
            overlayCommandsPopover.toggle(mgr)
            mainHandler.post { repairDetachedOverlayShellIfNeeded() }
        }

        lockIcon.setOnClickListener {
            val prefs = AppContainer.from(this@CombatOverlayService).userSettingsPreferences
            val nextLocked = !prefs.isOverlayDragLocked()
            prefs.setOverlayDragLocked(nextLocked)
            refreshLockIcon()
            lockIcon.performHapticFeedback(android.view.HapticFeedbackConstants.CONTEXT_CLICK)
        }

        val voicePrefs = AppContainer.from(this@CombatOverlayService).userSettingsPreferences
        voiceControls.applyState(
            voicePrefs.isOverlayVoiceMicEnabled(),
            voicePrefs.isOverlayVoiceSoundEnabled(),
        )

        btnCollapse.setOnTouchListener { v, event ->
            val dragLocked = AppContainer.from(this@CombatOverlayService).userSettingsPreferences.isOverlayDragLocked()
            if (dragLocked) return@setOnTouchListener false
            val panelParams = overlayMainWindowParams ?: return@setOnTouchListener false
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    OverlayChatInteractionHold.suppressGameForegroundGateForOverlayPanel = true
                    (v.parent as? ViewGroup)?.requestDisallowInterceptTouchEvent(true)
                    initialX = panelParams.x
                    initialY = panelParams.y
                    startTouchX = event.rawX
                    startTouchY = event.rawY
                    isDragging = false
                    dragScreenW = resources.displayMetrics.widthPixels
                    dragScreenH = resources.displayMetrics.heightPixels
                    dragClampW = windowRoot.width.takeIf { it > 0 }?.coerceAtLeast(dp(48)) ?: dp(120)
                    dragClampH = windowRoot.height.takeIf { it > 0 }?.coerceAtLeast(dp(48)) ?: dp(180)
                    false
                }
                MotionEvent.ACTION_MOVE -> {
                    val deltaX = (event.rawX - startTouchX).toInt()
                    val deltaY = (event.rawY - startTouchY).toInt()
                    if (!isDragging &&
                        (kotlin.math.abs(deltaX) > dragThreshold || kotlin.math.abs(deltaY) > dragThreshold)
                    ) {
                        isDragging = true
                    }
                    if (isDragging) {
                        val maxX = (dragScreenW - dragClampW).coerceAtLeast(0)
                        val maxY = (dragScreenH - dragClampH).coerceAtLeast(0)
                        val nextX = (initialX + deltaX).coerceIn(0, maxX)
                        val nextY = (initialY + deltaY).coerceIn(0, maxY) // gravity=TOP
                        if (panelParams.x != nextX || panelParams.y != nextY) {
                            panelParams.x = nextX
                            panelParams.y = nextY
                            runCatching {
                                manager.updateViewLayout(windowRoot, panelParams)
                            }.onFailure { e ->
                                Log.w(TAG, "overlay drag updateViewLayout failed", e)
                            }
                        }
                        true
                    } else {
                        false
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    OverlayChatInteractionHold.suppressGameForegroundGateForOverlayPanel = false
                    if (isDragging) {
                        runCatching { syncOverlayPanelEdgeLayout() }.onFailure { e ->
                            Log.w(TAG, "syncOverlayPanelEdgeLayout after drag failed", e)
                        }
                        overlayTicker.syncTickerPosition()
                        AppContainer.from(this@CombatOverlayService).userSettingsPreferences.setOverlayPanelPosPx(
                            x = panelParams.x,
                            y = panelParams.y,
                        )
                    }
                    val consumed = isDragging
                    isDragging = false
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
            overlayBtnMessageFab = null
            return
        }
        _overlayVisible.value = true
        windowManager = manager
        overlayTicker.syncTickerPosition()
        rebalanceOverlayChatStripZOrder()
        rebalanceOverlayFullscreenZOrder()
        applyOverlayVisibilityState()
        windowRoot.post {
            syncOverlayPanelEdgeLayout()
            beginOverlayChatSubscription()
        }
    }

    private fun applyOverlayVisibilityState() {
        val bubbleContainer = overlayView
        (windowManager ?: systemWindowManager())?.let { wm ->
            runCatching { ensureChatStripWindow(wm) }
        }
        chatStripHost?.visibility =
            if (overlayChatTeamPanelVisible) View.GONE else View.VISIBLE
        rebalanceOverlayChatStripZOrder()
        bubbleContainer?.animate()?.cancel()
        bubbleContainer?.visibility = View.VISIBLE
        bubbleContainer?.alpha = 1f
        bubbleContainer?.scaleX = 1f
        bubbleContainer?.scaleY = 1f
        rebalanceOverlayFullscreenZOrder()
    }

    private fun hideOverlayChatTeamPanel(clearStrip: Boolean = true) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { hideOverlayChatTeamPanel(clearStrip) }
            return
        }
        resumeOverlayWindowsAfterSystemActivity()
        val root = overlayChatTeamRoot
        val hadVisible = overlayChatTeamPanelVisible
        overlayChatTeamPanelVisible = false
        OverlayChatInteractionHold.isFullscreenChatTeamPanelVisible = false
        OverlayChatInteractionHold.releaseGameForegroundSuppress()
        if (hadVisible) {
            updateStripDismissScreenRects(emptyList())
            if (clearStrip) {
                stripBuffer.clear()
                lastStripRenderSignature = null
            }
        }
        overlayHistoryInput?.let { hideOverlayIme(it) }
        val manager = windowManager ?: systemWindowManager()
        if (root != null && manager != null) {
            runCatching { manager.removeView(root) }
        }
        overlayChatTeamRoot = null
        overlayChatTeamParams = null
        overlayHistoryScroll = null
        overlayHistoryLines = null
        overlayHistoryInput = null
        overlayHistorySend = null
        overlayHistoryStatus = null
        overlayHistoryDedupeIds.clear()
        overlayChatViewModel = null
        runCatching { AppContainer.from(this).chatRepository.notifyOverlayChatPanelClosed() }
        runCatching { unregisterReceiver(overlaySystemResultReceiver) }
        overlayChatTeamComposeOwner?.destroy()
        overlayChatTeamComposeOwner = null
        refreshOverlayChatStrip()
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
        val roomId = runCatching { AppContainer.from(this).chatRoomPreferences.getRaidRoomId() }.getOrNull()
        if (roomId.isNullOrBlank()) {
            showOverlayHistoryStatus(getString(R.string.overlay_strip_no_raid))
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
    private fun rebalanceOverlayFullscreenZOrder() {
        val mgr = windowManager ?: return
        if (overlayChatTeamPanelVisible) {
            val root = overlayChatTeamRoot ?: return
            val p = overlayChatTeamParams ?: return
            if (!root.isAttachedToWindow) return
            runCatching {
                mgr.removeView(root)
                mgr.addView(root, p)
            }.onFailure { e ->
                Log.w(TAG, "rebalanceOverlayFullscreenZOrder(chatTeam) failed", e)
            }
        }
    }

    /** Поднять ленту поверх остальных окон оверлея этого процесса (порядок addView на части OEM). */
    private fun rebalanceOverlayChatStripZOrder() {
        val host = chatStripHost ?: return
        val mgr = windowManager ?: systemWindowManager() ?: return
        val p = chatStripParams ?: return
        if (!host.isAttachedToWindow) return
        runCatching {
            mgr.removeView(host)
            mgr.addView(host, p)
        }.onFailure { e ->
            Log.w(TAG, "rebalanceOverlayChatStripZOrder failed", e)
        }
    }

    private fun showOverlayChatTeamPanel(initialTabIndex: Int = 0) {
        overlayChatViewModel?.refreshChatForOverlay()
        if (overlayChatTeamPanelVisible) return
        val initialTab = initialTabIndex.coerceIn(0, 1)
        overlayAllianceOnlinePopover.hide()
        overlayCommandsPopover.hide()
        OverlayChatInteractionHold.acquireGameForegroundSuppress()
        OverlayChatInteractionHold.isFullscreenChatTeamPanelVisible = true
        overlayUnreadChatCount = 0
        mainHandler.post { updateOverlayChatBadge() }
        val manager = windowManager ?: return
        chatStripHost?.visibility = View.GONE
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val owner = overlayChatTeamComposeOwner
            ?: OverlayChatComposeOwner().also { overlayChatTeamComposeOwner = it }
        runCatching { unregisterReceiver(overlaySystemResultReceiver) }
        runCatching {
            ContextCompat.registerReceiver(
                this@CombatOverlayService,
                overlaySystemResultReceiver,
                IntentFilter().apply {
                    addAction(OverlaySystemDialogActivity.ACTION_OVERLAY_PICK_IMAGES_RESULT)
                    addAction(OverlaySystemDialogActivity.ACTION_OVERLAY_GET_CONTENT_RESULT)
                    addAction(OverlaySystemDialogActivity.ACTION_OVERLAY_MIC_PERMISSION_RESULT)
                    addAction(OverlaySystemDialogActivity.ACTION_OVERLAY_SYSTEM_UI_FINISHED)
                },
                ContextCompat.RECEIVER_NOT_EXPORTED,
            )
        }
        val compose = ComposeView(this).apply {
            setContent {
                val container = remember { AppContainer.from(this@CombatOverlayService) }
                val app = this@CombatOverlayService.applicationContext as android.app.Application
                val userId = remember { jwtSubFromAccessToken().orEmpty() }
                val userRole = remember { jwtRoleFromAccessToken() }
                var selectedTab by remember(initialTab) { mutableIntStateOf(initialTab) }
                val vm = remember(userId, userRole) {
                    overlayChatViewModel ?: ChatViewModel(
                        application = app,
                        repository = container.chatRepository,
                        chatRoomPreferences = container.chatRoomPreferences,
                        usersRepository = container.usersRepository,
                        currentUserId = userId,
                        currentUserRole = userRole,
                    ).also {
                        overlayChatViewModel = it
                        it.refreshChatForOverlay()
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
                    LocalOverlayUiMode provides true,
                ) {
                    SquadRelayTheme {
                        val blockPanelBack = OverlayChatInteractionHold.blocksFullscreenPanelBack() ||
                            chatState.activeActionMessageId != null ||
                            chatState.confirmDeleteMessageId != null ||
                            chatState.confirmBulkDelete
                        BackHandler(
                            enabled = !blockPanelBack,
                        ) {
                            hideOverlayChatTeamPanel()
                        }
                        Surface(
                            modifier = Modifier.fillMaxSize(),
                            color = MaterialTheme.colorScheme.surface,
                        ) {
                            Column(Modifier.fillMaxSize()) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 2.dp, end = 4.dp),
                                    horizontalArrangement = Arrangement.End,
                                ) {
                                    IconButton(
                                        onClick = { hideOverlayChatTeamPanel() },
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.Close,
                                            contentDescription = getString(R.string.overlay_history_close_cd),
                                            tint = MaterialTheme.colorScheme.onSurface,
                                        )
                                    }
                                }
                                Box(
                                    Modifier
                                        .fillMaxWidth()
                                        .weight(1f),
                                ) {
                                    when (selectedTab) {
                                        0 -> {
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
                                        }
                                        1 -> {
                                            TeamScreen(
                                                currentUserId = userId,
                                                teamsRepository = container.teamsRepository,
                                                initialMainSection = TeamMainSection.News,
                                            )
                                        }
                                    }
                                }
                                TabRow(selectedTabIndex = selectedTab) {
                                    Tab(
                                        selected = selectedTab == 0,
                                        onClick = { selectedTab = 0 },
                                        text = { Text(stringResource(R.string.tab_chat)) },
                                    )
                                    Tab(
                                        selected = selectedTab == 1,
                                        onClick = { selectedTab = 1 },
                                        text = { Text(stringResource(R.string.tab_team)) },
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
            OverlayWindowLayout.applyOverlayFullscreenChatSoftInputMode(this)
        }

        val overlayUiContext = OverlayTickerUi.themedFabContext(this)
        val surfaceArgb = MaterialColors.getColor(
            overlayUiContext,
            com.google.android.material.R.attr.colorSurface,
            Color.parseColor("#10141E"),
        )
        val keyboardGapPx = (4f * resources.displayMetrics.density).toInt()
        val root = FrameLayout(this).apply {
            setBackgroundColor(surfaceArgb)
            elevation = 48f
            setViewTreeLifecycleOwner(owner)
            setViewTreeViewModelStoreOwner(owner)
            setViewTreeSavedStateRegistryOwner(owner)
            setViewTreeOnBackPressedDispatcherOwner(owner)
            ViewCompat.setOnApplyWindowInsetsListener(this) { view, windowInsets ->
                val safeTypes = WindowInsetsCompat.Type.systemBars() or
                    WindowInsetsCompat.Type.displayCutout()
                val safe = windowInsets.getInsets(safeTypes)
                val ime = windowInsets.getInsets(WindowInsetsCompat.Type.ime())
                val bottom = maxOf(safe.bottom, ime.bottom) + keyboardGapPx
                // Верхний отступ — в Compose (крестик в строке с камерой; комнаты — statusBarsPadding).
                view.setPadding(safe.left, 0, safe.right, bottom)
                WindowInsetsCompat.Builder(windowInsets)
                    .setInsets(WindowInsetsCompat.Type.ime(), Insets.NONE)
                    .setInsets(safeTypes, Insets.NONE)
                    .build()
            }
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
            Log.e(TAG, "showOverlayChatTeamPanel: failed to attach overlay", attachResult.exceptionOrNull())
            Toast.makeText(
                this,
                getString(R.string.overlay_team_open_failed),
                Toast.LENGTH_LONG,
            ).show()
            overlayChatTeamRoot = null
            overlayChatTeamParams = null
            overlayChatTeamPanelVisible = false
            OverlayChatInteractionHold.isFullscreenChatTeamPanelVisible = false
            OverlayChatInteractionHold.clearSuppressUnlessFullscreenPanel()
            runCatching { unregisterReceiver(overlaySystemResultReceiver) }
            overlayChatTeamComposeOwner?.destroy()
            overlayChatTeamComposeOwner = null
            refreshOverlayChatStrip()
            mainHandler.post {
                requestRebuildOverlayIfRunning(this@CombatOverlayService)
            }
            return
        }

        ViewCompat.requestApplyInsets(root)

        overlayChatTeamRoot = root
        overlayChatTeamParams = params
        overlayChatTeamPanelVisible = true
        OverlayChatInteractionHold.isFullscreenChatTeamPanelVisible = true
        rebalanceOverlayFullscreenZOrder()
        ViewCompat.requestApplyInsets(root)
    }
    private fun dp(value: Int): Int {
        val density = resources.displayMetrics.density
        return (value * density).toInt()
    }

    private fun ensureVoiceSession(): VoiceChatSession {
        voiceSession?.let { return it }
        val container = AppContainer.from(this)
        return container.newVoiceChatSession(
            onStateChanged = { micOn, soundOn ->
                overlayVoiceControls?.applyState(micOn, soundOn)
            },
            onMicForegroundChanged = { micActive ->
                updateVoiceForegroundService(micActive)
            },
            onActiveSpeakersChanged = { count ->
                overlayVoiceControls?.setActiveSpeakerCount(count)
            },
        ).also {
            voiceSession = it
            container.overlayVoiceSession = it
        }
    }

    private fun startOverlayVoiceIfRaidAvailable() {
        val raidId = AppContainer.from(this).chatRoomPreferences.getRaidRoomId() ?: return
        val userId = jwtSubFromAccessToken().orEmpty()
        if (userId.isBlank()) return
        val session = ensureVoiceSession()
        session.start(raidId, userId)
        overlayVoiceControls?.applyState(session.micOn, session.soundOn)
    }

    private fun stopOverlayVoice() {
        voiceSession?.stop()
        voiceSession = null
        AppContainer.from(this).overlayVoiceSession = null
        overlayVoiceControls?.collapse()
        updateVoiceForegroundService(false)
    }

    private fun updateVoiceForegroundService(micActive: Boolean) {
        val hasMic = micActive &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        if (!hasMic && !lastForegroundMicActive) {
            return
        }
        val types = if (hasMic && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC or
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
        } else {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        }
        val text = if (hasMic) {
            getString(R.string.overlay_notif_voice_active)
        } else {
            foregroundNotificationIdleText()
        }
        lastForegroundMicActive = hasMic
        lastForegroundNotificationText = text
        val notification = OverlayForegroundNotifications.build(
            this,
            text,
            AppContainer.from(this).userSettingsPreferences.isQuietMode(),
        )
        ServiceCompat.startForeground(
            this,
            OverlayForegroundNotifications.NOTIFICATION_ID,
            notification,
            types,
        )
    }

    private fun requestOverlayVoiceMicPermission() {
        registerVoiceMicPermissionReceiver()
        val intent = android.content.Intent(this, OverlaySystemDialogActivity::class.java).apply {
            addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            putExtra(OverlaySystemDialogActivity.EXTRA_KIND, OverlaySystemDialogActivity.KIND_REQUEST_MIC)
            putExtra(OverlaySystemDialogActivity.EXTRA_REQUEST_CODE, VOICE_MIC_PERMISSION_REQUEST)
        }
        runCatching { startActivity(intent) }
    }

    private fun registerVoiceMicPermissionReceiver() {
        if (voicePermissionReceiverRegistered) return
        runCatching {
            ContextCompat.registerReceiver(
                this,
                voiceMicPermissionReceiver,
                IntentFilter(OverlaySystemDialogActivity.ACTION_OVERLAY_MIC_PERMISSION_RESULT),
                ContextCompat.RECEIVER_NOT_EXPORTED,
            )
            voicePermissionReceiverRegistered = true
        }
    }

    private fun unregisterVoiceMicPermissionReceiver() {
        if (!voicePermissionReceiverRegistered) return
        runCatching { unregisterReceiver(voiceMicPermissionReceiver) }
        voicePermissionReceiverRegistered = false
    }

    private fun removeOverlayControl(force: Boolean = false) {
        if (!force && shouldKeepOverlayWindows()) {
            Log.i(TAG, "removeOverlayControl: skipped while overlay chat/team UI is active")
            return
        }
        OverlayChatInteractionHold.clearStaleSuppressForGameBackground(
            chatTeamPanelVisible = false,
            commandsPopoverShowing = false,
        )
        resumeOverlayWindowsAfterSystemActivity()
        speechPipeline.cancelActiveSession()
        endOverlayChatSubscription()
        overlayTicker.hideTicker()
        quickCommandsPopover.hide()
        overlayAllianceOnlinePopover.hide()
        overlayCommandsPopover.hide()
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
        overlayBtnMessageFab = null
        overlayCollapseButton = null
        overlayVoiceAnchor = null
        overlayPanelAnchoredEnd = false
        overlayPanelStableAnchorWidthPx = 0
        windowManager = null
    }

    companion object {
        /** Частый prune ленты относительно TTL ~10 с. */
        private const val STRIP_TICK_MS = 2_500L
        /** Реже обновляем ленту, пока панель свёрнута (только кнопка разворота). */
        private const val STRIP_TICK_COLLAPSED_MS = 5_000L
        /** Панель на экране — отзывчивый гейт. */
        private const val GAME_GATE_POLL_ACTIVE_MS = 900L
        /** Недавно были в игре / открыт чат — чаще, чем в простое. */
        private const val GAME_GATE_POLL_WARM_MS = 1_800L
        /** FGS включён, оверлей скрыт: редкий опрос usage stats. */
        private const val GAME_GATE_POLL_IDLE_MS = 3_500L
        /** Голос: отложенный connect, если mic/sound были включены в прошлой сессии. */
        private const val OVERLAY_VOICE_CONNECT_DELAY_MS = 4_000L
        /** Краткий grace при ложном «не в игре» во время чата/пикера; не применяется при явном лаунчере/другом приложении. */
        private const val OVERLAY_INGAME_GRACE_MS = 2_500L
        private const val OVERLAY_HISTORY_LOAD = 150
        /** Matches backend / user schema: ingame | online | away */
        private const val OVERLAY_PRESENCE_INGAME = "ingame"
        private const val OVERLAY_PRESENCE_AWAY = "away"
        private const val TAG = "CombatOverlayService"
        /** Logcat: `adb logcat -s SR_OverlayDiag:D` или фильтр по тегу в Android Studio. */
        private const val OVERLAY_DIAG_TAG = "SR_OverlayDiag"

        const val ACTION_START_RECORDING = "com.squadrelay.action.START_RECORDING"
        private const val VOICE_MIC_PERMISSION_REQUEST = 9107
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

        /**
         * Поднимает FGS, если пользователь включил панель, но процесс/сервис был остановлен системой
         * (свайп из recents, нехватка памяти). Оверлей по-прежнему показывается только в игре.
         */
        fun ensureRuntimeIfUserEnabled(context: Context): Boolean {
            val app = context.applicationContext
            if (!UserSettingsPreferences(app).isOverlayPanelEnabled()) return false
            if (isServiceInstanceActive) return true
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
                !Settings.canDrawOverlays(app)
            ) {
                return false
            }
            return setEnabled(context, true)
        }

        /** После смены пакета/activity-фильтра — пересчитать показ оверлея. */
        fun requestGateRecheckIfRunning(context: Context) {
            if (!isServiceInstanceActive) return
            GameForegroundGate.invalidateForegroundHintCache()
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
