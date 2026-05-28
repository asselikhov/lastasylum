package com.lastasylum.alliance.benchmark

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
 * Generic UI jank probe that works on both auth and logged-in states.
 * It launches the app, waits for the root view, then performs repeated swipes.
 */
@RunWith(AndroidJUnit4::class)
class UiJankBenchmark {
    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Test
    fun launchAndSwipe() = benchmarkRule.measureRepeated(
        packageName = "com.lastasylum.alliance",
        metrics = listOf(FrameTimingMetric()),
        iterations = 7,
        startupMode = StartupMode.WARM,
    ) {
        pressHome()
        startActivityAndWait()
        device.wait(Until.hasObject(By.pkg(packageName).depth(0)), 5_000)

        repeat(4) {
            device.swipe(
                device.displayWidth / 2,
                (device.displayHeight * 0.8f).toInt(),
                device.displayWidth / 2,
                (device.displayHeight * 0.2f).toInt(),
                18,
            )
            device.swipe(
                device.displayWidth / 2,
                (device.displayHeight * 0.2f).toInt(),
                device.displayWidth / 2,
                (device.displayHeight * 0.8f).toInt(),
                18,
            )
        }
    }
}
