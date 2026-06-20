package com.lastasylum.alliance.overlay

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.lastasylum.alliance.data.settings.UserSettingsPreferences

/** One-shot snapshot for overlay settings / support (why HUD may not appear in-game). */
object OverlayGateDiagnostics {
    data class Snapshot(
        val usageAccessForOverlay: Boolean,
        val usageAccessMode: GameForegroundGate.UsageAccessMode,
        val canDrawOverlays: Boolean,
        val panelEnabled: Boolean,
        val targetPackages: List<String>,
        val activityFilter: List<String>,
        val lastForegroundPackage: String?,
        val lastForegroundActivity: String?,
        val quickProbeLabel: String,
        val shouldShowInGame: Boolean,
        val weakTargetSignal: Boolean,
        val serviceRunning: Boolean,
        val overlayVisible: Boolean,
        val inGameOverlayUiActive: Boolean,
        val lastGateTickAgeMs: Long?,
        val lastInGameProbe: Boolean,
        val lastStableShow: Boolean,
        val hudStatusAttached: Boolean,
        val hudTopRightAttached: Boolean,
        val hudAttachAllowed: Boolean,
        val entryBoostActive: Boolean,
        val lastAddViewError: String?,
        val postNotificationsGranted: Boolean,
        val lastFcmTokenRegisteredAgeMs: Long?,
    ) {
        val hudExpectedVisible: Boolean
            get() = panelEnabled && canDrawOverlays && inGameOverlayUiActive

        val hudPhysicallyAttached: Boolean
            get() = hudStatusAttached && hudTopRightAttached

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
        lines += when (snapshot.usageAccessMode) {
            GameForegroundGate.UsageAccessMode.FULL ->
                "Статистика использования: полный доступ"
            GameForegroundGate.UsageAccessMode.LIMITED_FOREGROUND ->
                "Статистика использования: только при открытом SquadRelay — HUD не появится в игре"
            GameForegroundGate.UsageAccessMode.NONE ->
                "Статистика использования: нет доступа"
        }
        lines += if (snapshot.canDrawOverlays) {
            "Поверх других приложений: разрешено"
        } else {
            "Поверх других приложений: нет"
        }
        lines += if (snapshot.postNotificationsGranted) {
            "Push-уведомления: разрешены"
        } else {
            "Push-уведомления: нет — игровые события не покажутся"
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
        if (snapshot.weakTargetSignal && !snapshot.shouldShowInGame) {
            lines += "Слабый сигнал игры (TTF/события): да — HUD может появиться с задержкой"
        }
        lines += if (snapshot.serviceRunning) {
            "Сервис оверлея: работает"
        } else {
            "Сервис оверлея: не запущен — HUD не появится до запуска"
        }
        val gateAge = snapshot.lastGateTickAgeMs
        if (gateAge != null) {
            lines += "Последний опрос гейта: ${gateAge / 1000} с назад"
        }
        lines += if (snapshot.inGameOverlayUiActive) {
            "HUD должен быть: да"
        } else {
            "HUD должен быть: нет"
        }
        lines += if (snapshot.hudPhysicallyAttached) {
            "HUD окна: прикреплены"
        } else {
            "HUD окна: не прикреплены (status=${snapshot.hudStatusAttached}, topRight=${snapshot.hudTopRightAttached})"
        }
        if (!snapshot.hudAttachAllowed && snapshot.inGameOverlayUiActive) {
            lines += "Attach HUD заблокирован — перезапустите оверлей или войдите в игру снова"
        }
        snapshot.lastAddViewError?.let { lines += "Ошибка addView: $it" }
        if (snapshot.entryBoostActive) {
            lines += "Режим ускоренного входа в игру: активен"
        }
        snapshot.lastFcmTokenRegisteredAgeMs?.let { age ->
            lines += "FCM токен на сервере: ${age / 1000} с назад"
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
        if (snapshot.panelEnabled && snapshot.serviceRunning && snapshot.shouldShowInGame &&
            !snapshot.hudPhysicallyAttached
        ) {
            lines += "Игра на экране, но HUD не прикреплён — проверьте разрешения и перезапустите игру"
        }
        return lines
    }

    fun capture(context: Context, prefs: UserSettingsPreferences): Snapshot {
        val app = context.applicationContext
        val targets = prefs.getOverlayTargetGamePackages()
        val activityTokens = prefs.getOverlayTargetGameActivityTokens()
        val usageMode = GameForegroundGate.usageAccessMode(app)
        val usageOk = GameForegroundGate.hasUsageStatsAccessForOverlay(app)
        val canDraw = Build.VERSION.SDK_INT < Build.VERSION_CODES.M ||
            Settings.canDrawOverlays(app)
        val postNotifOk = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                app,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
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
        val weakSignal = usageOk && targets.isNotEmpty() &&
            GameForegroundGate.hasWeakTargetForegroundSignal(app, targets)
        val now = System.currentTimeMillis()
        val gateTickAt = OverlayRuntimeDiagnostics.lastGateTickAtMs
        val fcmAt = OverlayRuntimeDiagnostics.lastFcmTokenRegisteredAtMs
        return Snapshot(
            usageAccessForOverlay = usageOk,
            usageAccessMode = usageMode,
            canDrawOverlays = canDraw,
            panelEnabled = prefs.isOverlayPanelEnabled(),
            targetPackages = targets,
            activityFilter = activityTokens,
            lastForegroundPackage = last?.packageName,
            lastForegroundActivity = last?.className,
            quickProbeLabel = quick,
            shouldShowInGame = shouldShow,
            weakTargetSignal = weakSignal,
            serviceRunning = CombatOverlayService.serviceRunning.value,
            overlayVisible = CombatOverlayService.overlayVisible.value,
            inGameOverlayUiActive = CombatOverlayService.inGameOverlayUiActive.value,
            lastGateTickAgeMs = gateTickAt.takeIf { it > 0L }?.let { now - it },
            lastInGameProbe = OverlayRuntimeDiagnostics.lastInGameProbe,
            lastStableShow = OverlayRuntimeDiagnostics.lastStableShow,
            hudStatusAttached = OverlayRuntimeDiagnostics.hudStatusAttached,
            hudTopRightAttached = OverlayRuntimeDiagnostics.hudTopRightAttached,
            hudAttachAllowed = OverlayRuntimeDiagnostics.hudAttachAllowed,
            entryBoostActive = OverlayRuntimeDiagnostics.entryBoostActive,
            lastAddViewError = OverlayRuntimeDiagnostics.lastAddViewError,
            postNotificationsGranted = postNotifOk,
            lastFcmTokenRegisteredAgeMs = fcmAt.takeIf { it > 0L }?.let { now - it },
        )
    }
}
