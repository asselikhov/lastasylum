package com.lastasylum.alliance.overlay

import android.content.Context
import android.graphics.Color
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lastasylum.alliance.R
import com.lastasylum.alliance.data.settings.UserSettingsPreferences
import com.lastasylum.alliance.game.AllianceRallyPoint
import com.lastasylum.alliance.game.RelocateItemCounts
import com.lastasylum.alliance.game.RoutePlannerAccess
import com.lastasylum.alliance.game.RoutePlannerPoint
import com.lastasylum.alliance.game.RoutePlannerRoute
import com.lastasylum.alliance.game.RoutePlannerStore
import com.lastasylum.alliance.game.RoutePlannerSync
import com.lastasylum.alliance.game.RoutePlannerPointStatus
import com.lastasylum.alliance.game.RoutePointStatus
import com.lastasylum.alliance.game.RoutePlannerRelocateStats
import com.lastasylum.alliance.game.RoutePlannerType
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope

private data class RoutePointEditTarget(
  val route: RoutePlannerRoute,
  val point: RoutePlannerPoint,
)

private enum class TeleportPanelTab {
    Teleport,
    Planner,
}

/**
 * Компактное центральное окно «Перемещение»: вкладки перемещения и планировщика маршрутов.
 */
class OverlayTeleportPanel(
  private val context: Context,
  private val mainHandler: Handler,
  private val dp: (Int) -> Int,
  private val prefs: UserSettingsPreferences,
  private val defaultServerProvider: () -> Int?,
  private val rallyPointFlow: StateFlow<AllianceRallyPoint?>,
  private val relocateItemsFlow: StateFlow<RelocateItemCounts?>,
  private val panelOpenTick: StateFlow<Int>,
  private val onPanelShown: () -> Unit,
  private val installModalCompose: (View, ComposeView, @Composable () -> Unit) -> Unit,
  private val onDirectTeleport: (x: Int, y: Int, serverNumber: Int) -> Unit,
  private val onRandomTeleport: () -> Unit,
  private val onAllianceTeleport: () -> Unit,
  private val onFlyToRally: (x: Int, y: Int, serverNumber: Int) -> Unit,
  private val onRelocateAll: (route: RoutePlannerRoute) -> Unit,
  private val onRepositionPointOnMap: (route: RoutePlannerRoute, point: RoutePlannerPoint) -> Unit,
  private val onExportRouteToRaid: (route: RoutePlannerRoute) -> Unit,
  private val onScheduleRelocateAll: (route: RoutePlannerRoute, delayMinutes: Int) -> Unit,
) {
  private var scrim: FrameLayout? = null
  private var composeView: ComposeView? = null
  private var composeContentInstalled = false
  private var attached = false
  private var attachedWindowManager: WindowManager? = null

  val isShowing: Boolean get() = attached

  fun toggle(windowManager: WindowManager) {
    if (isShowing) hide(windowManager) else show(windowManager)
  }

  fun show(windowManager: WindowManager) {
    runOnMain {
      val root = scrim ?: buildScrim().also { scrim = it }
      if (!attached) {
        val params = buildParams()
        runCatching { windowManager.addView(root, params) }
          .onSuccess {
            attached = true
            attachedWindowManager = windowManager
            ensureComposeInstalled(root)
            onPanelShown()
          }
          .onFailure { e -> Log.w(TAG, "addView failed", e) }
      } else {
        onPanelShown()
      }
    }
  }

  fun hide(windowManager: WindowManager) {
    runOnMain {
      val root = scrim ?: return@runOnMain
      if (!attached) return@runOnMain
      val mgr = attachedWindowManager ?: windowManager
      runCatching { mgr.removeView(root) }
        .onSuccess {
          attached = false
          attachedWindowManager = null
        }
        .onFailure { e -> Log.w(TAG, "hide failed", e) }
    }
  }

  private fun runOnMain(block: () -> Unit) {
    if (Looper.myLooper() == mainHandler.looper) block() else mainHandler.post(block)
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
      android.graphics.PixelFormat.TRANSLUCENT,
    ).apply {
      OverlayWindowLayout.applyFullscreenOverlayWindow(context, this)
      OverlayWindowLayout.applyOverlayModalSoftInputMode(this)
    }
  }

  private fun ensureComposeInstalled(root: FrameLayout) {
    if (composeContentInstalled) return
    val compose = composeView ?: return
    installModalCompose(root, compose) {
      TeleportPanelContent(
        prefs = prefs,
        defaultServer = defaultServerProvider(),
        rallyPointFlow = rallyPointFlow,
        relocateItemsFlow = relocateItemsFlow,
        panelOpenTick = panelOpenTick,
        onDismiss = {
          attachedWindowManager?.let { hide(it) }
        },
        onDirectTeleport = { x, y, sid ->
          prefs.setDirectTeleportCoords(x, y, sid)
          onDirectTeleport(x, y, sid)
          attachedWindowManager?.let { hide(it) }
        },
        onRandomTeleport = {
          onRandomTeleport()
          attachedWindowManager?.let { hide(it) }
        },
        onAllianceTeleport = {
          onAllianceTeleport()
          attachedWindowManager?.let { hide(it) }
        },
        onFlyToRally = { x, y, sid ->
          onFlyToRally(x, y, sid)
          attachedWindowManager?.let { hide(it) }
        },
        onRelocateAll = { route ->
          onRelocateAll(route)
        },
        onRepositionPointOnMap = { route, point ->
          onRepositionPointOnMap(route, point)
        },
        onExportRouteToRaid = { route ->
          onExportRouteToRaid(route)
        },
        onScheduleRelocateAll = { route, delayMinutes ->
          onScheduleRelocateAll(route, delayMinutes)
        },
      )
    }
    composeContentInstalled = true
  }

  private fun buildScrim(): FrameLayout {
    val frame = FrameLayout(context).apply {
      setBackgroundColor(Color.parseColor("#99000000"))
    }
    val compose = ComposeView(context)
    composeView = compose
    frame.addView(
      compose,
      FrameLayout.LayoutParams(
        FrameLayout.LayoutParams.MATCH_PARENT,
        FrameLayout.LayoutParams.MATCH_PARENT,
      ),
    )
    frame.setOnTouchListener { _, event ->
      if (event.action == MotionEvent.ACTION_DOWN) {
        val child = compose
        val loc = IntArray(2)
        child.getLocationOnScreen(loc)
        val inside =
          event.rawX >= loc[0] &&
            event.rawX <= loc[0] + child.width &&
            event.rawY >= loc[1] &&
            event.rawY <= loc[1] + child.height
        if (!inside) {
          attachedWindowManager?.let { hide(it) }
          return@setOnTouchListener true
        }
      }
      false
    }
    return frame
  }

  companion object {
    private const val TAG = "OverlayTeleportPanel"
  }
}

