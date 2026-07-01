package com.lastasylum.alliance.overlay

import android.content.Context
import android.graphics.Color
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lastasylum.alliance.R
import com.lastasylum.alliance.data.settings.UserSettingsPreferences
import com.lastasylum.alliance.game.AllianceRallyPoint
import com.lastasylum.alliance.ui.theme.SquadRelayTheme

/**
 * Компактное центральное окно «Перемещение»: прямое / альянс + пункт сбора.
 */
class OverlayTeleportPanel(
  private val context: Context,
  private val mainHandler: Handler,
  private val dp: (Int) -> Int,
  private val prefs: UserSettingsPreferences,
  private val defaultServerProvider: () -> Int?,
  private val rallyPointProvider: () -> AllianceRallyPoint?,
  private val onDirectTeleport: (x: Int, y: Int, serverNumber: Int) -> Unit,
  private val onAllianceTeleport: () -> Unit,
  private val onFlyToRally: (x: Int, y: Int, serverNumber: Int) -> Unit,
) {
  private var scrim: FrameLayout? = null
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
          }
          .onFailure { e -> Log.w(TAG, "addView failed", e) }
      } else if (attachedWindowManager != windowManager) {
        runCatching { attachedWindowManager?.removeView(root) }
        attached = false
        attachedWindowManager = null
        show(windowManager)
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

  private fun buildScrim(): FrameLayout {
    val frame = FrameLayout(context).apply {
      setBackgroundColor(Color.parseColor("#99000000"))
    }
    val compose = ComposeView(context).apply {
      setContent {
        SquadRelayTheme {
          TeleportPanelContent(
            prefs = prefs,
            defaultServer = defaultServerProvider(),
            rallyPoint = rallyPointProvider(),
            onDismiss = {
              attachedWindowManager?.let { hide(it) }
            },
            onDirectTeleport = { x, y, sid ->
              prefs.setDirectTeleportCoords(x, y, sid)
              onDirectTeleport(x, y, sid)
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
          )
        }
      }
    }
    frame.addView(
      compose,
      FrameLayout.LayoutParams(
        FrameLayout.LayoutParams.MATCH_PARENT,
        FrameLayout.LayoutParams.MATCH_PARENT,
      ),
    )
    frame.setOnTouchListener { v, event ->
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
  rallyPoint: AllianceRallyPoint?,
  onDismiss: () -> Unit,
  onDirectTeleport: (x: Int, y: Int, serverNumber: Int) -> Unit,
  onAllianceTeleport: () -> Unit,
  onFlyToRally: (x: Int, y: Int, serverNumber: Int) -> Unit,
) {
  var serverText by remember {
    mutableStateOf(
      prefs.getDirectTeleportServer()?.takeIf { it > 0 }?.toString()
        ?: defaultServer?.takeIf { it > 0 }?.toString()
        ?: "109",
    )
  }
  var xText by remember {
    mutableStateOf(prefs.getDirectTeleportX()?.takeIf { it > 0 }?.toString().orEmpty())
  }
  var yText by remember {
    mutableStateOf(prefs.getDirectTeleportY()?.takeIf { it > 0 }?.toString().orEmpty())
  }

  val server = serverText.trim().toIntOrNull()
  val x = xText.trim().toIntOrNull()
  val y = yText.trim().toIntOrNull()
  val directReady = server != null && server in MIN_SERVER..MAX_SERVER &&
    x != null && x > 0 && y != null && y > 0

  val cardShape = RoundedCornerShape(16.dp)
  val cardBg = Brush.verticalGradient(
    colors = listOf(ComposeColor(0xF2141C2A), ComposeColor(0xF20B1018)),
  )

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
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      Text(
        text = contextString(R.string.overlay_teleport_title),
        color = ComposeColor(0xFFE8EAED),
        fontSize = 17.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.fillMaxWidth(),
        textAlign = TextAlign.Center,
      )

      RallyPointRow(
        rallyPoint = rallyPoint,
        onFly = onFlyToRally,
      )

      Text(
        text = contextString(R.string.overlay_teleport_direct_section),
        color = ComposeColor(0xFFB0BEC5),
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium,
      )

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        CoordField(
          label = "#",
          value = serverText,
          onValueChange = { serverText = it.filter { ch -> ch.isDigit() }.take(4) },
          modifier = Modifier.weight(0.85f),
        )
        CoordField(
          label = "X",
          value = xText,
          onValueChange = { xText = it.filter { ch -> ch.isDigit() }.take(4) },
          modifier = Modifier.weight(1f),
        )
        CoordField(
          label = "Y",
          value = yText,
          onValueChange = { yText = it.filter { ch -> ch.isDigit() }.take(4) },
          modifier = Modifier.weight(1f),
        )
      }

      Button(
        onClick = {
          if (directReady) {
            onDirectTeleport(x!!, y!!, server!!)
          }
        },
        enabled = directReady,
        modifier = Modifier.fillMaxWidth(),
        colors = ButtonDefaults.buttonColors(
          containerColor = ComposeColor(0xFF2E7D6E),
          disabledContainerColor = ComposeColor(0xFF2A3038),
          contentColor = ComposeColor.White,
          disabledContentColor = ComposeColor(0xFF6B7280),
        ),
        shape = RoundedCornerShape(10.dp),
      ) {
        Text(
          text = contextString(R.string.overlay_teleport_direct_action),
          fontSize = 14.sp,
          fontWeight = FontWeight.Medium,
        )
      }

      Spacer(modifier = Modifier.height(2.dp))

      OutlinedButton(
        onClick = onAllianceTeleport,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        colors = ButtonDefaults.outlinedButtonColors(
          contentColor = ComposeColor(0xFF80CBC4),
        ),
        border = androidx.compose.foundation.BorderStroke(1.dp, ComposeColor(0x6640A090)),
      ) {
        Text(
          text = contextString(R.string.overlay_teleport_alliance_action),
          fontSize = 14.sp,
          fontWeight = FontWeight.Medium,
        )
      }
    }
  }
}

@Composable
private fun RallyPointRow(
  rallyPoint: AllianceRallyPoint?,
  onFly: (x: Int, y: Int, serverNumber: Int) -> Unit,
) {
  val valid = rallyPoint?.isValid() == true
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(10.dp))
      .background(ComposeColor(0xFF1A222E))
      .border(1.dp, ComposeColor(0x334CAF50), RoundedCornerShape(10.dp))
      .then(
        if (valid && rallyPoint != null) {
          Modifier.clickable {
            onFly(rallyPoint.x, rallyPoint.y, rallyPoint.serverNumber)
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
        text = if (valid && rallyPoint != null) {
          contextString(
            R.string.overlay_teleport_rally_coords,
            rallyPoint.serverNumber,
            rallyPoint.x,
            rallyPoint.y,
          )
        } else {
          contextString(R.string.overlay_teleport_rally_unknown)
        },
        color = ComposeColor(0xFFE0E0E0),
        fontSize = 13.sp,
      )
    }
    if (valid) {
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
private fun contextString(resId: Int, vararg args: Any): String {
  val ctx = androidx.compose.ui.platform.LocalContext.current
  return if (args.isEmpty()) ctx.getString(resId) else ctx.getString(resId, *args)
}

private const val MIN_SERVER = 1
private const val MAX_SERVER = 9999
