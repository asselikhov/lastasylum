package com.lastasylum.alliance.game

import org.junit.Assert.assertEquals
import org.junit.Test

class GameMapPatchStatusTest {
    @Test
    fun evaluate_notInstalled_whenNoMeta() {
        assertEquals(
            GameMapPatchStatus.State.PATCH_NOT_INSTALLED,
            GameMapPatchStatus.evaluate(
                patchBridgePresent = false,
                gameVersionName = "1.0.77",
                patchForGameVersion = null,
                supportedGameVersion = "1.0.77",
            ),
        )
    }

    @Test
    fun evaluate_ready_whenVersionsMatch() {
        assertEquals(
            GameMapPatchStatus.State.PATCH_READY,
            GameMapPatchStatus.evaluate(
                patchBridgePresent = true,
                gameVersionName = "1.0.77",
                patchForGameVersion = "1.0.77",
                supportedGameVersion = "1.0.77",
            ),
        )
    }

    @Test
    fun evaluate_ready_whenMetaPresentAndGameMatchesSupported_unknownPatchStamp() {
        assertEquals(
            GameMapPatchStatus.State.PATCH_READY,
            GameMapPatchStatus.evaluate(
                patchBridgePresent = true,
                gameVersionName = "1.0.81",
                patchForGameVersion = "unknown",
                supportedGameVersion = "1.0.81",
            ),
        )
    }

    @Test
    fun evaluate_outdated_whenGameUpdated() {
        assertEquals(
            GameMapPatchStatus.State.PATCH_OUTDATED,
            GameMapPatchStatus.evaluate(
                patchBridgePresent = true,
                gameVersionName = "1.0.78",
                patchForGameVersion = "1.0.77",
                supportedGameVersion = "1.0.77",
            ),
        )
    }

    @Test
    fun evaluate_outdated_whenBridgeVersionOlder() {
        assertEquals(
            GameMapPatchStatus.State.PATCH_OUTDATED,
            GameMapPatchStatus.evaluate(
                patchBridgePresent = true,
                gameVersionName = "1.0.81",
                patchForGameVersion = "1.0.81",
                supportedGameVersion = "1.0.81",
                installedBridgeVersion = "1",
                expectedBridgeVersion = "2",
            ),
        )
    }

    @Test
    fun evaluate_ready_whenBridgeVersionMatches() {
        assertEquals(
            GameMapPatchStatus.State.PATCH_READY,
            GameMapPatchStatus.evaluate(
                patchBridgePresent = true,
                gameVersionName = "1.0.81",
                patchForGameVersion = "1.0.81",
                supportedGameVersion = "1.0.81",
                installedBridgeVersion = "2",
                expectedBridgeVersion = "2",
            ),
        )
    }
}
