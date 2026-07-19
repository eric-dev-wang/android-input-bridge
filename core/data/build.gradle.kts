plugins {
    alias(libs.plugins.inputbridge.android.library)
    alias(libs.plugins.inputbridge.koin)
}

android {
    namespace = "com.ericdevwang.inputbridge.core.data"
}

dependencies {
    implementation(project(":core:datastore"))
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)

    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.rules)
    androidTestImplementation(libs.kotlinx.coroutines.test)
}
