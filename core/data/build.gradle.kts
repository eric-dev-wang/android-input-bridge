plugins {
    alias(libs.plugins.inputbridge.android.library)
    alias(libs.plugins.inputbridge.koin)
}

android {
    namespace = "com.ericdevwang.inputbridge.core.data"
}

dependencies {
    implementation(project(":core:datastore"))
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
