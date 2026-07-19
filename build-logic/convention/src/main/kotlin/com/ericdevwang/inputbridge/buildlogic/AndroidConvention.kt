package com.ericdevwang.inputbridge.buildlogic

import com.android.build.api.dsl.CommonExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.jetbrains.kotlin.gradle.dsl.KotlinAndroidProjectExtension

internal fun Project.configureAndroid(commonExtension: CommonExtension) {
    commonExtension.apply {
        compileSdk = 37
        defaultConfig.minSdk = 31
        compileOptions.apply {
            sourceCompatibility = JavaVersion.VERSION_17
            targetCompatibility = JavaVersion.VERSION_17
        }
    }

    extensions.configure<KotlinAndroidProjectExtension> {
        jvmToolchain(17)
    }
}
