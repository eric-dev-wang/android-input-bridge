package com.ericdevwang.androidinputbridge.plugin.adb

import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import com.intellij.openapi.project.Project
import org.jetbrains.android.sdk.AndroidSdkUtils

class AdbLocator(
    private val configuredAdbProvider: () -> Path?,
    private val environment: Map<String, String> = emptyMap(),
    private val pathEntries: List<Path> = emptyList(),
    private val executableNames: List<String> = defaultExecutableNames(),
    private val isUsable: (Path) -> Boolean = { path ->
        Files.isRegularFile(path) && Files.isExecutable(path)
    },
) {
    fun locate(): Path? = candidatePaths()
        .distinct()
        .firstOrNull(isUsable)

    private fun candidatePaths(): Sequence<Path> = sequence {
        configuredAdbProvider()?.let { yield(it) }
        environment["ANDROID_SDK_ROOT"]?.let { yieldAll(sdkCandidates(Path.of(it))) }
        environment["ANDROID_HOME"]?.let { yieldAll(sdkCandidates(Path.of(it))) }
        yieldAll(pathEntries.asSequence().flatMap(::pathCandidates))
    }

    private fun sdkCandidates(root: Path): Sequence<Path> =
        executableNames.asSequence().map { root.resolve("platform-tools").resolve(it) }

    private fun pathCandidates(root: Path): Sequence<Path> =
        executableNames.asSequence().map { root.resolve(it) }

    companion object {
        @Suppress("UNUSED_PARAMETER")
        fun forProject(project: Project): AdbLocator = AdbLocator(
            configuredAdbProvider = {
                runCatching {
                    AndroidSdkUtils.findValidAndroidSdkPath()
                        ?.toPath()
                        ?.resolve("platform-tools")
                        ?.resolve(defaultExecutableNames().first())
                }.getOrNull()
            },
            environment = System.getenv(),
            pathEntries = System.getenv("PATH")
                .orEmpty()
                .split(File.pathSeparator)
                .filter(String::isNotBlank)
                .map(Path::of),
        )

        private fun defaultExecutableNames(): List<String> =
            if (System.getProperty("os.name").contains("win", ignoreCase = true)) {
                listOf("adb.exe", "adb")
            } else {
                listOf("adb")
            }
    }
}
