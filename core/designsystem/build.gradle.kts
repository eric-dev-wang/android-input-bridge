plugins {
    alias(libs.plugins.inputbridge.android.library)
    alias(libs.plugins.compose.compiler)
}

android {
    namespace = "com.ericdevwang.inputbridge.core.designsystem"
    buildFeatures {
        compose = true
    }
}

dependencies {
    val composeBom = platform(libs.androidx.compose.bom)
    implementation(composeBom)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
}
