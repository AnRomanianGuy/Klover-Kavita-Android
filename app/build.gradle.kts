import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.romanianguy.klover"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.romanianguy.klover"
        minSdk = 35
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // keystore.properties (not in the repo): storeFile, storePassword, keyAlias, keyPassword. Without it release uses the debug key
    val keystoreProps = Properties().apply {
        rootProject.file("keystore.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
    }
    signingConfigs {
        if (keystoreProps.isNotEmpty()) {
            create("release") {
                storeFile = file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            // -PkloverSuffix=.debug installs this next to the real app, with its own storage
            providers.gradleProperty("kloverSuffix").orNull?.let { applicationIdSuffix = it }
        }
        release {
            // same -PkloverSuffix switch, to measure real startup without replacing the real app
            providers.gradleProperty("kloverSuffix").orNull?.let { applicationIdSuffix = it }
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
            // shrinks the app and every library; limiting it to androidx / kotlin left all of Material Components (1,400 classes) in.
            // what reflection needs is kept by keepRules/rules.keep and the libraries' consumer rules
            optimization {
                enable = true
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(platform(libs.koin.bom))

    implementation(libs.androidx.core.ktx)
    // baseline profile (src/main/baseline-prof.txt): the first launch runs precompiled code
    implementation(libs.androidx.profileinstaller)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.viewmodel.navigation3)

    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.material3.adaptive.navigation.suite)

    implementation(libs.androidx.adaptive)
    implementation(libs.androidx.adaptive.layout)
    implementation(libs.androidx.adaptive.navigation3)
    implementation(libs.androidx.window)
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.androidx.datastore.preferences)

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.okhttp)
    implementation(libs.retrofit)
    implementation(libs.retrofit.kotlinx.serialization)
    implementation(libs.material.components)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    implementation(libs.coil.svg)
    implementation(libs.jsoup)
    implementation(libs.koin.android)
    implementation(libs.koin.compose)
    implementation(libs.koin.compose.viewmodel)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}

// installs the debug build and starts it (.run/debug.run.xml, with -PkloverSuffix=.debug); honours ANDROID_SERIAL
val adbExecutable = androidComponents.sdkComponents.adb
val kloverSuffix = providers.gradleProperty("kloverSuffix").orElse("")
tasks.register("startDebug") {
    group = "install"
    description = "Installs the debug build (as .debug with -PkloverSuffix=.debug) and starts it."
    dependsOn("installDebug")
    val adb = adbExecutable
    val applicationId = kloverSuffix.map { "com.romanianguy.klover$it" }
    doLast {
        val command = ArrayList<String>()
        command += adb.get().asFile.path
        System.getenv("ANDROID_SERIAL")?.let { command += listOf("-s", it) }
        command += listOf("shell", "am", "start", "-n", "${applicationId.get()}/com.romanianguy.klover.MainActivity")
        ProcessBuilder(command).inheritIO().start().waitFor()
    }
}

// release build with its baseline profile, installed as the .debug app, compiled ahead of time and started (.run/optimized.run.xml).
// the debug build can't be compiled ahead of time, so on a phone it runs interpreted: about 5x the start-up time
tasks.register("startOptimized") {
    group = "install"
    description = "Installs the optimized (release) build, compiles it with its baseline profile and starts it."
    dependsOn("installRelease")
    val adb = adbExecutable
    val applicationId = kloverSuffix.map { "com.romanianguy.klover$it" }
    doLast {
        fun adb(vararg args: String) {
            val command = ArrayList<String>()
            command += adb.get().asFile.path
            System.getenv("ANDROID_SERIAL")?.let { command += listOf("-s", it) }
            command += args
            ProcessBuilder(command).inheritIO().start().waitFor()
        }
        // otherwise the profile only applies once the system gets round to compiling the app, which can take days
        adb("shell", "cmd", "package", "compile", "-m", "speed-profile", "-f", applicationId.get())
        adb("shell", "am", "start", "-n", "${applicationId.get()}/com.romanianguy.klover.MainActivity")
    }
}