@Composable
private fun TeleportPanelContent(
  prefs: UserSettingsPreferences,
  defaultServer: Int?,
  rallyPointFlow: StateFlow<AllianceRallyPoint?>,
  relocateItemsFlow: StateFlow<RelocateItemCounts?>,
  panelOpenTick: StateFlow<Int>,
  onDismiss: () -> Unit,
  onDirectTeleport: (x: Int, y: Int, serverNumber: Int) -> Unit,
  onRandomTeleport: () -> Unit,
  onAllianceTeleport: () -> Unit,
  onFlyToRally: (x: Int, y: Int, serverNumber: Int) -> Unit,
  onRelocateAll: (route: RoutePlannerRoute) -> Unit,
  onRepositionPointOnMap: (route: RoutePlannerRoute, point: RoutePlannerPoint) -> Unit,
  onExportRouteToRaid: (route: RoutePlannerRoute) -> Unit,
  onScheduleRelocateAll: (route: RoutePlannerRoute, delayMinutes: Int) -> Unit,
) {
  var selectedTab by remember { mutableStateOf(TeleportPanelTab.Teleport) }
  var plannerModalLayer by remember { mutableStateOf<(@Composable () -> Unit)?>(null) }

  val cardShape = RoundedCornerShape(16.dp)
  val cardBg = Brush.verticalGradient(
    colors = listOf(ComposeColor(0xF2141C2A), ComposeColor(0xF20B1018)),
  )

  if (selectedTab != TeleportPanelTab.Planner) {
    SideEffect { plannerModalLayer = null }
  }

  Box(
    modifier = Modifier
      .fillMaxSize()
      .clickable(
        interactionSource = remember { MutableInteractionSource() },
        indication = null,
        onClick = onDismiss,
      ),
    contentAlignment = Alignment.Center,
  ) {
    Column(
      modifier = Modifier
        .widthIn(max = 340.dp)
        .wrapContentHeight()
        .heightIn(max = 520.dp)
        .padding(horizontal = 16.dp)
        .clip(cardShape)
        .background(cardBg)
        .border(1.dp, ComposeColor(0x55FFFFFF), cardShape)
        .clickable(
          interactionSource = remember { MutableInteractionSource() },
          indication = null,
          onClick = {},
        )
        .padding(horizontal = 14.dp, vertical = 12.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      TeleportPanelTabRow(
        selected = selectedTab,
        onSelect = { selectedTab = it },
      )

      when (selectedTab) {
        TeleportPanelTab.Teleport -> TeleportTabContent(
          prefs = prefs,
          defaultServer = defaultServer,
          rallyPointFlow = rallyPointFlow,
          relocateItemsFlow = relocateItemsFlow,
          panelOpenTick = panelOpenTick,
          onDirectTeleport = onDirectTeleport,
          onRandomTeleport = onRandomTeleport,
          onAllianceTeleport = onAllianceTeleport,
          onFlyToRally = onFlyToRally,
        )
        TeleportPanelTab.Planner -> RoutePlannerTabContent(
          panelOpenTick = panelOpenTick,
          onDirectTeleport = onDirectTeleport,
          onFlyToPoint = onFlyToRally,
          onRelocateAll = onRelocateAll,
          onRepositionPointOnMap = onRepositionPointOnMap,
          onExportRouteToRaid = onExportRouteToRaid,
          onScheduleRelocateAll = onScheduleRelocateAll,
          onModalLayer = { plannerModalLayer = it },
        )
      }
    }

    if (selectedTab == TeleportPanelTab.Planner) {
      plannerModalLayer?.invoke()
    }
  }
}

@Composable
private fun TeleportPanelTabRow(
  selected: TeleportPanelTab,
  onSelect: (TeleportPanelTab) -> Unit,
) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(10.dp))
      .background(ComposeColor(0xFF1A222E))
      .padding(3.dp),
    horizontalArrangement = Arrangement.spacedBy(4.dp),
  ) {
    TeleportPanelTab.entries.forEach { tab ->
      val isSelected = tab == selected
      val labelRes = when (tab) {
        TeleportPanelTab.Teleport -> R.string.overlay_teleport_tab_move
        TeleportPanelTab.Planner -> R.string.overlay_teleport_tab_planner
      }
      Box(
        modifier = Modifier
          .weight(1f)
          .clip(RoundedCornerShape(8.dp))
          .background(if (isSelected) ComposeColor(0xFF2E7D6E) else ComposeColor(0x001A222E))
          .clickable { onSelect(tab) }
          .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center,
      ) {
        Text(
          text = contextString(labelRes),
          color = if (isSelected) ComposeColor.White else ComposeColor(0xFFB0BEC5),
          fontSize = 13.sp,
          fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
        )
      }
    }
  }
}

@Composable
private fun TeleportTabContent(
  prefs: UserSettingsPreferences,
  defaultServer: Int?,
  rallyPointFlow: StateFlow<AllianceRallyPoint?>,
  relocateItemsFlow: StateFlow<RelocateItemCounts?>,
  panelOpenTick: StateFlow<Int>,
  onDirectTeleport: (x: Int, y: Int, serverNumber: Int) -> Unit,
  onRandomTeleport: () -> Unit,
  onAllianceTeleport: () -> Unit,
  onFlyToRally: (x: Int, y: Int, serverNumber: Int) -> Unit,
) {
  val itemCounts by relocateItemsFlow.collectAsState()
  val rallyPoint by rallyPointFlow.collectAsState()
  val openTick by panelOpenTick.collectAsState()

  val initialCoords = remember { loadDirectTeleportCoordTexts(prefs, defaultServer) }
  var serverText by remember { mutableStateOf(initialCoords.server) }
  var xText by remember { mutableStateOf(initialCoords.x) }
  var yText by remember { mutableStateOf(initialCoords.y) }
  var lastSyncedOpenTick by remember { mutableIntStateOf(0) }

  SideEffect {
    if (openTick <= lastSyncedOpenTick) return@SideEffect
    val loaded = loadDirectTeleportCoordTexts(prefs, defaultServer)
    serverText = loaded.server
    xText = loaded.x
    yText = loaded.y
    lastSyncedOpenTick = openTick
  }

  fun persistCoords() {
    prefs.setDirectTeleportCoordTexts(serverText, xText, yText)
  }

  val server = serverText.trim().toIntOrNull()
  val x = xText.trim().toIntOrNull()
  val y = yText.trim().toIntOrNull()
  val directReady = server != null && server in MIN_SERVER..MAX_SERVER &&
    x != null && x > 0 && y != null && y > 0
  val directCount = itemCounts?.direct
  val randomCount = itemCounts?.random
  val allianceCount = itemCounts?.alliance
  val directEnabled = directReady && directCount != null && directCount > 0
  val randomEnabled = randomCount != null && randomCount > 0
  val allianceEnabled = allianceCount != null && allianceCount > 0

  Column(
    modifier = Modifier.fillMaxWidth(),
    verticalArrangement = Arrangement.spacedBy(6.dp),
  ) {
    RallyPointRow(
      rallyPoint = rallyPoint,
      onFly = onFlyToRally,
    )

    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(6.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      CoordField(
        label = "#",
        value = serverText,
        onValueChange = {
          serverText = it.filter { ch -> ch.isDigit() }.take(4)
          persistCoords()
        },
        modifier = Modifier.weight(0.85f),
      )
      CoordField(
        label = "X",
        value = xText,
        onValueChange = {
          xText = it.filter { ch -> ch.isDigit() }.take(4)
          persistCoords()
        },
        modifier = Modifier.weight(1f),
      )
      CoordField(
        label = "Y",
        value = yText,
        onValueChange = {
          yText = it.filter { ch -> ch.isDigit() }.take(4)
          persistCoords()
        },
        modifier = Modifier.weight(1f),
      )
    }

    Column(
      modifier = Modifier.fillMaxWidth(),
      verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
      Button(
        onClick = {
          if (directEnabled) {
            onDirectTeleport(x, y, server)
          }
        },
        enabled = directEnabled,
        modifier = Modifier.fillMaxWidth(),
        colors = ButtonDefaults.buttonColors(
          containerColor = ComposeColor(0xFF2E7D6E),
          disabledContainerColor = ComposeColor(0xFF2A3038),
          contentColor = ComposeColor.White,
          disabledContentColor = ComposeColor(0xFF6B7280),
        ),
        shape = RoundedCornerShape(10.dp),
        contentPadding = ButtonDefaults.ContentPadding,
      ) {
        Text(
          text = teleportActionLabel(
            baseRes = R.string.overlay_teleport_direct_action,
            withCountRes = R.string.overlay_teleport_direct_action_with_count,
            count = directCount,
          ),
          fontSize = 14.sp,
          fontWeight = FontWeight.Medium,
        )
      }

      OutlinedButton(
        onClick = onRandomTeleport,
        enabled = randomEnabled,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        colors = ButtonDefaults.outlinedButtonColors(
          contentColor = ComposeColor(0xFF90CAF9),
          disabledContentColor = ComposeColor(0xFF6B7280),
        ),
        border = androidx.compose.foundation.BorderStroke(1.dp, ComposeColor(0x665A9BD5)),
        contentPadding = ButtonDefaults.ContentPadding,
      ) {
        Text(
          text = teleportActionLabel(
            baseRes = R.string.overlay_teleport_random_action,
            withCountRes = R.string.overlay_teleport_random_action_with_count,
            count = randomCount,
          ),
          fontSize = 14.sp,
          fontWeight = FontWeight.Medium,
        )
      }

      OutlinedButton(
        onClick = onAllianceTeleport,
        enabled = allianceEnabled,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        colors = ButtonDefaults.outlinedButtonColors(
          contentColor = ComposeColor(0xFF80CBC4),
          disabledContentColor = ComposeColor(0xFF6B7280),
        ),
        border = androidx.compose.foundation.BorderStroke(1.dp, ComposeColor(0x6640A090)),
        contentPadding = ButtonDefaults.ContentPadding,
      ) {
        Text(
          text = teleportActionLabel(
            baseRes = R.string.overlay_teleport_alliance_action,
            withCountRes = R.string.overlay_teleport_alliance_action_with_count,
            count = allianceCount,
          ),
          fontSize = 14.sp,
          fontWeight = FontWeight.Medium,
        )
      }
    }
  }
}

