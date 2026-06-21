package com.lastasylum.alliance.overlay

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.LifecycleOwner
import com.lastasylum.alliance.R
import com.lastasylum.alliance.game.GameSearchBridge
import com.lastasylum.alliance.ui.theme.SquadRelayTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Overlay modal: search player or alliance in the game (permission-gated HUD entry).
 */
class OverlayGameSearchPopover(
    private val context: Context,
    private val mainHandler: Handler,
    private val scope: CoroutineScope,
    private val dp: (Int) -> Int,
    private val serverNumberProvider: () -> Int?,
    private val attachComposeTree: (View) -> Unit,
    private val composeOwnerProvider: () -> LifecycleOwner,
) {
    private var scrimHost: FrameLayout? = null
    private var scrimParams: WindowManager.LayoutParams? = null
    private var attachedWindowManager: WindowManager? = null
    private var searchJob: Job? = null

    fun isShowing(): Boolean = scrimHost?.visibility == View.VISIBLE

    fun isBlockingGameGateDismiss(): Boolean = isShowing()

    fun toggle(manager: WindowManager) {
        if (isShowing()) {
            hide()
        } else {
            show(manager)
        }
    }

    fun hide() {
        searchJob?.cancel()
        searchJob = null
        val host = scrimHost
        if (host != null && host.isAttachedToWindow) {
            host.visibility = View.GONE
        } else {
            removeShell()
        }
        attachedWindowManager = null
        OverlayChatInteractionHold.cancelPreparedOverlayModalInteraction(isOverlayUi = true)
    }

    fun destroyCachedShells() {
        searchJob?.cancel()
        searchJob = null
        removeShell()
        attachedWindowManager = null
    }

    private fun show(manager: WindowManager) {
        attachedWindowManager = manager
        OverlayChatInteractionHold.prepareOverlayModalInteraction(isOverlayUi = true)
        composeOwnerProvider()
        val host = scrimHost ?: buildShell().also { scrimHost = it }
        val params = scrimParams ?: buildParams().also { scrimParams = it }
        if (!host.isAttachedToWindow) {
            runCatching { manager.addView(host, params) }
        }
        host.visibility = View.VISIBLE
        (host.getChildAt(0) as? ComposeView)?.requestFocus()
        OverlayChatInteractionHold.cancelPreparedOverlayModalInteraction(isOverlayUi = true)
    }

    private fun buildParams(): WindowManager.LayoutParams {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }
        return WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            type,
            OverlayWindowLayout.overlayModalWindowFlags(),
            PixelFormat.TRANSLUCENT,
        ).apply {
            OverlayWindowLayout.applyFullscreenOverlayWindow(context, this)
            OverlayWindowLayout.applyOverlayModalSoftInputMode(this)
            gravity = Gravity.TOP or Gravity.START
        }
    }

    private fun buildShell(): FrameLayout {
        val compose = ComposeView(context).apply {
            isFocusable = true
            isFocusableInTouchMode = true
            setContent {
                SquadRelayTheme {
                    OverlayGameSearchPanel(
                        onDismiss = { mainHandler.post { hide() } },
                        onSearch = { kind, query, onDone ->
                            searchJob?.cancel()
                            searchJob = scope.launch {
                                val result = withContext(Dispatchers.IO) {
                                    GameSearchBridge.search(
                                        context = context,
                                        kind = kind,
                                        query = query,
                                        serverNumber = serverNumberProvider(),
                                    )
                                }
                                mainHandler.post { onDone(result) }
                            }
                        },
                        onOpenProfile = { hit ->
                            GameSearchBridge.openProfile(
                                context,
                                hit,
                                serverNumberProvider(),
                            )
                        },
                        onOpenOnMap = { hit ->
                            GameSearchBridge.openOnMap(
                                context,
                                hit,
                                serverNumberProvider(),
                            )
                        },
                    )
                }
            }
        }
        return FrameLayout(context).apply {
            setBackgroundColor(android.graphics.Color.parseColor("#99000000"))
            setOnClickListener { hide() }
            attachComposeTree(this)
            addView(
                compose,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    Gravity.CENTER,
                ),
            )
            compose.setOnClickListener { /* consume */ }
        }
    }

    private fun removeShell() {
        val host = scrimHost ?: return
        val managers = listOfNotNull(attachedWindowManager).distinct()
        for (wm in managers) {
            if (host.isAttachedToWindow) {
                runCatching { wm.removeView(host) }
            }
        }
        scrimHost = null
        scrimParams = null
    }
}

