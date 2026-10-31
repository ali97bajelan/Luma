plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.luma.lifeos"
    compileSdk = 35

    flavorDimensions += "qa"
    productFlavors {
        create("production") {
            dimension = "qa"
        }
        create("appLockQa") {
            dimension = "qa"
            applicationIdSuffix = ".applockqa"
            versionNameSuffix = "-applockqa"
        }
        create("focusQa") {
            dimension = "qa"
            applicationIdSuffix = ".focusqa"
            versionNameSuffix = "-focusqa"
        }
    }

    defaultConfig {
        applicationId = "com.luma.lifeos"
        minSdk = 28
        targetSdk = 35
        versionCode = 8
        versionName = "1.5.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    buildTypes {
        debug { applicationIdSuffix = ".qa" }
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    // All three languages must be available for offline switching after installation.
    bundle { language { enableSplit = false } }
    buildFeatures { compose = true }
    testOptions { animationsDisabled = true }
}

kotlin { jvmToolchain(17) }

val apkVariants = listOf(
    "productionRelease" to ":app:assembleProductionRelease",
    "productionDebug" to ":app:assembleProductionDebug",
    "appLockQaRelease" to ":app:assembleAppLockQaRelease",
    "appLockQaDebug" to ":app:assembleAppLockQaDebug",
    "focusQaRelease" to ":app:assembleFocusQaRelease",
    "focusQaDebug" to ":app:assembleFocusQaDebug",
)

val buildApkRequested = gradle.startParameter.taskNames.any {
    it.substringAfterLast(':') == "buildApk"
}

val selectedAssemblyTask = if (buildApkRequested) {
    logger.lifecycle("Select an APK variant to build:")
    apkVariants.forEachIndexed { index, (variant, _) ->
        logger.lifecycle("${index + 1}. $variant")
    }
    logger.lifecycle("Enter a number (or send EOF to cancel):")

    val selection = readLine()?.trim()
    if (selection.isNullOrEmpty()) {
        throw GradleException("No variant selected; APK build canceled.")
    }

    val selectedVariant = selection.toIntOrNull()
        ?.let { apkVariants.getOrNull(it - 1) }
        ?: throw GradleException("Invalid selection '$selection'; APK build canceled.")

    logger.lifecycle("Building ${selectedVariant.first}...")
    selectedVariant.second
} else {
    null
}

tasks.register("buildApk") {
    group = "build"
    description = "Prompts for an APK variant and assembles it."
    notCompatibleWithConfigurationCache("This task prompts for interactive input.")
    if (selectedAssemblyTask != null) {
        dependsOn(selectedAssemblyTask)
    }
}

dependencies {
    implementation(project(":core:domain"))
    implementation(project(":core:data"))
    implementation(project(":integration:usage"))
    implementation(project(":integration:health"))
    implementation(libs.work.runtime)
    implementation(platform(libs.compose.bom))
    implementation(libs.activity.compose)
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.compose.tooling.preview)
    implementation(libs.navigation.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.lifecycle.viewmodel.compose)

    debugImplementation(libs.compose.tooling)
    debugImplementation(libs.compose.ui.test.manifest)
    testImplementation(libs.junit)
    testImplementation(libs.coroutines.test)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.room.runtime)
}
