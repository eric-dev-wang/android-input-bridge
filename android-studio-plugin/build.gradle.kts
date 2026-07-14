plugins {
    alias(libs.plugins.kotlin.jvm)
    id("org.jetbrains.intellij.platform")
}

group = "com.ericdevwang.androidinputbridge.plugin"
version = rootProject.version

kotlin {
    jvmToolchain(21)
}

val localAndroidStudioPath = providers.gradleProperty("androidStudioPath")

dependencies {
    implementation(project(":protocol"))

    intellijPlatform {
        if (localAndroidStudioPath.isPresent) {
            local(localAndroidStudioPath.get())
            bundledPlugin("org.jetbrains.android")
        } else {
            androidStudio("2026.1.1.10")
            plugin("org.jetbrains.android:261.23567.138")
        }
        testFramework(org.jetbrains.intellij.platform.gradle.TestFrameworkType.Platform)
    }

    testImplementation(libs.junit)
}

intellijPlatform {
    pluginConfiguration {
        ideaVersion {
            sinceBuild = "261"
            untilBuild = "261.*"
        }
    }

    pluginVerification {
        ides {
            if (localAndroidStudioPath.isPresent) {
                local(file(localAndroidStudioPath.get()))
            } else {
                current()
            }
        }
    }
}

tasks {
    withType<JavaCompile>().configureEach {
        sourceCompatibility = "21"
        targetCompatibility = "21"
    }
}
