package com.lastasylum.alliance.overlay

import android.content.Intent
import android.content.pm.PackageManager

/**
 * Подсказки пакетов установленных копий/сборок игры (другой applicationId, чем в дефолте).
 */
object OverlayGamePackageSuggestions {
    private val PACKAGE_MARKERS = listOf(
        "plague",
        "phs.global",
        "phs.",
        "lastasylum",
    )

    data class DetectedGamePackage(
        val packageName: String,
        val label: String,
        val alreadyInFilter: Boolean,
    )

    fun detectInstalled(
        pm: PackageManager,
        squadRelayPackage: String,
        configuredCsv: String,
    ): List<DetectedGamePackage> {
        val configured = configuredCsv
            .split(',')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toSet()
        return pm.getInstalledApplications(PackageManager.GET_META_DATA)
            .asSequence()
            .mapNotNull { app ->
                val pkg = app.packageName?.trim().orEmpty()
                if (pkg.isEmpty() || pkg == squadRelayPackage) return@mapNotNull null
                if (pkg.startsWith("com.android.") || pkg.startsWith("com.google.android.")) {
                    return@mapNotNull null
                }
                if (!looksLikeGame(pkg) || !hasLauncher(pm, pkg)) return@mapNotNull null
                val label = app.loadLabel(pm).toString().trim().ifBlank { pkg }
                DetectedGamePackage(
                    packageName = pkg,
                    label = label,
                    alreadyInFilter = pkg in configured,
                )
            }
            .sortedWith(
                compareBy<DetectedGamePackage> { it.alreadyInFilter }
                    .thenBy { it.label.lowercase() },
            )
            .toList()
    }

    fun appendToCsv(current: String, packageName: String): String {
        val pkg = packageName.trim()
        if (pkg.isEmpty()) return current
        val parts = current
            .split(',')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toMutableSet()
        if (!parts.add(pkg)) return current
        return parts.joinToString(",")
    }

    private fun looksLikeGame(packageName: String): Boolean {
        val lower = packageName.lowercase()
        return PACKAGE_MARKERS.any { lower.contains(it) }
    }

    private fun hasLauncher(pm: PackageManager, packageName: String): Boolean {
        val intent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
            setPackage(packageName)
        }
        return pm.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY).isNotEmpty()
    }
}
