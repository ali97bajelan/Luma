plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}
android {
    namespace = "com.luma.lifeos.integration.usage"
    compileSdk = 35
    defaultConfig { minSdk = 28 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
kotlin { jvmToolchain(17) }
dependencies {
    api(project(":core:domain"))
    implementation(libs.work.runtime)
    // Align WorkManager's older transitive baseline with versions available in this project/cache.
    constraints {
        implementation("androidx.core:core:1.15.0")
        implementation("androidx.lifecycle:lifecycle-livedata:2.8.7")
        implementation("androidx.lifecycle:lifecycle-service:2.8.7")
        implementation(libs.room.ktx)
        implementation("androidx.profileinstaller:profileinstaller:1.4.0")
    }
    testImplementation(libs.junit)
}
