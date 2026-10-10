plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.luma.lifeos"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.luma.lifeos"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures { compose = true }
    testOptions { animationsDisabled = true }
}

kotlin { jvmToolchain(17) }

dependencies {
    implementation(project(":core:domain"))
    implementation(platform(libs.compose.bom))
    implementation(libs.activity.compose)
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.compose.tooling.preview)
    implementation(libs.navigation.compose)

    debugImplementation(libs.compose.tooling)
    debugImplementation(libs.compose.ui.test.manifest)
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(libs.androidx.test.runner)
}
