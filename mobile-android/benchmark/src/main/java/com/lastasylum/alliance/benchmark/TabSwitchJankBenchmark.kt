package com.lastasylum.alliance.benchmark

import android.content.Intent
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Measures frame timing while switching start tabs via launch intents.
 * This probes transition smoothness across the main sections.
 */
@RunWith(AndroidJUnit4::class)
class TabSwitchJankBenchmark {
    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Test
    fun switchTabsByLaunchIntent() = benchmarkRule.measureRepeated(
        packageName = "com.lastasylum.alliance",
        metrics = listOf(FrameTimingMetric()),
        iterations = 9,
        startupMode = StartupMode.WARM,
    ) {
        pressHome()
        val tab = when (iteration % 3) {
            0 -> "chat"
            1 -> "team"
            else -> "profile"
        }
        val intent = Intent().apply {
            action = Intent.ACTION_MAIN
            addCategory(Intent.CATEGORY_LAUNCHER)
            setPackage(packageName)
            putExtra("com.lastasylum.alliance.extra.START_TAB", tab)
        }
        startActivityAndWait(intent)
        device.wait(Until.hasObject(By.pkg(packageName).depth(0)), 5_000)
    }
}
