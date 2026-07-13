package com.ericdevwang.androidinputbridge.plugin.adb

import java.nio.file.Path
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AdbLocatorTest {
    @Test
    fun locateUsesConfiguredSdkBeforeEnvironmentAndPath() {
        val configured = Path.of("/configured/platform-tools/adb")
        val sdkRoot = Path.of("/environment-sdk")
        val pathRoot = Path.of("/path-bin")
        val locator = AdbLocator(
            configuredAdbProvider = { configured },
            environment = mapOf("ANDROID_SDK_ROOT" to sdkRoot.toString()),
            pathEntries = listOf(pathRoot),
            executableNames = listOf("adb"),
            isUsable = { it == configured || it == sdkRoot.resolve("platform-tools/adb") || it == pathRoot.resolve("adb") },
        )

        assertEquals(configured, locator.locate())
    }

    @Test
    fun locateFallsBackThroughEnvironmentVariablesAndPath() {
        val androidHome = Path.of("/android-home")
        val pathRoot = Path.of("/path-bin")
        val expected = pathRoot.resolve("adb")
        val locator = AdbLocator(
            configuredAdbProvider = { null },
            environment = mapOf("ANDROID_HOME" to androidHome.toString()),
            pathEntries = listOf(pathRoot),
            executableNames = listOf("adb"),
            isUsable = { it == expected },
        )

        assertEquals(expected, locator.locate())
    }

    @Test
    fun locateReturnsNullWhenNoCandidateIsUsable() {
        val locator = AdbLocator(
            configuredAdbProvider = { null },
            environment = emptyMap(),
            pathEntries = emptyList(),
            executableNames = listOf("adb", "adb.exe"),
            isUsable = { false },
        )

        assertNull(locator.locate())
    }
}
