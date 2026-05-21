package com.lastasylum.alliance.overlay

import android.content.Context
import android.os.Build
import android.provider.Settings
import com.lastasylum.alliance.data.settings.UserSettingsPreferences

/** One-shot snapshot for overlay settings / support (why HUD may not appear in-game). */
object OverlayGateDiagnostics {
    data class Snapshot(
        val usageAccessForOverlay: Boolean,
        val canDrawOverlays: Boolean,
        val panelEnabled: Boolean,
        val targetPackages: List<String>,
        val activityFilter: List<String>,
        val lastForegroundPackage: String?,
        val lastForegroundActivity: String?,
        val quickProbeLabel: String,
        val shouldShowInGame: Boolean,
        val serviceRunning: Boolean,
        val overlayVisible: Boolean,
        val inGameOverlayUiActive: Boolean,
    ) {
        val hudExpectedVisible: Boolean
            get() = panelEnabled && canDrawOverlays && inGameOverlayUiActive

        val activityFilterBlocksHeuristics: Boolean
            get() = activityFilter.isNotEmpty() &&
                lastForegroundPackage != null &&
                targetPackages.contains(lastForegroundPackage) &&
                !lastForegroundActivity.isNullOrBlank() &&
                activityFilter.none { token ->
                    lastForegroundActivity.contains(token, ignoreCase = true)
                }
    }

    fun summaryLines(snapshot: Snapshot): List<String> {
        val lines = mutableListOf<String>()
        lines += if (snapshot.usageAccessForOverlay) {
            "Статистика использования: доступ есть"
        } else {
            "Статистика использования: нет (полный доступ для SquadRelay)"
        }
        lines += if (snapshot.canDrawOverlays) {
            "Поверх других приложений: разрешено"
        } else {
            "Поверх других приложений: нет"
        }
        lines += "Пакеты в фильтре: ${snapshot.targetPackages.joinToString().ifBlank { "—" }}"
        if (snapshot.activityFilter.isNotEmpty()) {
            lines += "Activity фильтр: ${snapshot.activityFilter.joinToString()}"
        }
        val fg = snapshot.lastForegroundPackage ?: "—"
        val act = snapshot.lastForegroundActivity?.takeIf { it.isNotBlank() } ?: "—"
        lines += "Система видит на экране: $fg ($act)"
        lines += "Быстрая проверка: ${snapshot.quickProbeLabel}"
        lines += if (snapshot.shouldShowInGame) {
            "Гейт: игра на экране — да"
        } else {
            "Гейт: игра на экране — нет"
        }
        lines += if (snapshot.serviceRunning) {
            "Сервис оверлея: работает"
        } else {
            "Сервис оверлея: не запущен"
        }
        lines += if (snapshot.inGameOverlayUiActive) {
            "HUD должен быть: да"
        } else {
            "HUD должен быть: нет"
        }
        if (snapshot.activityFilterBlocksHeuristics) {
            lines += "Activity фильтр не совпадает с экраном — очистите поле или исправьте токены"
        }
        if (snapshot.panelEnabled && snapshot.usageAccessForOverlay && snapshot.canDrawOverlays &&
            !snapshot.shouldShowInGame && snapshot.targetPackages.isNotEmpty()
        ) {
            val targets = snapshot.targetPackages.joinToString()
            if (snapshot.lastForegroundPackage != null &&
                !snapshot.targetPackages.any { it == snapshot.lastForegroundPackage }
            ) {
                lines += "Пакет на экране (${snapshot.lastForegroundPackage}) не в фильтре ($targets)"
            }
        }
        return lines
    }

    fun capture(context: Context, prefs: UserSettingsPreferences): Snapshot {
        val app = context.applicationContext
        val targets = prefs.getOverlayTargetGamePackages()
        val activityTokens = prefs.getOverlayTargetGameActivityTokens()
        val usageOk = GameForegroundGate.hasUsageStatsAccessForOverlay(app)
        val canDraw = Build.VERSION.SDK_INT < Build.VERSION_CODES.M ||
            Settings.canDrawOverlays(app)
        val last = if (usageOk) {
            GameForegroundGate.lastResumedComponent(app, forceRefresh = true)
        } else {
            null
        }
        val quick = if (usageOk && targets.isNotEmpty()) {
            when (
                GameForegroundGate.quickTargetForegroundProbe(
                    app,
                    targets,
                    activityTokens,
                )
            ) {
                GameForegroundGate.QuickForegroundProbe.IN_TARGET -> "в игре"
                GameForegroundGate.QuickForegroundProbe.NOT_IN_TARGET -> "не в игре"
                GameForegroundGate.QuickForegroundProbe.NEED_FULL_HEURISTICS -> "полная проверка"
            }
        } else {
            "—"
        }
        val shouldShow = usageOk && targets.isNotEmpty() &&
            GameForegroundGate.shouldShowOverlay(app, targets, activityTokens)
        return Snapshot(
            usageAccessForOverlay = usageOk,
            canDrawOverlays = canDraw,
            panelEnabled = prefs.isOverlayPanelEnabled(),
            targetPackages = targets,
            activityFilter = activityTokens,
            lastForegroundPackage = last?.packageName,
            lastForegroundActivity = last?.className,
            quickProbeLabel = quick,
            shouldShowInGame = shouldShow,
            serviceRunning = CombatOverlayService.serviceRunning.value,
            overlayVisible = CombatOverlayService.overlayVisible.value,
            inGameOverlayUiActive = CombatOverlayService.inGameOverlayUiActive.value,
        )
    }
}