@Composable
private fun RoutePlannerTabContent(
  panelOpenTick: StateFlow<Int>,
  onDirectTeleport: (x: Int, y: Int, serverNumber: Int) -> Unit,
  onFlyToPoint: (x: Int, y: Int, serverNumber: Int) -> Unit,
  onRelocateAll: (route: RoutePlannerRoute) -> Unit,
  onRepositionPointOnMap: (route: RoutePlannerRoute, point: RoutePlannerPoint) -> Unit,
  onExportRouteToRaid: (route: RoutePlannerRoute) -> Unit,
  onScheduleRelocateAll: (route: RoutePlannerRoute, delayMinutes: Int) -> Unit,
  onModalLayer: (layer: (@Composable () -> Unit)?) -> Unit,
) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val openTick by panelOpenTick.collectAsState()
  var lastLoadedTick by remember { mutableIntStateOf(-1) }
  var teamId by remember { mutableStateOf<String?>(null) }
  var canCreate by remember { mutableStateOf(false) }
  var canView by remember { mutableStateOf(false) }
  val routes = remember { mutableStateListOf<RoutePlannerRoute>() }
  var showCreateDialog by remember { mutableStateOf(false) }
  var editingRoute by remember { mutableStateOf<RoutePlannerRoute?>(null) }
  var deletingRoute by remember { mutableStateOf<RoutePlannerRoute?>(null) }
  var relocateAllConfirmRoute by remember { mutableStateOf<RoutePlannerRoute?>(null) }
  var editPointTarget by remember { mutableStateOf<RoutePointEditTarget?>(null) }
  var deletePointTarget by remember { mutableStateOf<RoutePointEditTarget?>(null) }
  var searchQuery by remember { mutableStateOf("") }
  var typeFilter by remember { mutableStateOf<RoutePlannerType?>(null) }
  var scheduleRelocateRoute by remember { mutableStateOf<RoutePlannerRoute?>(null) }

  fun reload() {
    teamId = RoutePlannerAccess.resolveTeamId(context)
    canCreate = RoutePlannerAccess.canCreateRoutes(context)
    canView = RoutePlannerAccess.canViewRoutes(context)
    routes.clear()
    val tid = teamId
    if (!tid.isNullOrBlank()) {
      routes.addAll(RoutePlannerStore.list(context, tid))
    }
  }

  val filteredRoutes = remember(routes.toList(), searchQuery, typeFilter) {
    val q = searchQuery.trim().lowercase()
    routes.filter { route ->
      val typeOk = typeFilter == null || route.type == typeFilter
      if (!typeOk) return@filter false
      if (q.isEmpty()) return@filter true
      route.name.lowercase().contains(q) ||
        route.points.any { point ->
          point.memberName.lowercase().contains(q)
        }
    }
  }

  fun afterMutation(tid: String) {
    reload()
    if (canCreate) {
      scope.launch {
        RoutePlannerSync.push(context, tid)
        reload()
      }
    }
  }

  SideEffect {
    if (openTick != lastLoadedTick) {
      reload()
      lastLoadedTick = openTick
      val tid = teamId?.trim().orEmpty()
      if (tid.isNotEmpty()) {
        scope.launch {
          if (RoutePlannerSync.pullIfNewer(context, tid)) {
            reload()
          }
        }
      }
    }
  }

  val onlineCountForRelocate = remember(relocateAllConfirmRoute?.id, openTick) {
    OverlayTeamPresenceCache.peek(teamId.orEmpty())?.ingame?.size ?: 0
  }

  SideEffect {
    onModalLayer(
      when {
        showCreateDialog -> {
          {
            CreateRouteDialog(
              onDismiss = { showCreateDialog = false },
              onConfirm = { name, type ->
                val tid = teamId?.trim().orEmpty()
                if (tid.isEmpty()) return@CreateRouteDialog
                val route = runCatching { RoutePlannerRoute.create(name, type) }.getOrNull()
                  ?: return@CreateRouteDialog
                if (RoutePlannerStore.add(context, tid, route)) {
                  afterMutation(tid)
                  Toast.makeText(
                    context,
                    context.getString(R.string.overlay_route_planner_created, route.name),
                    Toast.LENGTH_SHORT,
                  ).show()
                }
                showCreateDialog = false
              },
            )
          }
        }
        editingRoute != null -> {
          val route = editingRoute!!
          {
            CreateRouteDialog(
              titleRes = R.string.overlay_route_route_edit_title,
              confirmRes = R.string.overlay_route_planner_save,
              initialName = route.name,
              initialType = route.type,
              onDismiss = { editingRoute = null },
              onConfirm = { name, type ->
                val tid = teamId?.trim().orEmpty()
                if (tid.isEmpty()) return@CreateRouteDialog
                if (RoutePlannerStore.updateRoute(context, tid, route.id, name, type) != null) {
                  afterMutation(tid)
                  Toast.makeText(context, R.string.overlay_route_route_updated, Toast.LENGTH_SHORT).show()
                }
                editingRoute = null
              },
            )
          }
        }
        deletingRoute != null -> {
          val route = deletingRoute!!
          {
            DeleteRouteConfirmDialog(
              route = route,
              onDismiss = { deletingRoute = null },
              onConfirm = {
                val tid = teamId?.trim().orEmpty()
                if (tid.isNotEmpty()) {
                  RoutePlannerStore.deleteRoute(context, tid, route.id)
                  afterMutation(tid)
                  Toast.makeText(context, R.string.overlay_route_route_deleted, Toast.LENGTH_SHORT).show()
                }
                deletingRoute = null
              },
            )
          }
        }
        relocateAllConfirmRoute != null -> {
          val route = relocateAllConfirmRoute!!
          {
            RelocateAllConfirmDialog(
              route = route,
              onlineInOverlay = onlineCountForRelocate,
              onDismiss = { relocateAllConfirmRoute = null },
              onConfirm = {
                relocateAllConfirmRoute = null
                onRelocateAll(route)
              },
              onSchedule = {
                relocateAllConfirmRoute = null
                scheduleRelocateRoute = route
              },
            )
          }
        }
        scheduleRelocateRoute != null -> {
          val route = scheduleRelocateRoute!!
          {
            ScheduleRelocateDialog(
              route = route,
              onDismiss = { scheduleRelocateRoute = null },
              onConfirm = { minutes ->
                scheduleRelocateRoute = null
                onScheduleRelocateAll(route, minutes)
              },
            )
          }
        }
        editPointTarget != null -> {
          val target = editPointTarget!!
          {
            EditRoutePointDialog(
              point = target.point,
              onDismiss = { editPointTarget = null },
              onSaveMember = { member ->
                val tid = teamId?.trim().orEmpty()
                if (tid.isEmpty()) return@EditRoutePointDialog
                val updated = target.point.withMember(member.id, member.name)
                if (RoutePlannerStore.updatePoint(context, tid, target.route.id, updated) != null) {
                  afterMutation(tid)
                  Toast.makeText(context, R.string.overlay_route_point_updated, Toast.LENGTH_SHORT).show()
                }
                editPointTarget = null
              },
              onRepositionOnMap = {
                editPointTarget = null
                onRepositionPointOnMap(target.route, target.point)
              },
              onDelete = {
                editPointTarget = null
                deletePointTarget = target
              },
            )
          }
        }
        deletePointTarget != null -> {
          val target = deletePointTarget!!
          {
            DeleteRoutePointConfirmDialog(
              target = target,
              onDismiss = { deletePointTarget = null },
              onConfirm = {
                val tid = teamId?.trim().orEmpty()
                if (tid.isNotEmpty()) {
                  RoutePlannerStore.deletePoint(context, tid, target.route.id, target.point.id)
                  afterMutation(tid)
                  Toast.makeText(context, R.string.overlay_route_point_deleted, Toast.LENGTH_SHORT).show()
                }
                deletePointTarget = null
              },
            )
          }
        }
        else -> null
      },
    )
  }

  Column(
    modifier = Modifier.fillMaxWidth(),
    verticalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    when {
      !canView || teamId.isNullOrBlank() -> {
        Text(
          text = contextString(R.string.overlay_route_planner_no_team),
          color = ComposeColor(0xFF90A4AE),
          fontSize = 13.sp,
          lineHeight = 18.sp,
        )
      }
      else -> {
        if (!canCreate) {
          Text(
            text = contextString(R.string.overlay_route_planner_view_hint),
            color = ComposeColor(0xFF78909C),
            fontSize = 12.sp,
            lineHeight = 16.sp,
          )
        }
        OutlinedTextField(
          value = searchQuery,
          onValueChange = { searchQuery = it },
          modifier = Modifier.fillMaxWidth(),
          singleLine = true,
          placeholder = {
            Text(
              contextString(R.string.overlay_route_planner_search_hint),
              color = ComposeColor(0xFF78909C),
              fontSize = 13.sp,
            )
          },
          textStyle = androidx.compose.ui.text.TextStyle(
            fontSize = 14.sp,
            color = ComposeColor(0xFFE8EAED),
          ),
          colors = plannerFieldColors(),
          shape = RoundedCornerShape(10.dp),
        )
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          RouteTypeFilterChip(
            label = contextString(R.string.overlay_route_planner_filter_all),
            selected = typeFilter == null,
            onClick = { typeFilter = null },
          )
          RouteTypeFilterChip(
            label = contextString(R.string.overlay_route_planner_type_pvp),
            selected = typeFilter == RoutePlannerType.PVP,
            onClick = { typeFilter = RoutePlannerType.PVP },
          )
          RouteTypeFilterChip(
            label = contextString(R.string.overlay_route_planner_type_pve),
            selected = typeFilter == RoutePlannerType.PVE,
            onClick = { typeFilter = RoutePlannerType.PVE },
          )
        }
        when {
          routes.isEmpty() -> {
            Text(
              text = contextString(R.string.overlay_route_planner_empty),
              color = ComposeColor(0xFFE0E0E0),
              fontSize = 14.sp,
              fontWeight = FontWeight.Medium,
            )
            Text(
              text = contextString(R.string.overlay_route_planner_empty_hint),
              color = ComposeColor(0xFF90A4AE),
              fontSize = 12.sp,
              lineHeight = 16.sp,
            )
          }
          filteredRoutes.isEmpty() -> {
            Text(
              text = contextString(R.string.overlay_route_planner_search_empty),
              color = ComposeColor(0xFF90A4AE),
              fontSize = 13.sp,
              lineHeight = 18.sp,
            )
          }
          else -> {
            Text(
              text = contextString(R.string.overlay_route_planner_count, filteredRoutes.size),
              color = ComposeColor(0xFF90A4AE),
              fontSize = 12.sp,
            )
            LazyColumn(
              modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 280.dp),
              verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
              items(filteredRoutes, key = { it.id }) { route ->
                RoutePlannerListItem(
                  route = route,
                  canManage = canCreate,
                  onFlyToPoint = onFlyToPoint,
                  onDirectToPoint = onDirectTeleport,
                  onRelocateAll = { relocateAllConfirmRoute = route },
                  onEditPoint = { point -> editPointTarget = RoutePointEditTarget(route, point) },
                  onEditRoute = { editingRoute = route },
                  onDuplicateRoute = {
                    val tid = teamId?.trim().orEmpty()
                    if (tid.isNotEmpty() && RoutePlannerStore.duplicateRoute(context, tid, route.id) != null) {
                      afterMutation(tid)
                      Toast.makeText(context, R.string.overlay_route_route_duplicated, Toast.LENGTH_SHORT).show()
                    }
                  },
                  onDeleteRoute = { deletingRoute = route },
                  onExportToRaid = { onExportRouteToRaid(route) },
                  onMovePoint = { point, delta ->
                    val tid = teamId?.trim().orEmpty()
                    if (tid.isNotEmpty() && RoutePlannerStore.movePoint(context, tid, route.id, point.id, delta) != null) {
                      afterMutation(tid)
                    }
                  },
                )
              }
            }
          }
        }
      }
    }

    if (canCreate && !teamId.isNullOrBlank()) {
      Button(
        onClick = { showCreateDialog = true },
        modifier = Modifier.fillMaxWidth(),
        colors = ButtonDefaults.buttonColors(
          containerColor = ComposeColor(0xFF3949AB),
          contentColor = ComposeColor.White,
        ),
        shape = RoundedCornerShape(10.dp),
      ) {
        Text(
          text = contextString(R.string.overlay_route_planner_create),
          fontSize = 14.sp,
          fontWeight = FontWeight.Medium,
        )
      }
    }
  }
}

