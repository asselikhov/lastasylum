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
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.google.android.material.color.MaterialColors
import com.lastasylum.alliance.R
import com.lastasylum.alliance.data.chat.ChatMessage
import com.lastasylum.alliance.data.chat.OverlayReactionEvent
import com.lastasylum.alliance.data.isObjectIdNewer
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
import com.lastasylum.alliance.ui.chat.ChatViewModel
import com.lastasylum.alliance.ui.theme.SquadRelayTheme
import com.lastasylum.alliance.ui.util.OVERLAY_INGAME_PRESENCE_STALE_MS
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
import com.lastasylum.alliance.BuildConfig
import androidx.compose.ui.platform.LocalSavedStateRegistryOwner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.Instant

class CombatOverlayService : Service() {
    private var windowManager: WindowManager? = null
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
            notifyExcavation = {
                val roomId = AppContainer.from(this@CombatOverlayService).chatRoomPreferences.getRaidRoomId()
                    ?: return@OverlayCommandsPopover Result.failure(IllegalStateException("no_raid"))
                val text = getString(R.string.overlay_excavation_notify_message)
                AppContainer.from(this@CombatOverlayService).chatRepository
                    .sendExcavationAlertWithRetries(text, roomId)
            },
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
            mainHandler = mainHandler,
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
     * Пинги идут, пока игрок в целевой игре (в т.ч. без действий и при скрытом HUD),
     * а не пока открыта лента чата.
     */
    private fun shouldMaintainOverlayIngamePresence(inGameProbe: Boolean): Boolean {
        val container = AppContainer.from(this)
        if (!container.userSettingsPreferences.isOverlayPanelEnabled()) return false
        if (!container.authRepository.hasSession()) return false
        if (inGameProbe) return true
        val lastInGame = lastOverlayInGameAtMs
        if (lastInGame <= 0L) return false
        return System.currentTimeMillis() - lastInGame < OVERLAY_INGAME_PRESENCE_STALE_MS
    }

    private fun syncOverlayIngamePresence(inGameProbe: Boolean) {
        if (shouldMaintainOverlayIngamePresence(inGameProbe)) {
            val firstStart = !overlayIngamePresenceActive
            overlayIngamePresenceActive = true
            presenceHeartbeat.start()
            if (firstStart) {
                serviceScope.launch {
                    runCatching {
                        AppContainer.from(this@CombatOverlayService).usersRepository.updatePresence(
                            OVERLAY_PRESENCE_INGAME,
                        )
                    }
                }
            }
            return
        }
        stopOverlayIngamePresence(markAway = true)
    }

