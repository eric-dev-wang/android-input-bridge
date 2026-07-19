plugins {
    alias(libs.plugins.inputbridge.android.library)
    alias(libs.plugins.inputbridge.koin)
}

android {
    namespace = "com.ericdevwang.inputbridge.core.datastore"
}

dependencies {
    implementation(libs.androidx.datastore.preferences)
}