private fun loadAllianceMembers(context: android.content.Context): List<AllianceMember> =
  RoutePlannerAllianceMembers.load(context)

@Composable
private fun PlannerModalCard(
  modifier: Modifier = Modifier,
  content: @Composable ColumnScope.() -> Unit,
) {
  Surface(
    modifier = modifier.widthIn(min = 300.dp, max = 340.dp),
    shape = RoundedCornerShape(18.dp),
    color = ComposeColor(0xF218222F),
    border = androidx.compose.foundation.BorderStroke(1.dp, ComposeColor(0x33FFFFFF)),
    shadowElevation = 18.dp,
    tonalElevation = 6.dp,
  ) {
    Column(
      modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
      verticalArrangement = Arrangement.spacedBy(14.dp),
      content = content,
    )
  }
}

@Composable
private fun PlannerModalHeader(
  title: String,
  subtitle: String? = null,
) {
  Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
    Box(
      modifier = Modifier
        .widthIn(min = 32.dp, max = 48.dp)
        .height(3.dp)
        .clip(RoundedCornerShape(2.dp))
        .background(
          Brush.horizontalGradient(
            colors = listOf(ComposeColor(0xFF5C6BC0), ComposeColor(0xFF2E7D6E)),
          ),
        ),
    )
    Text(
      text = title,
      color = ComposeColor(0xFFF1F3F6),
      fontSize = 17.sp,
      fontWeight = FontWeight.SemiBold,
      lineHeight = 22.sp,
    )
    if (!subtitle.isNullOrBlank()) {
      Text(
        text = subtitle,
        color = ComposeColor(0xFF90A4AE),
        fontSize = 12.sp,
        lineHeight = 16.sp,
      )
    }
  }
}