    private fun stopOverlayIngamePresence(markAway: Boolean) {
        presenceHeartbeat.stop()
        overlayIngamePresenceActive = false
        if (!markAway) return
        val container = AppContainer.from(this)
        if (!container.authRepository.hasSession()) return
        serviceScope.launch {
            runCatching {
                container.usersRepository.updatePresence(OVERLAY_PRESENCE_AWAY)
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
    private var overlayReactionListener: ((OverlayReactionEvent) -> Unit)? = null
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
    private var overlayChatTeamComposeOwner: OverlayChatComposeOwner? = null
    private var overlayChatViewModel: ChatViewModel? = null
    /** URIs from picker if result arrived while Compose owner was torn down. */
    private var pendingOverlayPickedImageUris: List<Uri>? = null
    private var deferredHideOverlayChatTeamPanel = false
    private var deferredHideOverlayClearStrip = true
    /** Владелец Compose для ленты сообщений (отдельное окно). */
    private var overlayStripComposeOwner: OverlayChatComposeOwner? = null

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
        val vm = overlayChatViewModel
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

    private fun deliverOverlayPickImagesResult(
        requestCode: Int,
        resultCode: Int,
        data: Intent,
        fallbackUris: List<Uri>,
    ) {
        if (resultCode == android.app.Activity.RESULT_OK && fallbackUris.isNotEmpty()) {
            applyOverlayPickedUris(fallbackUris)
        }
        // Best-effort для Compose launcher (может не сработать после restore панели).
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
        fallbackUri: Uri?,
    ) {
        fallbackUri?.let { applyOverlayPickedUris(listOf(it)) }
        overlayChatTeamComposeOwner?.activityResultRegistry?.dispatchResult(
            requestCode,
            resultCode,
            data,
        )
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
        overlayChatViewModel?.onImagesPicked(pending)
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

    private val stripTickRunnable = Runnable {
        if (!isInGameOverlayUiActive() ||
            !isOverlayShellActive() ||
            overlayChatTeamPanelVisible
        ) {
            return@Runnable
        }
        val before = stripBuffer.visibleForPreview().size
        if (before == 0) {
            scheduleStripTick()
            return@Runnable
        }
        stripBuffer.prune()
        if (before != stripBuffer.visibleForPreview().size) {
            lastStripRenderSignature = null
            scheduleRefreshOverlayChatStrip()
        }
        scheduleStripTick()
    }

    private var stripUiCoalescePosted = false
    private val pendingStripSocketMessages = ArrayDeque<ChatMessage>()
    private var stripSocketDrainPosted = false
    private val stripSocketDrainRunnable = Runnable {
        stripSocketDrainPosted = false
        while (pendingStripSocketMessages.isNotEmpty()) {
            processOverlayChatMessage(pendingStripSocketMessages.removeFirst(), refreshStrip = false)
        }
        refreshOverlayChatStrip()
    }
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
    private var lastStripRenderSignature: String? = null
    private var lastAppliedGateShouldShow: Boolean? = null
    private var stableGatePollTicks = 0
    @Volatile
    private var gateCheckInFlight = false
    private var lastGateDiagLogMs: Long = 0L
    private var lastForegroundHintPkg: String? = null
    @Volatile
    private var lastOverlayInGameAtMs: Long = 0L
    /** Heartbeat «ingame» для списка союзников — не привязан к видимости HUD/ленты. */
    private var overlayIngamePresenceActive = false
    /** Не вызывать [NotificationManager.notify] с тем же текстом подряд (лишние всплытия на части OEM). */
    private var lastForegroundNotificationText: String? = null
    private var lastForegroundMicActive: Boolean = false
    /** FGS notification visible while in-game overlay is active (hidden when idle outside game). */
    private var overlayForegroundPromoted: Boolean = false

    private var screenOnReceiverRegistered: Boolean = false

    private val screenOnReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != Intent.ACTION_SCREEN_ON) return
            GameForegroundGate.invalidateForegroundHintCache()
            GameForegroundGate.invalidateUsageAccessCache()
            mainHandler.post { tickGameGate() }
        }
    }

    @Volatile
    private var overlaySessionActive: Boolean = false

    private var cachedAllianceHubRoomId: String? = null

    private var hubHudRefreshPosted: Boolean = false

    private val hubHudRefreshRunnable = Runnable {
        hubHudRefreshPosted = false
        refreshOverlayHubUnreadOnly()
    }

    @Volatile
    private var hudRefreshInFlight: Boolean = false

    @Volatile
    private var hudRefreshPending: Boolean = false

    private var hudRefreshJob: Job? = null

    private var lastHudRefreshCompletedAtMs: Long = 0L

    @Volatile
    private var deferredDismissWhenPickerEnds: Boolean = false

    private val overlayCloseHudRefreshRunnable = Runnable {
        refreshOverlayStatusHudData()
    }

    private var lastStripZOrderLiftMs: Long = 0L

    private var lastHudZOrderRebalanceMs: Long = 0L

    /** Подряд тиков гейта «не показывать UI» — скрываем HUD только после N, чтобы не мигать. */
    private var gateUiHideStreak: Int = 0

    private var stripZOrderLiftPosted: Boolean = false

    private val stripZOrderLiftRunnable = Runnable {
        stripZOrderLiftPosted = false
        requestChatStripZOrderLift()
    }

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
        promoteOverlayForeground(notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        Log.i(TAG, "onCreate: startForeground OK")
        isServiceInstanceActive = true
        _serviceRunning.value = true
        _overlayVisible.value = false
        OverlayRuntimeScheduler.syncSchedule(this)
        GameForegroundGate.invalidateForegroundHintCache()
        GameForegroundGate.invalidateUsageAccessCache()
        val targets = AppContainer.from(this).userSettingsPreferences.getOverlayTargetGamePackages()
        GameForegroundGate.primeTotalTimeForegroundWatch(this, targets)
        registerScreenOnReceiver()
        mainHandler.post { tickGameGate() }
    }

    private fun isOverlayShellActive(): Boolean {
        if (chatStripHost?.isAttachedToWindow == true) return true
        if (overlayStatusHudHost?.isAttachedToWindow == true) return true
        if (overlayTopRightHudHost?.isAttachedToWindow == true) return true
        if (overlayChatTeamRoot?.isAttachedToWindow == true) return true
        return overlaySessionActive &&
            (chatStripHost != null || overlayStatusHudHost != null || overlayTopRightHudHost != null)
    }

    private fun isInGameOverlayUiActive(): Boolean = lastAppliedGateShouldShow == true

    /**
     * Сглаживание ложных «не в игре» между тиками usage-stats: показ сразу, скрытие после
     * [GATE_HIDE_UI_HYSTERESIS_TICKS] подряд false.
     */
    private fun resolveStableOverlayUiVisible(probeShow: Boolean, forceHideNow: Boolean = false): Boolean {
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
        fun update(host: FrameLayout?, params: WindowManager.LayoutParams?, gravity: Int) {
            if (host == null || params == null || !host.isAttachedToWindow) return
            params.gravity = gravity
            params.x = dp(OVERLAY_HUD_WINDOW_X_DP)
            params.y = dp(OVERLAY_HUD_WINDOW_Y_DP)
            runCatching { mgr.updateViewLayout(host, params) }
        }
        update(overlayStatusHudHost, overlayStatusHudParams, Gravity.TOP or Gravity.START)
        update(overlayTopRightHudHost, overlayTopRightHudParams, Gravity.TOP or Gravity.END)
    }

    private fun isOverlayHudOnlyMode(): Boolean =
        AppContainer.from(this).userSettingsPreferences.isOverlayHudOnlyMode()

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
        if (!isOverlayHudOnlyMode()) {
            repairDetachedChatStripIfNeeded(mgr)
        }
        val host = chatStripHost ?: return
        if (host.isAttachedToWindow) return
        val params = chatStripParams
        if (params != null) {
            Log.w(TAG, "repairDetachedOverlayShellIfNeeded: re-attaching chat strip")
            runCatching { mgr.addView(host, params) }
                .onSuccess {
                    rebalanceOverlayHudZOrder(force = chatStripZOrderLifted)
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
        if (!isOverlayShellActive()) {
            val result = runCatching { showOverlayShell() }
            if (result.isFailure) {
                Log.e(TAG, "ensureOverlayIfPermitted: showOverlayShell crashed", result.exceptionOrNull())
                runCatching { removeOverlayControl() }
                _overlayVisible.value = false
                Log.w(TAG, "ensureOverlayIfPermitted: showOverlayShell failed")
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
            syncOverlayIngamePresence(inGameProbe = false)
            if (isOverlayShellActive()) {
                removeOverlayControl()
            }
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
                val hasUsageAccess = GameForegroundGate.hasUsageStatsAccessForOverlay(
                    this@CombatOverlayService,
                )
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
                val forceResumeRefresh = hasUsageAccess && targets.isNotEmpty() && when {
                    quickProbe == GameForegroundGate.QuickForegroundProbe.NEED_FULL_HEURISTICS -> true
                    quickProbe == null -> false
                    !stableInGameUi -> true
                    stableGatePollTicks % 5 == 0 -> true
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
                if (inGame) {
                    lastOverlayInGameAtMs = nowMs
                }
                // UI (HUD/лента) только в игре или пока открыт оверлей-чат/системный пикер — без grace после сворачивания.
                val shouldShowInGameOverlayUi = when {
                    inGame -> true
                    OverlayChatInteractionHold.isOverlaySystemPickerSessionActive() -> true
                    overlayChatTeamPanelVisible ||
                        OverlayChatInteractionHold.isFullscreenChatTeamPanelVisible -> true
                    else -> false
                }
                val stableShowInGameOverlayUi = resolveStableOverlayUiVisible(
                    probeShow = shouldShowInGameOverlayUi,
                    forceHideNow = conflictingForeground,
                )
                mainHandler.post {
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
        if (!isInGameOverlayUiActive()) return GAME_GATE_POLL_IDLE_MS
        if (isOverlayShellActive() &&
            stableGatePollTicks >= GATE_STABLE_TICKS_FOR_SLOW_POLL
        ) {
            return GAME_GATE_POLL_STABLE_MS
        }
        if (isOverlayShellActive()) return GAME_GATE_POLL_ACTIVE_MS
        if (overlayChatTeamPanelVisible || OverlayChatInteractionHold.isFullscreenChatTeamPanelVisible) {
            return GAME_GATE_POLL_ACTIVE_MS
        }
        return GAME_GATE_POLL_IDLE_MS
    }

    private fun scheduleOverlayStatusHudRefresh() {
        if (!isInGameOverlayUiActive()) return
        if (statusHudRefreshPosted) return
        statusHudRefreshPosted = true
        mainHandler.postDelayed(statusHudRefreshRunnable, STATUS_HUD_REFRESH_MS)
    }

    private fun refreshOverlayStatusHudData(force: Boolean = false) {
        if (!isInGameOverlayUiActive()) return
        val now = System.currentTimeMillis()
        if (!force && now - lastHudRefreshCompletedAtMs < HUD_REFRESH_MIN_INTERVAL_MS) {
            hudRefreshPending = true
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
                val rooms = com.lastasylum.alliance.data.chat.ChatSessionCache.getFreshRooms()
                    ?: runCatching { container.chatRepository.listRooms().getOrNull() }.getOrNull()
                rooms?.let { list ->
                    cachedAllianceHubRoomId = OverlayGameStatusHudRefresh.allianceHubRoom(list)?.id
                    com.lastasylum.alliance.data.chat.ChatSessionCache.update(list)
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
                val joinRequestCount = if (force || refreshNewsForum) {
                    runCatching {
                        OverlayGameStatusHudRefresh.loadTeamJoinRequestCount(this@CombatOverlayService)
                    }.getOrDefault(0)
                } else {
                    overlayTopRightHudFlow.value.teamJoinRequestCount
                }
                overlayStatusHudFlow.value = state
                mainHandler.post {
                    if (!isInGameOverlayUiActive()) return@post
                    val durationMs = android.os.SystemClock.elapsedRealtime() - startedAt
                    OverlayPerfDiag.logHudRefreshDone(
                        durationMs = durationMs,
                        allianceUnread = state.allianceChatUnread,
                        forumUnread = state.forumUnread,
                        newsUnread = state.teamNewsUnread,
                    )
                    overlayTopRightHudFlow.value = overlayTopRightHudFlow.value.copy(
                        teamJoinRequestCount = joinRequestCount,
                    )
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
        mainHandler.removeCallbacks(hubHudRefreshRunnable)
        hubHudRefreshPosted = false
        mainHandler.removeCallbacks(overlayCloseHudRefreshRunnable)
    }

    /** Hub chat badge only — one [listRooms], no news/forum/join-requests fan-out. */
    private fun refreshOverlayHubUnreadOnly() {
        if (!isInGameOverlayUiActive()) return
        serviceScope.launch(Dispatchers.IO) {
            val container = AppContainer.from(this@CombatOverlayService)
            val rooms = com.lastasylum.alliance.data.chat.ChatSessionCache.getFreshRooms()
                ?: runCatching { container.chatRepository.listRooms().getOrNull() }.getOrNull()
                ?: return@launch
            cachedAllianceHubRoomId = OverlayGameStatusHudRefresh.allianceHubRoom(rooms)?.id
            com.lastasylum.alliance.data.chat.ChatSessionCache.update(rooms)
            val localRead = container.chatRoomPreferences.loadAllLastReadMessageIds()
            val unread = OverlayGameStatusHudRefresh.allianceHubUnread(rooms, localRead)
            mainHandler.post {
                overlayStatusHudFlow.value = overlayStatusHudFlow.value.copy(
                    allianceChatUnread = unread,
                )
            }
        }
    }

    private fun resolveCachedAllianceHubRoomId() {
        if (!cachedAllianceHubRoomId.isNullOrBlank()) return
        serviceScope.launch(Dispatchers.IO) {
            val rooms = com.lastasylum.alliance.data.chat.ChatSessionCache.getFreshRooms()
                ?: AppContainer.from(this@CombatOverlayService).chatRepository.listRooms().getOrNull()
                ?: return@launch
            cachedAllianceHubRoomId = OverlayGameStatusHudRefresh.allianceHubRoom(rooms)?.id
        }
    }

    private fun scheduleDebouncedHubHudRefresh() {
        hubHudRefreshPosted = true
        mainHandler.removeCallbacks(hubHudRefreshRunnable)
        mainHandler.postDelayed(hubHudRefreshRunnable, HUB_HUD_REFRESH_DEBOUNCE_MS)
    }

    private fun bumpAllianceHubUnreadLocally() {
        val current = overlayStatusHudFlow.value
        overlayStatusHudFlow.value = current.copy(
            allianceChatUnread = (current.allianceChatUnread + 1).coerceAtMost(99),
        )
    }

    private fun shouldBumpHubUnreadForMessage(msg: ChatMessage, hubId: String): Boolean {
        val msgId = msg._id?.trim().orEmpty()
        if (msgId.isBlank()) return true
        val localRead = AppContainer.from(this).chatRoomPreferences.getLastReadMessageId(hubId)
            ?.trim()
            .orEmpty()
        if (localRead.isBlank()) return true
        return isObjectIdNewer(msgId, localRead)
    }

    private fun maybeBumpAllianceHubUnread(msg: ChatMessage, hubId: String) {
        if (!shouldBumpHubUnreadForMessage(msg, hubId)) return
        bumpAllianceHubUnreadLocally()
        scheduleDebouncedHubHudRefresh()
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
        if (overlayChatTeamPanelVisible) return
        ensureOverlayStatusHudWindow()
        ensureOverlayTopRightHudWindow()
        overlayStatusHudHost?.visibility = View.VISIBLE
        overlayTopRightHudHost?.visibility = View.VISIBLE
        syncOverlayHudWindowLayout()
        refreshOverlayTopRightHudState()
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
            if (!inGame) {
                removeOverlayStatusHudWindow()
            }
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
            if (!inGame) {
                removeOverlayTopRightHudWindow()
            }
            return
        }
        attachOverlayHudWindowsIfNeeded()
    }

    private fun refreshOverlayTopRightHudState() {
        val session = voiceSession
        val prefs = AppContainer.from(this).userSettingsPreferences
        val micOn = session?.micOn ?: prefs.isOverlayVoiceMicEnabled()
        val soundOn = session?.soundOn ?: prefs.isOverlayVoiceSoundEnabled()
        val current = overlayTopRightHudFlow.value
        if (current.micOn == micOn && current.soundOn == soundOn) return
        overlayTopRightHudFlow.value = current.copy(
            micOn = micOn,
            soundOn = soundOn,
        )
    }

    private fun toggleOverlayTopRightVoiceExpanded() {
        overlayTopRightHudFlow.value = overlayTopRightHudFlow.value.copy(
            voiceExpanded = !overlayTopRightHudFlow.value.voiceExpanded,
        )
    }

    private fun collapseOverlayTopRightVoice() {
        if (!overlayTopRightHudFlow.value.voiceExpanded) return
        overlayTopRightHudFlow.value = overlayTopRightHudFlow.value.copy(voiceExpanded = false)
    }

    private fun openOverlayQuickCommandsFromHud() {
        val mgr = windowManager ?: systemWindowManager() ?: return
        overlayCommandsPopover.toggle(mgr)
        mainHandler.post { repairDetachedOverlayShellIfNeeded() }
    }

    private fun toggleOverlayVoiceMicFromHud() {
        ensureOverlayVoiceStarted()
        val session = ensureVoiceSession()
        if (!session.micOn && !session.hasRecordAudioPermission()) {
            pendingVoiceMicEnable = true
            requestOverlayVoiceMicPermission()
        } else {
            session.toggleMic()
        }
    }

    private fun toggleOverlayVoiceSoundFromHud() {
        ensureOverlayVoiceStarted()
        ensureVoiceSession().toggleSound()
    }

    private fun showOverlayHudPane(pane: OverlayHudPane) {
        if (overlayChatTeamPanelVisible && currentOverlayHudPane == pane) return
        if (overlayChatTeamPanelVisible) {
            hideOverlayChatTeamPanelNow(clearStrip = false)
        }
        showOverlayChatTeamPanel(hudPane = pane)
    }

    private fun ensureOverlayStatusHudWindow() {
        if (!isInGameOverlayUiActive()) return
        if (!AppContainer.from(this).userSettingsPreferences.isOverlayPanelEnabled()) return
        if (overlayChatTeamPanelVisible) return
        val manager = windowManager ?: systemWindowManager() ?: return
        if (overlayStatusHudHost != null) return

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
            gravity = Gravity.TOP or Gravity.START
            x = dp(OVERLAY_HUD_LEFT_WINDOW_X_DP)
            y = dp(OVERLAY_HUD_WINDOW_Y_DP)
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

        val host = OverlayPassthroughMultitouchFrameLayout(this).apply {
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
        overlayStatusHudHost = host
        overlayStatusHudParams = params
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
            gravity = Gravity.TOP or Gravity.END
            x = dp(OVERLAY_HUD_WINDOW_X_DP)
            y = dp(OVERLAY_HUD_WINDOW_Y_DP)
        }

        val compose = ComposeView(this).apply {
            setContent {
                val state by overlayTopRightHudFlow.collectAsStateWithLifecycle(owner)
                SquadRelayTheme {
                    OverlayGameTopRightHud(
                        state = state,
                        onOnlineClick = {
                            overlayCommandsPopover.hide()
                            showOverlayHudPane(OverlayHudPane.Participants)
                        },
                        onQuickCommandsClick = { openOverlayQuickCommandsFromHud() },
                        onVoiceHubClick = { toggleOverlayTopRightVoiceExpanded() },
                        onMicClick = { toggleOverlayVoiceMicFromHud() },
                        onSoundClick = { toggleOverlayVoiceSoundFromHud() },
                    )
                }
            }
        }
        overlayTopRightHudCompose = compose

        val host = OverlayPassthroughMultitouchFrameLayout(this).apply {
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
        overlayTopRightHudHost = host
        overlayTopRightHudParams = params
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

    private fun scheduleOverlayVoiceConnect() {
        cancelOverlayVoiceConnectScheduled()
        val prefs = AppContainer.from(this).userSettingsPreferences
        if (!prefs.isOverlayVoiceMicEnabled()) {
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
        val wasInGame = lastAppliedGateShouldShow == true
        stableGatePollTicks = if (shouldShow == lastAppliedGateShouldShow) {
            stableGatePollTicks + 1
        } else {
            0
        }
        lastAppliedGateShouldShow = shouldShow
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
            if (isOverlayShellActive()) {
                removeOverlayControl(force = true)
            }
            return
        }
        gateNotifyKey = ""
        if (!canDrawOverlaysNow()) {
            logGateStateThrottled("overlayGate: нет разрешения «поверх других приложений»")
            if (isOverlayShellActive()) {
                removeOverlayControl(force = true)
            }
            return
        }
        promoteOverlayForeground()
        ensureOverlayIfPermitted()
        if (shouldShow && !wasInGame) {
            stableGatePollTicks = HUD_STABLE_TICKS_BEFORE_ATTACH
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
    }

    /**
     * Снимает панель/чат при уходе из игры. Не опирается на [shouldKeepOverlayWindows]: suppress/пикер
     * иначе оставляют оверлей «висеть» после сворачивания или закрытия игры.
     */
    private fun dismissOverlayUiBecauseNotInGame(logWaitingForGame: Boolean) {
        if (OverlayChatInteractionHold.isOverlaySystemPickerSessionActive()) {
            deferredDismissWhenPickerEnds = true
        } else {
            deferredDismissWhenPickerEnds = false
        }
        gateUiHideStreak = 0
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

    /**
     * Tear down overlay windows + FGS without touching [UserSettingsPreferences.isOverlayPanelEnabled].
     * Used for app-policy stops (hidden overlay tab, logout) — must not flip the user's "Показывать панель" toggle.
     */
    private fun shutdownRuntimeOnly(startId: Int): Int {
        gateCheckInFlight = false
        mainHandler.removeCallbacks(gameGateRunnable)
        stopOverlayIngamePresence(markAway = true)
        cancelOverlayVoiceConnectScheduled()
        runCatching { hideOverlayChatTeamPanel() }
        runCatching { overlayTicker.hideTicker() }
        runCatching { removeOverlayControl() }
        runCatching { stopForeground(STOP_FOREGROUND_REMOVE) }
        isServiceInstanceActive = false
        _serviceRunning.value = false
        _overlayVisible.value = false
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
        stopOverlayIngamePresence(markAway = true)
        removeOverlayControl(force = true)
        removeOverlayStatusHudWindow()
        removeOverlayTopRightHudWindow()
        mainHandler.removeCallbacks(statusHudRefreshRunnable)
        mainHandler.removeCallbacks(overlayCloseHudRefreshRunnable)
        statusHudRefreshPosted = false
        serviceScope.cancel()
        unregisterScreenOnReceiver()
        if (AppContainer.from(this).userSettingsPreferences.isOverlayPanelEnabled() &&
            AppContainer.from(this).authRepository.hasSession()
        ) {
            OverlayRuntimeScheduler.scheduleImmediateRetry(applicationContext)
        }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun setBubbleUi(@Suppress("UNUSED_PARAMETER") state: OverlayBubbleUi.BubbleState) = Unit

    private fun pulseBubbleError() = Unit

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
        val filter = IntentFilter(Intent.ACTION_SCREEN_ON)
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
        stripBuffer.removeMessageWithKey(key)
        lastStripRenderSignature = null
        // Сразу снимаем зоны крестика, пока Compose не пересчитал ленту — иначе один кадр с «призрачными» rect блокирует игру.
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
            mainHandler.postDelayed(stripPassthroughSyncRunnable, 48L)
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

    private fun refreshOverlayChatStripNow() {
        stripBuffer.prune()
        val preview = stripBuffer.visibleForPreview()
        val signature = preview.joinToString(separator = "|") { msg ->
            val key = msg._id?.takeIf { it.isNotBlank() } ?: msg.stableKey()
            val imageCount = msg.chatImageAttachments().size
            "$key:${msg.text.length}:${msg.senderRole}:${msg.senderId}:$imageCount"
        }
        if (signature == lastStripRenderSignature) return
        if (BuildConfig.DEBUG) {
            Log.d(
                OVERLAY_DIAG_TAG,
                "stripRefresh apply preview=${preview.size} sigLen=${signature.length}",
            )
        }
        chatStripPreviewFlow.value = preview
        lastStripRenderSignature = signature
        val wm = windowManager ?: systemWindowManager() ?: return
        runCatching { ensureChatStripWindow(wm) }
        chatStripHost?.visibility =
            if (overlayChatTeamPanelVisible) View.GONE else View.VISIBLE
    }

    /**
     * Исходящее сообщение по HTTP (текст из истории, голос, быстрые команды): сразу обновить ленту оверлея,
     * как только приходит ответ API (не ждать сокет). Вызывать с главного потока.
     */
    private fun applyLocalSentMessageToStrip(sent: ChatMessage) {
        stripBuffer.markClientSend(sent)
        processOverlayChatMessage(sent)
    }

    private fun processOverlayChatMessage(msg: ChatMessage, refreshStrip: Boolean = true) {
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
        if (refreshStrip) {
            refreshOverlayChatStrip()
        }
        val hubId = cachedAllianceHubRoomId
        if (hubId != null && msg.roomId == hubId) {
            val selfId = jwtSubFromAccessToken()
            if (!selfId.isNullOrBlank() && msg.senderId != selfId) {
                maybeBumpAllianceHubUnread(msg, hubId)
            }
        } else if (msg.roomId.isNotBlank()) {
            scheduleDebouncedHubHudRefresh()
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
                    ensureVoiceSession().setMicEnabled(true)
                    refreshOverlayTopRightHudState()
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
        val reactionListener: (OverlayReactionEvent) -> Unit = { event ->
            mainHandler.post {
                if (!overlaySessionActive) return@post
                val wm = windowManager ?: getSystemService(Context.WINDOW_SERVICE) as? WindowManager ?: return@post
                overlayCommandsPopover.showIncomingReactionBurst(
                    wm,
                    event.fromUsername,
                    event.reaction,
                )
            }
        }
        overlayReactionListener = reactionListener
        val listener: (ChatMessage) -> Unit = listener@{ msg ->
            val raidId = AppContainer.from(this).chatRoomPreferences.getRaidRoomId()
                ?: return@listener
            if (msg.roomId.isNotBlank() && msg.roomId != raidId) {
                Log.d(
                    OVERLAY_DIAG_TAG,
                    "overlayListener skipRoom msgRoom=${msg.roomId} raid=$raidId id=${msg._id}",
                )
                return@listener
            }
            mainHandler.post {
                pendingStripSocketMessages.addLast(msg)
                if (!stripSocketDrainPosted) {
                    stripSocketDrainPosted = true
                    mainHandler.postDelayed(stripSocketDrainRunnable, 48L)
                }
            }
        }
        overlayMessageListener = listener
        mainHandler.post {
            AppContainer.from(this).chatRepository.addOverlayMessageListener(listener)
            AppContainer.from(this).chatRepository.addOverlayReactionListener(reactionListener)
            if (!isOverlayHudOnlyMode()) {
                scheduleStripTick()
            }
            resolveCachedAllianceHubRoomId()
        }
        serviceScope.launch {
            val container = AppContainer.from(this@CombatOverlayService)
            val cachedRooms = com.lastasylum.alliance.data.chat.ChatSessionCache.getFreshRooms()
            if (cachedRooms != null) {
                cachedAllianceHubRoomId = OverlayGameStatusHudRefresh.allianceHubRoom(cachedRooms)?.id
            } else {
                container.chatRepository.listRooms().getOrNull()?.let { list ->
                    com.lastasylum.alliance.data.chat.ChatSessionCache.update(list)
                    cachedAllianceHubRoomId = OverlayGameStatusHudRefresh.allianceHubRoom(list)?.id
                }
            }
            val raidId = container.chatRoomPreferences.getRaidRoomId()
            if (raidId == null) {
                mainHandler.post {
                    setStripPlainMessage(getString(R.string.overlay_strip_no_raid))
                }
                return@launch
            }
            val cachedHistory = com.lastasylum.alliance.data.chat.ChatSessionCache
                .getFreshMessages(raidId)
            if (!cachedHistory.isNullOrEmpty()) {
                mainHandler.post {
                    stripBuffer.seedFromHistory(cachedHistory.take(OVERLAY_HISTORY_LOAD))
                    lastStripRenderSignature = null
                    refreshOverlayChatStripNow()
                }
            }
            container.chatRepository.loadRecentMessages(raidId, null, OVERLAY_HISTORY_LOAD)
                .onSuccess { loaded ->
                    com.lastasylum.alliance.data.chat.ChatSessionCache.updateMessages(raidId, loaded)
                    mainHandler.post {
                        stripBuffer.seedFromHistory(loaded)
                        lastStripRenderSignature = null
                        refreshOverlayChatStripNow()
                    }
                }
                .onFailure {
                    if (cachedHistory.isNullOrEmpty()) {
                        mainHandler.post {
                            setStripPlainMessage(getString(R.string.overlay_strip_history_failed))
                        }
                    }
                }
        }
    }

    private fun endOverlayChatSubscription() {
        mainHandler.removeCallbacks(stripZOrderLiftRunnable)
        stripZOrderLiftPosted = false
        cancelOverlayVoiceConnectScheduled()
        stopOverlayVoice()
        unregisterVoiceMicPermissionReceiver()
        cancelStripTick()
        updateStripDismissScreenRects(emptyList())
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
        overlayReactionListener?.let { listener ->
            runCatching {
                AppContainer.from(applicationContext).chatRepository.removeOverlayReactionListener(listener)
            }
        }
        overlayReactionListener = null
    }

    /** Лента чата и подписки; FAB-панель убрана — управление из угловых HUD. */
    private fun showOverlayShell() {
        repairDetachedOverlayShellIfNeeded()
        repairDetachedOverlayChatTeamPanelIfNeeded()
        if (isOverlayShellActive()) return

        val manager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        windowManager = manager
        overlaySessionActive = true
        if (isOverlayHudOnlyMode()) {
            _overlayVisible.value = true
        } else {
            ensureChatStripWindow(manager)
            if (chatStripHost == null) {
                Log.e(TAG, "showOverlayShell: failed to attach chat strip")
                overlaySessionActive = false
                windowManager = null
                _overlayVisible.value = false
                return
            }
            _overlayVisible.value = true
            applyOverlayStripVisibility()
            scheduleStripZOrderLift()
        }
        rebalanceOverlayFullscreenZOrder()
        mainHandler.post { beginOverlayChatSubscription() }
    }

    private fun applyOverlayStripVisibility(rebalanceZOrder: Boolean = false) {
        (windowManager ?: systemWindowManager())?.let { wm ->
            runCatching { ensureChatStripWindow(wm) }
        }
        if (!isOverlayHudOnlyMode()) {
            chatStripHost?.visibility =
                if (overlayChatTeamPanelVisible) View.GONE else View.VISIBLE
        }
        if (rebalanceZOrder) {
            rebalanceOverlayFullscreenZOrder()
        }
    }
    private fun hideOverlayChatTeamPanel(clearStrip: Boolean = true) {
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

    private fun hideOverlayChatTeamPanelNow(clearStrip: Boolean = true) {
        val root = overlayChatTeamRoot
        val hadVisible = overlayChatTeamPanelVisible
        overlayChatTeamPanelVisible = false
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
                lastStripRenderSignature = null
            }
        }
        overlayChatTeamRoot?.let { hideOverlayIme(it) }
        val manager = windowManager ?: systemWindowManager()
        if (root != null && manager != null) {
            runCatching { manager.removeView(root) }
        }
        overlayChatTeamRoot = null
        overlayChatTeamParams = null
        overlayChatViewModel = null
        pendingOverlayPickedImageUris = null
        deferredHideOverlayChatTeamPanel = false
        runCatching { AppContainer.from(this).chatRepository.notifyOverlayChatPanelClosed() }
        runCatching { unregisterReceiver(overlaySystemResultReceiver) }
        overlayChatTeamComposeOwner?.destroy()
        overlayChatTeamComposeOwner = null
        applyOverlayStripVisibility(rebalanceZOrder = false)
        syncOverlayStatusHudVisibility()
        if (chatStripZOrderLifted) {
            rebalanceOverlayHudZOrder(force = true)
        }
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
        mainHandler.postDelayed(stripZOrderLiftRunnable, STRIP_ZORDER_LIFT_DELAY_MS)
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
        rebalanceOverlayHudZOrder(force = true)
    }

    private fun rebalanceOverlayChatStripZOrder() = requestChatStripZOrderLift()

    private fun showOverlayChatTeamPanel(
        initialTabIndex: Int = 0,
        hudPane: OverlayHudPane? = null,
    ) {
        overlayChatViewModel?.refreshChatForOverlay()
        mainHandler.post { flushPendingOverlayPickedImages() }
        if (overlayChatTeamPanelVisible) return
        val initialTab = initialTabIndex.coerceIn(0, 1)
        currentOverlayHudPane = hudPane
        overlayCommandsPopover.hide()
        OverlayChatInteractionHold.acquireGameForegroundSuppress()
        OverlayChatInteractionHold.isFullscreenChatTeamPanelVisible = true
        val manager = windowManager ?: run {
            showOverlayShell()
            windowManager
        } ?: return
        chatStripHost?.visibility = View.GONE
        overlayStatusHudHost?.visibility = View.GONE
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
                val app = this@CombatOverlayService.applicationContext as android.app.Application
                val userId = remember { jwtSubFromAccessToken().orEmpty() }
                val userRole = remember { jwtRoleFromAccessToken() }
                var selectedTab by remember(initialTab) { mutableIntStateOf(initialTab) }
                val vm = remember(userId, userRole) {
                    activityScopedChatViewModel
                        ?: overlayChatViewModel
                        ?: ChatViewModel(
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
                LaunchedEffect(vm) {
                    flushPendingOverlayPickedImages()
                }
                val chatState by vm.state.collectAsStateWithLifecycle(owner)
                val draftMessage by vm.draftMessage.collectAsStateWithLifecycle(owner)
                val pickedImageUris by vm.pickedImageUris.collectAsStateWithLifecycle(owner)
                val typingPeers by vm.typingPeers.collectAsStateWithLifecycle(owner)
                val chatVoicePhase by vm.chatVoicePhase.collectAsStateWithLifecycle(owner)
                val otherReadUptoMessageId by vm.otherReadUptoMessageId.collectAsStateWithLifecycle(owner)

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
                                when (hudPane) {
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
                                    OverlayHudPane.Participants -> {
                                        OverlayHudPanelHeader(
                                            title = stringResource(R.string.overlay_online_title),
                                            onClose = { hideOverlayChatTeamPanel() },
                                        )
                                    }
                                    else -> {
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
                                    }
                                }
                                Box(
                                    Modifier
                                        .fillMaxWidth()
                                        .weight(1f),
                                ) {
                                    when (hudPane) {
                                        OverlayHudPane.Chat -> OverlayHudChatPane(
                                            chatState = chatState,
                                            typingPeers = typingPeers,
                                            draftMessage = draftMessage,
                                            pickedImageUris = pickedImageUris,
                                            chatVoicePhase = chatVoicePhase,
                                            otherReadUptoMessageId = otherReadUptoMessageId,
                                            vm = vm,
                                        )
                                        OverlayHudPane.News -> {
                                            OverlayTeamNewsPanel(
                                                currentUserId = userId,
                                                teamsRepository = container.teamsRepository,
                                            )
                                        }
                                        OverlayHudPane.Forum -> {
                                            OverlayTeamForumPanel(
                                                currentUserId = userId,
                                                teamsRepository = container.teamsRepository,
                                            )
                                        }
                                        OverlayHudPane.Participants -> {
                                            OverlayTeamOnlinePanel(
                                                teamsRepository = container.teamsRepository,
                                                usersRepository = container.usersRepository,
                                                onHudRefresh = {
                                                    OverlayGameStatusHudRefresh.invalidateNewsForumCache()
                                                    refreshOverlayStatusHudData(force = true)
                                                },
                                            )
                                        }
                                        null -> when (selectedTab) {
                                        0 -> OverlayHudChatPane(
                                            chatState = chatState,
                                            typingPeers = typingPeers,
                                            draftMessage = draftMessage,
                                            pickedImageUris = pickedImageUris,
                                            chatVoicePhase = chatVoicePhase,
                                            otherReadUptoMessageId = otherReadUptoMessageId,
                                            vm = vm,
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
                                    }
                                }
                                if (hudPane == null) {
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
                overlayTopRightHudFlow.value = overlayTopRightHudFlow.value.copy(
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

    private fun startOverlayVoiceIfRaidAvailable() {
        val raidId = AppContainer.from(this).chatRoomPreferences.getRaidRoomId() ?: return
        val userId = jwtSubFromAccessToken().orEmpty()
        if (userId.isBlank()) return
        val session = ensureVoiceSession()
        session.start(raidId, userId)
        refreshOverlayTopRightHudState()
    }

    private fun stopOverlayVoice() {
        voiceSession?.stop()
        voiceSession = null
        AppContainer.from(this).overlayVoiceSession = null
        overlayTopRightHudFlow.value = overlayTopRightHudFlow.value.copy(voiceExpanded = false)
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
            commandsPopoverShowing = false,
        )
        resumeOverlayWindowsAfterSystemActivity()
        speechPipeline.cancelActiveSession()
        endOverlayChatSubscription()
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
        var activityScopedChatViewModel: ChatViewModel? = null

        fun bindActivityChatViewModel(viewModel: ChatViewModel?) {
            activityScopedChatViewModel = viewModel
        }

        /** Частый prune ленты относительно TTL ~10 с. */
        private const val STRIP_TICK_MS = 2_500L
        /** Лента на экране — отзывчивый гейт. */
        private const val GAME_GATE_POLL_ACTIVE_MS = 1_200L
        /** Стабильно «в игре» — реже тяжёлых проверок usage stats. */
        private const val GAME_GATE_POLL_STABLE_MS = 2_500L
        /** Недавно были в игре / открыт чат — чаще, чем в простое. */
        private const val GAME_GATE_POLL_WARM_MS = 1_800L
        /** FGS включён, оверлей скрыт: редкий опрос usage stats. */
        private const val GAME_GATE_POLL_IDLE_MS = 2_000L
        private const val STATUS_HUD_REFRESH_MS = 60_000L
        /** Голос: отложенный connect, если mic/sound были включены в прошлой сессии. */
        private const val OVERLAY_VOICE_CONNECT_DELAY_MS = 4_000L
        /** Краткий grace при ложном «не в игре» во время чата/пикера; не применяется при явном лаунчере/другом приложении. */
        private const val OVERLAY_INGAME_GRACE_MS = 2_500L
        private const val OVERLAY_HISTORY_LOAD = 40
        /** 1 тик гейта (~1.2 с) после «в игре» — достаточно, если HUD уже показан при входе. */
        private const val HUD_STABLE_TICKS_BEFORE_ATTACH = 1
        private const val GATE_STABLE_TICKS_FOR_SLOW_POLL = 5
        private const val HUD_REFRESH_MIN_INTERVAL_MS = 2_000L
        private const val HUB_HUD_REFRESH_DEBOUNCE_MS = 4_000L
        private const val OVERLAY_CLOSE_HUD_REFRESH_DELAY_MS = 80L
        private const val STRIP_ZORDER_MIN_INTERVAL_MS = 30_000L
        private const val STRIP_ZORDER_LIFT_DELAY_MS = 450L
        /** Отступ правого HUD от края (Gravity.END). */
        private const val OVERLAY_HUD_WINDOW_X_DP = 10
        /** Левый HUD: не перекрывать типичную «стрелку назад» в игровом UI (~48dp). */
        private const val OVERLAY_HUD_LEFT_WINDOW_X_DP = 52
        /** Вертикальный отступ HUD-окон от верхнего края (меньше — выше на экране). */
        private const val OVERLAY_HUD_WINDOW_Y_DP = 2
        /** Минимум между remove/add HUD — иначе кнопки мигают на каждом тике гейта. */
        private const val HUD_ZORDER_REBALANCE_MIN_MS = 60_000L
        private const val GATE_HIDE_UI_HYSTERESIS_TICKS = 2
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