@Composable
private fun OverlayGameSearchPanel(
    onDismiss: () -> Unit,
    onSearch: (
        kind: GameSearchBridge.SearchKind,
        query: String,
        onDone: (Result<List<GameSearchBridge.SearchHit>>) -> Unit,
    ) -> Unit,
    onOpenProfile: (GameSearchBridge.SearchHit) -> Unit,
    onOpenOnMap: (GameSearchBridge.SearchHit) -> Unit,
) {
    var playerChecked by remember { mutableStateOf(true) }
    var allianceChecked by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var searching by remember { mutableStateOf(false) }
    var results by remember { mutableStateOf<List<GameSearchBridge.SearchHit>>(emptyList()) }
    var errorText by remember { mutableStateOf<String?>(null) }
    var infoText by remember { mutableStateOf<String?>(null) }
    val sentToGameText = stringResource(R.string.overlay_game_search_sent_to_game)
    val errorGenericText = stringResource(R.string.overlay_game_search_error)
    val errorShortQueryText = stringResource(R.string.overlay_game_search_query_short)
    val errorNoServerText = stringResource(R.string.overlay_game_search_no_server)
    val queryFocusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        queryFocusRequester.requestFocus()
    }
    fun runSearch() {
        if (searching || query.trim().length < 2) return
        val kind = if (playerChecked) {
            GameSearchBridge.SearchKind.PLAYER
        } else {
            GameSearchBridge.SearchKind.ALLIANCE
        }
        searching = true
        errorText = null
        infoText = null
        results = emptyList()
        onSearch(kind, query) { result ->
            searching = false
            result.onSuccess { hits ->
                results = hits
                infoText = if (hits.all { it.mapX == null && it.mapY == null }) {
                    sentToGameText
                } else {
                    null
                }
            }.onFailure { err ->
                infoText = null
                errorText = when (err.message) {
                    "query_too_short" -> errorShortQueryText
                    "no_active_server" -> errorNoServerText
                    else -> errorGenericText
                }
            }
        }
    }
    val shape = RoundedCornerShape(16.dp)
    val panelModifier = Modifier
        .padding(horizontal = 20.dp)
        .widthIn(max = 420.dp)
        .fillMaxWidth()
        .imePadding()
        .background(Color(0xF010141E), shape)
        .border(1.dp, Color(0x889B7CFF), shape)
        .padding(16.dp)

    Column(
        modifier = panelModifier,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.overlay_game_search_title),
                fontWeight = FontWeight.SemiBold,
                fontSize = 18.sp,
                color = Color(0xFFF4F0FF),
            )
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.overlay_game_search_close))
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(
                checked = playerChecked,
                onCheckedChange = { checked ->
                    if (checked) {
                        playerChecked = true
                        allianceChecked = false
                    } else if (!allianceChecked) {
                        playerChecked = true
                    }
                },
            )
            Text(
                text = stringResource(R.string.overlay_game_search_kind_player),
                color = Color(0xFFE8EAEF),
                modifier = Modifier.clickable {
                    playerChecked = true
                    allianceChecked = false
                },
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(
                checked = allianceChecked,
                onCheckedChange = { checked ->
                    if (checked) {
                        allianceChecked = true
                        playerChecked = false
                    } else if (!playerChecked) {
                        allianceChecked = true
                    }
                },
            )
            Text(
                text = stringResource(R.string.overlay_game_search_kind_alliance),
                color = Color(0xFFE8EAEF),
                modifier = Modifier.clickable {
                    allianceChecked = true
                    playerChecked = false
                },
            )
        }
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(queryFocusRequester),
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(
                onSearch = { runSearch() },
            ),
            label = {
                Text(
                    if (playerChecked) {
                        stringResource(R.string.overlay_game_search_hint_player)
                    } else {
                        stringResource(R.string.overlay_game_search_hint_alliance)
                    },
                )
            },
        )
        Button(
            onClick = { runSearch() },
            enabled = !searching && query.trim().length >= 2,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.overlay_game_search_action))
        }
        if (searching) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
        }
        errorText?.let { msg ->
            Text(text = msg, color = Color(0xFFFFB4AB), fontSize = 13.sp)
        }
        infoText?.let { msg ->
            Text(text = msg, color = Color(0xFFB8C5FF), fontSize = 13.sp)
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            results.forEach { hit ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0x331A1F2B), RoundedCornerShape(10.dp))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = hit.displayName,
                        color = Color(0xFFF4F0FF),
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedButton(onClick = { onOpenProfile(hit) }) {
                        Text(stringResource(R.string.overlay_game_search_profile))
                    }
                    OutlinedButton(onClick = { onOpenOnMap(hit) }) {
                        Text(stringResource(R.string.overlay_game_search_on_map))
                    }
                }
            }
        }
    }
}
