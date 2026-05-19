package com.lastasylum.alliance.overlay

import android.content.Context
import android.graphics.Color
import android.graphics.Outline
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import coil.Coil
import coil.request.ErrorResult
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.lastasylum.alliance.R
import com.lastasylum.alliance.data.users.TeamMemberDto
import com.lastasylum.alliance.di.AppContainer
import com.lastasylum.alliance.data.voice.TeamVoicePresenceStore
import com.lastasylum.alliance.ui.util.isOverlayIngameNow
import com.lastasylum.alliance.ui.util.telegramAvatarUrl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Full-screen dim + card listing alliance members with [ingame] presence and a fresh ping
 * (overlay combat mode / overlay heartbeat).
 */
class OverlayAllianceOnlinePopover(
    private val context: Context,
    private val mainHandler: Handler,
    private val scope: CoroutineScope,
    private val dp: (Int) -> Int,
) {
    private var shell: FrameLayout? = null
    private var attachedWindowManager: WindowManager? = null
    private var refreshRunnable: Runnable? = null

    fun isShowing(): Boolean = shell != null

    fun hide() {
        refreshRunnable?.let { mainHandler.removeCallbacks(it) }
        refreshRunnable = null
        val host = shell ?: return
        val wm = attachedWindowManager
            ?: context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
        attachedWindowManager = null
        shell = null
        runCatching { wm?.removeView(host) }
    }

    fun toggle(
        windowManager: WindowManager,
        panelParams: WindowManager.LayoutParams,
        panelRoot: View,
        anchoredEnd: Boolean,
    ) {
        if (isShowing()) {
            hide()
            return
        }
        show(windowManager, panelParams, panelRoot, anchoredEnd)
    }

    /** Centered card on the game screen (in-game status HUD). */
    fun toggleCentered(windowManager: WindowManager) {
        if (isShowing()) {
            hide()
            return
        }
        showCentered(windowManager)
    }

    fun showCentered(windowManager: WindowManager) {
        if (isShowing()) return
        attachPopover(windowManager, centered = true, panelParams = null, panelRoot = null, anchoredEnd = false)
    }

    private fun show(
        windowManager: WindowManager,
        panelParams: WindowManager.LayoutParams,
        panelRoot: View,
        anchoredEnd: Boolean,
    ) {
        attachPopover(
            windowManager = windowManager,
            centered = false,
            panelParams = panelParams,
            panelRoot = panelRoot,
            anchoredEnd = anchoredEnd,
        )
    }

    private fun attachPopover(
        windowManager: WindowManager,
        centered: Boolean,
        panelParams: WindowManager.LayoutParams?,
        panelRoot: View?,
        anchoredEnd: Boolean,
    ) {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val screenW = context.resources.displayMetrics.widthPixels
        val screenH = context.resources.displayMetrics.heightPixels

        val popoverW = minOf(dp(280), screenW - dp(16))
        val popoverH = minOf(dp(320), screenH - dp(24))

        val cardLp = if (centered) {
            FrameLayout.LayoutParams(popoverW, popoverH).apply {
                gravity = Gravity.CENTER
            }
        } else {
            val panel = panelParams ?: return
            val root = panelRoot ?: return
            val panelW = root.width.takeIf { it > 0 } ?: dp(120)
            val panelH = root.height.takeIf { it > 0 } ?: dp(180)
            var x = if (anchoredEnd) {
                panel.x - popoverW - dp(8)
            } else {
                panel.x + panelW + dp(8)
            }
            x = x.coerceIn(dp(8), (screenW - popoverW - dp(8)).coerceAtLeast(dp(8)))
            val yBottom = (panel.y + panelH / 2 - popoverH / 2)
                .coerceIn(0, (screenH - popoverH).coerceAtLeast(0))
            FrameLayout.LayoutParams(popoverW, popoverH).apply {
                gravity = Gravity.BOTTOM or Gravity.START
                leftMargin = x
                bottomMargin = yBottom
            }
        }

        val title = TextView(context).apply {
            text = context.getString(R.string.overlay_online_title)
            setTextColor(Color.parseColor("#FFF1F5FF"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            typeface = Typeface.DEFAULT_BOLD
        }

        val subtitle = TextView(context).apply {
            text = context.getString(R.string.overlay_online_subtitle)
            setTextColor(Color.parseColor("#99B8C0D9"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f)
            setPadding(0, dp(2), 0, 0)
        }

        val refreshBtn = TextView(context).apply {
            text = "↻"
            contentDescription = context.getString(R.string.overlay_online_refresh_cd)
            setTextColor(Color.parseColor("#FFB8C0D9"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
            setPadding(dp(10), dp(4), dp(6), dp(4))
            isClickable = true
        }

        val close = TextView(context).apply {
            text = "✕"
            contentDescription = context.getString(R.string.overlay_online_close_cd)
            setTextColor(Color.parseColor("#FFB8C0D9"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            setPadding(dp(6), dp(4), dp(10), dp(4))
            isClickable = true
            setOnClickListener { hide() }
        }

        val titleCol = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            addView(title)
            addView(subtitle)
        }

        val headerRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(10), dp(10), dp(4), dp(4))
            addView(
                titleCol,
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f),
            )
            addView(refreshBtn)
            addView(close)
        }

        val status = TextView(context).apply {
            text = context.getString(R.string.overlay_online_loading)
            setTextColor(Color.parseColor("#FFB8C0D9"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setPadding(dp(14), 0, dp(14), dp(10))
        }

        val listColumn = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(10), 0, dp(10), dp(10))
        }

        val scroll = ScrollView(context).apply {
            isFillViewport = true
            isVerticalScrollBarEnabled = true
            addView(
                listColumn,
                ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ),
            )
        }

        val card = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            isClickable = true
            clipToPadding = false
            clipChildren = false
            elevation = dp(6).toFloat()
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(14).toFloat()
                setColor(Color.parseColor("#E610141E"))
                setStroke(dp(1).coerceAtLeast(1), Color.parseColor("#559B7CFF"))
            }
            addView(headerRow)
            addView(status)
            addView(
                scroll,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    0,
                    1f,
                ),
            )
            setOnClickListener { /* keep panel open; scrim closes */ }
        }

        val scrim = FrameLayout(context).apply {
            setBackgroundColor(0x66000000)
            isClickable = true
            setOnClickListener { hide() }
        }

        scrim.addView(card, cardLp)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            type,
            OverlayWindowLayout.popupWindowFlags(),
            android.graphics.PixelFormat.TRANSLUCENT,
        ).apply {
            OverlayWindowLayout.applyPopupLayoutCompat(this)
            gravity = Gravity.TOP or Gravity.START
            this.x = 0
            this.y = 0
        }

        val attach = runCatching { windowManager.addView(scrim, params) }
        if (attach.isFailure) return

        shell = scrim
        attachedWindowManager = windowManager

        val usersRepository = AppContainer.from(context.applicationContext).usersRepository

        fun fetchMembers(silent: Boolean) {
            if (!silent) {
                status.visibility = View.VISIBLE
                status.text = context.getString(R.string.overlay_online_loading)
                status.setTextColor(Color.parseColor("#FFB8C0D9"))
                listColumn.removeAllViews()
            }
            scope.launch {
                usersRepository.updatePresence("ingame")
                val result = usersRepository.listMembers(allianceCode = null, q = null, skip = 0, limit = 300)
                mainHandler.post {
                    if (shell !== scrim) return@post
                    result.onSuccess { members ->
                        val online = filterOverlayOnlineMembers(members)
                        status.visibility = View.GONE
                        listColumn.removeAllViews()
                        if (online.isEmpty()) {
                            listColumn.addView(
                                emptyLabel(context.getString(R.string.overlay_online_empty)),
                                LinearLayout.LayoutParams(
                                    LinearLayout.LayoutParams.MATCH_PARENT,
                                    LinearLayout.LayoutParams.WRAP_CONTENT,
                                ),
                            )
                        } else {
                            online.forEachIndexed { index, m ->
                                listColumn.addView(
                                    memberRow(m),
                                    LinearLayout.LayoutParams(
                                        LinearLayout.LayoutParams.MATCH_PARENT,
                                        LinearLayout.LayoutParams.WRAP_CONTENT,
                                    ).apply {
                                        topMargin = if (index > 0) dp(6) else 0
                                    },
                                )
                            }
                        }
                        scroll.scrollTo(0, 0)
                    }.onFailure {
                        if (!silent) {
                            status.visibility = View.VISIBLE
                            status.text = context.getString(R.string.overlay_online_error)
                            status.setTextColor(Color.parseColor("#FFFF8A80"))
                        }
                    }
                }
            }
        }

        refreshBtn.setOnClickListener {
            fetchMembers(silent = false)
        }

        fetchMembers(silent = false)

        val tick = object : Runnable {
            override fun run() {
                if (shell !== scrim) return
                fetchMembers(silent = true)
                mainHandler.postDelayed(this, REFRESH_INTERVAL_MS)
            }
        }
        refreshRunnable = tick
        mainHandler.postDelayed(tick, REFRESH_INTERVAL_MS)
    }

    private fun emptyLabel(text: String): TextView =
        TextView(context).apply {
            this.text = text
            setTextColor(Color.parseColor("#FFB8C0D9"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setPadding(dp(4), dp(4), dp(4), dp(4))
        }

    private fun memberRow(member: TeamMemberDto): LinearLayout {
        val inGameNow = isOverlayIngameNow(member.presenceStatus, member.lastPresenceAt)
        val voicePeer = TeamVoicePresenceStore.peers.value[member.id]
        val avatarSide = dp(36)
        val avatarUrl = telegramAvatarUrl(member.telegramUsername)
        val letter = member.username.trim().take(1).uppercase().ifBlank { "?" }

        val avatarImage = ImageView(context).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
            scaleType = ImageView.ScaleType.CENTER_CROP
            visibility = if (avatarUrl != null) View.VISIBLE else View.GONE
        }
        val avatarInitial = TextView(context).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
            gravity = Gravity.CENTER
            text = letter
            setTextColor(Color.parseColor("#F8F6FF"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            typeface = Typeface.DEFAULT_BOLD
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#5537415C"))
            }
            visibility = View.VISIBLE
        }
        val avatarHost = FrameLayout(context).apply {
            layoutParams = LinearLayout.LayoutParams(avatarSide, avatarSide).apply {
                marginEnd = dp(10)
            }
            clipToOutline = true
            outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(view: View, outline: Outline) {
                    outline.setOval(0, 0, view.width, view.height)
                }
            }
            addView(avatarImage)
            addView(avatarInitial)
        }

        if (avatarUrl != null) {
            Coil.imageLoader(context).enqueue(
                ImageRequest.Builder(context)
                    .data(avatarUrl)
                    .size(128)
                    .target(avatarImage)
                    .listener(
                        object : ImageRequest.Listener {
                            override fun onSuccess(request: ImageRequest, result: SuccessResult) {
                                avatarInitial.visibility = View.GONE
                            }

                            override fun onError(request: ImageRequest, result: ErrorResult) {
                                avatarInitial.visibility = View.VISIBLE
                                avatarImage.setImageDrawable(null)
                            }
                        },
                    )
                    .build(),
            )
        }

        val nameRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val name = TextView(context).apply {
            text = member.username
            setTextColor(Color.parseColor("#FFF1F5FF"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            maxLines = 2
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        nameRow.addView(name)
        if (inGameNow) {
            nameRow.addView(
                overlayVoiceBadge(
                    iconRes = if (voicePeer?.micOn == true) {
                        R.drawable.ic_overlay_mic_on
                    } else {
                        R.drawable.ic_overlay_mic_off
                    },
                    active = voicePeer?.micOn == true,
                    accent = Color.parseColor("#FF2E7D32"),
                ),
                LinearLayout.LayoutParams(dp(22), dp(22)).apply {
                    marginStart = dp(6)
                },
            )
            nameRow.addView(
                overlayVoiceBadge(
                    iconRes = if (voicePeer?.soundOn == true) {
                        R.drawable.ic_overlay_volume_on
                    } else {
                        R.drawable.ic_overlay_volume_off
                    },
                    active = voicePeer?.soundOn == true,
                    accent = Color.parseColor("#FF1565C0"),
                ),
                LinearLayout.LayoutParams(dp(22), dp(22)).apply {
                    marginStart = dp(4)
                },
            )
        }

        val presenceDot = View(context).apply {
            layoutParams = LinearLayout.LayoutParams(dp(10), dp(10)).apply {
                marginEnd = dp(8)
            }
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(
                    if (inGameNow) Color.parseColor("#FF2E7D32") else Color.parseColor("#66888888"),
                )
            }
        }

        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(6), dp(6), dp(6), dp(6))
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(10).toFloat()
                setColor(Color.parseColor("#33101828"))
            }
            addView(avatarHost)
            addView(presenceDot)
            addView(
                nameRow,
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f),
            )
        }
    }

    private fun overlayVoiceBadge(iconRes: Int, active: Boolean, accent: Int): FrameLayout =
        FrameLayout(context).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(7).toFloat()
                setColor(if (active) accent else Color.parseColor("#5537415C"))
            }
            addView(
                ImageView(context).apply {
                    setImageResource(iconRes)
                    scaleType = ImageView.ScaleType.CENTER_INSIDE
                    setPadding(dp(4), dp(4), dp(4), dp(4))
                },
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT,
                ),
            )
        }

    private companion object {
        private const val REFRESH_INTERVAL_MS = 60_000L

        fun filterOverlayOnlineMembers(members: List<TeamMemberDto>): List<TeamMemberDto> =
            members.filter { m ->
                m.membershipStatus == "active" &&
                    isOverlayIngameNow(m.presenceStatus, m.lastPresenceAt)
            }.sortedBy { it.username.lowercase() }
    }
}