@Composable
private fun PlannerModalActions(
  confirmLabel: String,
  onDismiss: () -> Unit,
  onConfirm: () -> Unit,
  confirmEnabled: Boolean = true,
  confirmDanger: Boolean = false,
) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.spacedBy(10.dp),
  ) {
    OutlinedButton(
      onClick = onDismiss,
      modifier = Modifier.weight(1f),
      shape = RoundedCornerShape(10.dp),
      colors = ButtonDefaults.outlinedButtonColors(contentColor = ComposeColor(0xFFB0BEC5)),
      border = androidx.compose.foundation.BorderStroke(1.dp, ComposeColor(0x44FFFFFF)),
    ) {
      Text(contextString(R.string.overlay_route_planner_cancel), fontSize = 13.sp)
    }
    Button(
      onClick = onConfirm,
      enabled = confirmEnabled,
      modifier = Modifier.weight(1f),
      colors = ButtonDefaults.buttonColors(
        containerColor = if (confirmDanger) ComposeColor(0xFFC62828) else ComposeColor(0xFF3949AB),
        contentColor = ComposeColor.White,
        disabledContainerColor = ComposeColor(0xFF2A3038),
        disabledContentColor = ComposeColor(0xFF6B7280),
      ),
      shape = RoundedCornerShape(10.dp),
    ) {
      Text(confirmLabel, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
  }
}

@Composable
private fun DeleteRoutePointConfirmDialog(
  target: RoutePointEditTarget,
  onDismiss: () -> Unit,
  onConfirm: () -> Unit,
) {
  OverlayFloatingDialog(onDismissRequest = onDismiss) {
    PlannerModalCard {
      PlannerModalHeader(
        title = contextString(R.string.overlay_route_point_delete),
        subtitle = contextString(R.string.overlay_route_point_delete_confirm, target.point.memberName),
      )
      PlannerModalActions(
        confirmLabel = contextString(R.string.overlay_route_point_delete),
        onDismiss = onDismiss,
        onConfirm = onConfirm,
        confirmDanger = true,
      )
    }
  }
}

@Composable
private fun EditRoutePointDialog(
  point: RoutePlannerPoint,
  onDismiss: () -> Unit,
  onSaveMember: (AllianceMember) -> Unit,
  onRepositionOnMap: () -> Unit,
  onDelete: () -> Unit,
) {
  val context = LocalContext.current
  val members = remember { loadAllianceMembers(context) }
  var selectedMemberId by remember(point.id) {
    mutableStateOf(
      point.memberId?.takeIf { id -> members.any { it.id == id } }
        ?: members.firstOrNull { it.name.equals(point.memberName, ignoreCase = true) }?.id,
    )
  }

  OverlayFloatingDialog(onDismissRequest = onDismiss) {
    PlannerModalCard(
      modifier = Modifier.heightIn(max = 420.dp),
    ) {
      PlannerModalHeader(
        title = contextString(R.string.overlay_route_point_edit_title),
        subtitle = contextString(
          R.string.overlay_route_point_coords,
          point.sid,
          point.x,
          point.y,
          point.memberName,
        ),
      )
      Text(
        text = contextString(R.string.overlay_route_point_edit_member),
        color = ComposeColor(0xFF78909C),
        fontSize = 12.sp,
      )
      if (members.isEmpty()) {
        RoutePlannerMemberPickerList(
          members = emptyList(),
          selectedMemberId = null,
          onSelect = {},
          emptyText = contextString(R.string.overlay_route_assign_no_members),
        )
      } else {
        RoutePlannerMemberPickerList(
          members = members,
          selectedMemberId = selectedMemberId,
          onSelect = { selectedMemberId = it },
          emptyText = contextString(R.string.overlay_route_assign_no_members),
        )
      }
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        OutlinedButton(
          onClick = onRepositionOnMap,
          modifier = Modifier.weight(1f),
          shape = RoundedCornerShape(8.dp),
          colors = ButtonDefaults.outlinedButtonColors(contentColor = ComposeColor(0xFF81D4FA)),
        ) {
          Text(contextString(R.string.overlay_route_point_edit_on_map), fontSize = 12.sp)
        }
        OutlinedButton(
          onClick = onDelete,
          modifier = Modifier.weight(1f),
          shape = RoundedCornerShape(8.dp),
          colors = ButtonDefaults.outlinedButtonColors(contentColor = ComposeColor(0xFFE57373)),
        ) {
          Text(contextString(R.string.overlay_route_point_delete), fontSize = 12.sp)
        }
      }
      PlannerModalActions(
        confirmLabel = contextString(R.string.overlay_route_assign_confirm),
        onDismiss = onDismiss,
        onConfirm = {
          val member = members.firstOrNull { it.id == selectedMemberId } ?: return@PlannerModalActions
          onSaveMember(member)
        },
        confirmEnabled = selectedMemberId != null && members.isNotEmpty(),
      )
    }
  }
}

