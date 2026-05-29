package com.lastasylum.alliance.overlay

import android.app.ForegroundServiceStartNotAllowedException
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
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.view.ViewGroup
import android.util.Log
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.google.android.material.color.MaterialColors
import com.lastasylum.alliance.BuildConfig
import com.lastasylum.alliance.R
import com.lastasylum.alliance.data.ReadCursorSession
import com.lastasylum.alliance.data.chat.ChatUnreadCounts
import com.lastasylum.alliance.data.teams.TeamForumTopicActivityEvent
import com.lastasylum.alliance.data.teams.TeamInboxUnread
import com.lastasylum.alliance.data.isObjectIdNewer
import com.lastasylum.alliance.data.displayedUnreadCount
import com.lastasylum.alliance.data.effectiveUnreadCount
import com.lastasylum.alliance.data.chat.ChatMessage
import com.lastasylum.alliance.data.chat.isCompactReactionSocketUpdate
import com.lastasylum.alliance.data.chat.ChatRoomKindResolver
import com.lastasylum.alliance.data.chat.ChatSessionCache
import com.lastasylum.alliance.data.chat.OverlayReactionEvent
import com.lastasylum.alliance.data.chat.chatSenderDisplayWithTag
import com.lastasylum.alliance.data.auth.JwtAccessTokenClaims
import com.lastasylum.alliance.data.chat.chatImageAttachments
import com.lastasylum.alliance.data.voice.VoiceChatSession
import com.lastasylum.alliance.data.settings.UserSettingsPreferences
import com.lastasylum.alliance.di.AppContainer
import com.lastasylum.alliance.overlay.layout.OverlayLayoutDp
import com.lastasylum.alliance.ui.screens.ChatScreen
import com.lastasylum.alliance.ui.screens.TeamMainSection
import com.lastasylum.alliance.ui.screens.TeamScreen
import com.lastasylum.alliance.di.ChatViewModelRegistry
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.platform.LocalSavedStateRegistryOwner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.lastasylum.alliance.push.FcmTokenManager
import java.time.Instant
import java.time.temporal.ChronoUnit

class CombatOverlayService : Service() {
    private var windowManager: WindowManager? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var fcmRegistrationJob: Job? = null
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
    private val gateUserNotifier by lazy { OverlayGateUserNotifier(this, mainHandler) }
    private val gameGateCoordinator by lazy {
        OverlayGameGateCoordinator(gateUserNotifier, ::notifyGateBlocked)
    }
    private val overlayVoiceController = OverlayVoiceController(mainHandler)
    private val overlayCommandsPopover by lazy {
        OverlayCommandsPopover(
            context = this,
            mainHandler = mainHandler,
            scope = serviceScope,
            dp = { dp(it) },
            sendCoords = { label, x, y, excavation ->
                sendOverlayRaidQuickCommandHttp(
                    text = formatOverlayRaidQuickCommandText(label, x, y, excavation),
                    excavationAlert = excavation,
                )
            },
            notifyExcavation = {
                sendOverlayRaidQuickCommandHttp(
                    text = getString(R.string.overlay_excavation_notify_message),
                    excavationAlert = true,
                )
            },
            warmupOverlayRaid = { warmupOverlayRaidForQuickCommands() },
            prepareOptimisticRaidQuickCommand = { label, x, y, excavation ->
                postOptimisticOverlayRaidQuickCommand(
                    formatOverlayRaidQuickCommandText(label, x, y, excavation),
                )
            },
            prepareOptimisticRaidNotify = {
                postOptimisticOverlayRaidQuickCommand(
                    getString(R.string.overlay_excavation_notify_message),
                )
            },
            removeOptimisticRaidSend = { pendingId -> removeStripMessageByKey(pendingId) },
            emitOverlayReaction = { targetUserId, reactionId ->
                AppContainer.from(this@CombatOverlayService).chatRepository.emitOverlayReaction(targetUserId, reactionId)
            },
            emitOverlayReactionBroadcast = { reactionId ->
                AppContainer.from(this@CombatOverlayService).chatRepository.emitOverlayReactionBroadcast(reactionId)
            },
        )
    }
    private val presenceHeartbeat by lazy {
        OverlayPresenceHeartbeat(
            scope = serviceScope,
            intervalMs = 60_000L,
            ping = {
                AppContainer.from(this@CombatOverlayService).usersRepository.updatePresence(
                    OVERLAY_PRESENCE_INGAME,
                )
                Unit
            },
        )
    }

    /**
     * Список «Участники онлайн» — только ingame + свежий lastPresenceAt.
     * Пинги идут только пока гейт видит целевую игру; после выхода heartbeat останавливается
     * сразу, away — после нескольких тиков без игры (без 3‑минутного grace).
     */
    private fun syncOverlayIngamePresence(inGameProbe: Boolean) {
        val container = AppContainer.from(this)
        if (!container.userSettingsPreferences.isOverlayPanelEnabled() ||
            !container.authRepository.hasSession()
        ) {
            overlayIngameMissStreak = 0
            stopOverlayIngamePresence(markAway = false)
            return
        }
        val voiceActive = voiceSession?.let { it.micOn || it.soundOn } == true
        val keepIngamePing =
            inGameProbe || isInGameOverlayUiActive() || voiceActive
        if (keepIngamePing) {
            overlayIngameMissStreak = 0
            val firstStart = !overlayIngamePresenceActive
            overlayIngamePresenceActive = true
            presenceHeartbeat.start()
            if (firstStart || voiceActive) {
                serviceScope.launch {
                    runCatching {
                        container.usersRepository.updatePresence(OVERLAY_PRESENCE_INGAME)
                    }
                }
            }
            return
        }
        presenceHeartbeat.stop()
        overlayIngameMissStreak++
        if (overlayIngameMissStreak < OVERLAY_INGAME_AWAY_MISS_STREAK) {
            return
        }
        if (overlaySessionActive && isInGameOverlayUiActive()) {
            return
        }
        stopOverlayIngamePresence(markAway = true)
    }

    private fun stopOverlayIngamePresence(markAway: Boolean) {
        presenceHeartbeat.stop()
        overlayIngamePresenceActive = false
        if (markAway && !canUseOverlayVoiceNow()) {
            stopOverlayVoice()
        }
        if (!markAway) return
        val container = AppContainer.from(this)
        if (!container.authRepository.hasSession()) return
        serviceScope.launch {
            runCatching {
                container.usersRepository.updatePresence(OVERLAY_PRESENCE_AWAY)
            }
        }
    }

    private fun canUseOverlayVoiceNow(): Boolean =
        isInGameOverlayUiActive() || overlayInGameProbeActive

    /**
     * Клиент шлёт presence «ingame» перед подключением; сервер дополнительно проверяет ingame у
     * отправителя в relayFrame. Слушатели с soundOn могут быть вне игры.
     */
    private fun postOverlayVoiceAfterIngamePing(block: () -> Unit) {
        if (!canUseOverlayVoiceNow()) return
        val container = AppContainer.from(this)
        if (!container.authRepository.hasSession()) return
        serviceScope.launch(Dispatchers.IO) {
            val profile = runCatching { container.usersRepository.resolveMyProfilePreferCache() }
                .getOrNull()
            val hasTeam = !profile?.playerTeamId.isNullOrBlank()
            runCatching {
                container.usersRepository.updatePresence(OVERLAY_PRESENCE_INGAME)
            }
            mainHandler.post {
                if (!canUseOverlayVoiceNow()) return@post
                if (!hasTeam) {
                    Toast.makeText(
                        this@CombatOverlayService,
                        R.string.overlay_voice_no_team,
                        Toast.LENGTH_SHORT,
                    ).show()
                    return@post
                }
                block()
            }
        }
    }

    private fun runOverlayVoiceUserAction(afterStart: (VoiceChatSession) -> Unit) {
        postOverlayVoiceAfterIngamePing {
            overlayVoiceController.armFromUserAction()
            startOverlayTeamVoiceIfAvailable { session ->
                afterStart(session)
                refreshOverlayTopRightHudState()
            }
        }
    }
    private var voiceSession: VoiceChatSession? = null
    private var pendingVoiceMicEnable = false
    private var voicePermissionReceiverRegistered = false
    private var chatStripClipRoot: FrameLayout? = null
    private var chatStripCompose: ComposeView? = null
    private var chatStripHost: View? = null
    private var chatStripParams: WindowManager.LayoutParams? = null
    private val chatStripPreviewFlow = MutableStateFlow<List<ChatMessage>>(emptyList())
    private var overlayMessageListener: ((ChatMessage) -> Unit)? = null
    private var overlayMessageDeletedListener: ((com.lastasylum.alliance.data.chat.ChatMessageDeletedEvent) -> Unit)? = null
    private var overlayChatHistoryClearedListener: (() -> Unit)? = null
    private var overlayForumTopicActivityListener: ((TeamForumTopicActivityEvent) -> Unit)? = null
    private var overlayReadListener: ((com.lastasylum.alliance.data.chat.ChatRoomReadEvent) -> Unit)? = null
    private var overlayRoomUnreadListener: ((com.lastasylum.alliance.data.chat.ChatRoomUnreadEvent) -> Unit)? = null
    private var overlayTypingListener: ((com.lastasylum.alliance.data.chat.ChatTypingEvent) -> Unit)? = null
    private var overlayReactionListener: ((OverlayReactionEvent) -> Unit)? = null
    private val inboxBadgeCoordinator = OverlayInboxBadgeCoordinator()
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
    private var currentOverlayHudPane: OverlayHudPane? = null
    /** 0 = chat, 1 = team tab in legacy overlay panel ([showOverlayChatTeamPanel] without HUD pane). */
    @Volatile
    private var overlayChatTeamTabIndex: Int = 0
    @Volatile
    private var pendingOpenJoinInboxOnParticipants = false
    private var overlayChatTeamComposeOwner: OverlayChatComposeOwner? = null
    /** When activity VM is gone (game-only FGS), overlay chat uses this until panel closes. */
    private var overlayFallbackChatViewModel: ChatViewModel? = null
    /** URIs from picker if result arrived while Compose owner was torn down. */
    private var pendingOverlayPickedImageUris: List<Uri>? = null
    private var deferredHideOverlayChatTeamPanel = false
    private var deferredHideOverlayClearStrip = true
    /** Владелец Compose для ленты сообщений (отдельное окно). */
    private var overlayStripComposeOwner: OverlayChatComposeOwner? = null
    private var overlayPopoverComposeOwner: OverlayChatComposeOwner? = null

    private val overlayStatusHudFlow = MutableStateFlow(OverlayGameStatusHudState())
    private var overlayStatusHudHost: FrameLayout? = null
    private var overlayStatusHudParams: WindowManager.LayoutParams? = null
    private var overlayStatusHudCompose: ComposeView? = null
    private var overlayStatusHudComposeOwner: OverlayChatComposeOwner? = null
    private var statusHudRefreshPosted = false
    private val statusHudRefreshRunnable = Runnable {
        statusHudRefreshPosted = false
        refreshOverlayStatusHudData()
        scheduleOverlayStatusHudRefresh()
    }

    private val overlayTopRightHudFlow = MutableStateFlow(OverlayGameTopRightHudState())
    private var overlayTopRightHudHost: FrameLayout? = null
    private var overlayTopRightHudParams: WindowManager.LayoutParams? = null
    private var overlayTopRightHudCompose: ComposeView? = null
    private var overlayTopRightHudComposeOwner: OverlayChatComposeOwner? = null

    private data class OverlayWindowFlagSnap(
        val view: View,
        val params: WindowManager.LayoutParams,
        val prevFlags: Int,
        val prevVisibility: Int,
        val prevAlpha: Float = 1f,
    )

    /** Снимок окон overlay на время системного пикера (TYPE_APPLICATION_OVERLAY выше Activity — иначе галерея «под» чатом). */
    private val overlayTouchPassthroughSnaps = mutableListOf<OverlayWindowFlagSnap>()

    private fun suspendOverlayWindowsForSystemActivity(keepOverlayChromeVisible: Boolean = false) {
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
                    prevAlpha = view.alpha,
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
        // Системный Photo Picker (как в приложении): чат, лента и комнаты остаются на экране;
        // только NOT_TOUCHABLE, чтобы касания ушли в Activity пикера поверх оверлея.
        if (keepOverlayChromeVisible) {
            snap(overlayChatTeamParams, overlayChatTeamRoot, hideFromScreen = false)
            snap(chatStripParams, chatStripHost, hideFromScreen = false)
        } else {
            // Микрофон и прочее: панель GONE, иначе TYPE_APPLICATION_OVERLAY перекрывает системный UI.
            hideOverlayChatPanelForPicker(mgr)
            snap(chatStripParams, chatStripHost, hideFromScreen = true)
        }
        overlayTicker.applyTouchPassthrough(true)
        overlayCommandsPopover.hide()
    }

    private fun hideOverlayChatPanelForPicker(mgr: WindowManager) {
        val root = overlayChatTeamRoot ?: return
        val params = overlayChatTeamParams ?: return
        if (!root.isAttachedToWindow) return
        overlayTouchPassthroughSnaps.add(
            OverlayWindowFlagSnap(
                view = root,
                params = params,
                prevFlags = params.flags,
                prevVisibility = root.visibility,
                prevAlpha = root.alpha,
            ),
        )
        // GONE: TYPE_APPLICATION_OVERLAY иначе перекрывает системную Activity пикера.
        if (root.visibility != View.GONE) {
            root.visibility = View.GONE
        }
        val with = params.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        if (params.flags != with) {
            params.flags = with
            runCatching { mgr.updateViewLayout(root, params) }
                .onFailure { e ->
                    Log.w(TAG, "hideOverlayChatPanelForPicker: updateViewLayout failed", e)
                }
        }
    }

    private fun resumeOverlayWindowsAfterSystemActivity(skipFullscreenRebalance: Boolean = false) {
        val mgr = windowManager
        val hadSuspendedWindows = overlayTouchPassthroughSnaps.isNotEmpty()
        if (hadSuspendedWindows && mgr != null) {
            for (snap in overlayTouchPassthroughSnaps) {
                snap.params.flags = snap.prevFlags
                snap.view.visibility = snap.prevVisibility
                snap.view.alpha = snap.prevAlpha
                if (snap.view.isAttachedToWindow) {
                    runCatching { mgr.updateViewLayout(snap.view, snap.params) }
                }
            }
            overlayTouchPassthroughSnaps.clear()
        }
        overlayTicker.applyTouchPassthrough(false)
        if (OverlayChatInteractionHold.isOverlaySystemPickerSessionActive()) {
            OverlayChatInteractionHold.endOverlaySystemPickerSession()
        }
        OverlayChatInteractionHold.cancelPreparedOverlayModalInteraction(true)
        restoreOverlayChatTeamPanelOpaqueAppearance()
        if (!skipFullscreenRebalance) {
            rebalanceOverlayFullscreenZOrder()
        }
        repairDetachedOverlayChatTeamPanelIfNeeded()
        repairDetachedOverlayShellIfNeeded()
        if (deferredHideOverlayChatTeamPanel) {
            val clearStrip = deferredHideOverlayClearStrip
            deferredHideOverlayChatTeamPanel = false
            hideOverlayChatTeamPanelNow(clearStrip)
        }
        if (deferredDismissWhenPickerEnds && !isInGameOverlayUiActive()) {
            deferredDismissWhenPickerEnds = false
            dismissOverlayUiBecauseNotInGame(logWaitingForGame = false)
        }
    }

    private fun extractPickerUris(intent: Intent): List<Uri> =
        if (Build.VERSION.SDK_INT >= 33) {
            intent.getParcelableArrayListExtra(
                OverlaySystemDialogActivity.EXTRA_URIS,
                Uri::class.java,
            ).orEmpty()
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableArrayListExtra<Uri>(OverlaySystemDialogActivity.EXTRA_URIS).orEmpty()
        }

    private fun deliverOverlayActivityResult(
        requestCode: Int,
        resultCode: Int,
        data: Intent,
    ) {
        val owner = overlayChatTeamComposeOwner
        if (owner == null) {
            Log.w(TAG, "deliverOverlayActivityResult: compose owner gone (requestCode=$requestCode)")
            return
        }
        owner.activityResultRegistry.dispatchResult(requestCode, resultCode, data)
    }

