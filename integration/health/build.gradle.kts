plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}
android {
    namespace = "com.luma.lifeos.integration.health"
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
    api(libs.health.connect)
    constraints {
        implementation("androidx.activity:activity:${libs.versions.activity.get()}")
        implementation("androidx.core:core-ktx:1.15.0")
        implementation("androidx.lifecycle:lifecycle-common:${libs.versions.lifecycle.get()}")
        implementation("androidx.lifecycle:lifecycle-runtime:${libs.versions.lifecycle.get()}")
        implementation("androidx.lifecycle:lifecycle-viewmodel:${libs.versions.lifecycle.get()}")
        implementation("androidx.lifecycle:lifecycle-viewmodel-savedstate:${libs.versions.lifecycle.get()}")
    }
    testImplementation(libs.junit)
}
