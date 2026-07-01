package com.lastasylum.alliance.overlay

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.lastasylum.alliance.R
import com.lastasylum.alliance.game.RoutePlannerRoute

/**
 * Выбор маршрута и участника альянса после фиксации области 3×3 на карте.
 */
class OverlayRouteAssignPanel(
    private val context: Context,
    private val mainHandler: Handler,
    private val dp: (Int) -> Int,
    private val onAssign: (route: RoutePlannerRoute, member: AllianceMember) -> Unit,
    private val onDismiss: () -> Unit,
) {
    private var root: LinearLayout? = null
    private var attached = false
    private var attachedWindowManager: WindowManager? = null
    private var routes: List<RoutePlannerRoute> = emptyList()
    private var members: List<AllianceMember> = emptyList()
    private var selectedRouteId: String? = null
    private var selectedMemberId: String? = null
    private var routeSection: LinearLayout? = null
    private var memberSection: LinearLayout? = null
    private var confirmBtn: TextView? = null

    val isShowing: Boolean get() = attached

    fun show(
        windowManager: WindowManager,
        routes: List<RoutePlannerRoute>,
        members: List<AllianceMember>,
    ) {
        runOnMain {
            this.routes = routes
            this.members = members.sortedBy { it.name.lowercase() }
            selectedRouteId = routes.firstOrNull()?.id
            selectedMemberId = this.members.firstOrNull()?.id
            val view = root ?: buildView().also { root = it }
            rebuildLists()
            if (!attached) {
                runCatching { windowManager.addView(view, buildParams()) }
                    .onSuccess {
                        attached = true
                        attachedWindowManager = windowManager
                    }
                    .onFailure { e -> Log.w(TAG, "addView failed", e) }
            } else {
                updateConfirmState()
            }
        }
    }

    fun hide(windowManager: WindowManager) {
        runOnMain {
            val view = root ?: return@runOnMain
            if (!attached) return@runOnMain
            val mgr = attachedWindowManager ?: windowManager
            runCatching { mgr.removeView(view) }
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
            dp(320),
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            OverlayWindowLayout.popupWindowFlags(),
            android.graphics.PixelFormat.TRANSLUCENT,
        ).apply {
            OverlayWindowLayout.applyPopupLayoutCompat(this)
            gravity = Gravity.CENTER
        }
    }

    private fun sectionTitle(text: String) = TextView(context).apply {
        this.text = text
        setTextColor(Color.parseColor("#FF90A4AE"))
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
        typeface = Typeface.DEFAULT_BOLD
        setPadding(0, dp(4), 0, dp(6))
    }

    private fun chip(label: String, selected: Boolean, onClick: () -> Unit): TextView =
        TextView(context).apply {
            text = label
            gravity = Gravity.CENTER_VERTICAL
            setTextColor(if (selected) Color.WHITE else Color.parseColor("#FFB0BEC5"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            typeface = if (selected) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            maxLines = 1
            isSingleLine = true
            ellipsize = android.text.TextUtils.TruncateAt.END
            setPadding(dp(12), dp(8), dp(12), dp(8))
            background = GradientDrawable().apply {
                cornerRadius = dp(8).toFloat()
                setColor(if (selected) Color.parseColor("#FF3949AB") else Color.parseColor("#FF1A222E"))
                setStroke(dp(1), if (selected) Color.parseColor("#FF7986CB") else Color.parseColor("#33445566"))
            }
            setOnClickListener { onClick() }
        }

    private fun rebuildLists() {
        routeSection?.let { section ->
            section.removeAllViews()
            routes.forEach { route ->
                val selected = route.id == selectedRouteId
                section.addView(
                    chip(route.name, selected) {
                        selectedRouteId = route.id
                        rebuildLists()
                    },
                    LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                    ).apply { bottomMargin = dp(6) },
                )
            }
            if (routes.isEmpty()) {
                section.addView(emptyHint(context.getString(R.string.overlay_route_assign_no_routes)))
            }
        }
        memberSection?.let { section ->
            section.removeAllViews()
            members.forEach { member ->
                val selected = member.id == selectedMemberId
                section.addView(
                    chip(member.name, selected) {
                        selectedMemberId = member.id
                        rebuildLists()
                    },
                    LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                    ).apply { bottomMargin = dp(6) },
                )
            }
            if (members.isEmpty()) {
                section.addView(emptyHint(context.getString(R.string.overlay_route_assign_no_members)))
            }
        }
        updateConfirmState()
    }

    private fun emptyHint(text: String) = TextView(context).apply {
        this.text = text
        setTextColor(Color.parseColor("#FF78909C"))
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
        setPadding(dp(4), dp(4), dp(4), dp(8))
    }

    private fun updateConfirmState() {
        val ready = selectedRouteId != null && selectedMemberId != null &&
            routes.isNotEmpty() && members.isNotEmpty()
        confirmBtn?.alpha = if (ready) 1f else 0.45f
        confirmBtn?.isEnabled = ready
    }

    private fun buildView(): LinearLayout {
        val routeList = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }.also { routeSection = it }

        val memberList = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }.also { memberSection = it }

        val scroll = ScrollView(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(240),
            )
            addView(
                LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    addView(sectionTitle(context.getString(R.string.overlay_route_assign_route_label)))
                    addView(routeList)
                    addView(sectionTitle(context.getString(R.string.overlay_route_assign_member_label)))
                    addView(memberList)
                },
            )
        }

        val cancelBtn = TextView(context).apply {
            text = context.getString(R.string.overlay_route_planner_cancel)
            gravity = Gravity.CENTER
            setTextColor(Color.parseColor("#FFB0BEC5"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setPadding(dp(16), dp(10), dp(16), dp(10))
            setOnClickListener {
                onDismiss()
            }
        }

        val confirm = TextView(context).apply {
            text = context.getString(R.string.overlay_route_assign_confirm)
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            typeface = Typeface.DEFAULT_BOLD
            setPadding(dp(16), dp(10), dp(16), dp(10))
            background = GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                intArrayOf(Color.parseColor("#FF2563EB"), Color.parseColor("#FF4F46E5")),
            ).apply { cornerRadius = dp(10).toFloat() }
            setOnClickListener {
                val route = routes.firstOrNull { it.id == selectedRouteId } ?: return@setOnClickListener
                val member = members.firstOrNull { it.id == selectedMemberId } ?: return@setOnClickListener
                onAssign(route, member)
            }
        }.also { confirmBtn = it }

        val actions = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
            addView(cancelBtn)
            addView(
                confirm,
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    marginStart = dp(8)
                },
            )
        }

        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(14))
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(Color.parseColor("#F51E293B"), Color.parseColor("#F50B1220")),
            ).apply {
                cornerRadius = dp(16).toFloat()
                setStroke(dp(1), Color.parseColor("#3360A5FA"))
            }
            addView(
                TextView(context).apply {
                    text = context.getString(R.string.overlay_route_assign_title)
                    setTextColor(Color.parseColor("#FFE8F4FF"))
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                    typeface = Typeface.DEFAULT_BOLD
                    setPadding(0, 0, 0, dp(10))
                },
            )
            addView(scroll)
            addView(
                actions,
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    topMargin = dp(10)
                },
            )
        }
    }

    companion object {
        private const val TAG = "OverlayRouteAssignPanel"
    }
}