    /**
     * Единственный надёжный путь для оверлея: [ChatViewModel.onImagesPicked].
     * [ActivityResultRegistry.dispatchResult] часто не доходит до [rememberLauncherForActivityResult]
     * после GONE/restore панели (другой requestCode / пустой parseResult).
     */
    private fun applyOverlayPickedUris(uris: List<Uri>) {
        if (uris.isEmpty()) return
        val vm = resolveChatViewModel()
        if (vm != null) {
            vm.onImagesPicked(uris)
            pendingOverlayPickedImageUris = null
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "applyOverlayPickedUris: applied ${uris.size} via ChatViewModel")
            }
        } else {
            stashPendingOverlayPickedImages(uris)
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "applyOverlayPickedUris: stashed ${uris.size} (vm not ready)")
            }
        }
    }

    private fun shouldApplyOverlayImagePickToChatViewModel(): Boolean {
        when (currentOverlayHudPane) {
            OverlayHudPane.Forum,
            OverlayHudPane.News,
            OverlayHudPane.Participants,
            -> return false
            OverlayHudPane.Chat -> return true
            null -> return overlayChatTeamTabIndex == 0
        }
    }

    private fun deliverOverlayPickImagesResult(
        requestCode: Int,
        resultCode: Int,
        data: Intent,
        fallbackUris: List<Uri>,
    ) {
        if (resultCode == android.app.Activity.RESULT_OK && fallbackUris.isNotEmpty()) {
            if (shouldApplyOverlayImagePickToChatViewModel()) {
                // Единственный надёжный путь для оверлей-чата: иначе dispatchResult дублирует вложения.
                applyOverlayPickedUris(fallbackUris)
            } else {
                deliverOverlayActivityResult(requestCode, resultCode, data)
            }
            return
        }
        overlayChatTeamComposeOwner?.activityResultRegistry?.dispatchResult(
            requestCode,
            resultCode,
            data,
        )
    }

    private fun deliverOverlayGetContentResult(
        requestCode: Int,
        resultCode: Int,
        data: Intent,
        @Suppress("UNUSED_PARAMETER") fallbackUri: Uri?,
    ) {
        // APK / файлы форума — только через ActivityResultRegistry (не в ChatViewModel).
        deliverOverlayActivityResult(requestCode, resultCode, data)
    }

    private fun stashPendingOverlayPickedImages(uris: List<Uri>) {
        if (uris.isEmpty()) return
        val merged = (pendingOverlayPickedImageUris.orEmpty() + uris)
            .distinctBy { it.toString() }
            .take(12)
        pendingOverlayPickedImageUris = merged
    }

    private fun flushPendingOverlayPickedImages() {
        val pending = pendingOverlayPickedImageUris ?: return
        pendingOverlayPickedImageUris = null
        resolveChatViewModel()?.onImagesPicked(pending)
    }

    private fun intentForOverlayGalleryPermissionResults(): Intent {
        val permissions = OverlayDeviceGallery.requiredReadPermissions()
        val grantResults = permissions.map { perm ->
            if (ContextCompat.checkSelfPermission(this, perm) == PackageManager.PERMISSION_GRANTED) {
                PackageManager.PERMISSION_GRANTED
            } else {
                PackageManager.PERMISSION_DENIED
            }
        }.toIntArray()
        return Intent().apply {
            putExtra(
                "androidx.activity.result.contract.extra.PERMISSIONS",
                permissions,
            )
            putExtra(
                "androidx.activity.result.contract.extra.PERMISSION_GRANT_RESULTS",
                grantResults,
            )
        }
    }

    private val overlaySystemResultReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val i = intent ?: return
            val requestCode = i.getIntExtra(OverlaySystemDialogActivity.EXTRA_REQUEST_CODE, -1)
            if (requestCode < 0) return
            mainHandler.post {
                when (i.action) {
                    OverlaySystemDialogActivity.ACTION_OVERLAY_ACTIVITY_CANCELED -> {
                        deliverOverlayActivityResult(
                            requestCode = requestCode,
                            resultCode = android.app.Activity.RESULT_CANCELED,
                            data = Intent(),
                        )
                        resumeOverlayWindowsAfterSystemActivity(skipFullscreenRebalance = true)
                    }
                    OverlaySystemDialogActivity.ACTION_OVERLAY_PICK_IMAGES_RESULT -> {
                        if (i.getBooleanExtra(OverlaySystemDialogActivity.EXTRA_COPY_FAILED, false)) {
                            Toast.makeText(
                                this@CombatOverlayService,
                                getString(R.string.chat_attachment_read_failed),
                                Toast.LENGTH_LONG,
                            ).show()
                            deliverOverlayActivityResult(
                                requestCode = requestCode,
                                resultCode = android.app.Activity.RESULT_CANCELED,
                                data = Intent(),
                            )
                            resumeOverlayWindowsAfterSystemActivity(skipFullscreenRebalance = true)
                            return@post
                        }
                        if (i.getBooleanExtra(OverlaySystemDialogActivity.EXTRA_PARTIAL_COPY_FAILED, false)) {
                            val picked = i.getIntExtra(OverlaySystemDialogActivity.EXTRA_PICKED_COUNT, 0)
                            val copied = i.getIntExtra(OverlaySystemDialogActivity.EXTRA_COPIED_COUNT, 0)
                            Toast.makeText(
                                this@CombatOverlayService,
                                getString(R.string.chat_attachment_partial_read_failed, copied, picked),
                                Toast.LENGTH_LONG,
                            ).show()
                        }
                        val uris = extractPickerUris(i)
                        val data = OverlayImagePickDelivery.intentForPickedImages(uris)
                        deliverOverlayPickImagesResult(
                            requestCode = requestCode,
                            resultCode = android.app.Activity.RESULT_OK,
                            data = data,
                            fallbackUris = uris,
                        )
                        resumeOverlayWindowsAfterSystemActivity(skipFullscreenRebalance = true)
                        if (uris.isNotEmpty()) {
                            Toast.makeText(
                                this@CombatOverlayService,
                                getString(R.string.chat_attachments_added, uris.size),
                                Toast.LENGTH_SHORT,
                            ).show()
                        }
                    }
                    OverlaySystemDialogActivity.ACTION_OVERLAY_GET_CONTENT_RESULT -> {
                        if (i.getBooleanExtra(OverlaySystemDialogActivity.EXTRA_COPY_FAILED, false)) {
                            Toast.makeText(
                                this@CombatOverlayService,
                                getString(R.string.chat_attachment_read_failed),
                                Toast.LENGTH_LONG,
                            ).show()
                            deliverOverlayActivityResult(
                                requestCode = requestCode,
                                resultCode = android.app.Activity.RESULT_CANCELED,
                                data = Intent(),
                            )
                            resumeOverlayWindowsAfterSystemActivity(skipFullscreenRebalance = true)
                            return@post
                        }
                        val uri = if (Build.VERSION.SDK_INT >= 33) {
                            i.getParcelableExtra(OverlaySystemDialogActivity.EXTRA_URI, Uri::class.java)
                        } else {
                            @Suppress("DEPRECATION")
                            i.getParcelableExtra(OverlaySystemDialogActivity.EXTRA_URI)
                        }
                        val data = OverlayImagePickDelivery.intentForGetContent(uri)
                        deliverOverlayGetContentResult(
                            requestCode = requestCode,
                            resultCode = android.app.Activity.RESULT_OK,
                            data = data,
                            fallbackUri = uri,
                        )
                        resumeOverlayWindowsAfterSystemActivity(skipFullscreenRebalance = true)
                        uri?.let {
                            Toast.makeText(
                                this@CombatOverlayService,
                                getString(R.string.chat_attachments_added, 1),
                                Toast.LENGTH_SHORT,
                            ).show()
                        }
                    }
                    OverlaySystemDialogActivity.ACTION_OVERLAY_MIC_PERMISSION_RESULT -> {
                        val granted = i.getBooleanExtra(OverlaySystemDialogActivity.EXTRA_GRANTED, false)
                        val data = Intent().apply {
                            putExtra(
                                "androidx.activity.result.contract.extra.PERMISSION_GRANT_RESULTS",
                                intArrayOf(if (granted) 0 else -1),
                            )
                        }
                        deliverOverlayActivityResult(
                            requestCode = requestCode,
                            resultCode = android.app.Activity.RESULT_OK,
                            data = data,
                        )
                        resumeOverlayWindowsAfterSystemActivity(skipFullscreenRebalance = true)
                    }
                    OverlaySystemDialogActivity.ACTION_OVERLAY_GALLERY_PERMISSION_RESULT -> {
                        val data = intentForOverlayGalleryPermissionResults()
                        deliverOverlayActivityResult(
                            requestCode = requestCode,
                            resultCode = android.app.Activity.RESULT_OK,
                            data = data,
                        )
                        resumeOverlayWindowsAfterSystemActivity(skipFullscreenRebalance = true)
                    }
                }
            }
        }
    }

    internal inner class OverlayChatComposeOwner :
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
                val kind = OverlayActivityResultKind.kindFor(contract, input)
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
                    // Без CLEAR_TOP: иначе при пересоздании хоста onDestroy шлёт CANCELED и сбивает пикер.
                    addFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK or
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
                // Пикер/разрешения: полностью убираем overlay-чат, иначе TYPE_APPLICATION_OVERLAY
                // просвечивает сквозь системную галерею.
                suspendOverlayWindowsForSystemActivity(keepOverlayChromeVisible = false)
                mainHandler.post {
                    try {
                        startActivity(i)
                    } catch (e: Exception) {
                        Log.e(TAG, "Overlay system dialog start failed", e)
                        resumeOverlayWindowsAfterSystemActivity(skipFullscreenRebalance = true)
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

    internal fun obtainOverlayPopoverComposeOwner(): OverlayChatComposeOwner =
        overlayPopoverComposeOwner
            ?: OverlayChatComposeOwner().also { overlayPopoverComposeOwner = it }

    /** Compose in overlay popups/HUD windows needs ViewTree owners or LaunchedEffect crashes. */
    internal fun attachOverlayComposeTree(vararg targets: View) {
        val owner = obtainOverlayPopoverComposeOwner()
        targets.forEach { target ->
            target.setViewTreeLifecycleOwner(owner)
            target.setViewTreeViewModelStoreOwner(owner)
            target.setViewTreeSavedStateRegistryOwner(owner)
            target.setViewTreeOnBackPressedDispatcherOwner(owner)
        }
    }

    private val stripTickRunnable = Runnable {
        if (!isInGameOverlayUiActive() || !isOverlayShellActive()) {
            cancelStripTick()
            return@Runnable
        }
        val previewBefore = stripBuffer.visibleForPreview().size
        if (previewBefore == 0) {
            stripBuffer.prune()
            cancelStripTick()
            return@Runnable
        }
        stripBuffer.prune()
        val previewAfter = stripBuffer.visibleForPreview().size
        if (previewBefore != previewAfter) {
            lastStripRenderSignature = 0
            scheduleRefreshOverlayChatStrip()
        }
        if (previewAfter > 0) {
            scheduleStripTick()
        } else {
            cancelStripTick()
        }
    }

    private var stripUiCoalescePosted = false
    private var chatStripZOrderLifted = false
    private var stripPassthroughSyncPosted = false
    private val stripPassthroughSyncRunnable = Runnable {
        stripPassthroughSyncPosted = false
        syncChatStripWindowTouchPassthrough()
    }
    private val stripUiCoalesceRunnable = Runnable {
        stripUiCoalescePosted = false
        refreshOverlayChatStripNow()
    }

    /** Throttle для Log при «панель скрыта» — не дёргаем FGS-текст при каждом тике гейта. */
    private var gateNotifyKey: String = ""
    private var lastStripRenderSignature: Int = 0
    /** Increments on each live strip publish so Compose/refresh never skip repeat sends. */
    private var stripLiveRevision: Int = 0
    /** After gate «not in game» cleared strip session; restart visibleSince on next in-game tick. */
    private var stripSessionNeedsRestart = false
    private var lastAppliedGateShouldShow: Boolean? = null
    private var stableGatePollTicks = 0
    @Volatile
    private var gateCheckInFlight = false
    private var lastGateDiagLogMs: Long = 0L
    private var lastForegroundHintPkg: String? = null
    @Volatile
    private var lastOverlayInGameAtMs: Long = 0L
    /** Последний результат foreground-пробы «в игре» (не путать с UI-гейтом при открытом чате). */
    @Volatile
    private var overlayInGameProbeActive: Boolean = false
    /** Heartbeat «ingame» для списка союзников — не привязан к видимости HUD/ленты. */
    private var overlayIngamePresenceActive = false
    private var overlayIngameMissStreak = 0
    /** Не вызывать [NotificationManager.notify] с тем же текстом подряд (лишние всплытия на части OEM). */
    private var lastForegroundNotificationText: String? = null
    private var lastForegroundMicActive: Boolean = false
    /** FGS notification visible while in-game overlay is active (hidden when idle outside game). */
    private var overlayForegroundPromoted: Boolean = false

    @Volatile
    private var cachedHasUsageAccess: Boolean? = null

    @Volatile
    private var cachedHasUsageAccessAtMs: Long = 0L

    private fun hasUsageAccessCached(force: Boolean = false): Boolean {
        val now = System.currentTimeMillis()
        val cached = cachedHasUsageAccess
        if (!force && cached != null && now - cachedHasUsageAccessAtMs <= USAGE_ACCESS_CACHE_MS) {
            return cached
        }
        val fresh = GameForegroundGate.hasUsageStatsAccessForOverlay(this)
        cachedHasUsageAccess = fresh
        cachedHasUsageAccessAtMs = now
        return fresh
    }

    private var screenOnReceiverRegistered: Boolean = false

    private val screenOnReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_ON -> {
                    GameForegroundGate.invalidateForegroundHintCache()
                    GameForegroundGate.invalidateUsageAccessCache()
                }
                Intent.ACTION_SCREEN_OFF -> {
                    GameForegroundGate.invalidateForegroundHintCache()
                    overlayInGameProbeActive = false
                    gateUiHideStreak = GATE_HIDE_UI_HYSTERESIS_TICKS
                    gateSoftHideStartedAtMs = 0L
                    overlayUiHoldUntilMs = 0L
                }
                else -> return
            }
            mainHandler.post { tickGameGate() }
        }
    }

    @Volatile
    private var overlaySessionActive: Boolean = false

    /** Id «Рейд» с последней успешной отправки/ensure — лента без кэша комнат. */
    @Volatile
    private var trustedOverlayRaidRoomId: String? = null

    private var cachedAllianceHubRoomId: String? = null
    private var lastGateBlockReason: OverlayGateUserNotifier.BlockReason? = null

    private var hudBadgeRefreshPosted: Boolean = false
    private var pendingHubHudRefresh: Boolean = false
    private var pendingForumHudRefresh: Boolean = false

    /** Optimistic hub badge from socket before [listRooms] / cache catch up. */
    @Volatile
    private var hubUnreadOptimisticFloor: Int = 0

    private var hubUnreadLastBumpAtMs: Long = 0L

    /** After raid send / quick commands — block game-gate dismiss that removes HUD windows. */
    @Volatile
    private var overlayUiHoldUntilMs: Long = 0L

    private val hudBadgeRefreshRunnable = Runnable {
        hudBadgeRefreshPosted = false
        val doHub = pendingHubHudRefresh
        val doForum = pendingForumHudRefresh
        pendingHubHudRefresh = false
        pendingForumHudRefresh = false
        if (doHub) refreshOverlayHubUnreadOnly()
        if (doForum) refreshOverlayForumBadgeOnly()
    }

    @Volatile
    private var hudRefreshInFlight: Boolean = false

    @Volatile
    private var hudRefreshPending: Boolean = false

    private var hudRefreshJob: Job? = null

    private var lastHudRefreshCompletedAtMs: Long = 0L

    private var lastHudPresenceCountRefreshAtMs: Long = 0L

    @Volatile
    private var deferredDismissWhenPickerEnds: Boolean = false

    private val overlayCloseHudRefreshRunnable = Runnable {
        if (!isInGameOverlayUiActive()) return@Runnable
        restoreOverlayHudChromeAfterPanel()
        refreshOverlayChatStripNow()
        refreshOverlayHubUnreadFromCache()
        refreshOverlayNewsBadgeOnly()
        refreshOverlayForumBadgeOnly()
    }

    private var lastStripZOrderLiftMs: Long = 0L

    private var lastHudZOrderRebalanceMs: Long = 0L

    /** Подряд тиков гейта «не показывать UI» — скрываем HUD только после N, чтобы не мигать. */
    private var gateUiHideStreak: Int = 0

    /** When gate first went false — hard dismiss only after [GATE_DISMISS_AFTER_MS]. */
    private var gateSoftHideStartedAtMs: Long = 0L

    /** Keep raid strip visible after local send despite brief gate flap. */
    @Volatile
    private var forceShowStripUntilMs: Long = 0L

    private var stripZOrderLiftPosted: Boolean = false

    private val stripZOrderLiftRunnable = Runnable {
        stripZOrderLiftPosted = false
        requestChatStripZOrderLift()
    }

    private val gameGateRunnable = Runnable {
        runCatching { tickGameGate() }
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
        promoteOverlayForeground(notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        Log.i(TAG, "onCreate: startForeground OK")
        bindOverlayReadCursorsIfPossible()
        isServiceInstanceActive = true
        runningInstance = this
        _serviceRunning.value = true
        _overlayVisible.value = false
        OverlayRuntimeScheduler.syncSchedule(this)
        GameForegroundGate.invalidateForegroundHintCache()
        GameForegroundGate.invalidateUsageAccessCache()
        val targets = AppContainer.from(this).userSettingsPreferences.getOverlayTargetGamePackages()
        GameForegroundGate.primeTotalTimeForegroundWatch(this, targets)
        registerScreenOnReceiver()
        startOverlayFcmTokenRegistration()
        mainHandler.post {
            ensureOverlayRaidRealtimeIfNeeded()
            tickGameGate()
        }
    }

    /** FCM token on backend even when MainActivity never opens (overlay-only players). */
    private fun startOverlayFcmTokenRegistration() {
        val container = AppContainer.from(this)
        if (!container.authRepository.hasSession()) return
        fcmRegistrationJob?.cancel()
        fcmRegistrationJob = serviceScope.launch {
            val bootstrapDelaysMs = longArrayOf(0L, 2_000L, 10_000L, 30_000L, 120_000L)
            for (delayMs in bootstrapDelaysMs) {
                if (!isActive) return@launch
                if (delayMs > 0) delay(delayMs)
                if (FcmTokenManager.registerWithBackend(this@CombatOverlayService).isSuccess) break
            }
            val intervalMs = 20 * 60 * 1000L
            while (isActive) {
                delay(intervalMs)
                runCatching {
                    FcmTokenManager.registerWithBackend(this@CombatOverlayService)
                }
            }
        }
    }

    /** True only when an overlay window is actually attached (not merely cached in memory). */
    private fun isOverlayShellActive(): Boolean {
        if (chatStripHost?.isAttachedToWindow == true) return true
        if (overlayStatusHudHost?.isAttachedToWindow == true) return true
        if (overlayTopRightHudHost?.isAttachedToWindow == true) return true
        return overlayChatTeamRoot?.isAttachedToWindow == true
    }

    private fun isInGameOverlayUiActive(): Boolean = lastAppliedGateShouldShow == true

    private fun extendOverlayUiHold(durationMs: Long = OVERLAY_UI_HOLD_AFTER_RAID_SEND_MS) {
        overlayUiHoldUntilMs = maxOf(
            overlayUiHoldUntilMs,
            System.currentTimeMillis() + durationMs,
        )
    }

    private fun isOverlayUiHoldActive(): Boolean =
        System.currentTimeMillis() < overlayUiHoldUntilMs

    /**
     * Сглаживание ложных «не в игре» между тиками usage-stats: показ сразу, скрытие после
     * [GATE_HIDE_UI_HYSTERESIS_TICKS] подряд false. Grace только пока [overlayInGameProbeActive].
     */
    private fun resolveStableOverlayUiVisible(
        probeShow: Boolean,
        forceHideNow: Boolean = false,
        inGameProbe: Boolean = overlayInGameProbeActive,
    ): Boolean {
        if (!inGameProbe) {
            gateUiHideStreak = GATE_HIDE_UI_HYSTERESIS_TICKS
            return probeShow
        }
        if (probeShow) {
            gateUiHideStreak = 0
            return true
        }
        if (forceHideNow) {
            gateUiHideStreak = GATE_HIDE_UI_HYSTERESIS_TICKS
            return false
        }
        val inInGameGrace = lastOverlayInGameAtMs > 0L &&
            System.currentTimeMillis() - lastOverlayInGameAtMs < OVERLAY_INGAME_GRACE_MS
        if (inInGameGrace && lastAppliedGateShouldShow == true) {
            gateUiHideStreak = 0
            return true
        }
        gateUiHideStreak++
        return if (gateUiHideStreak >= GATE_HIDE_UI_HYSTERESIS_TICKS) {
            false
        } else {
            lastAppliedGateShouldShow == true
        }
    }

    private fun overlayChatTeamSurfaceColor(): Int {
        val overlayUiContext = OverlayTickerUi.themedFabContext(this)
        return MaterialColors.getColor(
            overlayUiContext,
            com.google.android.material.R.attr.colorSurface,
            Color.parseColor("#10141E"),
        )
    }

    /** После пикера/снимка окон — непрозрачный фон панели чата (без remove/add). */
    private fun restoreOverlayChatTeamPanelOpaqueAppearance() {
        val root = overlayChatTeamRoot ?: return
        val mgr = windowManager ?: systemWindowManager() ?: return
        root.setBackgroundColor(overlayChatTeamSurfaceColor())
        root.alpha = 1f
        if (root.visibility != View.VISIBLE) {
            root.visibility = View.VISIBLE
        }
        val params = overlayChatTeamParams ?: return
        var needsLayout = false
        if (params.format != PixelFormat.OPAQUE) {
            params.format = PixelFormat.OPAQUE
            needsLayout = true
        }
        if (needsLayout && root.isAttachedToWindow) {
            runCatching { mgr.updateViewLayout(root, params) }
        }
    }

    private fun syncOverlayHudWindowLayout() {
        val mgr = windowManager ?: systemWindowManager() ?: return
        fun update(
            host: FrameLayout?,
            params: WindowManager.LayoutParams?,
            gravity: Int,
            xDp: Int,
        ) {
            if (host == null || params == null || !host.isAttachedToWindow) return
            params.gravity = gravity
            params.x = dp(xDp)
            params.y = dp(OVERLAY_HUD_WINDOW_Y_DP)
            runCatching { mgr.updateViewLayout(host, params) }
        }
        update(
            overlayStatusHudHost,
            overlayStatusHudParams,
            Gravity.TOP or Gravity.START,
            OVERLAY_HUD_LEFT_WINDOW_X_DP,
        )
        update(
            overlayTopRightHudHost,
            overlayTopRightHudParams,
            Gravity.TOP or Gravity.END,
            OVERLAY_HUD_WINDOW_X_DP,
        )
    }

    private fun isOverlayHudOnlyMode(): Boolean =
        AppContainer.from(this).userSettingsPreferences.isOverlayHudOnlyMode()

    /** Лента «Рейд» / карточки сообщений — при включённой панели (не режим «только HUD»). */
    private fun isOverlayChatStripEnabled(): Boolean {
        val prefs = AppContainer.from(this).userSettingsPreferences
        return prefs.isOverlayPanelEnabled() && !prefs.isOverlayHudOnlyMode()
    }

    private fun retainWindowManager(manager: WindowManager) {
        if (windowManager == null) {
            windowManager = manager
        }
    }

    private fun isOverlayLightStripMode(): Boolean =
        AppContainer.from(this).userSettingsPreferences.isOverlayLightStrip()

    /**
     * Система (OEM / нехватка памяти) может снять ленту с экрана, оставив ссылки на View.
     */
    private fun repairDetachedOverlayShellIfNeeded() {
        if (!isInGameOverlayUiActive()) {
            repairOrRemoveDetachedHudWindows()
            return
        }
        val mgr = windowManager ?: systemWindowManager()
        if (mgr == null) {
            if (isInGameOverlayUiActive() && canDrawOverlaysNow()) {
                runCatching { showOverlayShell() }
            }
            return
        }
        repairOrRemoveDetachedHudWindows()
        if (isOverlayChatStripEnabled()) {
            repairDetachedChatStripIfNeeded(mgr)
        }
        val host = chatStripHost ?: return
        if (host.isAttachedToWindow) return
        val params = chatStripParams
        if (params != null) {
            Log.w(TAG, "repairDetachedOverlayShellIfNeeded: re-attaching chat strip")
            runCatching { mgr.addView(host, params) }
                .onSuccess {
                    scheduleStripZOrderLift()
                    return
                }
                .onFailure { e ->
                    Log.w(TAG, "repairDetachedOverlayShellIfNeeded: re-attach failed", e)
                }
        }
        Log.w(TAG, "repairDetachedOverlayShellIfNeeded: rebuilding chat strip")
        removeChatStripWindow(mgr)
        ensureChatStripWindow(mgr)
        if (chatStripHost != null) {
            _overlayVisible.value = true
            beginOverlayChatSubscription()
        }
        repairDetachedOverlayChatTeamPanelIfNeeded()
    }

    private fun repairOrRemoveDetachedHudWindows() {
        val mgr = windowManager ?: systemWindowManager() ?: return
        if (!isInGameOverlayUiActive()) {
            removeOverlayStatusHudWindow()
            removeOverlayTopRightHudWindow()
            return
        }
        fun reattach(host: FrameLayout?, params: WindowManager.LayoutParams?, label: String) {
            if (host == null || params == null) return
            if (host.isAttachedToWindow) return
            Log.w(TAG, "repairOrRemoveDetachedHudWindows: re-attaching $label")
            runCatching { mgr.addView(host, params) }
                .onFailure { e -> Log.w(TAG, "repairOrRemoveDetachedHudWindows: $label failed", e) }
        }
        reattach(overlayStatusHudHost, overlayStatusHudParams, "statusHud")
        reattach(overlayTopRightHudHost, overlayTopRightHudParams, "topRightHud")
    }

    private fun removeOverlayWindowTracked(
        host: View?,
        windowLabel: String,
        onCleared: () -> Unit,
    ) {
        if (host == null) {
            onCleared()
            return
        }
        val managers = listOfNotNull(windowManager, systemWindowManager()).distinct()
        for (wm in managers) {
            if (!host.isAttachedToWindow) break
            runCatching { wm.removeView(host) }
        }
        if (host.isAttachedToWindow) {
            Log.w(TAG, "removeOverlayWindowTracked: $windowLabel still attached")
            if (BuildConfig.DEBUG) {
                Log.d(OVERLAY_DIAG_TAG, "orphanWindow label=$windowLabel")
            }
            host.visibility = View.GONE
        }
        onCleared()
    }

    /**
     * Поднять HUD-окна поверх ленты (remove/add). Дорого и даёт видимое мерцание — не чаще
     * [HUD_ZORDER_REBALANCE_MIN_MS], кроме [force] (подъём ленты, возврат в игру).
     */
    private fun rebalanceOverlayHudZOrder(force: Boolean = false) {
        if (!isInGameOverlayUiActive()) return
        val now = System.currentTimeMillis()
        if (!force && now - lastHudZOrderRebalanceMs < HUD_ZORDER_REBALANCE_MIN_MS) return
        val mgr = windowManager ?: systemWindowManager() ?: return
        fun lift(host: FrameLayout?, params: WindowManager.LayoutParams?) {
            if (host == null || params == null || !host.isAttachedToWindow) return
            runCatching {
                mgr.removeView(host)
                mgr.addView(host, params)
            }.onFailure { e ->
                Log.w(TAG, "rebalanceOverlayHudZOrder failed", e)
            }
        }
        lift(overlayStatusHudHost, overlayStatusHudParams)
        lift(overlayTopRightHudHost, overlayTopRightHudParams)
        lastHudZOrderRebalanceMs = now
    }

    /**
     * На части ROM addView AlertDialog/системного окна временно отцепляет полноэкранный чат/команду.
     * Восстанавливаем то же [overlayChatTeamRoot], не пересоздавая Compose.
     */
    private fun repairDetachedOverlayChatTeamPanelIfNeeded() {
        if (OverlayChatInteractionHold.isOverlaySystemPickerSessionActive()) {
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
            overlayCommandsPopover.isBlockingGameGateDismiss() ||
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
        if (!isOverlayShellActive()) {
            val result = runCatching { showOverlayShell() }
            if (result.isFailure) {
                Log.e(TAG, "ensureOverlayIfPermitted: showOverlayShell crashed", result.exceptionOrNull())
                runCatching { removeOverlayControl() }
                _overlayVisible.value = false
                Log.w(TAG, "ensureOverlayIfPermitted: showOverlayShell failed")
            }
        } else {
            ensureOverlayMessageStripIfNeeded()
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
            syncOverlayIngamePresence(inGameProbe = false)
            if (isOverlayShellActive()) {
                removeOverlayControl()
            }
            return
        }
        // Main app UI is open and overlay HUD is not shown — skip UsageStats heuristics.
        if (mainAppForegroundActive &&
            !isInGameOverlayUiActive() &&
            !isOverlayShellActive() &&
            !overlayChatTeamPanelVisible &&
            !OverlayChatInteractionHold.isFullscreenChatTeamPanelVisible
        ) {
            overlayInGameProbeActive = false
            syncOverlayIngamePresence(inGameProbe = false)
            scheduleGameGateTick(GAME_GATE_POLL_MAIN_APP_FOREGROUND_MS)
            return
        }
        if (gateCheckInFlight) {
            scheduleGameGateTick(nextGameGateDelayMs())
            return
        }
        gateCheckInFlight = true
        val targets = prefs.getOverlayTargetGamePackages()
        val activityTokens = prefs.getOverlayTargetGameActivityTokens()
        serviceScope.launch {
            try {
                val hasUsageAccess = hasUsageAccessCached()
                var hintedPkg: String? = lastForegroundHintPkg
                val targetSet = targets.toSet()
                val stableInGameUi = lastAppliedGateShouldShow == true &&
                    stableGatePollTicks >= GATE_STABLE_TICKS_FOR_SLOW_POLL
                val quickProbe = if (!hasUsageAccess || targets.isEmpty()) {
                    null
                } else {
                    GameForegroundGate.quickTargetForegroundProbe(
                        context = this@CombatOverlayService,
                        targetGamePackages = targets,
                        allowedActivitySubstrings = activityTokens,
                        preferredForegroundPackage = lastForegroundHintPkg,
                    )
                }
                val popoverUiActive = overlayCommandsPopover.isBlockingGameGateDismiss()
                val forceResumeRefresh = hasUsageAccess && targets.isNotEmpty() &&
                    !popoverUiActive &&
                    when {
                        quickProbe == GameForegroundGate.QuickForegroundProbe.NEED_FULL_HEURISTICS -> true
                        quickProbe == null -> false
                        !stableInGameUi -> true
                        stableGatePollTicks % 10 == 0 -> true
                        else -> false
                    }
                val resumeComp = if (hasUsageAccess && targets.isNotEmpty()) {
                    runCatching {
                        GameForegroundGate.lastResumedComponent(
                            this@CombatOverlayService,
                            forceRefresh = forceResumeRefresh,
                        )
                    }.getOrNull()
                } else {
                    null
                }
                val freshResumePkg = resumeComp?.packageName
                hintedPkg = freshResumePkg ?: hintedPkg
                val inGameProbe = if (!hasUsageAccess || targets.isEmpty()) {
                    false
                } else {
                    when (quickProbe ?: GameForegroundGate.QuickForegroundProbe.NEED_FULL_HEURISTICS) {
                        GameForegroundGate.QuickForegroundProbe.IN_TARGET -> {
                            lastForegroundHintPkg = freshResumePkg?.takeIf { it in targetSet }
                                ?: targets.firstOrNull()
                            true
                        }
                        GameForegroundGate.QuickForegroundProbe.NOT_IN_TARGET -> {
                            lastForegroundHintPkg = null
                            GameForegroundGate.invalidateForegroundHintCache()
                            false
                        }
                        GameForegroundGate.QuickForegroundProbe.NEED_FULL_HEURISTICS -> {
                            lastForegroundHintPkg = freshResumePkg
                            if (activityTokens.isNotEmpty()) {
                                Log.d(
                                    OVERLAY_DIAG_TAG,
                                    "activityGate hint pkg=${resumeComp?.packageName ?: "-"} " +
                                        "cls=${resumeComp?.className ?: "-"} tokens=${activityTokens.joinToString()}",
                                )
                            }
                            GameForegroundGate.shouldShowOverlayCached(
                                context = this@CombatOverlayService,
                                targetGamePackages = targets,
                                allowedActivitySubstrings = activityTokens,
                            )
                        }
                    }
                }
                val conflictingForeground = freshResumePkg != null &&
                    GameForegroundGate.isConflictingForegroundHint(
                        freshResumePkg,
                        targetSet,
                        packageName,
                    )
                val inGame = if (conflictingForeground) {
                    lastForegroundHintPkg = null
                    GameForegroundGate.invalidateForegroundHintCache()
                    false
                } else if (!inGameProbe) {
                    lastForegroundHintPkg = null
                    GameForegroundGate.invalidateForegroundHintCache()
                    false
                } else {
                    if (freshResumePkg != null) {
                        lastForegroundHintPkg = freshResumePkg
                    }
                    true
                }
                val nowMs = System.currentTimeMillis()
                overlayInGameProbeActive = inGame
                if (inGame) {
                    lastOverlayInGameAtMs = nowMs
                }
                mainHandler.post {
                    if (!hasUsageAccess && shouldKeepUsageAccessDiag()) {
                        Log.i(TAG, "overlayGate: usage access missing (cached), idlePollMs=$GAME_GATE_POLL_IDLE_MS")
                    }
                    // Попап команд/реакций — только на main: иначе на части ROM гонка снимает HUD.
                    // HUD только в игре (или при системном пикере). Попап/чат не держат кнопки после minimize.
                    val shouldShowInGameOverlayUi = when {
                        !inGame -> OverlayChatInteractionHold.isOverlaySystemPickerSessionActive() ||
                            overlayCommandsPopover.isBlockingGameGateDismiss()
                        else -> true
                    }
                    val popoverBlocksDismiss = overlayCommandsPopover.isBlockingGameGateDismiss()
                    val stableShowInGameOverlayUi = resolveStableOverlayUiVisible(
                        probeShow = shouldShowInGameOverlayUi,
                        forceHideNow = (!inGame && !popoverBlocksDismiss) ||
                            (conflictingForeground && !popoverBlocksDismiss),
                        inGameProbe = inGame,
                    )
                    val diagNowMs = System.currentTimeMillis()
                    if (diagNowMs - lastGateDiagLogMs >= 25_000L) {
                        lastGateDiagLogMs = diagNowMs
                        val draw = canDrawOverlaysNow()
                        if (!hasUsageAccess || !stableShowInGameOverlayUi || !draw || !isOverlayShellActive()) {
                            Log.i(
                                TAG,
                                "overlayGate usage=$hasUsageAccess inGame=$inGame showUi=$stableShowInGameOverlayUi " +
                                    "probeUi=$shouldShowInGameOverlayUi hideStreak=$gateUiHideStreak " +
                                    "hint=${hintedPkg ?: "-"} drawOverlays=$draw overlayAttached=${isOverlayShellActive()} " +
                                    "targets=${targets.joinToString()}",
                            )
                        }
                        OverlayPerfDiag.logGateState(
                            inGame = inGame,
                            showUi = stableShowInGameOverlayUi,
                            stripNotTouchable = chatStripParams?.flags?.let {
                                it and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE != 0
                            } ?: true,
                            dismissRectCount = (chatStripHost as? OverlayStripPassthroughFrameLayout)
                                ?.dismissRectsInCompose?.size ?: 0,
                            zOrderLifted = chatStripZOrderLifted,
                        )
                    }
                    syncOverlayIngamePresence(inGameProbe = inGame)
                    applyGameGateState(
                        hasUsageAccess = hasUsageAccess,
                        shouldShow = stableShowInGameOverlayUi,
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
        if (mainAppForegroundActive && !isInGameOverlayUiActive() && !isOverlayShellActive()) {
            return GAME_GATE_POLL_MAIN_APP_FOREGROUND_MS
        }
        if (overlayCommandsPopover.isBlockingGameGateDismiss() ||
            overlayChatTeamPanelVisible ||
            OverlayChatInteractionHold.isFullscreenChatTeamPanelVisible
        ) {
            return GAME_GATE_POLL_MODAL_UI_MS
        }
        if (!isInGameOverlayUiActive()) {
            val last = lastOverlayInGameAtMs
            return if (last > 0L && System.currentTimeMillis() - last <= GAME_GATE_RECENT_INGAME_WINDOW_MS) {
                GAME_GATE_POLL_WARM_MS
            } else {
                GAME_GATE_POLL_IDLE_MS
            }
        }
        if (isOverlayShellActive() &&
            stableGatePollTicks >= GATE_STABLE_TICKS_FOR_SLOW_POLL
        ) {
            return GAME_GATE_POLL_STABLE_MS
        }
        if (isOverlayShellActive()) return GAME_GATE_POLL_ACTIVE_MS
        return GAME_GATE_POLL_IDLE_MS
    }

    private fun shouldKeepUsageAccessDiag(): Boolean {
        val now = System.currentTimeMillis()
        if (now - lastGateDiagLogMs < 25_000L) return false
        return true
    }

    private fun scheduleOverlayStatusHudRefresh() {
        if (!isInGameOverlayUiActive()) return
        if (statusHudRefreshPosted) return
        statusHudRefreshPosted = true
        mainHandler.postDelayed(statusHudRefreshRunnable, STATUS_HUD_REFRESH_MS)
    }

    private fun bindOverlayReadCursorsIfPossible() {
        serviceScope.launch(Dispatchers.IO) {
            val container = AppContainer.from(this@CombatOverlayService)
            val uid = container.usersRepository.resolveMyProfilePreferCache()?.id?.trim().orEmpty()
                .ifEmpty { jwtSubFromAccessToken()?.trim().orEmpty() }
            if (uid.isEmpty()) return@launch
            ReadCursorSession.bind(
                container.chatRoomPreferences,
                container.teamForumPreferences,
                container.userSettingsPreferences,
                uid,
            )
            ReadCursorSession.syncTeamNewsReadCursor(
                container.usersRepository,
                container.teamsRepository,
                container.userSettingsPreferences,
            )
        }
    }

    private fun syncOverlayChatPanelVisibilityToViewModel(visible: Boolean) {
        val shared = resolveChatViewModel()
        shared?.setOverlayChatPanelVisible(visible)
        val fallback = overlayFallbackChatViewModel
        if (fallback != null && fallback !== shared) {
            fallback.setOverlayChatPanelVisible(visible)
        }
    }

    private fun refreshOverlayStatusHudData(force: Boolean = false) {
        if (!isInGameOverlayUiActive()) return
        bindOverlayReadCursorsIfPossible()
        val now = System.currentTimeMillis()
        if (!force && now - lastHudRefreshCompletedAtMs < HUD_REFRESH_MIN_INTERVAL_MS) {
            val remaining = HUD_REFRESH_MIN_INTERVAL_MS - (now - lastHudRefreshCompletedAtMs)
            if (!statusHudRefreshPosted) {
                statusHudRefreshPosted = true
                mainHandler.postDelayed(statusHudRefreshRunnable, remaining.coerceAtLeast(50L))
            }
            return
        }
        if (hudRefreshInFlight) {
            hudRefreshPending = true
            return
        }
        hudRefreshInFlight = true
        hudRefreshJob?.cancel()
        OverlayPerfDiag.logHudRefreshScheduled()
        val startedAt = android.os.SystemClock.elapsedRealtime()
        hudRefreshJob = serviceScope.launch(Dispatchers.IO) {
            try {
                val container = AppContainer.from(this@CombatOverlayService)
                val cachedRooms = ChatSessionCache.getFreshRooms()
                val rooms = cachedRooms
                    ?: runCatching { container.chatRepository.listRooms().getOrNull() }.getOrNull()?.also {
                        ChatSessionCache.update(it)
                    }
                rooms?.let { list ->
                    cachedAllianceHubRoomId = OverlayGameStatusHudRefresh.allianceHubRoom(list)?.id
                    com.lastasylum.alliance.data.chat.ChatSessionCache.update(list)
                    container.chatRepository.applyOverlayRoomsFromRooms(list)
                    mainHandler.post { syncOverlayRaidRoomSubscription() }
                }
                val refreshNewsForum = force ||
                    now - lastHudRefreshCompletedAtMs >= STATUS_HUD_REFRESH_MS
                val state = runCatching {
                    OverlayGameStatusHudRefresh.load(
                        context = this@CombatOverlayService,
                        preloadedRooms = rooms,
                        refreshNewsForum = refreshNewsForum,
                    )
                }.getOrElse { OverlayGameStatusHudState() }
                val hubDisplayed = if (rooms != null) {
                    val localRead = container.chatRoomPreferences.loadAllLastReadMessageIds()
                    ChatUnreadCounts.overlayAllianceHubBadge(
                        rooms = rooms,
                        localReadByRoom = localRead,
                        optimisticFloor = hubUnreadOptimisticFloor,
                        previouslyDisplayed = overlayStatusHudFlow.value.allianceChatUnread,
                    ).also { merged ->
                        if (merged <= 0) {
                            val locallyRead = ChatUnreadCounts.isAllianceHubLocallyReadSuppressed(
                                rooms,
                                localRead,
                            )
                            if (locallyRead) {
                                hubUnreadOptimisticFloor = 0
                            }
                        }
                    }
                } else {
                    state.allianceChatUnread
                }
                val prevHud = overlayStatusHudFlow.value
                val refreshTeamInboxBadges = force || refreshNewsForum
                val hubBumpGraceActive = hubUnreadOptimisticFloor > 0 &&
                    System.currentTimeMillis() - hubUnreadLastBumpAtMs < HUB_UNREAD_RECONCILE_GRACE_MS
                val hubMerged = when {
                    hubBumpGraceActive || shouldDeferHubUnreadReconcile() ->
                        maxOf(hubDisplayed, hubUnreadOptimisticFloor)
                    rooms != null -> hubDisplayed
                    else -> maxOf(hubDisplayed, hubUnreadOptimisticFloor)
                }
                val mergedState = state.copy(
                    allianceChatUnread = hubMerged,
                    forumUnread = inboxBadgeCoordinator.mergeHudForum(
                        authoritative = state.forumUnread,
                        prevDisplayed = prevHud.forumUnread,
                        useAuthoritative = refreshTeamInboxBadges,
                    ),
                    teamNewsUnread = inboxBadgeCoordinator.mergeHudNews(
                        authoritative = state.teamNewsUnread,
                        prevDisplayed = prevHud.teamNewsUnread,
                        useAuthoritative = refreshTeamInboxBadges,
                    ),
                )
                val nowMs = System.currentTimeMillis()
                val refreshPresenceCounts = force ||
                    nowMs - lastHudPresenceCountRefreshAtMs >= HUD_PRESENCE_COUNT_REFRESH_MS
                val onlineIngameCount = if (refreshPresenceCounts) {
                    runCatching {
                        OverlayGameStatusHudRefresh.countTeamIngameOverlayMembers(
                            container.usersRepository,
                            container.teamsRepository,
                        )
                    }.getOrDefault(0).also {
                        lastHudPresenceCountRefreshAtMs = nowMs
                    }
                } else {
                    overlayTopRightHudFlow.value.onlineIngameCount
                }
                val joinRequestCount = if (force || refreshNewsForum) {
                    runCatching {
                        OverlayGameStatusHudRefresh.loadTeamJoinRequestCount(this@CombatOverlayService)
                    }.getOrDefault(0)
                } else {
                    overlayTopRightHudFlow.value.teamJoinRequestCount
                }
                mainHandler.post {
                    if (!isInGameOverlayUiActive()) return@post
                    if (mergedState != overlayStatusHudFlow.value) {
                        overlayStatusHudFlow.value = mergedState
                    }
                    val durationMs = android.os.SystemClock.elapsedRealtime() - startedAt
                    OverlayPerfDiag.logHudRefreshDone(
                        durationMs = durationMs,
                        allianceUnread = mergedState.allianceChatUnread,
                        forumUnread = mergedState.forumUnread,
                        newsUnread = mergedState.teamNewsUnread,
                    )
                    val prevTopRight = overlayTopRightHudFlow.value
                    val nextTopRight = prevTopRight.copy(
                        onlineIngameCount = onlineIngameCount,
                        teamJoinRequestCount = joinRequestCount,
                    )
                    if (nextTopRight != prevTopRight) {
                        overlayTopRightHudFlow.value = nextTopRight
                    }
                    attachOverlayHudWindowsIfNeeded()
                    logOverlayRuntimeSnapshot()
                }
            } finally {
                hudRefreshInFlight = false
                lastHudRefreshCompletedAtMs = System.currentTimeMillis()
                if (hudRefreshPending && isInGameOverlayUiActive()) {
                    hudRefreshPending = false
                    refreshOverlayStatusHudData()
                } else {
                    hudRefreshPending = false
                }
            }
        }
    }

    private fun cancelOverlayHudRefreshWork() {
        hudRefreshJob?.cancel()
        hudRefreshJob = null
        hudRefreshInFlight = false
        hudRefreshPending = false
        mainHandler.removeCallbacks(statusHudRefreshRunnable)
        statusHudRefreshPosted = false
        mainHandler.removeCallbacks(hudBadgeRefreshRunnable)
        hudBadgeRefreshPosted = false
        pendingHubHudRefresh = false
        pendingForumHudRefresh = false
        mainHandler.removeCallbacks(overlayCloseHudRefreshRunnable)
    }

    private fun shouldDeferHubUnreadReconcile(): Boolean {
        if (hubUnreadOptimisticFloor <= 0) return false
        return System.currentTimeMillis() - hubUnreadLastBumpAtMs < HUB_UNREAD_RECONCILE_GRACE_MS
    }

    /** Hub «Альянс» badge on mail chip — cache-first, API reconcile. */
    private fun refreshOverlayHubUnreadOnly() {
        if (!isInGameOverlayUiActive()) return
        if (shouldDeferHubUnreadReconcile()) return
        serviceScope.launch(Dispatchers.IO) {
            val counts = fetchAllianceHubUnreadCounts() ?: return@launch
            mainHandler.post { applyAllianceHubUnreadReconciled(counts.first, counts.second) }
        }
    }

    private fun refreshOverlayHubUnreadFromCache() {
        if (!isInGameOverlayUiActive()) return
        if (shouldDeferHubUnreadReconcile()) return
        serviceScope.launch(Dispatchers.IO) {
            val counts = computeAllianceHubUnreadFromCache() ?: return@launch
            mainHandler.post { applyAllianceHubUnreadReconciled(counts.first, counts.second) }
        }
    }

    private fun refreshOverlayNewsBadgeOnly() {
        if (!isInGameOverlayUiActive()) return
        inboxBadgeCoordinator.clearNewsOptimistic()
        if (inboxBadgeCoordinator.shouldDeferNewsReconcile()) return
        serviceScope.launch(Dispatchers.IO) {
            val container = AppContainer.from(this@CombatOverlayService)
            val profile = container.usersRepository.resolveMyProfilePreferCache() ?: return@launch
            val teamId = profile.playerTeamId?.trim().orEmpty()
            if (teamId.isEmpty()) return@launch
            val userId = profile.id.trim()
            val count = inboxBadgeCoordinator.fetchNewsUnread(this@CombatOverlayService, teamId, userId)
            mainHandler.post {
                if (!isInGameOverlayUiActive()) return@post
                val prev = overlayStatusHudFlow.value
                val merged = inboxBadgeCoordinator.mergeNewsDisplayed(count, prev.teamNewsUnread)
                overlayStatusHudFlow.value = prev.copy(teamNewsUnread = merged)
                inboxBadgeCoordinator.cacheNewsForum(teamId, merged, prev.forumUnread)
            }
        }
    }

    /**
     * Keep HUD chips visible after re-entering the game until the next network refresh completes.
     * Uses in-memory coordinator cache and local read cursors (no zero flash).
     */
    private fun seedOverlayInboxBadgesBeforeRefresh() {
        bindOverlayReadCursorsIfPossible()
        serviceScope.launch(Dispatchers.IO) {
            val container = AppContainer.from(this@CombatOverlayService)
            val profile = container.usersRepository.resolveMyProfilePreferCache() ?: return@launch
            val teamId = profile.playerTeamId?.trim().orEmpty()
            if (teamId.isEmpty()) return@launch
            val userId = profile.id.trim()
            ReadCursorSession.bind(
                container.chatRoomPreferences,
                container.teamForumPreferences,
                container.userSettingsPreferences,
                userId,
            )
            val prev = overlayStatusHudFlow.value
            val cachedNews = inboxBadgeCoordinator.readCachedNews(teamId)
            val cachedForum = inboxBadgeCoordinator.readCachedForum(teamId)
            val localForumRead = container.teamForumPreferences.loadAllLastReadMessageIds(teamId)
            val forumFromTopics = container.teamsRepository.listForumTopics(teamId)
                .getOrNull()
                ?.let { TeamInboxUnread.sumForumUnread(it, localForumRead) }
            val hubPair = computeAllianceHubUnreadFromCache()
            mainHandler.post {
                if (!isInGameOverlayUiActive()) return@post
                val forumSeed = maxOf(
                    prev.forumUnread,
                    cachedForum ?: 0,
                    forumFromTopics ?: 0,
                    inboxBadgeCoordinator.forumOptimisticFloor,
                )
                val newsSeed = maxOf(prev.teamNewsUnread, cachedNews ?: 0)
                val hubSeed = maxOf(
                    prev.allianceChatUnread,
                    hubPair?.first ?: 0,
                    hubUnreadOptimisticFloor,
                )
                overlayStatusHudFlow.value = prev.copy(
                    allianceChatUnread = hubSeed,
                    teamNewsUnread = newsSeed,
                    forumUnread = forumSeed,
                )
            }
        }
    }

    private fun refreshOverlayForumBadgeOnly() {
        if (!isInGameOverlayUiActive()) return
        inboxBadgeCoordinator.clearForumOptimistic()
        if (inboxBadgeCoordinator.shouldDeferForumReconcile()) return
        serviceScope.launch(Dispatchers.IO) {
            val container = AppContainer.from(this@CombatOverlayService)
            val teamId = container.usersRepository.resolveMyProfilePreferCache()?.playerTeamId?.trim().orEmpty()
            if (teamId.isEmpty()) return@launch
            val count = inboxBadgeCoordinator.fetchForumUnread(this@CombatOverlayService, teamId)
            mainHandler.post {
                if (!isInGameOverlayUiActive()) return@post
                val prev = overlayStatusHudFlow.value
                val merged = inboxBadgeCoordinator.mergeForumDisplayed(count, prev.forumUnread)
                overlayStatusHudFlow.value = prev.copy(forumUnread = merged)
                inboxBadgeCoordinator.cacheNewsForum(teamId, prev.teamNewsUnread, merged)
            }
        }
    }

    /** App/VM hub chip sync — does not set [hubUnreadOptimisticFloor] (realtime bumps only). */
    private fun pushAllianceHubUnreadFromApp(displayed: Int) {
        val clamped = displayed.coerceIn(0, 999)
        if (clamped <= 0) {
            clearHubUnreadOptimisticState()
            applyAllianceHubUnreadCount(0)
            patchHubUnreadInSessionCacheAfterLocalRead()
            return
        }
        applyAllianceHubUnreadCount(clamped)
    }

    private fun clearHubUnreadOptimisticState() {
        hubUnreadOptimisticFloor = 0
        hubUnreadBumpedMessageIds.clear()
        mainHandler.removeCallbacks(hudBadgeRefreshRunnable)
        hudBadgeRefreshPosted = false
        pendingHubHudRefresh = false
    }

    /** Pair(effective unread, raw server unread) for hub badge merge. */
    private fun computeAllianceHubUnreadFromCache(): Pair<Int, Int>? {
        val rooms = ChatSessionCache.getFreshRooms() ?: return null
        val hub = ChatRoomKindResolver.allianceHubRoom(rooms) ?: return 0 to 0
        cachedAllianceHubRoomId = hub.id
        val localRead = AppContainer.from(this).chatRoomPreferences.loadAllLastReadMessageIds()
        val effective = OverlayGameStatusHudRefresh.allianceHubUnread(rooms, localRead)
        val raw = OverlayGameStatusHudRefresh.allianceHubRawUnread(rooms)
        return effective to raw
    }

    /** Authoritative hub unread for overlay mail chip (cache, then listRooms). */
    private suspend fun fetchAllianceHubUnreadCounts(): Pair<Int, Int>? {
        val container = AppContainer.from(this@CombatOverlayService)
        val localRead = container.chatRoomPreferences.loadAllLastReadMessageIds()
        val cachedRooms = ChatSessionCache.getFreshRooms()
        val cachedCounts = cachedRooms?.let { rooms ->
            val hub = ChatRoomKindResolver.allianceHubRoom(rooms) ?: return@let null
            cachedAllianceHubRoomId = hub.id
            val effective = OverlayGameStatusHudRefresh.allianceHubUnread(rooms, localRead)
            val raw = OverlayGameStatusHudRefresh.allianceHubRawUnread(rooms)
            effective to raw
        }
        val rooms = cachedRooms
            ?: runCatching { container.chatRepository.listRooms().getOrNull() }.getOrNull()
            ?: return cachedCounts
        val hub = ChatRoomKindResolver.allianceHubRoom(rooms) ?: return 0 to 0
        cachedAllianceHubRoomId = hub.id
        ChatSessionCache.update(rooms)
        container.chatRepository.applyOverlayRoomsFromRooms(rooms)
        val effective = OverlayGameStatusHudRefresh.allianceHubUnread(rooms, localRead)
        val raw = OverlayGameStatusHudRefresh.allianceHubRawUnread(rooms)
        return effective to raw
    }

    private fun applyAllianceHubUnreadCount(count: Int) {
        if (!isInGameOverlayUiActive()) return
        val clamped = count.coerceIn(0, 999)
        if (overlayStatusHudFlow.value.allianceChatUnread == clamped) return
        overlayStatusHudFlow.value = overlayStatusHudFlow.value.copy(
            allianceChatUnread = clamped,
        )
    }

    private fun applyAllianceHubUnreadReconciled(
        serverEffectiveCount: Int,
        rawServerUnread: Int = serverEffectiveCount,
    ) {
        val effective = serverEffectiveCount.coerceAtLeast(0)
        if (effective <= 0) {
            val rooms = ChatSessionCache.getFreshRooms()
            if (rooms != null) {
                val localRead = AppContainer.from(this).chatRoomPreferences.loadAllLastReadMessageIds()
                if (ChatUnreadCounts.isAllianceHubLocallyReadSuppressed(rooms, localRead)) {
                    hubUnreadOptimisticFloor = 0
                    applyAllianceHubUnreadCount(0)
                    patchHubUnreadInSessionCacheAfterLocalRead()
                    return
                }
            }
            if (hubUnreadOptimisticFloor > 0) {
                val displayed = maxOf(
                    hubUnreadOptimisticFloor,
                    overlayStatusHudFlow.value.allianceChatUnread,
                ).coerceAtMost(999)
                applyAllianceHubUnreadCount(displayed)
                return
            }
            hubUnreadOptimisticFloor = 0
            applyAllianceHubUnreadCount(0)
            patchHubUnreadInSessionCacheAfterLocalRead()
            return
        }
        val rooms = ChatSessionCache.getFreshRooms()
        val merged = if (rooms != null) {
            val localRead = AppContainer.from(this).chatRoomPreferences.loadAllLastReadMessageIds()
            ChatUnreadCounts.overlayAllianceHubBadge(
                rooms = rooms,
                localReadByRoom = localRead,
                optimisticFloor = hubUnreadOptimisticFloor,
                previouslyDisplayed = overlayStatusHudFlow.value.allianceChatUnread,
            )
        } else {
            displayedUnreadCount(
                effectiveUnread = effective,
                previouslyDisplayed = maxOf(
                    overlayStatusHudFlow.value.allianceChatUnread,
                    hubUnreadOptimisticFloor,
                ),
                rawServerUnread = rawServerUnread.coerceAtLeast(0),
                optimisticFloor = hubUnreadOptimisticFloor,
            )
        }
        if (effective > 0 && merged >= effective) {
            hubUnreadOptimisticFloor = 0
        } else if (merged <= 0) {
            hubUnreadOptimisticFloor = 0
        }
        applyAllianceHubUnreadCount(merged)
    }

    private fun patchHubUnreadInSessionCacheAfterLocalRead() {
        val hubId = runCatching { resolveOverlayHubRoomId() }.getOrNull()?.trim().orEmpty()
        if (hubId.isEmpty()) return
        val lastRead = AppContainer.from(this).chatRoomPreferences.getLastReadMessageId(hubId)
        patchHubUnreadInSessionCache(0, lastRead)
    }

    /** Reconcile hub mail chip with app SharedPreferences read cursors (e.g. before entering the game). */
    private fun syncOverlayHubBadgeFromAppReadState() {
        if (!shouldRetainOverlayRaidRealtime()) return
        bindOverlayReadCursorsIfPossible()
        serviceScope.launch(Dispatchers.IO) {
            val counts = fetchAllianceHubUnreadCounts() ?: return@launch
            mainHandler.post {
                if (!isInGameOverlayUiActive() && counts.first > 0) {
                    hubUnreadOptimisticFloor = maxOf(hubUnreadOptimisticFloor, counts.first)
                    return@post
                }
                applyAllianceHubUnreadReconciled(counts.first, counts.second)
            }
        }
    }

    private fun patchHubUnreadInSessionCache(unread: Int, lastReadMessageId: String? = null) {
        val rooms = ChatSessionCache.getFreshRooms() ?: return
        val hub = ChatRoomKindResolver.allianceHubRoom(rooms) ?: return
        val lastRead = lastReadMessageId?.trim().orEmpty()
        if (unread <= 0 && lastRead.isNotBlank()) {
            ChatSessionCache.patchRoomRead(hub.id, lastRead)
            return
        }
        val clamped = unread.coerceIn(0, 999)
        val updated = rooms.map { room ->
            if (room.id == hub.id) room.copy(unreadCount = clamped) else room
        }
        ChatSessionCache.update(updated)
    }

    private val hubUnreadBumpedMessageIds = LinkedHashSet<String>()

    /** Realtime [rooms:unread] for any overlay chat room (hub HUD + overlay panel tabs). */
    private fun applyOverlayRoomUnreadFromSocket(event: com.lastasylum.alliance.data.chat.ChatRoomUnreadEvent) {
        if (activityChatViewModelHandlesUnread()) return
        val roomId = event.roomId.trim()
        if (roomId.isEmpty()) return
        ChatSessionCache.patchRoomUnread(
            roomId,
            event.unreadCount.coerceAtLeast(0),
            event.lastReadMessageId,
        )
        resolveChatViewModel()?.applyRoomUnreadFromServer(event)
        val hubId = resolveOverlayHubRoomId().trim()
        if (hubId.isEmpty() || roomId != hubId) return
        applyOverlayHubUnreadHudFromSocket(event)
    }

    /** Alliance hub badge on status HUD (independent of [ChatViewModel]). */
    private fun applyOverlayHubUnreadHudFromSocket(event: com.lastasylum.alliance.data.chat.ChatRoomUnreadEvent) {
        val hubId = resolveOverlayHubRoomId().trim()
        if (hubId.isEmpty() || event.roomId.trim() != hubId) return
        val container = AppContainer.from(this)
        val localRead = container.chatRoomPreferences.getLastReadMessageId(hubId)
        val serverLast = event.lastReadMessageId?.trim().orEmpty()
        val serverUnread = event.unreadCount.coerceAtLeast(0)

        if (serverUnread > 0 && !localRead.isNullOrBlank()) {
            val suppressed = effectiveUnreadCount(
                serverUnread = serverUnread,
                lastReadMessageId = serverLast.takeIf { it.isNotEmpty() },
                localLastReadMessageId = localRead,
            ) == 0
            if (suppressed) {
                hubUnreadOptimisticFloor = 0
                applyAllianceHubUnreadCount(0)
                patchHubUnreadInSessionCache(0, localRead)
                return
            }
        }

        if (serverUnread <= 0) {
            hubUnreadOptimisticFloor = 0
            applyAllianceHubUnreadCount(0)
            if (serverLast.isNotBlank()) {
                container.chatRoomPreferences.setLastReadMessageId(hubId, serverLast)
                patchHubUnreadInSessionCache(0, serverLast)
            }
            return
        }

        if (serverLast.isNotBlank() && !localRead.isNullOrBlank() &&
            !isObjectIdNewer(serverLast, localRead)
        ) {
            container.chatRoomPreferences.setLastReadMessageId(hubId, serverLast)
        }

        val rooms = ChatSessionCache.getFreshRooms()
        val displayed = if (rooms != null) {
            ChatUnreadCounts.overlayAllianceHubBadge(
                rooms = rooms,
                localReadByRoom = container.chatRoomPreferences.loadAllLastReadMessageIds(),
                optimisticFloor = hubUnreadOptimisticFloor,
                previouslyDisplayed = overlayStatusHudFlow.value.allianceChatUnread,
            )
        } else {
            displayedUnreadCount(
                effectiveUnread = serverUnread,
                previouslyDisplayed = maxOf(
                    overlayStatusHudFlow.value.allianceChatUnread,
                    hubUnreadOptimisticFloor,
                ),
                rawServerUnread = serverUnread,
                optimisticFloor = hubUnreadOptimisticFloor,
            )
        }
        if (displayed >= serverUnread) {
            hubUnreadOptimisticFloor = 0
        } else if (displayed <= 0) {
            hubUnreadOptimisticFloor = 0
        }
        applyAllianceHubUnreadCount(displayed)
        patchHubUnreadInSessionCache(
            displayed,
            serverLast.takeIf { it.isNotBlank() } ?: localRead,
        )
    }

    private fun bumpAllianceHubUnreadLocally(messageId: String? = null) {
        val mid = messageId?.trim().orEmpty()
        if (mid.isNotEmpty() && !hubUnreadBumpedMessageIds.add(mid)) return
        while (hubUnreadBumpedMessageIds.size > 512) {
            hubUnreadBumpedMessageIds.remove(hubUnreadBumpedMessageIds.first())
        }
        hubUnreadLastBumpAtMs = System.currentTimeMillis()
        val container = AppContainer.from(this)
        val rooms = ChatSessionCache.getFreshRooms()
        val localRead = container.chatRoomPreferences.loadAllLastReadMessageIds()
        val baseEffective = if (rooms != null) {
            ChatUnreadCounts.overlayAllianceHubBadge(
                rooms = rooms,
                localReadByRoom = localRead,
                optimisticFloor = 0,
                previouslyDisplayed = 0,
            )
        } else {
            overlayStatusHudFlow.value.allianceChatUnread
        }
        val next = (baseEffective + 1).coerceAtMost(999)
        hubUnreadOptimisticFloor = next
        applyAllianceHubUnreadCount(next)
        scheduleDebouncedHubHudRefresh()
    }

    private fun shouldBumpOverlayHubUnread(msg: ChatMessage, hubId: String): Boolean {
        val selfId = jwtSubFromAccessToken()?.trim().orEmpty()
        if (selfId.isNotBlank() && msg.senderId.trim() == selfId) return false
        val messageId = msg._id?.trim().orEmpty()
        if (messageId.isBlank()) return false
        val lastRead = AppContainer.from(this).chatRoomPreferences.getLastReadMessageId(hubId)
        if (!isObjectIdNewer(messageId, lastRead)) return false
        if (overlayChatTeamPanelVisible) {
            if (overlayChatTeamTabIndex != 0) return false
            val vm = activeOverlayChatSessionViewModel()
            if (vm != null && vm.state.value.selectedRoomId == hubId) {
                return false
            }
        }
        return true
    }

    /** Realtime hub traffic for alliance mail badge (not raid strip). */
    private fun handleOverlayHubMessage(msg: ChatMessage) {
        val prefs = AppContainer.from(this).chatRoomPreferences
        val hubId = resolveOverlayHubRoomId()
        if (!OverlayStripMessageRouter.shouldRouteHubUnread(msg, hubId, prefs.getRaidRoomId())) {
            return
        }
        if (shouldBumpOverlayHubUnread(msg, hubId)) {
            if (!activityChatViewModelHandlesUnread()) {
                bumpAllianceHubUnreadLocally(msg._id)
            }
            return
        }
        scheduleDebouncedHubHudRefresh()
    }

    /** Primary socket listener on activity VM — avoid double unread bump from overlay path. */
    private fun activityChatViewModelHandlesUnread(): Boolean {
        val vmBound = ChatViewModelRegistry.shared != null || activityScopedChatViewModel != null
        if (!vmBound) return false
        return AppContainer.from(this).chatRepository.hasPrimaryRealtimeSubscription()
    }

    private fun forwardOverlaySocketMessageToViewModel(msg: ChatMessage) {
        val vm = resolveChatViewModel() ?: return
        if (vm.shouldSuppressOwnOutgoingRealtimeEcho(msg)) return
        if (isOverlayRaidStripSocketMessage(msg)) {
            vm.stashOverlayRealtimeMessage(msg)
        } else if (!activityChatViewModelHandlesUnread()) {
            vm.stashOverlayRealtimeMessage(msg)
        }
        if (!overlayChatTeamPanelVisible) return
        if (activityChatViewModelHandlesUnread()) return
        vm.applyOverlayChatMessageFromSocket(msg)
    }

    private fun forwardOverlayRaidMessageToViewModel(msg: ChatMessage) {
        val vm = resolveChatViewModel() ?: return
        if (vm.shouldSuppressOwnOutgoingRealtimeEcho(msg)) return
        vm.stashOverlayRealtimeMessage(msg)
    }

    private fun isOverlayRaidStripSocketMessage(msg: ChatMessage): Boolean {
        if (msg.isCompactReactionSocketUpdate()) {
            return msg.roomId.trim().isNotEmpty()
        }
        val raidId = resolveOverlayRaidRoomId() ?: trustedOverlayRaidRoomId
        return shouldIngestForRaidStrip(normalizeStripRaidMessage(msg, raidId))
    }

    private fun forwardOverlayTypingToViewModel(event: com.lastasylum.alliance.data.chat.ChatTypingEvent) {
        if (!overlayChatTeamPanelVisible) return
        resolveChatViewModel()?.applyOverlayChatTypingFromSocket(event)
    }

    private fun forwardOverlayReadToViewModel(event: com.lastasylum.alliance.data.chat.ChatRoomReadEvent) {
        resolveChatViewModel()?.applyOverlayChatReadFromSocket(event)
    }

    private fun forwardOverlayDeleteToViewModel(event: com.lastasylum.alliance.data.chat.ChatMessageDeletedEvent) {
        resolveChatViewModel()?.applyOverlayChatDeletedFromSocket(event)
    }

    private fun resolveOverlayHubRoomId(): String {
        cachedAllianceHubRoomId?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
        AppContainer.from(this).chatRoomPreferences.getHubRoomId()?.trim()?.takeIf { it.isNotEmpty() }
            ?.let { id ->
                cachedAllianceHubRoomId = id
                return id
            }
        AppContainer.from(this).chatRepository.hubRoomIdFromPrefs()?.trim()?.takeIf { it.isNotEmpty() }
            ?.let { id ->
                cachedAllianceHubRoomId = id
                return id
            }
        return ""
    }

    private fun resolveCachedAllianceHubRoomId() {
        if (resolveOverlayHubRoomId().isNotBlank()) return
        serviceScope.launch(Dispatchers.IO) {
            val container = AppContainer.from(this@CombatOverlayService)
            val rooms = com.lastasylum.alliance.data.chat.ChatSessionCache.getFreshRooms()
                ?: container.chatRepository.listRooms().getOrNull()
                ?: return@launch
            container.chatRepository.applyOverlayRoomsFromRooms(rooms)
            cachedAllianceHubRoomId = container.chatRepository.hubRoomIdFromPrefs()
                ?: OverlayGameStatusHudRefresh.allianceHubRoom(rooms)?.id
            mainHandler.post { syncOverlayRaidRoomSubscription() }
        }
    }

    private fun scheduleDebouncedHubHudRefresh() {
        pendingHubHudRefresh = true
        val delayMs = if (hubUnreadOptimisticFloor > 0) {
            maxOf(
                HUB_UNREAD_RECONCILE_GRACE_MS,
                HUB_HUD_REFRESH_DEBOUNCE_MS + 1_500L,
            )
        } else {
            HUB_HUD_REFRESH_DEBOUNCE_MS
        }
        scheduleDebouncedHudBadgeRefresh(delayMs)
    }

    private fun scheduleDebouncedForumHudRefresh() {
        pendingForumHudRefresh = true
        val delayMs = if (inboxBadgeCoordinator.forumOptimisticFloor > 0) {
            maxOf(
                OverlayInboxBadgeCoordinator.RECONCILE_GRACE_MS,
                HUB_HUD_REFRESH_DEBOUNCE_MS + 1_500L,
            )
        } else {
            HUB_HUD_REFRESH_DEBOUNCE_MS
        }
        scheduleDebouncedHudBadgeRefresh(delayMs)
    }

    private fun scheduleDebouncedHudBadgeRefresh(delayMs: Long) {
        mainHandler.removeCallbacks(hudBadgeRefreshRunnable)
        hudBadgeRefreshPosted = true
        mainHandler.postDelayed(hudBadgeRefreshRunnable, delayMs)
    }

    /** Only this user's read cursor clears the hub badge — not other members' room:read events. */
    private fun applyOverlayHubReadFromSelf(event: com.lastasylum.alliance.data.chat.ChatRoomReadEvent) {
        val selfId = jwtSubFromAccessToken()?.trim().orEmpty()
        if (selfId.isBlank() || event.userId.trim() != selfId) return
        val hubId = resolveOverlayHubRoomId()
        if (hubId.isBlank() || event.roomId.trim() != hubId) return
        val messageId = event.messageId.trim()
        if (messageId.isBlank()) return
        AppContainer.from(this).chatRoomPreferences.setLastReadMessageId(hubId, messageId)
        clearHubUnreadOptimisticState()
        applyAllianceHubUnreadCount(0)
        patchHubUnreadInSessionCache(0, messageId)
        activityScopedChatViewModel?.let { vm ->
            mainHandler.post { vm.syncRoomsFromServer() }
        }
    }

    private fun logOverlayRuntimeSnapshot() {
        val session = voiceSession
        val prefs = AppContainer.from(this).userSettingsPreferences
        var windows = 0
        if (chatStripHost?.isAttachedToWindow == true) windows++
        if (overlayStatusHudHost?.isAttachedToWindow == true) windows++
        if (overlayTopRightHudHost?.isAttachedToWindow == true) windows++
        if (overlayChatTeamPanelVisible) windows++
        OverlayPerfDiag.logRuntimeSnapshot(
            context = this,
            micOn = session?.micOn ?: prefs.isOverlayVoiceMicEnabled(),
            soundOn = session?.soundOn ?: prefs.isOverlayVoiceSoundEnabled(),
            overlayWindows = windows,
            hudOnly = isOverlayHudOnlyMode(),
            lightStrip = isOverlayLightStripMode(),
        )
    }

    /**
     * Пользователь открыл приложение SquadRelay поверх игры — скрываем окна, подписки и FGS сохраняем.
     */
    private fun pauseOverlayUiForAllianceAppForeground() {
        overlayCommandsPopover.hide()
        overlayStatusHudHost?.visibility = View.GONE
        overlayTopRightHudHost?.visibility = View.GONE
        chatStripHost?.visibility = View.GONE
        overlayTicker.hideTicker()
    }

    private fun clearOverlayStripForOffline() {
        stripSessionNeedsRestart = true
        stripBuffer.clear()
        lastStripRenderSignature = 0
        chatStripPreviewFlow.value = emptyList()
    }

    private fun ensureOverlayStripVisibleSession() {
        if (!stripBuffer.hasVisibleSession()) {
            stripBuffer.resetVisibleSession()
        }
        stripSessionNeedsRestart = false
    }

    /** Пустой буфер + новая видимая сессия: только трафик после входа в игру. */
    private fun beginOverlayStripGameSession() {
        stripBuffer.clear()
        stripBuffer.resetVisibleSession()
        lastStripRenderSignature = 0
        chatStripPreviewFlow.value = emptyList()
        stripLiveRevision++
    }

    /** Краткий «не в игре» — GONE, окна остаются attached (без removeOverlayControl). */
    private fun softHideOverlayUiBecauseNotInGame() {
        overlayCommandsPopover.hide()
        overlayStatusHudHost?.visibility = View.GONE
        overlayTopRightHudHost?.visibility = View.GONE
        chatStripHost?.visibility = View.GONE
        overlayTicker.hideTicker()
    }

    /** После паузы или ingest ленты — снова показать HUD/ленту, если гейт «в игре». */
    private fun restoreOverlayInGameWindowVisibility() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { restoreOverlayInGameWindowVisibility() }
            return
        }
        if (!isInGameOverlayUiActive()) return
        val prefs = AppContainer.from(this).userSettingsPreferences
        if (!prefs.isOverlayPanelEnabled() || !canDrawOverlaysNow()) return
        repairDetachedOverlayShellIfNeeded()
        if (!overlayChatTeamPanelVisible) {
            overlayStatusHudHost?.visibility = View.VISIBLE
            overlayTopRightHudHost?.visibility = View.VISIBLE
        }
        applyOverlayStripVisibility()
    }

    private fun syncOverlayHudsIfReady() {
        if (stableGatePollTicks < HUD_STABLE_TICKS_BEFORE_ATTACH) return
        attachOverlayHudWindowsIfNeeded()
    }

    /** Левый и правый HUD в одном кадре — иначе правый ждёт IO/гейт и появляется позже. */
    private fun attachOverlayHudWindowsIfNeeded() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { attachOverlayHudWindowsIfNeeded() }
            return
        }
        if (!isInGameOverlayUiActive()) return
        val prefs = AppContainer.from(this).userSettingsPreferences
        if (!prefs.isOverlayPanelEnabled() || !canDrawOverlaysNow()) return
        ensureOverlayIfPermitted()
        ensureOverlayMessageStripIfNeeded()
        if (overlayChatTeamPanelVisible) {
            applyOverlayStripVisibility()
            return
        }
        ensureOverlayStatusHudWindow()
        ensureOverlayTopRightHudWindow()
        if (overlayStatusHudHost?.visibility != View.VISIBLE) {
            overlayStatusHudHost?.visibility = View.VISIBLE
        }
        if (overlayTopRightHudHost?.visibility != View.VISIBLE) {
            overlayTopRightHudHost?.visibility = View.VISIBLE
        }
        syncOverlayHudWindowLayout()
        refreshOverlayTopRightHudState()
        applyOverlayStripVisibility()
        ensureOverlayRaidRealtimeIfNeeded()
        seedOverlayInboxBadgesBeforeRefresh()
        refreshOverlayStatusHudData(force = true)
    }

    private fun syncOverlayStatusHudVisibility() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { syncOverlayStatusHudVisibility() }
            return
        }
        val inGame = isInGameOverlayUiActive()
        val prefs = AppContainer.from(this).userSettingsPreferences
        val allowed = prefs.isOverlayPanelEnabled() && canDrawOverlaysNow()
        val show = inGame && allowed && !overlayChatTeamPanelVisible
        if (!show) {
            overlayStatusHudHost?.visibility = View.GONE
            return
        }
        attachOverlayHudWindowsIfNeeded()
    }

    private fun syncOverlayTopRightHudVisibility() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { syncOverlayTopRightHudVisibility() }
            return
        }
        val inGame = isInGameOverlayUiActive()
        val prefs = AppContainer.from(this).userSettingsPreferences
        val allowed = prefs.isOverlayPanelEnabled() && canDrawOverlaysNow()
        val show = inGame && allowed && !overlayChatTeamPanelVisible
        if (!show) {
            overlayTopRightHudHost?.visibility = View.GONE
            return
        }
        attachOverlayHudWindowsIfNeeded()
    }

    private fun refreshOverlayTopRightHudState() {
        val session = voiceSession
        val prefs = AppContainer.from(this).userSettingsPreferences
        overlayTopRightHudFlow.value = overlayTopRightHudFlow.value.copy(
            micOn = session?.micOn == true,
            soundOn = session?.soundOn == true,
            soundVolume = prefs.getOverlayVoiceSoundVolume(),
            micVolume = prefs.getOverlayVoiceMicVolume(),
        )
    }

    private fun toggleOverlayTopRightVoiceExpanded() {
        val cur = overlayTopRightHudFlow.value
        val expanding = !cur.voiceExpanded
        val session = voiceSession
        val prefs = AppContainer.from(this).userSettingsPreferences
        overlayTopRightHudFlow.value = cur.copy(
            voiceExpanded = expanding,
            voiceSettingsVisible = if (expanding) cur.voiceSettingsVisible else false,
            micOn = session?.micOn == true,
            soundOn = session?.soundOn == true,
            soundVolume = prefs.getOverlayVoiceSoundVolume(),
            micVolume = prefs.getOverlayVoiceMicVolume(),
        )
    }

    private fun toggleOverlayVoiceSettingsFromHud() {
        overlayTopRightHudFlow.value = overlayTopRightHudFlow.value.copy(
            voiceSettingsVisible = !overlayTopRightHudFlow.value.voiceSettingsVisible,
        )
    }

    private fun dismissOverlayVoiceSettingsFromHud() {
        if (!overlayTopRightHudFlow.value.voiceSettingsVisible) return
        overlayTopRightHudFlow.value = overlayTopRightHudFlow.value.copy(
            voiceSettingsVisible = false,
        )
    }

    private fun setOverlayVoiceSoundVolumeFromHud(level: Float) {
        val prefs = AppContainer.from(this).userSettingsPreferences
        prefs.setOverlayVoiceSoundVolume(level)
        voiceSession?.setPlaybackVolume(level)
        overlayTopRightHudFlow.value = overlayTopRightHudFlow.value.copy(soundVolume = level)
    }

    private fun setOverlayVoiceMicVolumeFromHud(level: Float) {
        val prefs = AppContainer.from(this).userSettingsPreferences
        prefs.setOverlayVoiceMicVolume(level)
        voiceSession?.setCaptureVolume(level)
        overlayTopRightHudFlow.value = overlayTopRightHudFlow.value.copy(micVolume = level)
    }

    private fun openOverlayQuickCommandsFromHud() {
        OverlayChatInteractionHold.prepareOverlayModalInteraction(isOverlayUi = true)
        extendOverlayUiHold(OVERLAY_UI_HOLD_PANEL_TRANSITION_MS)
        ensureOverlayIfPermitted()
        ensureOverlayRaidRealtimeIfNeeded()
        prefetchOverlayRaidRoomForStrip()
        val mgr = windowManager ?: systemWindowManager()
        if (mgr == null) {
            OverlayChatInteractionHold.cancelPreparedOverlayModalInteraction(isOverlayUi = true)
            return
        }
        val wasShowing = overlayCommandsPopover.isShowing()
        overlayCommandsPopover.toggle(mgr)
        if (!wasShowing && !overlayCommandsPopover.isShowing()) {
            OverlayChatInteractionHold.cancelPreparedOverlayModalInteraction(isOverlayUi = true)
        }
        if (isOverlayChatStripEnabled() && chatStripHost?.isAttachedToWindow != true) {
            repairDetachedOverlayShellIfNeeded()
        }
    }

    private fun overlayVoiceSoundDisplayedOn(): Boolean {
        voiceSession?.let { return it.soundOn }
        return AppContainer.from(this).userSettingsPreferences.isOverlayVoiceSoundEnabled()
    }

    private fun overlayVoiceMicDisplayedOn(): Boolean {
        voiceSession?.let { return it.micOn }
        return AppContainer.from(this).userSettingsPreferences.isOverlayVoiceMicEnabled()
    }

    private fun toggleOverlayVoiceMicFromHud() {
        if (!overlayVoiceController.isMicSupportedOnDevice()) {
            Toast.makeText(this, R.string.overlay_voice_mic_unsupported, Toast.LENGTH_SHORT).show()
            return
        }
        val targetOn = !overlayVoiceMicDisplayedOn()
        val prefs = AppContainer.from(this).userSettingsPreferences
        prefs.setOverlayVoiceMicEnabled(targetOn)
        if (targetOn) {
            prefs.setOverlayVoiceSoundEnabled(true)
        }
        runOverlayVoiceUserAction { session ->
            session.whenVoiceReady {
                if (targetOn && !session.hasRecordAudioPermission()) {
                    pendingVoiceMicEnable = true
                    requestOverlayVoiceMicPermission()
                } else {
                    val ok = session.setMicEnabled(targetOn)
                    if (!ok && targetOn) {
                        Toast.makeText(
                            this@CombatOverlayService,
                            R.string.overlay_voice_mic_unsupported,
                            Toast.LENGTH_SHORT,
                        ).show()
                    }
                }
                refreshOverlayTopRightHudState()
            }
        }
    }

    private fun toggleOverlayVoiceSoundFromHud() {
        val prefs = AppContainer.from(this).userSettingsPreferences
        val targetOn = !overlayVoiceSoundDisplayedOn()
        prefs.setOverlayVoiceSoundEnabled(targetOn)
        runOverlayVoiceUserAction { session ->
            session.whenVoiceReady {
                if (session.soundOn != targetOn) {
                    session.setSoundEnabled(targetOn)
                }
                refreshOverlayTopRightHudState()
            }
        }
    }

    private fun showOverlayHudPane(pane: OverlayHudPane) {
        extendOverlayUiHold(OVERLAY_UI_HOLD_PANEL_TRANSITION_MS)
        if (overlayChatTeamPanelVisible && currentOverlayHudPane == pane) return
        if (overlayChatTeamPanelVisible) {
            hideOverlayChatTeamPanelNow(clearStrip = false)
        }
        showOverlayChatTeamPanel(hudPane = pane)
    }

    /** Показать HUD/ленту после закрытия полноэкранной панели без remove/add окон. */
    private fun restoreOverlayHudChromeAfterPanel() {
        if (!isInGameOverlayUiActive()) return
        if (overlayStatusHudHost?.visibility != View.VISIBLE) {
            overlayStatusHudHost?.visibility = View.VISIBLE
        }
        if (overlayTopRightHudHost?.visibility != View.VISIBLE) {
            overlayTopRightHudHost?.visibility = View.VISIBLE
        }
        applyOverlayStripVisibility(rebalanceZOrder = false)
    }

    private fun ensureOverlayStatusHudWindow() {
        if (!isInGameOverlayUiActive()) return
        if (!AppContainer.from(this).userSettingsPreferences.isOverlayPanelEnabled()) return
        if (overlayChatTeamPanelVisible) return
        val manager = windowManager ?: systemWindowManager() ?: return
        if (overlayStatusHudHost != null) return
        prefetchOverlayRaidRoomForStrip()

        val owner = overlayStatusHudComposeOwner
            ?: OverlayChatComposeOwner().also { overlayStatusHudComposeOwner = it }
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
            OverlayWindowLayout.popupWindowFlags(),
            android.graphics.PixelFormat.TRANSLUCENT,
        ).apply {
            OverlayWindowLayout.applyPopupLayoutCompat(this)
            OverlayHudLayout.applyStatusHudPosition(this) { dp(it) }
        }

        val compose = ComposeView(this).apply {
            setContent {
                val state by overlayStatusHudFlow.collectAsStateWithLifecycle(owner)
                SquadRelayTheme {
                    OverlayGameStatusHud(
                        state = state,
                        onForumClick = { showOverlayHudPane(OverlayHudPane.Forum) },
                        onMailClick = { showOverlayHudPane(OverlayHudPane.Chat) },
                        onNewsClick = { showOverlayHudPane(OverlayHudPane.News) },
                    )
                }
            }
        }
        overlayStatusHudCompose = compose

        val host = OverlayHudRootLayout(this).apply {
            setBackgroundColor(Color.TRANSPARENT)
            clipChildren = false
            clipToPadding = false
            setViewTreeLifecycleOwner(owner)
            setViewTreeViewModelStoreOwner(owner)
            setViewTreeSavedStateRegistryOwner(owner)
            setViewTreeOnBackPressedDispatcherOwner(owner)
            addView(
                compose,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                ),
            )
        }

        val attach = runCatching { manager.addView(host, params) }
        if (attach.isFailure) {
            Log.w(TAG, "ensureOverlayStatusHudWindow addView failed", attach.exceptionOrNull())
            return
        }
        compose.post { compose.requestLayout() }
        overlayStatusHudHost = host
        overlayStatusHudParams = params
        retainWindowManager(manager)
        _overlayVisible.value = true
    }

    private fun removeOverlayStatusHudWindow() {
        val host = overlayStatusHudHost
        val composeOwner = overlayStatusHudComposeOwner
        removeOverlayWindowTracked(host, "statusHud") {
            overlayStatusHudHost = null
            overlayStatusHudParams = null
            overlayStatusHudCompose = null
            composeOwner?.destroy()
            overlayStatusHudComposeOwner = null
        }
    }

    private fun ensureOverlayTopRightHudWindow() {
        if (!isInGameOverlayUiActive()) return
        if (!AppContainer.from(this).userSettingsPreferences.isOverlayPanelEnabled()) return
        if (overlayChatTeamPanelVisible) return
        val manager = windowManager ?: systemWindowManager() ?: return
        if (overlayTopRightHudHost != null) return

        val owner = overlayTopRightHudComposeOwner
            ?: OverlayChatComposeOwner().also { overlayTopRightHudComposeOwner = it }
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
            OverlayWindowLayout.popupWindowFlags(),
            android.graphics.PixelFormat.TRANSLUCENT,
        ).apply {
            OverlayWindowLayout.applyPopupLayoutCompat(this)
            OverlayHudLayout.applyTopRightHudPosition(this) { dp(it) }
        }

        val compose = ComposeView(this).apply {
            setContent {
                val state by overlayTopRightHudFlow.collectAsStateWithLifecycle(owner)
                SquadRelayTheme {
                    OverlayGameTopRightHud(
                        state = state,
                        onOnlineClick = {
                            overlayCommandsPopover.hide()
                            pendingOpenJoinInboxOnParticipants = false
                            showOverlayHudPane(OverlayHudPane.Participants)
                        },
                        onQuickCommandsClick = { openOverlayQuickCommandsFromHud() },
                        onVoiceHubClick = { toggleOverlayTopRightVoiceExpanded() },
                        onMicClick = { toggleOverlayVoiceMicFromHud() },
                        onSoundClick = { toggleOverlayVoiceSoundFromHud() },
                        onVoiceSettingsClick = { toggleOverlayVoiceSettingsFromHud() },
                        onSoundVolumeChange = { setOverlayVoiceSoundVolumeFromHud(it) },
                        onMicVolumeChange = { setOverlayVoiceMicVolumeFromHud(it) },
                        onVoiceSettingsDismiss = { dismissOverlayVoiceSettingsFromHud() },
                    )
                }
            }
        }
        overlayTopRightHudCompose = compose

        val host = OverlayHudRootLayout(this).apply {
            setBackgroundColor(Color.TRANSPARENT)
            clipChildren = false
            clipToPadding = false
            setViewTreeLifecycleOwner(owner)
            setViewTreeViewModelStoreOwner(owner)
            setViewTreeSavedStateRegistryOwner(owner)
            setViewTreeOnBackPressedDispatcherOwner(owner)
            addView(
                compose,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                ),
            )
        }

        val attach = runCatching { manager.addView(host, params) }
        if (attach.isFailure) {
            Log.w(TAG, "ensureOverlayTopRightHudWindow addView failed", attach.exceptionOrNull())
            return
        }
        compose.post { compose.requestLayout() }
        overlayTopRightHudHost = host
        overlayTopRightHudParams = params
        retainWindowManager(manager)
        _overlayVisible.value = true
    }

    private fun removeOverlayTopRightHudWindow() {
        val host = overlayTopRightHudHost
        val composeOwner = overlayTopRightHudComposeOwner
        removeOverlayWindowTracked(host, "topRightHud") {
            overlayTopRightHudHost = null
            overlayTopRightHudParams = null
            overlayTopRightHudCompose = null
            composeOwner?.destroy()
            overlayTopRightHudComposeOwner = null
            overlayTopRightHudFlow.value = OverlayGameTopRightHudState()
        }
    }

    /** Новый заход в игру: отключаем сокет, prefs пользователя сохраняем. */
    private fun resetOverlayVoiceForGameEntry() {
        overlayVoiceController.resetSession()
        stopOverlayVoice()
        overlayTopRightHudFlow.value = overlayTopRightHudFlow.value.copy(
            voiceExpanded = false,
            voiceSettingsVisible = false,
        )
        refreshOverlayTopRightHudState()
    }

    private fun applyGameGateState(
        hasUsageAccess: Boolean,
        shouldShow: Boolean,
    ) {
        val wasInGame = lastAppliedGateShouldShow == true
        stableGatePollTicks = if (shouldShow == lastAppliedGateShouldShow) {
            stableGatePollTicks + 1
        } else {
            0
        }
        lastAppliedGateShouldShow = shouldShow
        _inGameOverlayUiActive.value = shouldShow
        if (!shouldShow) {
            if (wasInGame) {
                clearOverlayStripForOffline()
            }
            if (overlayChatTeamPanelVisible ||
                OverlayChatInteractionHold.isFullscreenChatTeamPanelVisible
            ) {
                // Не снимать полноэкранный чат по краткому ложному «не в игре» (IME, usage-stats).
                gateSoftHideStartedAtMs = 0L
                return
            }
            softHideOverlayUiBecauseNotInGame()
            if (!overlayInGameProbeActive) {
                gateSoftHideStartedAtMs = 0L
                overlayUiHoldUntilMs = 0L
                dismissOverlayUiBecauseNotInGame(logWaitingForGame = true)
                return
            }
            if (overlayCommandsPopover.isBlockingGameGateDismiss()) {
                gateSoftHideStartedAtMs = 0L
                return
            }
            val nowMs = System.currentTimeMillis()
            if (gateSoftHideStartedAtMs <= 0L) {
                gateSoftHideStartedAtMs = nowMs
            }
            if (BuildConfig.DEBUG) {
                Log.d(
                    OVERLAY_DIAG_TAG,
                    "overlayGate softHide hideStreak=$gateUiHideStreak hold=${isOverlayUiHoldActive()} " +
                        "softMs=${nowMs - gateSoftHideStartedAtMs}",
                )
            }
            if (nowMs - gateSoftHideStartedAtMs >= GATE_DISMISS_AFTER_MS) {
                gateSoftHideStartedAtMs = 0L
                if (!overlayChatTeamPanelVisible &&
                    !OverlayChatInteractionHold.isFullscreenChatTeamPanelVisible
                ) {
                    dismissOverlayUiBecauseNotInGame(logWaitingForGame = true)
                }
            }
            return
        }
        gateSoftHideStartedAtMs = 0L
        // SquadRelay на переднем плане при игре в фоне — прячем HUD, но не рвём FGS/сокет/ленту.
        if (lastForegroundHintPkg == packageName &&
            !OverlayChatInteractionHold.isOverlaySystemPickerSessionActive() &&
            !overlayChatTeamPanelVisible &&
            !OverlayChatInteractionHold.isFullscreenChatTeamPanelVisible &&
            !shouldKeepOverlayWindows() &&
            overlayTouchPassthroughSnaps.isEmpty()
        ) {
            if (isOverlayUiHoldActive()) {
                restoreOverlayInGameWindowVisibility()
            } else {
                pauseOverlayUiForAllianceAppForeground()
            }
            return
        }
        if (!hasUsageAccess) {
            logGateStateThrottled(
                "overlayGate: нет доступа к статистике использования — панель скрыта",
            )
            gameGateCoordinator.onMissingUsageAccess()
            if (isOverlayShellActive()) {
                removeOverlayControl(force = true)
            }
            return
        }
        gateNotifyKey = ""
        if (!canDrawOverlaysNow()) {
            logGateStateThrottled("overlayGate: нет разрешения «поверх других приложений»")
            gameGateCoordinator.onMissingDrawOverlay()
            if (isOverlayShellActive()) {
                removeOverlayControl(force = true)
            }
            return
        }
        lastGateBlockReason = null
        promoteOverlayForeground()
        ensureOverlayIfPermitted()
        if (shouldShow && !wasInGame) {
            stableGatePollTicks = HUD_STABLE_TICKS_BEFORE_ATTACH
            beginOverlayStripGameSession()
            prefetchOverlayRaidRoomForStrip()
            stripSessionNeedsRestart = false
        } else if (shouldShow && stripSessionNeedsRestart) {
            ensureOverlayStripVisibleSession()
            resetOverlayVoiceForGameEntry()
            ensureOverlayRaidRealtimeIfNeeded()
            ensureOverlayForumInboxRealtimeIfNeeded()
            seedOverlayInboxBadgesBeforeRefresh()
            OverlayGameStatusHudRefresh.invalidateNewsForumCache()
            lastHudRefreshCompletedAtMs = 0L
            syncOverlayHubBadgeFromAppReadState()
            repairDetachedOverlayShellIfNeeded()
            attachOverlayHudWindowsIfNeeded()
            mainHandler.post {
                if (!isInGameOverlayUiActive()) return@post
                refreshOverlayStatusHudData(force = true)
                scheduleOverlayStatusHudRefresh()
            }
        }
        syncOverlayHudsIfReady()
        if (shouldShow && wasInGame) {
            scheduleOverlayStatusHudRefresh()
        }
        if (shouldShow) {
            if (overlayStatusHudHost == null || overlayTopRightHudHost == null) {
                repairDetachedOverlayShellIfNeeded()
                runCatching { showOverlayShell() }
                attachOverlayHudWindowsIfNeeded()
            } else {
                restoreOverlayInGameWindowVisibility()
            }
        } else if (isOverlayShellActive() && isOverlayChatStripEnabled()) {
            applyOverlayStripVisibility()
        }
    }

    /**
     * Снимает панель/чат при уходе из игры. Не опирается на [shouldKeepOverlayWindows]: suppress/пикер
     * иначе оставляют оверлей «висеть» после сворачивания или закрытия игры.
     */
    private fun dismissOverlayUiBecauseNotInGame(logWaitingForGame: Boolean) {
        if (OverlayChatInteractionHold.isOverlaySystemPickerSessionActive()) {
            deferredDismissWhenPickerEnds = true
            return
        }
        deferredDismissWhenPickerEnds = false
        stopOverlayIngamePresence(markAway = true)
        gateUiHideStreak = 0
        gateSoftHideStartedAtMs = 0L
        lastForegroundHintPkg = null
        GameForegroundGate.invalidateForegroundHintCache()
        cancelOverlayHudRefreshWork()
        updateStripDismissScreenRects(emptyList())
        stripPassthroughSyncPosted = false
        mainHandler.removeCallbacks(stripPassthroughSyncRunnable)
        syncChatStripWindowTouchPassthrough()
        overlayCommandsPopover.hide()
        OverlayChatInteractionHold.cancelPreparedOverlayModalInteraction(true)
        OverlayChatInteractionHold.clearStaleSuppressForGameBackground(
            chatTeamPanelVisible = false,
            commandsPopoverShowing = overlayCommandsPopover.isBlockingGameGateDismiss(),
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
        if (isOverlayShellActive()) {
            removeOverlayControl(force = true)
        }
        removeOverlayStatusHudWindow()
        removeOverlayTopRightHudWindow()
        mainHandler.removeCallbacks(stripZOrderLiftRunnable)
        stripZOrderLiftPosted = false
        chatStripZOrderLifted = false
        refreshOverlayForegroundWhileIdle()
    }

    private fun canDrawOverlaysNow(): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(this)
    }

    private fun notifyGateBlocked(reason: OverlayGateUserNotifier.BlockReason) {
        lastGateBlockReason = reason
        gateUserNotifier.maybeToast(reason) {
            OverlayMainActivityLaunch.launchOverlaySettingsTab(this)
        }
        val text = gateUserNotifier.notificationText(reason)
        if (text != lastForegroundNotificationText) {
            promoteOverlayForeground(
                OverlayForegroundNotifications.build(
                    this,
                    text,
                    AppContainer.from(this).userSettingsPreferences.isQuietMode(),
                ),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
            )
            lastForegroundNotificationText = text
        }
    }

    /**
     * Tear down overlay windows + FGS without touching [UserSettingsPreferences.isOverlayPanelEnabled].
     * Used for app-policy stops (hidden overlay tab, logout) — must not flip the user's "Показывать панель" toggle.
     */
    private fun shutdownRuntimeOnly(startId: Int): Int {
        gateCheckInFlight = false
        mainHandler.removeCallbacks(gameGateRunnable)
        stopOverlayIngamePresence(markAway = true)
        runCatching { hideOverlayChatTeamPanel() }
        runCatching { overlayTicker.hideTicker() }
        runCatching { removeOverlayControl(force = true) }
        runCatching { stopForeground(STOP_FOREGROUND_REMOVE) }
        isServiceInstanceActive = false
        _serviceRunning.value = false
        _overlayVisible.value = false
        _inGameOverlayUiActive.value = false
        if (AppContainer.from(this).userSettingsPreferences.isOverlayPanelEnabled() &&
            AppContainer.from(this).authRepository.hasSession()
        ) {
            OverlayRuntimeScheduler.scheduleImmediateRetry(applicationContext)
        }
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
                if (isOverlayShellActive()) {
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
            ACTION_SHARE_MAP_COORD -> {
                val raw = intent.getStringExtra(EXTRA_SHARE_TEXT).orEmpty()
                if (raw.isNotBlank()) {
                    shareMapTextToRaid(raw)
                }
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
        if (runningInstance === this) {
            runningInstance = null
        }
        isServiceInstanceActive = false
        _serviceRunning.value = false
        gateCheckInFlight = false
        mainHandler.removeCallbacks(gameGateRunnable)
        lastForegroundNotificationText = null
        lastForegroundMicActive = false
        overlayVoiceController.resetSession()
        OverlayReactionBitmapCache.clear()
        stopOverlayVoice()
        overlayTicker.hideTicker()
        runCatching { hideOverlayChatTeamPanel() }
        overlayPopoverComposeOwner?.destroy()
        overlayPopoverComposeOwner = null
        fcmRegistrationJob?.cancel()
        fcmRegistrationJob = null
        stopOverlayIngamePresence(markAway = true)
        removeOverlayControl(force = true)
        removeOverlayStatusHudWindow()
        removeOverlayTopRightHudWindow()
        teardownOverlayForumInboxRealtime()
        mainHandler.removeCallbacks(statusHudRefreshRunnable)
        mainHandler.removeCallbacks(overlayCloseHudRefreshRunnable)
        statusHudRefreshPosted = false
        serviceScope.cancel()
        unregisterScreenOnReceiver()
        unregisterVoiceMicPermissionReceiver()
        if (AppContainer.from(this).userSettingsPreferences.isOverlayPanelEnabled() &&
            AppContainer.from(this).authRepository.hasSession()
        ) {
            OverlayRuntimeScheduler.scheduleImmediateRetry(applicationContext)
        }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun foregroundNotificationIdleText(): String =
        getString(R.string.overlay_notif_fgs_idle)

    private fun promoteOverlayForeground(
        notification: android.app.Notification? = null,
        serviceTypes: Int = ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
    ) {
        val notif = notification ?: OverlayForegroundNotifications.build(
            this,
            foregroundNotificationIdleText(),
            AppContainer.from(this).userSettingsPreferences.isQuietMode(),
        )
        ServiceCompat.startForeground(
            this,
            OverlayForegroundNotifications.NOTIFICATION_ID,
            notif,
            serviceTypes,
        )
        overlayForegroundPromoted = true
    }

    /**
     * Вне игры оверлей скрыт — убираем «боевой режим» из шторки; FGS остаётся для опроса game gate.
     * Push о раскопках приходит отдельным каналом FCM.
     */
    private fun refreshOverlayForegroundWhileIdle() {
        if (voiceSession?.micOn == true) return
        promoteOverlayForeground(
            OverlayForegroundNotifications.build(
                this,
                foregroundNotificationIdleText(),
                AppContainer.from(this).userSettingsPreferences.isQuietMode(),
            ),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
        )
    }

    private fun registerScreenOnReceiver() {
        if (screenOnReceiverRegistered) return
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
        }
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(screenOnReceiver, filter, RECEIVER_NOT_EXPORTED)
            } else {
                registerReceiver(screenOnReceiver, filter)
            }
            screenOnReceiverRegistered = true
        }.onFailure { e ->
            Log.w(TAG, "registerScreenOnReceiver failed", e)
        }
    }

    private fun unregisterScreenOnReceiver() {
        if (!screenOnReceiverRegistered) return
        runCatching { unregisterReceiver(screenOnReceiver) }
        screenOnReceiverRegistered = false
    }

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

    /** Prune TTL strip cards only while preview is non-empty (avoids 2.5s main-thread wakeups in idle match). */
    private fun ensureStripPruneScheduled() {
        if (!isOverlayChatStripEnabled()) {
            cancelStripTick()
            return
        }
        if (!isInGameOverlayUiActive() || !isOverlayShellActive()) {
            cancelStripTick()
            return
        }
        if (stripBuffer.visibleForPreview().isEmpty()) {
            cancelStripTick()
            return
        }
        scheduleStripTick()
    }

    private fun scheduleStripTick() {
        mainHandler.removeCallbacks(stripTickRunnable)
        mainHandler.postDelayed(stripTickRunnable, STRIP_TICK_MS)
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
        removeStripMessageByKey(key)
    }

    private fun removeStripMessageByKey(messageKey: String) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { removeStripMessageByKey(messageKey) }
            return
        }
        val key = messageKey.trim()
        if (key.isEmpty()) return
        stripBuffer.removeMessageWithKey(key)
        lastStripRenderSignature = 0
        // Сразу снимаем зоны крестика, пока Compose не пересчитал ленту — иначе один кадр с «призрачными» rect блокирует игру.
        updateStripDismissScreenRects(emptyList())
        refreshOverlayChatStrip()
        ensureStripPruneScheduled()
    }

    private fun handleOverlayMessageDeleted(event: com.lastasylum.alliance.data.chat.ChatMessageDeletedEvent) {
        removeStripServerMessageAndOptimisticEchoes(event.messageId)
        forwardOverlayDeleteToViewModel(event)
    }

    private fun removeStripServerMessageAndOptimisticEchoes(serverMessageId: String) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { removeStripServerMessageAndOptimisticEchoes(serverMessageId) }
            return
        }
        val id = serverMessageId.trim()
        if (id.isEmpty()) return
        stripBuffer.removeServerMessageAndOptimisticEchoes(id)
        lastStripRenderSignature = 0
        updateStripDismissScreenRects(emptyList())
        refreshOverlayChatStrip()
    }

    private var lastStripDismissRects: List<Rect> = emptyList()

    private fun updateStripDismissScreenRects(rects: List<Rect>) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { updateStripDismissScreenRects(rects) }
            return
        }
        val nonEmpty = rects.filterNot { it.isEmpty }
        val hadDismiss = lastStripDismissRects.isNotEmpty()
        val hasDismiss = nonEmpty.isNotEmpty()
        if (stripDismissRectsEqual(lastStripDismissRects, nonEmpty) && hadDismiss == hasDismiss) return
        lastStripDismissRects = nonEmpty
        (chatStripHost as? OverlayStripPassthroughFrameLayout)?.dismissRectsInCompose = nonEmpty
        scheduleSyncChatStripWindowTouchPassthrough()
    }

    private fun stripDismissRectsEqual(a: List<Rect>, b: List<Rect>): Boolean {
        if (a.size != b.size) return false
        for (i in a.indices) {
            val left = a[i]
            val right = b[i]
            if (left.left != right.left || left.top != right.top ||
                left.right != right.right || left.bottom != right.bottom
            ) {
                return false
            }
        }
        return true
    }

    /**
     * Когда зон крестика нет, на окно ленты ставим [FLAG_NOT_TOUCHABLE]: иначе на части OEM
     * касания по «пустому» месту после закрытия карточек остаются в оверлее и блокируют игру,
     * даже если корень ленты не забирает жест в [OverlayStripPassthroughFrameLayout].
     */
    private fun scheduleSyncChatStripWindowTouchPassthrough() {
        if (!stripPassthroughSyncPosted) {
            stripPassthroughSyncPosted = true
            mainHandler.post(stripPassthroughSyncRunnable)
        }
    }

    private fun syncChatStripWindowTouchPassthrough() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { scheduleSyncChatStripWindowTouchPassthrough() }
            return
        }
        val host = chatStripHost as? OverlayStripPassthroughFrameLayout ?: return
        val params = chatStripParams ?: return
        val mgr = windowManager ?: systemWindowManager() ?: return
        if (!host.isAttachedToWindow) return
        val stripEmpty = stripBuffer.visibleForPreview().isEmpty() &&
            chatStripPreviewFlow.value.isEmpty()
        val hasDismissZones = !stripEmpty && host.dismissRectsInCompose.isNotEmpty()
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

    private fun scheduleRefreshOverlayChatStrip() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { scheduleRefreshOverlayChatStrip() }
            return
        }
        if (stripUiCoalescePosted) return
        stripUiCoalescePosted = true
        mainHandler.post(stripUiCoalesceRunnable)
    }

    private fun refreshOverlayChatStrip() = scheduleRefreshOverlayChatStrip()

    private fun overlayStripPreviewSignature(preview: List<ChatMessage>): Int {
        var h = 17
        h = 31 * h + stripLiveRevision
        h = 31 * h + preview.size
        for (msg in preview) {
            val key = msg._id?.takeIf { it.isNotBlank() } ?: msg.stableKey()
            h = 31 * h + key.hashCode()
            h = 31 * h + msg.text.hashCode()
            h = 31 * h + msg.text.length
            h = 31 * h + msg.senderRole.hashCode()
            h = 31 * h + msg.senderId.hashCode()
            h = 31 * h + msg.chatImageAttachments().size
        }
        return h
    }

    private fun stripPreviewContentEqual(
        a: List<ChatMessage>,
        b: List<ChatMessage>,
    ): Boolean {
        if (a === b) return true
        if (a.size != b.size) return false
        for (i in a.indices) {
            if (a[i] != b[i]) return false
        }
        return true
    }

    private fun refreshOverlayChatStripNow() {
        stripBuffer.prune()
        val preview = stripBuffer.visibleForPreview()
        val signature = overlayStripPreviewSignature(preview)
        if (signature == lastStripRenderSignature &&
            stripPreviewContentEqual(preview, chatStripPreviewFlow.value)
        ) {
            ensureStripWindowVisibleForRaidTraffic()
            return
        }
        if (BuildConfig.DEBUG) {
            Log.d(
                OVERLAY_DIAG_TAG,
                "stripRefresh apply preview=${preview.size} sig=$signature",
            )
        }
        chatStripPreviewFlow.value = preview
        lastStripRenderSignature = signature
        val wm = windowManager ?: systemWindowManager() ?: return
        runCatching { ensureChatStripWindow(wm) }
        ensureStripWindowVisibleForRaidTraffic()
    }

    /** Лента «Рейд» на экране — только в игре / краткий grace / принудительный показ после отправки. */
    private fun isOverlayRaidStripEligible(): Boolean {
        if (!AppContainer.from(this).userSettingsPreferences.isOverlayPanelEnabled()) return false
        if (!isOverlayChatStripEnabled()) return false
        if (shouldForceShowRaidStrip()) return true
        if (isInGameOverlayUiActive()) return true
        if (overlayInGameProbeActive) return true
        val last = lastOverlayInGameAtMs
        if (last <= 0L) return false
        return System.currentTimeMillis() - last < OVERLAY_INGAME_GRACE_MS
    }

    private fun shouldIngestInboundRaidStrip(): Boolean =
        OverlayRaidStripIngestPolicy.shouldIngestInbound(
            overlayIngamePresenceActive = overlayIngamePresenceActive,
            stripEligible = isOverlayRaidStripEligible(),
        )

    /** Показать ленту сразу при входящем raid-сообщении, не дожидаясь game-gate tick. */
    private fun revealStripForInboundRaidIfNeeded() {
        if (!isOverlayChatStripEnabled()) return
        if (!shouldIngestInboundRaidStrip()) return
        ensureOverlayStripVisibleSession()
        chatStripHost?.visibility = View.VISIBLE
        applyOverlayStripVisibility(rebalanceZOrder = false)
        publishStripAfterLocalRaidSend()
    }

    private fun shouldForceShowRaidStrip(): Boolean =
        System.currentTimeMillis() < forceShowStripUntilMs

    /** Поднять окно ленты, когда в буфере есть карточки и игрок в матче. */
    private fun ensureStripWindowVisibleForRaidTraffic() {
        val hasContent = stripBuffer.visibleForPreview().isNotEmpty() ||
            chatStripPreviewFlow.value.isNotEmpty()
        if (!hasContent && !isOverlayRaidStripEligible()) return
        publishStripAfterLocalRaidSend()
        if (!isOverlayShellActive()) {
            ensureOverlayIfPermitted()
            repairDetachedOverlayShellIfNeeded()
        }
    }

    private fun shouldRetainOverlayRaidRealtime(): Boolean {
        val container = AppContainer.from(this)
        return container.userSettingsPreferences.isOverlayPanelEnabled() &&
            container.authRepository.hasSession()
    }

    /** Подписка на «Рейд»/hub, пока FGS активен и панель включена (не только пока окна на экране). */
    private fun ensureOverlayRaidRealtimeIfNeeded() {
        if (!shouldRetainOverlayRaidRealtime()) return
        ensureOverlayForumInboxRealtimeIfNeeded()
        if (overlayMessageListener != null) {
            syncOverlayRaidRoomSubscription()
            refreshOverlayHubUnreadFromCache()
            return
        }
        beginOverlayChatSubscription()
    }

    /** Forum topic inbox for overlay «Форум» badge while in-game (not only inside a topic). */
    private fun ensureOverlayForumInboxRealtimeIfNeeded() {
        if (!isInGameOverlayUiActive()) return
        if (overlayForumTopicActivityListener != null) return
        val container = AppContainer.from(this)
        val selfId = jwtSubFromAccessToken()?.trim().orEmpty()
        val listener: (TeamForumTopicActivityEvent) -> Unit = listener@{ event ->
            if (selfId.isNotBlank() && event.senderUserId.trim() == selfId) return@listener
            mainHandler.post {
                if (!isInGameOverlayUiActive()) return@post
                if (overlayChatTeamPanelVisible && currentOverlayHudPane == OverlayHudPane.Forum) {
                    return@post
                }
                OverlayGameStatusHudRefresh.invalidateForumCache()
                val current = overlayStatusHudFlow.value.forumUnread
                val next = (current + 1).coerceAtMost(999)
                inboxBadgeCoordinator.bumpForumOptimistic(next)
                overlayStatusHudFlow.value = overlayStatusHudFlow.value.copy(forumUnread = next)
                scheduleDebouncedForumHudRefresh()
            }
        }
        overlayForumTopicActivityListener = listener
        container.teamForumSocket.addTopicActivityListener(listener)
        serviceScope.launch(Dispatchers.IO) {
            val teamId = container.usersRepository.resolveMyProfilePreferCache()
                ?.playerTeamId
                ?.trim()
                .orEmpty()
            if (teamId.isEmpty()) return@launch
            val baseUrl = BuildConfig.API_BASE_URL.trimEnd('/')
            mainHandler.post {
                if (!isInGameOverlayUiActive()) return@post
                container.teamForumSocket.connectTeamInbox(baseUrl, teamId) {
                    container.tokenStore.getAccessToken()
                }
            }
        }
    }

    private fun teardownOverlayForumInboxRealtime() {
        overlayForumTopicActivityListener?.let { listener ->
            runCatching {
                AppContainer.from(this).teamForumSocket.removeTopicActivityListener(listener)
            }
        }
        overlayForumTopicActivityListener = null
    }

    /** Маршрутизация raid/hub в ленту, пока слушатель оверлея активен или игрок в матче. */
    private fun isOverlayChatRoutingActive(): Boolean {
        if (!AppContainer.from(this).userSettingsPreferences.isOverlayPanelEnabled()) return false
        if (overlayMessageListener != null) return true
        if (overlaySessionActive) return true
        if (isOverlayUiHoldActive()) return true
        if (isInGameOverlayUiActive()) return true
        return lastOverlayInGameAtMs > 0L &&
            System.currentTimeMillis() - lastOverlayInGameAtMs < OVERLAY_INGAME_GRACE_MS
    }

    /**
     * Исходящее сообщение по HTTP (текст из истории, голос, быстрые команды): сразу обновить ленту оверлея,
     * как только приходит ответ API (не ждать сокет). Вызывать с главного потока.
     */
    /**
     * Локальная отправка (быстрые команды, HTTP-эхо) — пишем в буфер ленты напрямую,
     * не полагаясь только на [shouldIngestForRaidStrip] и сокет.
     */
    private fun shareMapTextToRaid(raw: String) {
        val coord = com.lastasylum.alliance.game.MapCoordinateParser.parseSharedText(raw.trim())
        if (coord == null) {
            mainHandler.post {
                Toast.makeText(this, R.string.share_coord_parse_failed, Toast.LENGTH_SHORT).show()
            }
            return
        }
        val text = coord.fullMessageText()
        serviceScope.launch {
            val container = AppContainer.from(this@CombatOverlayService)
            if (!container.authRepository.hasSession()) return@launch
            val repo = container.chatRepository
            val roomId = ensureOverlayRaidRoomReadyForSend()
            if (roomId == null) {
                mainHandler.post {
                    Toast.makeText(
                        this@CombatOverlayService,
                        R.string.overlay_strip_no_raid,
                        Toast.LENGTH_SHORT,
                    ).show()
                }
                return@launch
            }
            rememberOverlayRaidRoomId(roomId)
            mainHandler.post {
                applyLocalSentMessageToStrip(
                    buildOptimisticRaidCommandMessage(roomId, text),
                    trustedRaidRoomId = roomId,
                    displayText = text,
                )
            }
            val result = repo.sendOverlayRaidCommandFast(text = text, roomId = roomId)
            mainHandler.post {
                result.onSuccess { sent ->
                    publishQuickCommandToStrip(sent, roomId, text)
                    extendInGameOverlayUiHold()
                    Toast.makeText(
                        this@CombatOverlayService,
                        R.string.share_coord_sent_raid,
                        Toast.LENGTH_SHORT,
                    ).show()
                }.onFailure { e ->
                    val msg = when (e.message) {
                        "no_room" -> getString(R.string.overlay_strip_no_room)
                        "no_raid" -> getString(R.string.overlay_strip_no_raid)
                        else ->
                            e.message?.takeIf { it.isNotBlank() }
                                ?: getString(R.string.overlay_history_send_failed, e.javaClass.simpleName)
                    }
                    Toast.makeText(this@CombatOverlayService, msg, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun warmupOverlayRaidForQuickCommands() {
        serviceScope.launch {
            ensureOverlayRaidRoomReadyForSend()
        }
    }

    private fun formatOverlayRaidQuickCommandText(
        label: String,
        x: Int,
        y: Int,
        excavation: Boolean,
    ): String =
        if (excavation) {
            getString(R.string.overlay_excavation_message, x, y)
        } else {
            com.lastasylum.alliance.game.MapCoordinateFormatter.format(
                label = label,
                targetName = null,
                x = x,
                y = y,
            )
        }

    /** Optimistic card before HTTP (popover closes immediately; call from main). */
    private fun postOptimisticOverlayRaidQuickCommand(text: String): String? {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { postOptimisticOverlayRaidQuickCommand(text) }
            return null
        }
        val body = text.trim()
        if (body.isEmpty()) return null
        val roomId = resolveCachedRaidRoomIdForSend()
        if (roomId.isNullOrBlank()) {
            warmupOverlayRaidForQuickCommands()
            return null
        }
        scheduleOverlayRaidRealtimeWarm(roomId)
        val optimistic = buildOptimisticRaidCommandMessage(roomId, body)
        applyLocalSentMessageToStrip(
            optimistic,
            trustedRaidRoomId = roomId,
            displayText = body,
        )
        return optimistic._id
    }

    /** Cached raid room id (prefs / trusted / session cache) without network. */
    private fun resolveCachedRaidRoomIdForSend(): String? =
        resolveOverlayRaidRoomId()?.trim()?.takeIf { it.isNotEmpty() }
            ?: trustedOverlayRaidRoomId?.trim()?.takeIf { it.isNotEmpty() }
            ?: AppContainer.from(this).chatRoomPreferences.getRaidRoomId()?.trim()?.takeIf { it.isNotEmpty() }

    private fun scheduleOverlayRaidRealtimeWarm(roomId: String) {
        rememberOverlayRaidRoomId(roomId)
        if (Looper.myLooper() == Looper.getMainLooper()) {
            ensureOverlayRaidRealtimeIfNeeded()
            syncOverlayRaidRoomSubscription()
        } else {
            mainHandler.post {
                ensureOverlayRaidRealtimeIfNeeded()
                syncOverlayRaidRoomSubscription()
            }
        }
    }

    private suspend fun sendOverlayRaidQuickCommandHttp(
        text: String,
        excavationAlert: Boolean,
    ): Result<ChatMessage> {
        val repo = AppContainer.from(this@CombatOverlayService).chatRepository
        val roomId = ensureOverlayRaidRoomReadyForSend()
            ?: return Result.failure(IllegalStateException("no_raid"))
        val result = repo.sendOverlayRaidCommandFast(
            text = text,
            roomId = roomId,
            excavationAlert = excavationAlert,
        )
        result.onSuccess { sent ->
            mainHandler.post {
                publishQuickCommandToStrip(sent, roomId, text)
            }
        }
        return result
    }

    private fun buildOptimisticRaidCommandMessage(roomId: String, text: String): ChatMessage {
        val rid = roomId.trim()
        val body = text.trim()
        val senderId = jwtSubFromAccessToken()?.trim().orEmpty()
        val profile = AppContainer.from(this).usersRepository.peekMyProfile()
            ?: AppContainer.from(this).usersRepository.peekMyProfileDisk()
        return ChatMessage(
            _id = "overlay-pending-${System.nanoTime()}",
            allianceId = "",
            roomId = rid,
            senderId = senderId,
            senderUsername = profile?.username?.trim().orEmpty(),
            senderRole = profile?.role?.trim().orEmpty(),
            senderTeamTag = profile?.playerTeamTag?.trim()?.takeIf { it.isNotEmpty() },
            senderTelegramUsername = profile?.telegramUsername?.trim()?.takeIf { it.isNotEmpty() },
            text = body,
            attachments = emptyList(),
            createdAt = Instant.now().toString(),
            updatedAt = null,
            replyToMessageId = null,
            replyTo = null,
            deletedAt = null,
            deletedByUserId = null,
        )
    }

    private fun applyLocalSentMessageToStrip(
        sent: ChatMessage,
        trustedRaidRoomId: String? = null,
        displayText: String? = null,
    ) {
        if (isOverlayChatStripEnabled()) {
            ensureOverlayStripVisibleSession()
        }
        extendOverlayUiHold()
        val raid = trustedRaidRoomId?.trim()?.takeIf { it.isNotEmpty() }
            ?: resolveOverlayRaidRoomId()
            ?: trustedOverlayRaidRoomId
        if (raid.isNullOrBlank()) {
            ingestOverlayRaidMessage(sent, refreshNow = true, forceIngest = true)
            return
        }
        rememberOverlayRaidRoomId(raid)
        val text = sent.text.trim().ifBlank { displayText?.trim().orEmpty() }
        val normalized = sent.copy(
            roomId = sent.roomId.trim().ifBlank { raid },
            text = text,
        )
        stripBuffer.removeOptimisticEchoesForServerMessage(normalized)
        stripBuffer.upsert(normalized)
        stripBuffer.mergeReceiveTimeline(normalized, jwtSubFromAccessToken())
        stripBuffer.markClientSend(normalized)
        stripLiveRevision++
        val preview = stripBuffer.visibleForPreview().toList()
        lastStripRenderSignature = overlayStripPreviewSignature(preview)
        forceShowStripUntilMs = maxOf(
            forceShowStripUntilMs,
            System.currentTimeMillis() + FORCE_SHOW_STRIP_AFTER_LOCAL_SEND_MS,
        )
        ensureOverlayIfPermitted()
        val wm = windowManager ?: systemWindowManager()
        if (wm != null && isOverlayChatStripEnabled()) {
            runCatching { ensureChatStripWindow(wm) }
        }
        chatStripPreviewFlow.value = preview
        publishStripAfterLocalRaidSend()
        Log.i(
            TAG,
            "stripLocalSend preview=${preview.size} room=${normalized.roomId} " +
                "textLen=${normalized.text.length} stripAttached=${chatStripHost?.isAttachedToWindow == true} " +
                "stripVisible=${chatStripHost?.visibility == View.VISIBLE}",
        )
    }

    private fun publishStripAfterLocalRaidSend() {
        if (!isOverlayChatStripEnabled()) return
        extendOverlayUiHold()
        forceShowStripUntilMs = maxOf(
            forceShowStripUntilMs,
            System.currentTimeMillis() + FORCE_SHOW_STRIP_AFTER_LOCAL_SEND_MS,
        )
        val wm = windowManager ?: systemWindowManager()
        if (wm != null) {
            overlaySessionActive = true
            runCatching { ensureChatStripWindow(wm) }
        }
        chatStripHost?.visibility = View.VISIBLE
        applyOverlayStripVisibility(rebalanceZOrder = false)
    }

    /** Быстрые команды → лента «Рейд»: нормализуем roomId/текст с ответа API. */
    private fun publishQuickCommandToStrip(sent: ChatMessage, raidRoomId: String, fallbackText: String) {
        val raid = raidRoomId.trim()
        val normalized = sent.copy(
            roomId = sent.roomId.trim().ifBlank { raid },
            text = sent.text.trim().ifBlank { fallbackText },
        )
        applyLocalSentMessageToStrip(
            normalized,
            trustedRaidRoomId = raid,
            displayText = fallbackText,
        )
        forwardOverlayRaidMessageToViewModel(normalized)
    }

    private fun resolveOverlayReactionSenderDisplayName(event: OverlayReactionEvent): String {
        event.fromUsername.trim().takeIf { it.isNotBlank() }?.let { return it }
        OverlayTeamContextCache.memberUsername(event.fromUserId)?.let { return it }
        return getString(R.string.overlay_reaction_sender_unknown)
    }

    /** Id «Рейд» из prefs или свежего кэша комнат (если prefs ещё пуст после старта оверлея). */
    private fun resolveOverlayRaidRoomId(): String? {
        val prefs = AppContainer.from(this).chatRoomPreferences
        prefs.getRaidRoomId()?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
        val rooms = com.lastasylum.alliance.data.chat.ChatSessionCache.getFreshRooms() ?: return null
        val raid = rooms.firstOrNull {
            com.lastasylum.alliance.data.chat.ChatRaidRoomSync.isAllianceRaidRoom(it)
        } ?: return null
        val id = raid.id.trim().takeIf { it.isNotEmpty() } ?: return null
        if (prefs.getRaidRoomId() != id) {
            prefs.setRaidRoomId(id)
            syncOverlayRaidRoomSubscription()
        }
        rememberOverlayRaidRoomId(id)
        return id
    }

    private fun rememberOverlayRaidRoomId(roomId: String?) {
        val id = roomId?.trim()?.takeIf { it.isNotEmpty() } ?: return
        trustedOverlayRaidRoomId = id
        AppContainer.from(this).chatRoomPreferences.setRaidRoomId(id)
    }

    private fun trustedOverlayRaidRoomIds(): Set<String> = buildSet {
        trustedOverlayRaidRoomId?.let { add(it) }
        resolveOverlayRaidRoomId()?.let { add(it) }
    }

    private fun onOverlayRaidRoomIdResolved(roomId: String) {
        rememberOverlayRaidRoomId(roomId)
        syncOverlayRaidRoomSubscription()
    }

    private fun shouldIngestForRaidStrip(msg: ChatMessage): Boolean {
        if (OverlayRaidStripRouting.acceptsRaidStripMessage(
                msg,
                resolveOverlayRaidRoomId(),
                ::onOverlayRaidRoomIdResolved,
                trustedRaidRoomIds = trustedOverlayRaidRoomIds(),
            )
        ) {
            return true
        }
        val room = msg.roomId.trim()
        if (room.isEmpty()) return false
        if (room in trustedOverlayRaidRoomIds()) return true
        val prefsRaid = AppContainer.from(this).chatRoomPreferences.getRaidRoomId()?.trim().orEmpty()
        if (prefsRaid.isNotEmpty() && room == prefsRaid) {
            onOverlayRaidRoomIdResolved(room)
            return true
        }
        resolveOverlayRaidRoomId()?.trim()?.takeIf { it.isNotEmpty() }?.let { resolved ->
            if (room == resolved) return true
        }
        val cached = com.lastasylum.alliance.data.chat.ChatSessionCache.getFreshRooms() ?: return false
        val dto = cached.firstOrNull { it.id.trim() == room } ?: return false
        if (!com.lastasylum.alliance.data.chat.ChatRaidRoomSync.isAllianceRaidRoom(dto)) return false
        onOverlayRaidRoomIdResolved(room)
        return true
    }

    private fun normalizeStripRaidMessage(msg: ChatMessage, raidId: String?): ChatMessage {
        val raid = raidId?.trim().orEmpty()
        if (raid.isEmpty()) return msg
        return msg.copy(
            roomId = msg.roomId.trim().ifBlank { raid },
        )
    }

    private fun isStaleOverlayStripMessage(msg: ChatMessage): Boolean {
        val selfId = jwtSubFromAccessToken()?.trim().orEmpty()
        if (selfId.isNotEmpty() && msg.senderId.trim() == selfId) {
            return false
        }
        val parsed = OverlayChatTime.parseInstant(msg.createdAt) ?: return false
        val cutoff = Instant.now().minus(
            OverlayChatStripBuffer.DEFAULT_MESSAGE_TTL_SECONDS,
            ChronoUnit.SECONDS,
        )
        return parsed.isBefore(cutoff)
    }

    private fun refreshExistingStripMessage(
        msg: ChatMessage,
        refreshNow: Boolean,
    ) {
        stripBuffer.upsert(msg)
        stripBuffer.touchReceivedNow(msg)
        stripLiveRevision++
        val preview = stripBuffer.visibleForPreview().toList()
        lastStripRenderSignature = overlayStripPreviewSignature(preview)
        chatStripPreviewFlow.value = preview
        if (refreshNow) {
            refreshOverlayChatStripNow()
        } else {
            scheduleRefreshOverlayChatStrip()
        }
        ensureStripWindowVisibleForRaidTraffic()
    }

    private fun ingestOverlayRaidMessage(
        msg: ChatMessage,
        refreshNow: Boolean,
        retryIndex: Int = 0,
        forceIngest: Boolean = false,
        inbound: Boolean = false,
    ) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post {
                ingestOverlayRaidMessage(msg, refreshNow, retryIndex, forceIngest, inbound)
            }
            return
        }
        // Не копим карточки вне матча — иначе при входе в игру всплывает старый рейд-чат.
        if (!forceIngest) {
            val allowed = if (inbound) {
                shouldIngestInboundRaidStrip()
            } else {
                OverlayRaidStripIngestPolicy.shouldIngestOutbound(isOverlayRaidStripEligible())
            }
            if (!allowed) {
                if (BuildConfig.DEBUG) {
                    Log.d(
                        OVERLAY_DIAG_TAG,
                        "stripDrop reason=not_eligible id=${msg._id} inbound=$inbound",
                    )
                }
                return
            }
        }
        val raidId = resolveOverlayRaidRoomId() ?: trustedOverlayRaidRoomId
        val normalized = normalizeStripRaidMessage(msg, raidId)
        if (!forceIngest && isStaleOverlayStripMessage(normalized)) {
            if (BuildConfig.DEBUG) {
                Log.d(OVERLAY_DIAG_TAG, "stripDrop reason=stale id=${normalized._id}")
            }
            return
        }
        normalized._id?.trim()?.takeIf { it.isNotEmpty() }?.let { existingId ->
            if (stripBuffer.containsMessageId(existingId)) {
                refreshExistingStripMessage(normalized, refreshNow)
                return
            }
        }
        if (!shouldIngestForRaidStrip(normalized)) {
            val forcedRaid = raidId?.trim().orEmpty()
            if (forceIngest && forcedRaid.isNotEmpty()) {
                val forced = normalized.copy(
                    roomId = normalized.roomId.trim().ifBlank { forcedRaid },
                )
                rememberOverlayRaidRoomId(forcedRaid)
                if (isOverlayChatStripEnabled()) {
                    ensureOverlayStripVisibleSession()
                }
                stripBuffer.upsert(forced)
                stripBuffer.mergeReceiveTimeline(forced, jwtSubFromAccessToken())
                stripBuffer.touchReceivedNow(forced)
                stripLiveRevision++
                val forcedPreview = stripBuffer.visibleForPreview().toList()
                lastStripRenderSignature = overlayStripPreviewSignature(forcedPreview)
                chatStripPreviewFlow.value = forcedPreview
                if (refreshNow) {
                    refreshOverlayChatStripNow()
                } else {
                    scheduleRefreshOverlayChatStrip()
                }
                ensureStripWindowVisibleForRaidTraffic()
                ensureStripPruneScheduled()
                return
            }
            if (BuildConfig.DEBUG) {
                Log.d(
                    OVERLAY_DIAG_TAG,
                    "stripDrop reason=no_room room=${normalized.roomId} raid=$raidId trusted=$trustedOverlayRaidRoomId id=${normalized._id} retry=$retryIndex",
                )
            }
            if (retryIndex < STRIP_INGEST_RETRY_DELAYS_MS.size) {
                prefetchOverlayRaidRoomForStrip()
                mainHandler.postDelayed(
                    {
                        ingestOverlayRaidMessage(
                            msg,
                            refreshNow,
                            retryIndex + 1,
                            forceIngest,
                            inbound,
                        )
                    },
                    STRIP_INGEST_RETRY_DELAYS_MS[retryIndex],
                )
            }
            return
        }
        if (BuildConfig.DEBUG) {
            Log.d(
                OVERLAY_DIAG_TAG,
                "stripIngest id=${normalized._id} room=${normalized.roomId} sender=${normalized.senderId} textLen=${normalized.text.length}",
            )
        }
        if (isOverlayChatStripEnabled()) {
            ensureOverlayStripVisibleSession()
        }
        stripBuffer.removeOptimisticEchoesForServerMessage(normalized)
        stripBuffer.upsert(normalized)
        stripBuffer.mergeReceiveTimeline(normalized, jwtSubFromAccessToken())
        stripBuffer.touchReceivedNow(normalized)
        stripLiveRevision++
        val preview = stripBuffer.visibleForPreview().toList()
        chatStripPreviewFlow.value = preview
        if (refreshNow) {
            lastStripRenderSignature = 0
            refreshOverlayChatStripNow()
        } else {
            lastStripRenderSignature = overlayStripPreviewSignature(preview)
            scheduleRefreshOverlayChatStrip()
        }
        ensureOverlayMessageStripIfNeeded()
        ensureStripWindowVisibleForRaidTraffic()
        ensureStripPruneScheduled()
    }

    private fun setStripPlainMessage(message: String, noticeId: String = OverlayStripNoticeIds.GENERIC) {
        if (BuildConfig.DEBUG) {
            Log.d(OVERLAY_DIAG_TAG, "stripNotice id=$noticeId textLen=${message.length}")
        }
        val notice = ChatMessage(
            _id = noticeId,
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
        )
        stripBuffer.clear()
        stripBuffer.upsert(notice)
        lastStripRenderSignature = 0
        refreshOverlayChatStripNow()
        ensureStripWindowVisibleForRaidTraffic()
        ensureStripPruneScheduled()
    }

    private fun accessTokenOrNull(): String? =
        runCatching { AppContainer.from(this).tokenStore.getAccessToken() }.getOrNull()

    private fun jwtSubFromAccessToken(): String? = JwtAccessTokenClaims.sub(accessTokenOrNull())

    private fun jwtRoleFromAccessToken(): String = JwtAccessTokenClaims.role(accessTokenOrNull()).orEmpty()

    private fun removeChatStripWindow(forManager: WindowManager? = null) {
        val mgr = forManager ?: windowManager ?: systemWindowManager() ?: return
        val host = chatStripHost ?: return
        updateStripDismissScreenRects(emptyList())
        runCatching { mgr.removeView(host) }
        chatStripHost = null
        chatStripParams = null
        chatStripClipRoot = null
        chatStripCompose = null
        chatStripZOrderLifted = false
        lastStripDismissRects = emptyList()
        (host as? OverlayStripPassthroughFrameLayout)?.dismissRectsInCompose = emptyList()
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
                val preview by chatStripPreviewFlow.collectAsStateWithLifecycle(owner)
                val selfId = remember { jwtSubFromAccessToken().orEmpty() }
                SquadRelayTheme {
                    OverlayChatStrip(
                        messages = preview,
                        selfUserId = selfId,
                        lightStrip = isOverlayLightStripMode(),
                        onDismissMessage = { m -> dismissStripMessage(m) },
                        onNoticeClick = { noticeId ->
                            when (noticeId) {
                                OverlayStripNoticeIds.NO_RAID ->
                                    OverlayMainActivityLaunch.launchChatTab(this@CombatOverlayService)
                            }
                        },
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
        if (BuildConfig.DEBUG) {
            Log.d(OVERLAY_DIAG_TAG, "chatStripWindow attached stripOwner=${overlayStripComposeOwner != null}")
        }
        chatStripHost = host
        chatStripParams = params
        chatStripClipRoot = clipRoot
        retainWindowManager(manager)
        _overlayVisible.value = true
        syncChatStripWindowTouchPassthrough()
        scheduleStripZOrderLift()
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
                    runOverlayVoiceUserAction { session ->
                        session.whenVoiceReady {
                            session.setMicEnabled(true)
                            refreshOverlayTopRightHudState()
                        }
                    }
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

    private fun syncOverlayRaidRoomSubscription() {
        resolveOverlayRaidRoomId()?.let { rememberOverlayRaidRoomId(it) }
        if (overlayMessageListener == null) return
        AppContainer.from(this).chatRepository.refreshOverlayRealtimeSubscriptions()
    }

    /** Подтянуть id «Рейд» до первого сообщения в ленту (оверлей мог стартовать без вкладки «Чат»). */
    private fun prefetchOverlayRaidRoomForStrip() {
        serviceScope.launch {
            ensureOverlayRaidRoomReadyForSend()
        }
    }

    /**
     * Блокирующий prefetch raid room + socket join перед отправкой или ingest retry.
     * @return resolved raid room id or null
     */
    private suspend fun ensureOverlayRaidRoomReadyForSend(): String? {
        resolveCachedRaidRoomIdForSend()?.let { id ->
            scheduleOverlayRaidRealtimeWarm(id)
            return id
        }
        val container = AppContainer.from(this@CombatOverlayService)
        var raidId = container.chatRepository.ensureRaidRoomId()?.trim()?.takeIf { it.isNotEmpty() }
        if (raidId == null) {
            val cached = com.lastasylum.alliance.data.chat.ChatSessionCache.getFreshRooms()
            val rooms = cached ?: container.chatRepository.listRooms().getOrNull()
            if (rooms != null) {
                if (cached == null) {
                    com.lastasylum.alliance.data.chat.ChatSessionCache.update(rooms)
                }
                container.chatRepository.applyOverlayRoomsFromRooms(rooms)
                raidId = container.chatRepository.ensureRaidRoomId()?.trim()?.takeIf { it.isNotEmpty() }
            }
        }
        raidId?.let { scheduleOverlayRaidRealtimeWarm(it) }
        return raidId
    }

    private fun registerOverlayChatListenersOnMain(
        listener: (ChatMessage) -> Unit,
        deleteListener: (com.lastasylum.alliance.data.chat.ChatMessageDeletedEvent) -> Unit,
        readListener: (com.lastasylum.alliance.data.chat.ChatRoomReadEvent) -> Unit,
        typingListener: (com.lastasylum.alliance.data.chat.ChatTypingEvent) -> Unit,
        roomUnreadListener: (com.lastasylum.alliance.data.chat.ChatRoomUnreadEvent) -> Unit,
        reactionListener: (OverlayReactionEvent) -> Unit,
    ) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post {
                registerOverlayChatListenersOnMain(
                    listener,
                    deleteListener,
                    readListener,
                    typingListener,
                    roomUnreadListener,
                    reactionListener,
                )
            }
            return
        }
        val repo = AppContainer.from(this).chatRepository
        repo.addOverlayMessageListener(listener)
        repo.addOverlayMessageDeletedListener(deleteListener)
        repo.addOverlayReadListener(readListener)
        repo.addOverlayTypingListener(typingListener)
        repo.addOverlayRoomUnreadListener(roomUnreadListener)
        repo.addOverlayReactionListener(reactionListener)
        val historyListener: () -> Unit = {
            mainHandler.post {
                stripBuffer.clear()
                lastStripRenderSignature = 0
                updateStripDismissScreenRects(emptyList())
                refreshOverlayChatStrip()
                CombatOverlayService.resolveChatViewModel()?.applyChatHistoryClearedFromServer()
            }
        }
        overlayChatHistoryClearedListener = historyListener
        repo.addOverlayChatHistoryClearedListener(historyListener)
        resolveOverlayRaidRoomId()?.let { rememberOverlayRaidRoomId(it) }
            ?: AppContainer.from(this).chatRoomPreferences.getRaidRoomId()
                ?.let { rememberOverlayRaidRoomId(it) }
        syncOverlayRaidRoomSubscription()
        refreshOverlayHubUnreadFromCache()
        refreshOverlayHubUnreadOnly()
        if (isOverlayChatStripEnabled()) {
            ensureStripPruneScheduled()
        }
        resolveCachedAllianceHubRoomId()
    }

    private fun beginOverlayChatSubscription() {
        if (overlayMessageListener != null) {
            syncOverlayRaidRoomSubscription()
            refreshOverlayHubUnreadFromCache()
            return
        }
        prefetchOverlayRaidRoomForStrip()
        registerVoiceMicPermissionReceiver()
        cancelStripTick()
        val reactionListener: (OverlayReactionEvent) -> Unit = { event ->
            mainHandler.post {
                if (!overlaySessionActive) return@post
                val selfId = jwtSubFromAccessToken()?.trim().orEmpty()
                if (selfId.isBlank()) return@post
                if (event.fromUserId == selfId) return@post
                if (event.targetUserId != selfId) return@post
                val wm = windowManager ?: getSystemService(Context.WINDOW_SERVICE) as? WindowManager ?: return@post
                overlayCommandsPopover.showIncomingReactionBurst(
                    wm,
                    resolveOverlayReactionSenderDisplayName(event),
                    event.reaction,
                    event.broadcast,
                )
            }
        }
        overlayReactionListener = reactionListener
        val readListener: (com.lastasylum.alliance.data.chat.ChatRoomReadEvent) -> Unit = readListener@{ event ->
            mainHandler.post {
                if (!isOverlayChatRoutingActive()) return@post
                applyOverlayHubReadFromSelf(event)
                forwardOverlayReadToViewModel(event)
            }
        }
        overlayReadListener = readListener
        val typingListener: (com.lastasylum.alliance.data.chat.ChatTypingEvent) -> Unit = typingListener@{ event ->
            mainHandler.post {
                if (!isOverlayChatRoutingActive()) return@post
                forwardOverlayTypingToViewModel(event)
            }
        }
        overlayTypingListener = typingListener
        val roomUnreadListener: (com.lastasylum.alliance.data.chat.ChatRoomUnreadEvent) -> Unit =
            roomUnreadListener@{ event ->
                mainHandler.post {
                    if (!isOverlayChatRoutingActive()) return@post
                    applyOverlayRoomUnreadFromSocket(event)
                }
            }
        overlayRoomUnreadListener = roomUnreadListener
        val listener: (ChatMessage) -> Unit = listener@{ msg ->
            mainHandler.post {
                if (!isOverlayChatRoutingActive()) return@post
                forwardOverlaySocketMessageToViewModel(msg)
                if (msg.isCompactReactionSocketUpdate()) {
                    val id = msg._id?.trim().orEmpty()
                    if (id.isNotEmpty() && stripBuffer.containsMessageId(id)) {
                        refreshExistingStripMessage(msg, refreshNow = true)
                    }
                    return@post
                }
                val hubId = resolveOverlayHubRoomId()
                val isHub = hubId.isNotBlank() && msg.roomId.trim() == hubId
                val raidId = resolveOverlayRaidRoomId() ?: trustedOverlayRaidRoomId
                val normalized = normalizeStripRaidMessage(msg, raidId)
                val isRaid = shouldIngestForRaidStrip(normalized)
                if (!isRaid && !isHub) {
                    if (!activityChatViewModelHandlesUnread()) {
                        resolveChatViewModel()?.recordRealtimeUnreadHint(msg)
                    }
                    return@post
                }
                val selfId = jwtSubFromAccessToken()?.trim().orEmpty()
                val isSelf = selfId.isNotEmpty() && msg.senderId.trim() == selfId
                if (isRaid) {
                    val allowIngest = if (isSelf) {
                        OverlayRaidStripIngestPolicy.shouldIngestOutbound(isOverlayRaidStripEligible())
                    } else {
                        shouldIngestInboundRaidStrip()
                    }
                    if (allowIngest) {
                        ingestOverlayRaidMessage(
                            normalized,
                            refreshNow = true,
                            inbound = !isSelf,
                        )
                        if (!isSelf) {
                            revealStripForInboundRaidIfNeeded()
                        }
                        ensureOverlayMessageStripIfNeeded()
                    } else if (BuildConfig.DEBUG) {
                        Log.d(
                            OVERLAY_DIAG_TAG,
                            "stripDrop reason=not_eligible id=${normalized._id} self=$isSelf " +
                                "ingamePresence=$overlayIngamePresenceActive",
                        )
                    }
                    if (!isSelf && !activityChatViewModelHandlesUnread()) {
                        resolveChatViewModel()?.recordRealtimeUnreadHint(msg)
                    }
                } else if (isHub) {
                    handleOverlayHubMessage(msg)
                }
            }
        }
        val deleteListener: (com.lastasylum.alliance.data.chat.ChatMessageDeletedEvent) -> Unit =
            deleteListener@{ event ->
                mainHandler.post {
                    if (!isOverlayChatRoutingActive()) return@post
                    handleOverlayMessageDeleted(event)
                }
            }
        overlayMessageListener = listener
        overlayMessageDeletedListener = deleteListener
        registerOverlayChatListenersOnMain(
            listener,
            deleteListener,
            readListener,
            typingListener,
            roomUnreadListener,
            reactionListener,
        )
        serviceScope.launch {
            val container = AppContainer.from(this@CombatOverlayService)
            val cachedRooms = com.lastasylum.alliance.data.chat.ChatSessionCache.getFreshRooms()
            if (cachedRooms != null) {
                container.chatRepository.applyOverlayRoomsFromRooms(cachedRooms)
                cachedAllianceHubRoomId = container.chatRepository.hubRoomIdFromPrefs()
                    ?: OverlayGameStatusHudRefresh.allianceHubRoom(cachedRooms)?.id
            } else {
                container.chatRepository.listRooms().getOrNull()?.let { list ->
                    com.lastasylum.alliance.data.chat.ChatSessionCache.update(list)
                    container.chatRepository.applyOverlayRoomsFromRooms(list)
                    cachedAllianceHubRoomId = container.chatRepository.hubRoomIdFromPrefs()
                        ?: OverlayGameStatusHudRefresh.allianceHubRoom(list)?.id
                    mainHandler.post { syncOverlayRaidRoomSubscription() }
                }
            }
            var raidId = container.chatRepository.ensureRaidRoomId()
            if (raidId == null) {
                kotlinx.coroutines.delay(120)
                container.chatRepository.listRooms().getOrNull()?.let { list ->
                    com.lastasylum.alliance.data.chat.ChatSessionCache.update(list)
                    container.chatRepository.applyOverlayRoomsFromRooms(list)
                    cachedAllianceHubRoomId = container.chatRepository.hubRoomIdFromPrefs()
                        ?: OverlayGameStatusHudRefresh.allianceHubRoom(list)?.id
                    mainHandler.post { syncOverlayRaidRoomSubscription() }
                }
                raidId = container.chatRepository.ensureRaidRoomId()
            }
            raidId?.let {
                rememberOverlayRaidRoomId(it)
                mainHandler.post { syncOverlayRaidRoomSubscription() }
            }
            if (raidId == null) {
                mainHandler.post {
                    setStripPlainMessage(
                        getString(R.string.overlay_strip_no_raid),
                        OverlayStripNoticeIds.NO_RAID,
                    )
                }
            }
        }
    }

    private fun endOverlayChatSubscription() {
        mainHandler.removeCallbacks(stripZOrderLiftRunnable)
        stripZOrderLiftPosted = false
        stopOverlayVoice()
        unregisterVoiceMicPermissionReceiver()
        cancelStripTick()
        updateStripDismissScreenRects(emptyList())
        if (overlayChatTeamPanelVisible || OverlayChatInteractionHold.isFullscreenChatTeamPanelVisible) {
            hideOverlayChatTeamPanel()
        }
        stripBuffer.clear()
        lastStripRenderSignature = 0
        overlayMessageListener?.let { listener ->
            runCatching {
                AppContainer.from(applicationContext).chatRepository.removeOverlayMessageListener(listener)
            }
        }
        overlayMessageListener = null
        overlayMessageDeletedListener?.let { listener ->
            runCatching {
                AppContainer.from(applicationContext).chatRepository.removeOverlayMessageDeletedListener(listener)
            }
        }
        overlayMessageDeletedListener = null
        overlayReadListener?.let { listener ->
            runCatching {
                AppContainer.from(applicationContext).chatRepository.removeOverlayReadListener(listener)
            }
        }
        overlayReadListener = null
        overlayTypingListener?.let { listener ->
            runCatching {
                AppContainer.from(applicationContext).chatRepository.removeOverlayTypingListener(listener)
            }
        }
        overlayTypingListener = null
        overlayRoomUnreadListener?.let { listener ->
            runCatching {
                AppContainer.from(applicationContext).chatRepository.removeOverlayRoomUnreadListener(listener)
            }
        }
        overlayRoomUnreadListener = null
        overlayReactionListener?.let { listener ->
            runCatching {
                AppContainer.from(applicationContext).chatRepository.removeOverlayReactionListener(listener)
            }
        }
        overlayReactionListener = null
        overlayChatHistoryClearedListener?.let { listener ->
            runCatching {
                AppContainer.from(applicationContext).chatRepository.removeOverlayChatHistoryClearedListener(listener)
            }
        }
        overlayChatHistoryClearedListener = null
    }

    /**
     * HUD может быть на экране без ленты (ранний attach только угловых окон).
     * Лента обязательна для карточек «Рейд» / быстрых команд.
     */
    private fun ensureOverlayMessageStripIfNeeded() {
        if (!isOverlayChatStripEnabled()) return
        val manager = windowManager ?: systemWindowManager() ?: return
        retainWindowManager(manager)
        overlaySessionActive = true
        if (chatStripHost?.isAttachedToWindow != true) {
            prefetchOverlayRaidRoomForStrip()
            resolveCachedAllianceHubRoomId()
            runCatching { ensureChatStripWindow(manager) }
            if (chatStripHost == null) {
                Log.e(TAG, "ensureOverlayMessageStripIfNeeded: chat strip attach failed")
            } else {
                Log.i(TAG, "ensureOverlayMessageStripIfNeeded: chat strip attached")
            }
        }
        applyOverlayStripVisibility(rebalanceZOrder = false)
        _overlayVisible.value = isOverlayShellActive()
    }

    /** Лента чата и подписки; FAB-панель убрана — управление из угловых HUD. */
    private fun showOverlayShell() {
        repairDetachedOverlayShellIfNeeded()
        repairDetachedOverlayChatTeamPanelIfNeeded()
        val manager = windowManager ?: systemWindowManager()
            ?: getSystemService(Context.WINDOW_SERVICE) as WindowManager
        retainWindowManager(manager)
        overlaySessionActive = true
        if (isOverlayShellActive()) {
            ensureOverlayMessageStripIfNeeded()
            mainHandler.post { ensureOverlayRaidRealtimeIfNeeded() }
            return
        }
        prefetchOverlayRaidRoomForStrip()
        resolveCachedAllianceHubRoomId()
        if (isOverlayChatStripEnabled()) {
            runCatching { ensureChatStripWindow(manager) }
            if (chatStripHost == null) {
                Log.e(TAG, "showOverlayShell: failed to attach chat strip; continuing chat subscription")
            } else {
                applyOverlayStripVisibility()
                scheduleStripZOrderLift()
            }
        }
        _overlayVisible.value = true
        rebalanceOverlayFullscreenZOrder()
        mainHandler.post { ensureOverlayRaidRealtimeIfNeeded() }
    }

    private fun applyOverlayStripVisibility(rebalanceZOrder: Boolean = false) {
        (windowManager ?: systemWindowManager())?.let { wm ->
            runCatching { ensureChatStripWindow(wm) }
        }
        if (isOverlayChatStripEnabled()) {
            val showStrip = shouldForceShowRaidStrip() || isOverlayRaidStripEligible()
            chatStripHost?.visibility = if (showStrip) View.VISIBLE else View.GONE
        }
        if (rebalanceZOrder) {
            rebalanceOverlayFullscreenZOrder()
        }
    }
    private fun hideOverlayChatTeamPanel(clearStrip: Boolean = false) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { hideOverlayChatTeamPanel(clearStrip) }
            return
        }
        if (OverlayChatInteractionHold.isOverlaySystemPickerSessionActive()) {
            deferredHideOverlayChatTeamPanel = true
            deferredHideOverlayClearStrip = clearStrip
            return
        }
        hideOverlayChatTeamPanelNow(clearStrip)
    }

    private fun hideOverlayChatTeamPanelNow(clearStrip: Boolean = false) {
        val root = overlayChatTeamRoot
        val hadVisible = overlayChatTeamPanelVisible
        overlayChatTeamPanelVisible = false
        syncOverlayChatPanelVisibilityToViewModel(false)
        currentOverlayHudPane = null
        OverlayChatInteractionHold.isFullscreenChatTeamPanelVisible = false
        OverlayChatInteractionHold.releaseGameForegroundSuppress()
        resumeOverlayWindowsAfterSystemActivity(skipFullscreenRebalance = true)
        if (hadVisible) {
            chatStripZOrderLifted = false
            updateStripDismissScreenRects(emptyList())
            syncChatStripWindowTouchPassthrough()
            if (clearStrip) {
                stripBuffer.clear()
                lastStripRenderSignature = 0
            }
        }
        overlayChatTeamRoot?.let { hideOverlayIme(it) }
        val manager = windowManager ?: systemWindowManager()
        if (root != null && manager != null) {
            runCatching { manager.removeView(root) }
        }
        overlayChatTeamRoot = null
        overlayChatTeamParams = null
        pendingOverlayPickedImageUris = null
        deferredHideOverlayChatTeamPanel = false
        finalizeOverlayChatSessionAfterClose()
        runCatching { unregisterReceiver(overlaySystemResultReceiver) }
        overlayChatTeamComposeOwner?.destroy()
        overlayChatTeamComposeOwner = null
        ensureOverlayStatusHudWindow()
        ensureOverlayTopRightHudWindow()
        restoreOverlayHudChromeAfterPanel()
        extendOverlayUiHold(OVERLAY_UI_HOLD_PANEL_TRANSITION_MS)
        lastStripRenderSignature = 0
        applyOverlayStripVisibility(rebalanceZOrder = false)
        refreshOverlayChatStripNow()
        mainHandler.removeCallbacks(overlayCloseHudRefreshRunnable)
        mainHandler.postDelayed(overlayCloseHudRefreshRunnable, OVERLAY_CLOSE_HUD_REFRESH_DELAY_MS)
    }

    private fun hideOverlayIme(view: View) {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager ?: return
        imm.hideSoftInputFromWindow(view.windowToken, 0)
    }

    /**
     * У нескольких [TYPE_APPLICATION_OVERLAY] окон порядок «кто выше» на OEM может не совпадать с порядком
     * addView; remove+add поднимает окно чата поверх ленты и тикера.
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

    private fun scheduleStripZOrderLift() {
        if (chatStripZOrderLifted) return
        if (stripZOrderLiftPosted) return
        stripZOrderLiftPosted = true
        mainHandler.removeCallbacks(stripZOrderLiftRunnable)
        mainHandler.post(stripZOrderLiftRunnable)
    }

    /** Поднять ленту поверх остальных окон оверлея (дорого: remove/add; не на каждое сообщение). */
    private fun requestChatStripZOrderLift() {
        if (chatStripZOrderLifted) return
        val now = System.currentTimeMillis()
        if (now - lastStripZOrderLiftMs < STRIP_ZORDER_MIN_INTERVAL_MS) return
        val host = chatStripHost ?: return
        val mgr = windowManager ?: systemWindowManager() ?: return
        val p = chatStripParams ?: return
        if (!host.isAttachedToWindow) return
        lastStripZOrderLiftMs = now
        chatStripZOrderLifted = true
        runCatching {
            mgr.removeView(host)
            mgr.addView(host, p)
        }.onFailure { e ->
            chatStripZOrderLifted = false
            Log.w(TAG, "requestChatStripZOrderLift failed", e)
        }
    }

    private fun rebalanceOverlayChatStripZOrder() = requestChatStripZOrderLift()

    private fun resolveChatViewModel(): ChatViewModel? = Companion.resolveChatViewModel()

    private fun activeOverlayChatSessionViewModel(): ChatViewModel? =
        resolveChatViewModel()

    private fun ensureOverlayFallbackChatViewModel(
        userId: String,
        userRole: String,
    ): ChatViewModel {
        overlayFallbackChatViewModel?.let { return it }
        resolveChatViewModel()?.let { return it }
        val container = AppContainer.from(this)
        ReadCursorSession.bind(
            container.chatRoomPreferences,
            container.teamForumPreferences,
            container.userSettingsPreferences,
            userId,
        )
        return ChatViewModel(
            application = application,
            repository = container.chatRepository,
            chatRoomPreferences = container.chatRoomPreferences,
            usersRepository = container.usersRepository,
            launchDiskCache = container.launchDiskCache,
            currentUserId = userId,
            currentUserRole = userRole,
        ).also { vm ->
            overlayFallbackChatViewModel = vm
            vm.primeFromLaunchDisk()
            vm.primeOverlayChatFromCache(preferAllianceHubRoom = true)
        }
    }

    private fun finalizeOverlayChatSessionAfterClose() {
        val vm = resolveChatViewModel()
        val clearFallback = vm != null && vm === overlayFallbackChatViewModel
        serviceScope.launch {
            vm?.awaitPendingMarkRead()
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                vm?.syncReadStateFromPreferences()
                vm?.syncRoomsFromServer(reconfirmVisibleRoom = false)
                runCatching {
                    AppContainer.from(this@CombatOverlayService).chatRepository
                        .notifyOverlayChatPanelClosed()
                }
                if (clearFallback) {
                    overlayFallbackChatViewModel = null
                }
            }
        }
    }

    private fun refreshOverlayChatSession() {
        activeOverlayChatSessionViewModel()?.refreshChatForOverlay()
    }

    /** Кэш ленты до attach Compose — без кадра «Пока нет сообщений…». */
    private fun prepareOverlayChatBeforePanelShow() {
        val uid = jwtSubFromAccessToken()?.trim().orEmpty()
        if (uid.isBlank()) return
        val container = AppContainer.from(this)
        ReadCursorSession.bind(
            container.chatRoomPreferences,
            container.teamForumPreferences,
            container.userSettingsPreferences,
            uid,
        )
        val vm = resolveChatViewModel()
            ?: ensureOverlayFallbackChatViewModel(uid, jwtRoleFromAccessToken())
        vm.primeOverlayChatFromCache(preferAllianceHubRoom = true)
        if (!vm.overlayHubReadyForPanel()) {
            vm.primeFromLaunchDisk()
            vm.primeOverlayChatFromCache(preferAllianceHubRoom = true)
        }
    }

    private fun showOverlayChatTeamPanel(
        initialTabIndex: Int = 0,
        hudPane: OverlayHudPane? = null,
    ) {
        mainHandler.post { flushPendingOverlayPickedImages() }
        if (overlayChatTeamPanelVisible) return
        val initialTab = initialTabIndex.coerceIn(0, 1)
        val overlayPane = hudPane
        if (overlayPane == null || overlayPane == OverlayHudPane.Chat) {
            prepareOverlayChatBeforePanelShow()
        }
        currentOverlayHudPane = hudPane
        overlayCommandsPopover.hide()
        extendOverlayUiHold(OVERLAY_UI_HOLD_PANEL_TRANSITION_MS)
        OverlayChatInteractionHold.acquireGameForegroundSuppress()
        OverlayChatInteractionHold.isFullscreenChatTeamPanelVisible = true
        val manager = windowManager ?: run {
            showOverlayShell()
            windowManager
        }
        if (manager == null) {
            OverlayChatInteractionHold.isFullscreenChatTeamPanelVisible = false
            OverlayChatInteractionHold.releaseGameForegroundSuppress()
            return
        }
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
                    addAction(OverlaySystemDialogActivity.ACTION_OVERLAY_GALLERY_PERMISSION_RESULT)
                    addAction(OverlaySystemDialogActivity.ACTION_OVERLAY_ACTIVITY_CANCELED)
                },
                ContextCompat.RECEIVER_NOT_EXPORTED,
            )
        }
        val compose = ComposeView(this).apply {
            setContent {
                val container = remember { AppContainer.from(this@CombatOverlayService) }
                val userId = remember { jwtSubFromAccessToken().orEmpty() }
                val userRole = remember { jwtRoleFromAccessToken() }
                val needsChatVm = overlayPane == null || overlayPane == OverlayHudPane.Chat

                CompositionLocalProvider(
                    LocalActivityResultRegistryOwner provides owner,
                    LocalOnBackPressedDispatcherOwner provides owner,
                    LocalLifecycleOwner provides owner,
                    LocalSavedStateRegistryOwner provides owner,
                    LocalOverlayUiMode provides true,
                ) {
                    SquadRelayTheme {
                        if (!needsChatVm && overlayPane != null) {
                            BackHandler { hideOverlayChatTeamPanel() }
                            Surface(
                                modifier = Modifier.fillMaxSize(),
                                color = com.lastasylum.alliance.ui.theme.premium.PremiumSurfaces.layer1(),
                            ) {
                                Column(Modifier.fillMaxSize()) {
                                    when (overlayPane) {
                                        OverlayHudPane.News -> {
                                            OverlayHudPanelHeader(
                                                title = stringResource(R.string.team_tab_news),
                                                onClose = { hideOverlayChatTeamPanel() },
                                            )
                                        }
                                        OverlayHudPane.Forum -> {
                                            OverlayHudPanelHeader(
                                                title = stringResource(R.string.team_tab_forum),
                                                onClose = { hideOverlayChatTeamPanel() },
                                            )
                                        }
                                        OverlayHudPane.Participants -> Unit
                                        else -> Unit
                                    }
                                    Box(
                                        Modifier
                                            .fillMaxWidth()
                                            .weight(1f),
                                    ) {
                                        when (overlayPane) {
                                            OverlayHudPane.News -> {
                                                OverlayTeamNewsPanel(
                                                    currentUserId = userId,
                                                    teamsRepository = container.teamsRepository,
                                                    onNewsInboxChanged = {
                                                        OverlayGameStatusHudRefresh.invalidateNewsCache()
                                                        refreshOverlayNewsBadgeOnly()
                                                    },
                                                )
                                            }
                                            OverlayHudPane.Forum -> {
                                                OverlayTeamForumPanel(
                                                    currentUserId = userId,
                                                    teamsRepository = container.teamsRepository,
                                                    onForumInboxChanged = {
                                                        OverlayGameStatusHudRefresh.invalidateForumCache()
                                                        refreshOverlayForumBadgeOnly()
                                                    },
                                                )
                                            }
                                            OverlayHudPane.Participants -> {
                                                OverlayTeamOnlinePanel(
                                                    teamsRepository = container.teamsRepository,
                                                    usersRepository = container.usersRepository,
                                                    teamPresenceSocket = container.teamPresenceSocket,
                                                    tokenProvider = { container.tokenStore.getAccessToken() },
                                                    openJoinInboxInitially = pendingOpenJoinInboxOnParticipants,
                                                    onOpenJoinInboxConsumed = {
                                                        pendingOpenJoinInboxOnParticipants = false
                                                    },
                                                    onClose = { hideOverlayChatTeamPanel() },
                                                    onIngameCountChanged = { count ->
                                                        overlayTopRightHudFlow.value =
                                                            overlayTopRightHudFlow.value.copy(
                                                                onlineIngameCount = count,
                                                            )
                                                    },
                                                    onSendReactionToUser = { userId ->
                                                        val wm = windowManager ?: systemWindowManager()
                                                        if (wm != null) {
                                                            overlayCommandsPopover.openReactionsPreselectUser(
                                                                wm,
                                                                userId,
                                                            )
                                                        }
                                                    },
                                                    onHudRefresh = {
                                                        OverlayGameStatusHudRefresh.invalidateNewsForumCache()
                                                        refreshOverlayStatusHudData(force = true)
                                                    },
                                                )
                                            }
                                            else -> Unit
                                        }
                                    }
                                }
                            }
                            return@SquadRelayTheme
                        }

                        var selectedTab by remember(initialTab) { mutableIntStateOf(initialTab) }
                        LaunchedEffect(selectedTab) {
                            overlayChatTeamTabIndex = selectedTab
                        }
                        var vm by remember(userId, userRole) {
                            mutableStateOf(
                                run {
                                    if (userId.isNotBlank()) {
                                        ReadCursorSession.bind(
                                            container.chatRoomPreferences,
                                            container.teamForumPreferences,
                                            container.userSettingsPreferences,
                                            userId,
                                        )
                                    }
                                    resolveChatViewModel()
                                        ?: userId.takeIf { it.isNotBlank() }?.let { uid ->
                                            ensureOverlayFallbackChatViewModel(uid, userRole)
                                        }
                                },
                            )
                        }
                        LaunchedEffect(vm) {
                            vm?.refreshChatForOverlay()
                            flushPendingOverlayPickedImages()
                        }
                        val chatVm = vm
                        if (userId.isBlank()) {
                            Surface(
                                modifier = Modifier.fillMaxSize(),
                                color = com.lastasylum.alliance.ui.theme.premium.PremiumSurfaces.layer1(),
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(24.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        text = getString(R.string.overlay_chat_session_unavailable),
                                        style = MaterialTheme.typography.bodyMedium,
                                    )
                                }
                            }
                            return@SquadRelayTheme
                        }
                        if (chatVm == null) {
                            Surface(
                                modifier = Modifier.fillMaxSize(),
                                color = com.lastasylum.alliance.ui.theme.premium.PremiumSurfaces.layer1(),
                            ) {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    CircularProgressIndicator()
                                }
                            }
                            return@SquadRelayTheme
                        }
                        val listPane by chatVm.listPaneState.collectAsStateWithLifecycle(owner)
                        val chromePane by chatVm.chromePaneState.collectAsStateWithLifecycle(owner)
                        val composerPane by chatVm.composerPaneState.collectAsStateWithLifecycle(owner)
                        val draftMessage by chatVm.draftMessage.collectAsStateWithLifecycle(owner)
                        val pickedImageUris by chatVm.pickedImageUris.collectAsStateWithLifecycle(owner)
                        val typingPeers by chatVm.typingPeers.collectAsStateWithLifecycle(owner)
                        val otherReadUptoMessageId by chatVm.otherReadUptoMessageId.collectAsStateWithLifecycle(owner)

                        val blockPanelBack = OverlayChatInteractionHold.blocksFullscreenPanelBack() ||
                            chromePane.activeActionMessageId != null ||
                            chromePane.confirmDeleteMessageId != null ||
                            chromePane.confirmBulkDelete
                        BackHandler(enabled = !blockPanelBack) {
                            hideOverlayChatTeamPanel()
                        }
                        Surface(
                            modifier = Modifier.fillMaxSize(),
                            color = com.lastasylum.alliance.ui.theme.premium.PremiumSurfaces.layer1(),
                        ) {
                            Column(Modifier.fillMaxSize()) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 2.dp, end = 4.dp),
                                    horizontalArrangement = Arrangement.End,
                                ) {
                                    IconButton(onClick = { hideOverlayChatTeamPanel() }) {
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
                                    when (overlayPane) {
                                        OverlayHudPane.Chat -> OverlayHudChatPane(
                                            listPane = listPane,
                                            chromePane = chromePane,
                                            composerPane = composerPane,
                                            typingPeers = typingPeers,
                                            draftMessage = draftMessage,
                                            pickedImageUris = pickedImageUris,
                                            otherReadUptoMessageId = otherReadUptoMessageId,
                                            vm = chatVm,
                                        )
                                        null -> when (selectedTab) {
                                            0 -> OverlayHudChatPane(
                                                listPane = listPane,
                                                chromePane = chromePane,
                                                composerPane = composerPane,
                                                typingPeers = typingPeers,
                                                draftMessage = draftMessage,
                                                pickedImageUris = pickedImageUris,
                                                otherReadUptoMessageId = otherReadUptoMessageId,
                                                vm = chatVm,
                                            )
                                            1 -> {
                                                TeamScreen(
                                                    currentUserId = userId,
                                                    teamsRepository = container.teamsRepository,
                                                    usersRepository = container.usersRepository,
                                                    initialMainSection = TeamMainSection.News,
                                                )
                                            }
                                        }
                                        else -> Unit
                                    }
                                }
                                val overlayChatModalOpen = chromePane.activeActionMessageId != null ||
                                    chromePane.confirmDeleteMessageId != null ||
                                    chromePane.confirmBulkDelete
                                if (overlayPane == null && !overlayChatModalOpen) {
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
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            type,
            OverlayWindowLayout.historyPanelWindowFlags(),
            PixelFormat.OPAQUE,
        ).apply {
            OverlayWindowLayout.applyFullscreenOverlayWindow(this@CombatOverlayService, this)
            OverlayWindowLayout.applyOverlayFullscreenChatSoftInputMode(this)
        }

        val surfaceArgb = overlayChatTeamSurfaceColor()
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
                val imeTypes = WindowInsetsCompat.Type.ime()
                val safe = windowInsets.getInsets(safeTypes)
                val ime = windowInsets.getInsets(imeTypes)
                // Подъём всего Compose-дерева над клавиатурой; композер — только +8dp.
                val bottom = if (ime.bottom > 0) ime.bottom else safe.bottom
                view.setPadding(safe.left, 0, safe.right, bottom)
                WindowInsetsCompat.Builder(windowInsets)
                    .setInsets(safeTypes, Insets.of(safe.left, 0, safe.right, 0))
                    .setInsets(imeTypes, Insets.NONE)
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
            OverlayChatInteractionHold.releaseGameForegroundSuppress()
            OverlayChatInteractionHold.clearSuppressUnlessFullscreenPanel()
            restoreOverlayHudChromeAfterPanel()
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

        overlayStatusHudHost?.visibility = View.GONE
        overlayTopRightHudHost?.visibility = View.GONE

        overlayChatTeamRoot = root
        overlayChatTeamParams = params
        overlayChatTeamPanelVisible = true
        syncOverlayChatPanelVisibilityToViewModel(true)
        OverlayChatInteractionHold.isFullscreenChatTeamPanelVisible = true
        rebalanceOverlayFullscreenZOrder()
        if (isOverlayRaidStripEligible()) {
            ensureOverlayMessageStripIfNeeded()
            applyOverlayStripVisibility(rebalanceZOrder = false)
            chatStripZOrderLifted = false
            requestChatStripZOrderLift()
        }
        ViewCompat.requestApplyInsets(root)
    }
    private fun dp(value: Int): Int {
        val density = resources.displayMetrics.density
        return (value * density).toInt()
    }

    private fun ensureVoiceSession(): VoiceChatSession {
        voiceSession?.let { return it }
        val container = AppContainer.from(this)
        container.voiceSocket.setJoinFailedListener {
            mainHandler.post {
                Toast.makeText(
                    this@CombatOverlayService,
                    R.string.overlay_voice_join_failed,
                    Toast.LENGTH_SHORT,
                ).show()
                refreshOverlayTopRightHudState()
            }
        }
        return container.newVoiceChatSession(
            onStateChanged = { micOn, soundOn ->
                val cur = overlayTopRightHudFlow.value
                overlayTopRightHudFlow.value = cur.copy(
                    micOn = micOn,
                    soundOn = soundOn,
                )
            },
            onMicForegroundChanged = { micActive ->
                updateVoiceForegroundService(micActive)
            },
            onActiveSpeakersChanged = { _ -> },
        ).also {
            voiceSession = it
            container.overlayVoiceSession = it
        }
    }

    private fun startOverlayTeamVoiceIfAvailable(onReady: (VoiceChatSession) -> Unit) {
        if (!canUseOverlayVoiceNow()) return
        val userId = jwtSubFromAccessToken().orEmpty()
        if (userId.isBlank()) return
        val session = ensureVoiceSession()
        session.start(com.lastasylum.alliance.data.chat.ChatTeamVoiceRoom.SOCKET_ROOM_ID, userId)
        onReady(session)
    }

    private fun stopOverlayVoice() {
        AppContainer.from(this).voiceSocket.setJoinFailedListener(null)
        voiceSession?.stop()
        voiceSession = null
        AppContainer.from(this).overlayVoiceSession = null
        overlayTopRightHudFlow.value = overlayTopRightHudFlow.value.copy(
            voiceExpanded = false,
            voiceSettingsVisible = false,
        )
        refreshOverlayTopRightHudState()
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
        promoteOverlayForeground(notification, types)
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
            commandsPopoverShowing = overlayCommandsPopover.isBlockingGameGateDismiss(),
        )
        resumeOverlayWindowsAfterSystemActivity()
        overlayVoiceController.resetSession()
        val endRealtime = force || !shouldRetainOverlayRaidRealtime()
        if (endRealtime) {
            endOverlayChatSubscription()
        } else {
            lastStripRenderSignature = 0
            mainHandler.post { refreshOverlayChatStripNow() }
        }
        overlayTicker.hideTicker()
        overlayCommandsPopover.hide()
        removeOverlayStatusHudWindow()
        removeOverlayTopRightHudWindow()
        val wm = windowManager ?: systemWindowManager()
        removeChatStripWindow(wm)
        _overlayVisible.value = false
        overlaySessionActive = false
        cachedAllianceHubRoomId = null
        mainHandler.removeCallbacks(stripZOrderLiftRunnable)
        stripZOrderLiftPosted = false
        chatStripZOrderLifted = false
        cancelOverlayHudRefreshWork()
        chatStripClipRoot = null
        windowManager = null
    }

    companion object {
        @Volatile
        private var runningInstance: CombatOverlayService? = null

        @Volatile
        var activityScopedChatViewModel: ChatViewModel? = null

        fun bindActivityChatViewModel(viewModel: ChatViewModel?) {
            activityScopedChatViewModel = viewModel
        }

        /** Activity VM, then app registry, then overlay fallback when game runs without activity UI. */
        fun resolveChatViewModel(): ChatViewModel? =
            activityScopedChatViewModel
                ?: ChatViewModelRegistry.shared
                ?: runningInstance?.overlayFallbackChatViewModel

        /** True when overlay team panel is on the Chat tab (not Team news/forum). */
        fun isOverlayChatTabActive(): Boolean = runningInstance?.overlayChatTeamTabIndex == 0

        /** Reset hub mail chip optimistic floor after admin chat wipe. */
        fun clearHubUnreadState() {
            val service = runningInstance ?: return
            service.mainHandler.post {
                service.clearHubUnreadOptimisticState()
                service.applyAllianceHubUnreadCount(0)
            }
        }

        /** Sync alliance hub unread badge on overlay mail chip (room «Альянс» only). */
        fun notifyAllianceHubUnread(count: Int) {
            syncHubBadgeFromSharedReadState(count)
        }

        /**
         * Push hub badge from app read cursors / merged room list.
         * When [authoritativeEffective] is null, overlay refetches listRooms + prefs (game entry).
         */
        fun syncHubBadgeFromSharedReadState(authoritativeEffective: Int? = null) {
            val service = runningInstance ?: return
            service.mainHandler.post {
                if (authoritativeEffective != null) {
                    val clamped = authoritativeEffective.coerceIn(0, 999)
                    if (clamped > 0) {
                        service.pushAllianceHubUnreadFromApp(clamped)
                    } else {
                        service.clearHubUnreadOptimisticState()
                        service.syncOverlayHubBadgeFromAppReadState()
                    }
                    return@post
                }
                service.syncOverlayHubBadgeFromAppReadState()
            }
        }

        /** Optimistic +1 on hub mail chip when realtime arrives before listRooms (no activity VM). */
        fun bumpAllianceHubUnreadFromRealtime(messageId: String?) {
            val service = runningInstance ?: return
            service.mainHandler.post {
                service.bumpAllianceHubUnreadLocally(messageId)
            }
        }

        /** Удержать HUD/ленту на экране после отправки в «Рейд» / быстрых команд (IME, game gate). */
        fun extendInGameOverlayUiHold(durationMs: Long = OVERLAY_UI_HOLD_AFTER_RAID_SEND_MS) {
            val service = runningInstance ?: return
            service.mainHandler.post { service.extendOverlayUiHold(durationMs) }
        }

        /**
         * Системный Share / внешний текст с координатами → канал «Рейд».
         * @return false если текст не распознан или нет сессии/оверлея.
         */
        fun shareMapCoordinatesFromExternal(context: Context, rawText: String): Boolean {
            val app = context.applicationContext
            val trimmed = rawText.trim()
            if (trimmed.isEmpty()) return false
            if (com.lastasylum.alliance.game.MapCoordinateParser.parseSharedText(trimmed) == null) {
                return false
            }
            if (!AppContainer.from(app).authRepository.hasSession()) return false
            ensureRuntimeIfUserEnabled(app, showErrorToast = false)
            val intent = Intent(app, CombatOverlayService::class.java).apply {
                action = ACTION_SHARE_MAP_COORD
                putExtra(EXTRA_SHARE_TEXT, trimmed)
            }
            runCatching { app.startService(intent) }
            return true
        }

        /** HTTP/VM path: карточка в ленте «Рейд» сразу (в т.ч. при открытой панели чата). */
        fun publishRaidMessageToStripFromApp(message: ChatMessage) {
            val service = runningInstance ?: return
            service.mainHandler.post {
                service.applyLocalSentMessageToStrip(message)
            }
        }

        /** Main app read news/forum — refresh overlay HUD chips (mirrors hub badge sync). */
        fun notifyOverlayTeamInboxChanged(news: Boolean = false, forum: Boolean = false) {
            val service = runningInstance ?: return
            service.mainHandler.post {
                if (news) {
                    OverlayGameStatusHudRefresh.invalidateNewsCache()
                    service.inboxBadgeCoordinator.clearNewsOptimistic()
                    service.refreshOverlayNewsBadgeOnly()
                }
                if (forum) {
                    OverlayGameStatusHudRefresh.invalidateForumCache()
                    service.inboxBadgeCoordinator.clearForumOptimistic()
                    service.refreshOverlayForumBadgeOnly()
                }
            }
        }

        /** Убрать карточку из ленты оверлея после delete (HTTP или socket message:deleted). */
        fun publishMessageDeletedFromApp(messageId: String, roomId: String = "") {
            val service = runningInstance ?: return
            service.mainHandler.post {
                service.handleOverlayMessageDeleted(
                    com.lastasylum.alliance.data.chat.ChatMessageDeletedEvent(
                        messageId = messageId,
                        roomId = roomId,
                    ),
                )
            }
        }

        /** Main app in foreground — rare gate polls, no UsageStats churn while browsing UI. */
        private const val GAME_GATE_POLL_MAIN_APP_FOREGROUND_MS = 45_000L

        @Volatile
        var mainAppForegroundActive: Boolean = false

        fun setMainAppUiInForeground(inForeground: Boolean) {
            mainAppForegroundActive = inForeground
            val service = runningInstance ?: return
            if (!inForeground) {
                service.mainHandler.post { service.scheduleGameGateTick(service.nextGameGateDelayMs()) }
                return
            }
            if (!service.isInGameOverlayUiActive() && !service.isOverlayShellActive()) {
                service.mainHandler.post {
                    service.scheduleGameGateTick(GAME_GATE_POLL_MAIN_APP_FOREGROUND_MS)
                }
            }
        }

        /** Частый prune ленты относительно TTL ~10 с. */
        private const val STRIP_TICK_MS = 2_500L
        /** Лента на экране — отзывчивый гейт. */
        private const val GAME_GATE_POLL_ACTIVE_MS = 1_200L
        /** Стабильно «в игре» — реже тяжёлых проверок usage stats. */
        private const val GAME_GATE_POLL_STABLE_MS = 3_500L
        /** Меню команд/реакций или полноэкранный оверлей-чат — редкий опрос usage stats. */
        private const val GAME_GATE_POLL_MODAL_UI_MS = 5_000L
        /** Недавно были в игре / открыт чат — чаще, чем в простое. */
        private const val GAME_GATE_POLL_WARM_MS = 1_800L
        /** FGS включён, оверлей скрыт: редкий опрос usage stats. */
        private const val GAME_GATE_POLL_IDLE_MS = 6_000L
        /** Fallback poll for news/forum when no socket activity (realtime is primary). */
        private const val STATUS_HUD_REFRESH_MS = 20_000L
        private const val HUD_PRESENCE_COUNT_REFRESH_MS = 60_000L
        private const val USAGE_ACCESS_CACHE_MS = 30_000L
        private const val GAME_GATE_RECENT_INGAME_WINDOW_MS = 45_000L
        /** Краткий grace при ложном «не в игре» во время чата/пикера; не применяется при явном лаунчере/другом приложении. */
        private const val OVERLAY_INGAME_GRACE_MS = 3_500L
        /** Sustained «not in game» (краткий лаг usage-stats) перед снятием окон. */
        private const val GATE_DISMISS_AFTER_MS = 2_500L
        private const val FORCE_SHOW_STRIP_AFTER_LOCAL_SEND_MS = 4_000L
        /** После отправки в «Рейд» / координат — не снимать HUD на ложном «не в игре». */
        private const val OVERLAY_UI_HOLD_AFTER_RAID_SEND_MS = 3_500L
        private const val OVERLAY_UI_HOLD_PANEL_TRANSITION_MS = 2_500L
        /** Gate ticks without in-game probe before POST away (~4–10 s). */
        private const val OVERLAY_INGAME_AWAY_MISS_STREAK = 3
        private const val OVERLAY_HISTORY_LOAD = 40
        /** 1 тик гейта (~1.2 с) после «в игре» — достаточно, если HUD уже показан при входе. */
        private const val HUD_STABLE_TICKS_BEFORE_ATTACH = 1
        private const val GATE_STABLE_TICKS_FOR_SLOW_POLL = 5
        private const val HUD_REFRESH_MIN_INTERVAL_MS = 2_000L
        private const val HUB_HUD_REFRESH_DEBOUNCE_MS = 500L
        /** After socket/VM bump, ignore stale listRooms that would zero the chip. */
        private const val HUB_UNREAD_RECONCILE_GRACE_MS = 4_000L
        private const val OVERLAY_CLOSE_HUD_REFRESH_DELAY_MS = 80L
        private const val STRIP_ZORDER_MIN_INTERVAL_MS = 30_000L
        private const val STRIP_ZORDER_LIFT_DELAY_MS = 0L
        private val STRIP_INGEST_RETRY_DELAYS_MS = longArrayOf(0L, 120L)
        /** Симметричный отступ HUD от края игрового экрана (левый START / правый END). */
        private const val OVERLAY_HUD_WINDOW_X_DP = OverlayHudLayout.WINDOW_X_DP
        private const val OVERLAY_HUD_LEFT_WINDOW_X_DP = OverlayHudLayout.WINDOW_X_DP
        /** Вертикальный отступ HUD-окон от верхнего края (меньше — выше на экране). */
        private const val OVERLAY_HUD_WINDOW_Y_DP = OverlayHudLayout.WINDOW_Y_DP
        /** Минимум между remove/add HUD — иначе кнопки мигают на каждом тике гейта. */
        private const val HUD_ZORDER_REBALANCE_MIN_MS = 60_000L
        private const val GATE_HIDE_UI_HYSTERESIS_TICKS = 5
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
        const val ACTION_SHARE_MAP_COORD = "com.squadrelay.action.SHARE_MAP_COORD"
        private const val EXTRA_SHARE_TEXT = "share_text"
        private const val EXTRA_ENABLED = "enabled"

        @Volatile
        var isServiceInstanceActive: Boolean = false

        private val _serviceRunning = MutableStateFlow(false)
        private val _overlayVisible = MutableStateFlow(false)
        private val _inGameOverlayUiActive = MutableStateFlow(false)

        /** Для UI вкладки «Оверлей»: синхронно с жизненным циклом сервиса (без гонки с [isServiceInstanceActive]). */
        val serviceRunning: StateFlow<Boolean> = _serviceRunning.asStateFlow()
        /** True when the overlay windows are attached (panel visible on screen). */
        val overlayVisible: StateFlow<Boolean> = _overlayVisible.asStateFlow()
        /** True when game gate shows in-game overlay (HUD/лента) — не путать с [overlayVisible]. */
        val inGameOverlayUiActive: StateFlow<Boolean> = _inGameOverlayUiActive.asStateFlow()

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
         * Поднимает FGS, если пользователь включил панель и есть сессия.
         * Сервис живёт в фоне (в т.ч. после свайпа SquadRelay из recents) и рисует HUD только в игре.
         */
        fun ensureRuntimeIfUserEnabled(
            context: Context,
            showErrorToast: Boolean = true,
        ): Boolean {
            val app = context.applicationContext
            if (!AppContainer.from(app).authRepository.hasSession()) return false
            if (!UserSettingsPreferences(app).isOverlayPanelEnabled()) return false
            if (isServiceInstanceActive) {
                requestGateRecheckIfRunning(app)
                OverlayRuntimeScheduler.syncSchedule(app)
                return true
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
                !Settings.canDrawOverlays(app)
            ) {
                return false
            }
            val started = setEnabled(context, true, showErrorToast = showErrorToast)
            if (started) {
                OverlayRuntimeScheduler.syncSchedule(app)
            } else {
                OverlayRuntimeScheduler.scheduleImmediateRetry(app)
            }
            return started
        }

        /** После смены пакета/activity-фильтра — пересчитать показ оверлея. */
        fun requestGateRecheckIfRunning(context: Context) {
            val app = context.applicationContext
            if (!isServiceInstanceActive) {
                ensureRuntimeIfUserEnabled(app, showErrorToast = false)
                return
            }
            GameForegroundGate.invalidateForegroundHintCache()
            GameForegroundGate.invalidateUsageAccessCache()
            val intent = Intent(app, CombatOverlayService::class.java).apply {
                action = ACTION_TICK_GAME_GATE
            }
            runCatching { app.startService(intent) }
        }

        /**
         * Запуск боевого сервиса и оверлея. Микрофон не обязателен: панель может работать без голоса;
         * запись голоса запросит разрешение при использовании.
         */
        fun setEnabled(
            context: Context,
            enabled: Boolean,
            showErrorToast: Boolean = true,
        ): Boolean {
            val app = context.applicationContext
            // Persist desired state first (single source of truth).
            runCatching { UserSettingsPreferences(app).setOverlayPanelEnabled(enabled) }
            if (!enabled) {
                OverlayRuntimeScheduler.cancel(app)
            }
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
                if (enabled) {
                    OverlayRuntimeScheduler.syncSchedule(app)
                }
                true
            } catch (t: Throwable) {
                val blockedFromBackground = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                    t is ForegroundServiceStartNotAllowedException
                Log.e(
                    TAG,
                    "setEnabled failed enabled=$enabled blockedFromBackground=$blockedFromBackground",
                    t,
                )
                if (enabled) {
                    OverlayRuntimeScheduler.scheduleImmediateRetry(
                        app,
                        delayMs = if (blockedFromBackground) 20_000L else 8_000L,
                    )
                }
                if (showErrorToast) {
                    Toast.makeText(
                        app,
                        app.getString(R.string.overlay_start_service_failed),
                        Toast.LENGTH_LONG,
                    ).show()
                }
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