@Composable
private fun RouteTypeFilterChip(
  label: String,
  selected: Boolean,
  onClick: () -> Unit,
) {
  val bg = if (selected) ComposeColor(0xFF3949AB) else ComposeColor(0x22FFFFFF)
  val fg = if (selected) ComposeColor.White else ComposeColor(0xFFB0BEC5)
  Box(
    modifier = Modifier
      .clip(RoundedCornerShape(8.dp))
      .background(bg)
      .clickable(onClick = onClick)
      .padding(horizontal = 10.dp, vertical = 6.dp),
  ) {
    Text(text = label, color = fg, fontSize = 12.sp, fontWeight = FontWeight.Medium)
  }
}

@Composable
private fun RoutePlannerListItem(
  route: RoutePlannerRoute,
  canManage: Boolean,
  onFlyToPoint: (x: Int, y: Int, serverNumber: Int) -> Unit,
  onDirectToPoint: (x: Int, y: Int, serverNumber: Int) -> Unit,
  onRelocateAll: () -> Unit,
  onEditPoint: (RoutePlannerPoint) -> Unit,
  onEditRoute: () -> Unit,
  onDuplicateRoute: () -> Unit,
  onDeleteRoute: () -> Unit,
  onExportToRaid: () -> Unit,
  onMovePoint: (RoutePlannerPoint, delta: Int) -> Unit,
) {
  val context = LocalContext.current
  var expanded by remember(route.id) { mutableStateOf(route.points.isNotEmpty()) }
  val typeLabelRes = when (route.type) {
    RoutePlannerType.PVP -> R.string.overlay_route_planner_type_pvp
    RoutePlannerType.PVE -> R.string.overlay_route_planner_type_pve
  }
  val typeColor = when (route.type) {
    RoutePlannerType.PVP -> ComposeColor(0xFFE57373)
    RoutePlannerType.PVE -> ComposeColor(0xFF81C784)
  }
  Column(
    modifier = Modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(10.dp))
      .background(ComposeColor(0xFF1A222E))
      .border(1.dp, ComposeColor(0x33445566), RoundedCornerShape(10.dp))
      .clickable { expanded = !expanded }
      .padding(horizontal = 10.dp, vertical = 9.dp),
    verticalArrangement = Arrangement.spacedBy(6.dp),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = route.name,
          color = ComposeColor(0xFFECEFF1),
          fontSize = 14.sp,
          fontWeight = FontWeight.Medium,
          maxLines = 2,
          overflow = TextOverflow.Ellipsis,
        )
        if (route.points.isNotEmpty()) {
          Text(
            text = contextString(R.string.overlay_route_points_count, route.points.size),
            color = ComposeColor(0xFF78909C),
            fontSize = 11.sp,
          )
        }
      }
      Text(
        text = contextString(typeLabelRes),
        color = typeColor,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier
          .padding(start = 8.dp)
          .clip(RoundedCornerShape(6.dp))
          .background(typeColor.copy(alpha = 0.15f))
          .padding(horizontal = 8.dp, vertical = 4.dp),
      )
    }
    if (expanded && route.points.isNotEmpty()) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        OutlinedButton(
          onClick = onExportToRaid,
          modifier = Modifier.weight(1f),
          shape = RoundedCornerShape(8.dp),
          colors = ButtonDefaults.outlinedButtonColors(contentColor = ComposeColor(0xFF80CBC4)),
        ) {
          Text(contextString(R.string.overlay_route_route_export_raid), fontSize = 11.sp)
        }
        if (canManage) {
          OutlinedButton(
            onClick = onEditRoute,
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(8.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = ComposeColor(0xFFCE93D8)),
          ) {
            Text(contextString(R.string.overlay_route_route_edit), fontSize = 11.sp)
          }
        }
      }
      if (canManage) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          OutlinedButton(
            onClick = onDuplicateRoute,
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(8.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = ComposeColor(0xFF81D4FA)),
          ) {
            Text(contextString(R.string.overlay_route_route_duplicate), fontSize = 11.sp)
          }
          OutlinedButton(
            onClick = onDeleteRoute,
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(8.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = ComposeColor(0xFFE57373)),
          ) {
            Text(contextString(R.string.overlay_route_route_delete), fontSize = 11.sp)
          }
        }
        Button(
          onClick = onRelocateAll,
          modifier = Modifier.fillMaxWidth(),
          colors = ButtonDefaults.buttonColors(
            containerColor = ComposeColor(0xFF047857),
            contentColor = ComposeColor.White,
          ),
          shape = RoundedCornerShape(8.dp),
        ) {
          Text(
            text = contextString(R.string.overlay_route_relocate_all),
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
          )
        }
      }
      route.orderedPoints().forEachIndexed { index, point ->
        val ordered = route.orderedPoints()
        RoutePlannerPointRow(
          point = point,
          stepIndex = index + 1,
          isMine = RoutePlannerAccess.isPointAssignedToMe(context, point),
          status = RoutePlannerPointStatus.resolve(context, point),
          canEdit = canManage,
          canMoveUp = canManage && index > 0,
          canMoveDown = canManage && index < ordered.lastIndex,
          onFly = { onFlyToPoint(point.x, point.y, point.sid) },
          onTeleport = { onDirectToPoint(point.x, point.y, point.sid) },
          onEdit = { onEditPoint(point) },
          onMoveUp = { onMovePoint(point, -1) },
          onMoveDown = { onMovePoint(point, 1) },
        )
      }
    } else if (expanded && canManage) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        OutlinedButton(
          onClick = onEditRoute,
          modifier = Modifier.weight(1f),
          shape = RoundedCornerShape(8.dp),
          colors = ButtonDefaults.outlinedButtonColors(contentColor = ComposeColor(0xFFCE93D8)),
        ) {
          Text(contextString(R.string.overlay_route_route_edit), fontSize = 11.sp)
        }
        OutlinedButton(
          onClick = onDeleteRoute,
          modifier = Modifier.weight(1f),
          shape = RoundedCornerShape(8.dp),
          colors = ButtonDefaults.outlinedButtonColors(contentColor = ComposeColor(0xFFE57373)),
        ) {
          Text(contextString(R.string.overlay_route_route_delete), fontSize = 11.sp)
        }
      }
    }
  }
}

