package com.kidsenglish.companion.conversation

import org.junit.Assert.assertEquals
import org.junit.Test

class ModelSelectionTest {
    @Test
    fun ordersKnownModelsByCompatibilityAndSpeedAndKeepsUnknownInstalledModels() {
        assertEquals(
            listOf("qwen3.5:9b", "smollm2:1.7b", "qwen3:4b", "custom:latest"),
            ModelSelection.ordered(
                listOf("custom:latest", "qwen3.5:9b", "smollm2:1.7b", "qwen3:4b"),
            ),
        )
    }

    @Test
    fun fallsBackToBuiltInChoicesWhenOllamaCannotBeReached() {
        assertEquals(
            listOf("qwen3.5:4b-mlx", "qwen3.5:9b", "smollm2:1.7b", "qwen3:4b"),
            ModelSelection.fallbackModels,
        )
    }

    @Test
    fun choosesFastestCompatibleInstalledModelWhenSavedSelectionIsMissing() {
        assertEquals(
            "qwen3.5:9b",
            ModelSelection.resolveSelection(
                saved = "missing:latest",
                installed = listOf("qwen3.5:9b", "qwen3:4b"),
            ),
        )
    }

    @Test
    fun preservesPersistedSelectionWhenItIsStillInstalled() {
        assertEquals(
            "qwen3.5:9b",
            ModelSelection.resolveSelection(
                saved = "qwen3.5:9b",
                installed = listOf("qwen3:4b", "qwen3.5:9b"),
            ),
        )
    }
}
