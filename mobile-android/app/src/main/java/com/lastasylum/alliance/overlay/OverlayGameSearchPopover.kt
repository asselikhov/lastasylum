package com.lastasylum.alliance.overlay

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
            OverlayWindowLayout.applyOverlaySearchSoftInputMode(this)
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
            // Стабильный подъём над клавиатурой: окно с ADJUST_NOTHING не ресайзится (нет «прыжков»),
            // карточку поднимаем ручным padding корня на высоту IME. ime-инсет поглощаем, чтобы
            // Compose внутри не добавил отступ повторно.
            ViewCompat.setOnApplyWindowInsetsListener(this) { view, windowInsets ->
                val safeTypes = WindowInsetsCompat.Type.systemBars() or
                    WindowInsetsCompat.Type.displayCutout()
                val imeTypes = WindowInsetsCompat.Type.ime()
                val safe = windowInsets.getInsets(safeTypes)
                val ime = windowInsets.getInsets(imeTypes)
                val bottom = if (ime.bottom > 0) ime.bottom else safe.bottom
                view.setPadding(safe.left, safe.top, safe.right, bottom)
                WindowInsetsCompat.Builder(windowInsets)
                    .setInsets(safeTypes, Insets.of(safe.left, 0, safe.right, 0))
                    .setInsets(imeTypes, Insets.NONE)
                    .build()
            }
            addView(
                compose,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    Gravity.BOTTOM,
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
    var isPlayer by remember { mutableStateOf(true) }
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
    val canSearch = !searching && query.trim().length >= 2
    fun runSearch() {
        if (!canSearch) return
        val kind = if (isPlayer) {
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

    val sheetShape = RoundedCornerShape(18.dp)
    Column(
        modifier = Modifier
            .padding(horizontal = 10.dp)
            .padding(bottom = 10.dp)
            .widthIn(max = 460.dp)
            .fillMaxWidth()
            .background(Color(0xF20D111B), sheetShape)
            .border(1.dp, Color(0x559B7CFF), sheetShape)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.overlay_game_search_title),
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp,
                color = Color(0xFFF4F0FF),
            )
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onDismiss() },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "\u2715",
                    color = Color(0xFFAEB4C2),
                    fontSize = 15.sp,
                )
            }
        }

        SearchKindToggle(
            isPlayer = isPlayer,
            onSelect = { selectedPlayer ->
                if (isPlayer != selectedPlayer) {
                    isPlayer = selectedPlayer
                    results = emptyList()
                    infoText = null
                    errorText = null
                }
            },
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(queryFocusRequester),
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                textStyle = LocalTextStyle.current.copy(fontSize = 14.sp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { runSearch() }),
                placeholder = {
                    Text(
                        text = if (isPlayer) {
                            stringResource(R.string.overlay_game_search_hint_player)
                        } else {
                            stringResource(R.string.overlay_game_search_hint_alliance)
                        },
                        fontSize = 14.sp,
                        maxLines = 1,
                    )
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0x889B7CFF),
                    unfocusedBorderColor = Color(0x33FFFFFF),
                    focusedContainerColor = Color(0x22000000),
                    unfocusedContainerColor = Color(0x22000000),
                    cursorColor = Color(0xFF9B7CFF),
                ),
            )
            Button(
                onClick = { runSearch() },
                enabled = canSearch,
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(horizontal = 18.dp),
                modifier = Modifier.heightIn(min = 48.dp),
            ) {
                if (searching) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = Color(0xFFF4F0FF),
                    )
                } else {
                    Text(
                        text = stringResource(R.string.overlay_game_search_action),
                        fontSize = 14.sp,
                    )
                }
            }
        }

        errorText?.let { msg ->
            Text(text = msg, color = Color(0xFFFFB4AB), fontSize = 12.sp)
        }
        infoText?.let { msg ->
            Text(text = msg, color = Color(0xFFB8C5FF), fontSize = 12.sp)
        }

        if (results.isNotEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 240.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                results.forEach { hit ->
                    SearchResultRow(
                        hit = hit,
                        onOpenProfile = { onOpenProfile(hit) },
                        onOpenOnMap = { onOpenOnMap(hit) },
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchKindToggle(
    isPlayer: Boolean,
    onSelect: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0x22000000))
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        SegmentPill(
            text = stringResource(R.string.overlay_game_search_kind_player),
            selected = isPlayer,
            onClick = { onSelect(true) },
            modifier = Modifier.weight(1f),
        )
        SegmentPill(
            text = stringResource(R.string.overlay_game_search_kind_alliance),
            selected = !isPlayer,
            onClick = { onSelect(false) },
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun SegmentPill(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) Color(0x339B7CFF) else Color.Transparent)
            .clickable { onClick() }
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = if (selected) Color(0xFFF4F0FF) else Color(0xFF9AA0AE),
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
        )
    }
}

@Composable
private fun SearchResultRow(
    hit: GameSearchBridge.SearchHit,
    onOpenProfile: () -> Unit,
    onOpenOnMap: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0x33161B27), RoundedCornerShape(10.dp))
            .padding(start = 12.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = hit.displayName,
            color = Color(0xFFF4F0FF),
            fontSize = 14.sp,
            maxLines = 1,
            modifier = Modifier.weight(1f),
        )
        TextButton(
            onClick = onOpenProfile,
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
        ) {
            Text(stringResource(R.string.overlay_game_search_profile), fontSize = 12.sp)
        }
        TextButton(
            onClick = onOpenOnMap,
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
        ) {
            Text(stringResource(R.string.overlay_game_search_on_map), fontSize = 12.sp)
        }
    }
}