@Composable
private fun RoutePlannerPointRow(
  point: RoutePlannerPoint,
  stepIndex: Int,
  isMine: Boolean,
  status: RoutePointStatus,
  canEdit: Boolean,
  canMoveUp: Boolean,
  canMoveDown: Boolean,
  onFly: () -> Unit,
  onTeleport: () -> Unit,
  onEdit: () -> Unit,
  onMoveUp: () -> Unit,
  onMoveDown: () -> Unit,
) {
  val borderColor = if (isMine) ComposeColor(0xFF7986CB) else ComposeColor(0x22334455)
  val bgColor = if (isMine) ComposeColor(0xFF1A2438) else ComposeColor(0xFF121820)
  Column(
    modifier = Modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(8.dp))
      .background(bgColor)
      .border(1.dp, borderColor, RoundedCornerShape(8.dp))
      .padding(horizontal = 8.dp, vertical = 7.dp),
    verticalArrangement = Arrangement.spacedBy(6.dp),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Text(
        text = contextString(R.string.overlay_route_point_step, stepIndex),
        color = ComposeColor(0xFF90CAF9),
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
      )
      if (isMine) {
        Text(
          text = contextString(R.string.overlay_route_point_mine),
          color = ComposeColor(0xFFCE93D8),
          fontSize = 11.sp,
          fontWeight = FontWeight.Medium,
        )
      }
    }
    val statusLabel = when (status) {
      RoutePointStatus.OnPlace -> contextString(R.string.overlay_route_point_status_on_place)
      RoutePointStatus.NotMoved -> contextString(R.string.overlay_route_point_status_not_moved)
      RoutePointStatus.Unknown -> null
    }
    if (statusLabel != null) {
      Text(
        text = statusLabel,
        color = if (status == RoutePointStatus.OnPlace) ComposeColor(0xFF81C784) else ComposeColor(0xFFE57373),
        fontSize = 11.sp,
      )
    }
    Text(
      text = contextString(
        R.string.overlay_route_point_coords,
        point.sid,
        point.x,
        point.y,
        point.memberName,
      ),
      color = ComposeColor(0xFFB0BEC5),
      fontSize = 12.sp,
      lineHeight = 16.sp,
    )
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      OutlinedButton(
        onClick = onFly,
        modifier = Modifier.weight(1f),
        shape = RoundedCornerShape(8.dp),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = ComposeColor(0xFF81D4FA)),
      ) {
        Text(contextString(R.string.overlay_route_point_fly), fontSize = 12.sp)
      }
      Button(
        onClick = onTeleport,
        modifier = Modifier.weight(1f),
        colors = ButtonDefaults.buttonColors(
          containerColor = ComposeColor(0xFF3949AB),
          contentColor = ComposeColor.White,
        ),
        shape = RoundedCornerShape(8.dp),
      ) {
        Text(contextString(R.string.overlay_route_point_teleport), fontSize = 12.sp)
      }
    }
    if (canEdit) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        OutlinedButton(
          onClick = onMoveUp,
          enabled = canMoveUp,
          modifier = Modifier.weight(1f),
          shape = RoundedCornerShape(8.dp),
          colors = ButtonDefaults.outlinedButtonColors(contentColor = ComposeColor(0xFF90CAF9)),
        ) {
          Text(contextString(R.string.overlay_route_point_move_up), fontSize = 11.sp)
        }
        OutlinedButton(
          onClick = onMoveDown,
          enabled = canMoveDown,
          modifier = Modifier.weight(1f),
          shape = RoundedCornerShape(8.dp),
          colors = ButtonDefaults.outlinedButtonColors(contentColor = ComposeColor(0xFF90CAF9)),
        ) {
          Text(contextString(R.string.overlay_route_point_move_down), fontSize = 11.sp)
        }
      }
      OutlinedButton(
        onClick = onEdit,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = ComposeColor(0xFFCE93D8)),
      ) {
        Text(contextString(R.string.overlay_route_point_edit), fontSize = 12.sp)
      }
    }
  }
}

@Composable
private fun RelocateAllConfirmDialog(
  route: RoutePlannerRoute,
  onlineInOverlay: Int,
  onDismiss: () -> Unit,
  onConfirm: () -> Unit,
  onSchedule: () -> Unit,
) {
  val stats = RoutePlannerRelocateStats.forRoute(route, emptySet())
  OverlayFloatingDialog(onDismissRequest = onDismiss) {
    PlannerModalCard {
      PlannerModalHeader(
        title = contextString(R.string.overlay_route_relocate_all_confirm_title),
        subtitle = contextString(
          R.string.overlay_route_relocate_all_confirm_body,
          route.points.size,
          route.name,
        ),
      )
      Text(
        text = contextString(
          R.string.overlay_route_relocate_all_hint,
          stats.totalPoints,
          onlineInOverlay,
          stats.unassignedMembers,
        ),
        color = ComposeColor(0xFF78909C),
        fontSize = 12.sp,
        lineHeight = 16.sp,
      )
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        OutlinedButton(
          onClick = onSchedule,
          modifier = Modifier.weight(1f),
          shape = RoundedCornerShape(10.dp),
          colors = ButtonDefaults.outlinedButtonColors(contentColor = ComposeColor(0xFF80CBC4)),
        ) {
          Text(contextString(R.string.overlay_route_relocate_schedule), fontSize = 12.sp)
        }
        Button(
          onClick = onConfirm,
          modifier = Modifier.weight(1f),
          colors = ButtonDefaults.buttonColors(
            containerColor = ComposeColor(0xFF047857),
            contentColor = ComposeColor.White,
          ),
          shape = RoundedCornerShape(10.dp),
        ) {
          Text(contextString(R.string.overlay_route_relocate_all), fontSize = 12.sp)
        }
      }
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
      ) {
        TextButton(onClick = onDismiss) {
          Text(contextString(R.string.overlay_route_planner_cancel), color = ComposeColor(0xFF90A4AE))
        }
      }
    }
  }
}

@Composable
private fun ScheduleRelocateDialog(
  route: RoutePlannerRoute,
  onDismiss: () -> Unit,
  onConfirm: (minutes: Int) -> Unit,
) {
  var minutesText by remember { mutableStateOf("5") }
  OverlayFloatingDialog(onDismissRequest = onDismiss) {
    PlannerModalCard {
      PlannerModalHeader(
        title = contextString(R.string.overlay_route_relocate_schedule_title),
        subtitle = contextString(R.string.overlay_route_relocate_schedule_body),
      )
      OutlinedTextField(
        value = minutesText,
        onValueChange = { minutesText = it.filter { ch -> ch.isDigit() }.take(3) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        label = { Text("мин", fontSize = 12.sp) },
        colors = plannerFieldColors(),
        shape = RoundedCornerShape(10.dp),
      )
      PlannerModalActions(
        confirmLabel = contextString(R.string.overlay_route_relocate_schedule_confirm),
        onDismiss = onDismiss,
        onConfirm = {
          val minutes = minutesText.toIntOrNull()?.coerceIn(1, 180) ?: return@PlannerModalActions
          onConfirm(minutes)
        },
        confirmEnabled = minutesText.toIntOrNull()?.coerceIn(1, 180) != null,
      )
    }
  }
}

@Composable
private fun DeleteRouteConfirmDialog(
  route: RoutePlannerRoute,
  onDismiss: () -> Unit,
  onConfirm: () -> Unit,
) {
  OverlayFloatingDialog(onDismissRequest = onDismiss) {
    PlannerModalCard {
      PlannerModalHeader(
        title = contextString(R.string.overlay_route_route_delete),
        subtitle = contextString(R.string.overlay_route_route_delete_confirm, route.name),
      )
      PlannerModalActions(
        confirmLabel = contextString(R.string.overlay_route_route_delete),
        onDismiss = onDismiss,
        onConfirm = onConfirm,
        confirmDanger = true,
      )
    }
  }
}

@Composable
private fun CreateRouteDialog(
  onDismiss: () -> Unit,
  onConfirm: (name: String, type: RoutePlannerType) -> Unit,
  titleRes: Int = R.string.overlay_route_planner_create_title,
  confirmRes: Int = R.string.overlay_route_planner_confirm,
  initialName: String = "",
  initialType: RoutePlannerType = RoutePlannerType.PVP,
) {
  var name by remember(initialName) { mutableStateOf(initialName) }
  var selectedType by remember(initialType) { mutableStateOf(initialType) }
  var nameError by remember { mutableStateOf(false) }

  OverlayFloatingDialog(onDismissRequest = onDismiss) {
    PlannerModalCard {
      PlannerModalHeader(
        title = contextString(titleRes),
        subtitle = if (titleRes == R.string.overlay_route_planner_create_title) {
          contextString(R.string.overlay_route_planner_empty_hint)
        } else {
          null
        },
      )

      OutlinedTextField(
        value = name,
        onValueChange = {
          name = it.take(64)
          nameError = false
        },
        modifier = Modifier.fillMaxWidth(),
        label = { Text(contextString(R.string.overlay_route_planner_name_label), fontSize = 12.sp) },
        singleLine = true,
        isError = nameError,
        supportingText = if (nameError) {
          { Text(contextString(R.string.overlay_route_planner_name_required), color = ComposeColor(0xFFE57373)) }
        } else {
          null
        },
        colors = plannerFieldColors(),
        shape = RoundedCornerShape(10.dp),
      )

      Text(
        text = contextString(R.string.overlay_route_planner_type_label),
        color = ComposeColor(0xFF90A4AE),
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium,
      )

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        RoutePlannerType.entries.forEach { type ->
          val selected = type == selectedType
          val labelRes = when (type) {
            RoutePlannerType.PVP -> R.string.overlay_route_planner_type_pvp
            RoutePlannerType.PVE -> R.string.overlay_route_planner_type_pve
          }
          val accent = when (type) {
            RoutePlannerType.PVP -> ComposeColor(0xFFE57373)
            RoutePlannerType.PVE -> ComposeColor(0xFF81C784)
          }
          Box(
            modifier = Modifier
              .weight(1f)
              .clip(RoundedCornerShape(10.dp))
              .background(
                if (selected) accent.copy(alpha = 0.22f) else ComposeColor(0xFF1A222E),
              )
              .border(
                1.dp,
                if (selected) accent.copy(alpha = 0.85f) else ComposeColor(0x33445566),
                RoundedCornerShape(10.dp),
              )
              .clickable { selectedType = type }
              .padding(vertical = 11.dp),
            contentAlignment = Alignment.Center,
          ) {
            Text(
              text = contextString(labelRes),
              color = if (selected) ComposeColor.White else ComposeColor(0xFFB0BEC5),
              fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
              fontSize = 13.sp,
            )
          }
        }
      }

      PlannerModalActions(
        confirmLabel = contextString(confirmRes),
        onDismiss = onDismiss,
        onConfirm = {
          if (name.trim().isEmpty()) {
            nameError = true
            return@PlannerModalActions
          }
          onConfirm(name, selectedType)
        },
      )
    }
  }
}

@Composable
private fun plannerFieldColors() = OutlinedTextFieldDefaults.colors(
  focusedTextColor = ComposeColor(0xFFECEFF1),
  unfocusedTextColor = ComposeColor(0xFFECEFF1),
  focusedBorderColor = ComposeColor(0xFF7986CB),
  unfocusedBorderColor = ComposeColor(0xFF455A64),
  focusedLabelColor = ComposeColor(0xFF9FA8DA),
  unfocusedLabelColor = ComposeColor(0xFF78909C),
  cursorColor = ComposeColor(0xFF7986CB),
)

@Composable
private fun RallyPointRow(
  rallyPoint: AllianceRallyPoint?,
  onFly: (x: Int, y: Int, serverNumber: Int) -> Unit,
) {
  val activeRally = rallyPoint?.takeIf { it.isValid() }
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(10.dp))
      .background(ComposeColor(0xFF1A222E))
      .border(1.dp, ComposeColor(0x334CAF50), RoundedCornerShape(10.dp))
      .then(
        if (activeRally != null) {
          Modifier.clickable {
            onFly(activeRally.x, activeRally.y, activeRally.serverNumber)
          }
        } else {
          Modifier
        },
      )
      .padding(horizontal = 10.dp, vertical = 8.dp),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Column(modifier = Modifier.weight(1f)) {
      Text(
        text = contextString(R.string.overlay_teleport_rally_label),
        color = ComposeColor(0xFF81C784),
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium,
      )
      Text(
        text = if (activeRally != null) {
          contextString(
            R.string.overlay_teleport_rally_coords,
            activeRally.serverNumber,
            activeRally.x,
            activeRally.y,
          )
        } else {
          contextString(R.string.overlay_teleport_rally_unknown)
        },
        color = ComposeColor(0xFFE0E0E0),
        fontSize = 13.sp,
      )
    }
    if (activeRally != null) {
      Text(
        text = contextString(R.string.overlay_teleport_rally_fly),
        color = ComposeColor(0xFF4DD0E1),
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(start = 8.dp),
      )
    }
  }
}

@Composable
private fun CoordField(
  label: String,
  value: String,
  onValueChange: (String) -> Unit,
  modifier: Modifier = Modifier,
) {
  OutlinedTextField(
    value = value,
    onValueChange = onValueChange,
    modifier = modifier,
    label = {
      Text(label, fontSize = 11.sp)
    },
    singleLine = true,
    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 14.sp),
    colors = OutlinedTextFieldDefaults.colors(
      focusedTextColor = ComposeColor(0xFFECEFF1),
      unfocusedTextColor = ComposeColor(0xFFECEFF1),
      focusedBorderColor = ComposeColor(0xFF4DB6AC),
      unfocusedBorderColor = ComposeColor(0xFF455A64),
      focusedLabelColor = ComposeColor(0xFF80CBC4),
      unfocusedLabelColor = ComposeColor(0xFF78909C),
      cursorColor = ComposeColor(0xFF4DB6AC),
    ),
    shape = RoundedCornerShape(8.dp),
  )
}

@Composable
private fun teleportActionLabel(baseRes: Int, withCountRes: Int, count: Int?): String {
  return if (count == null) {
    contextString(baseRes)
  } else {
    contextString(withCountRes, count)
  }
}

@Composable
private fun contextString(resId: Int, vararg args: Any): String {
  val ctx = LocalContext.current
  return if (args.isEmpty()) ctx.getString(resId) else ctx.getString(resId, *args)
}

private const val MIN_SERVER = 1
private const val MAX_SERVER = 9999

private data class DirectTeleportCoordTexts(
  val server: String,
  val x: String,
  val y: String,
)

private fun loadDirectTeleportCoordTexts(
  prefs: UserSettingsPreferences,
  defaultServer: Int?,
): DirectTeleportCoordTexts {
  val savedServer = prefs.getDirectTeleportServerText()
  val savedX = prefs.getDirectTeleportXText()
  val savedY = prefs.getDirectTeleportYText()
  val hasSaved = savedServer.isNotBlank() || savedX.isNotBlank() || savedY.isNotBlank()
  val server = if (hasSaved) {
    savedServer
  } else {
    defaultServer?.takeIf { it > 0 }?.toString() ?: "109"
  }
  return DirectTeleportCoordTexts(server, savedX, savedY)
}
