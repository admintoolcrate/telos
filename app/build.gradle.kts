import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

/** Upload-key settings from keystore.properties, else from RELEASE_* environment variables (CI). */
val releaseSigning: Map<String, String>?
    get() {
        val file = rootProject.file("keystore.properties")
        if (file.exists()) {
            val p = Properties().apply { file.inputStream().use { load(it) } }
            return listOf("storeFile", "storePassword", "keyAlias", "keyPassword")
                .associateWith { p.getProperty(it) ?: error("keystore.properties is missing $it") }
        }
        // Trimmed: values pasted into GitHub secrets easily pick up a stray space or line break.
        val env = mapOf(
            "storeFile" to System.getenv("RELEASE_STORE_FILE")?.trim(),
            "storePassword" to System.getenv("RELEASE_STORE_PASSWORD")?.trim(),
            "keyAlias" to System.getenv("RELEASE_KEY_ALIAS")?.trim(),
            "keyPassword" to System.getenv("RELEASE_KEY_PASSWORD")?.trim(),
        )
        return if (env.values.all { !it.isNullOrEmpty() }) env.mapValues { it.value!! } else null
    }

android {
    namespace = "cc.toolcrate.telos"
    compileSdk = 36

    defaultConfig {
        applicationId = "cc.toolcrate.telos"
        minSdk = 26
        targetSdk = 36
        // CI builds number themselves from the run (run 57 is version 1057), so every upload to
        // Play has a new, higher code. Builds made elsewhere use the fixed fallback.
        versionCode = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull()?.let { 1000 + it } ?: 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        // The upload key, from keystore.properties (never committed) or, in CI, from environment
        // variables. Without either, release builds are unsigned. See docs/play-store/RELEASE.md.
        releaseSigning?.let { s ->
            create("release") {
                storeFile = file(s.getValue("storeFile"))
                storePassword = s.getValue("storePassword")
                keyAlias = s.getValue("keyAlias")
                keyPassword = s.getValue("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            signingConfigs.findByName("release")?.let { signingConfig = it }
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
        unitTests.all {
            // Robolectric downloads Android's runtime jars; Google's Maven Central mirror avoids
            // Central's rate limits on shared CI hosts.
            it.systemProperty("robolectric.dependency.repo.url", "https://maven-central.storage-download.googleapis.com/maven2")
            it.maxHeapSize = "3g"
        }
    }

    packaging {
        resources.excludes += setOf("/META-INF/{AL2.0,LGPL2.1}", "META-INF/versions/9/OSGI-INF/MANIFEST.MF")
    }

    sourceSets {
        // Room's exported schemas, so migration tests can open every past version. Robolectric
        // only sees the app's merged assets, so they ride in debug builds (never release).
        getByName("debug").assets.srcDir("$projectDir/schemas")
    }
}

// Release unit tests would only repeat the debug ones.
androidComponents {
    beforeVariants(selector().withBuildType("release")) { it.enableUnitTest = false }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.generateKotlin", "true")
}

kotlin {
    compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
}

dependencies {
    implementation(project(":domain"))

    val composeBom = platform(libs.compose.bom)
    implementation(composeBom)
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.compose.icons)
    implementation(libs.compose.tooling.preview)
    debugImplementation(libs.compose.tooling)

    implementation(libs.androidx.core)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.fragment)
    implementation(libs.androidx.lifecycle.runtime)
    implementation(libs.androidx.lifecycle.viewmodel)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.biometric)
    implementation(libs.androidx.datastore)
    implementation(libs.androidx.work)
    implementation(libs.androidx.glance)
    implementation(libs.androidx.glance.material3)
    implementation(libs.coroutines.android)
    implementation(libs.serialization.json)
    implementation(libs.play.services.auth)
    implementation(libs.coroutines.play)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.work)
    ksp(libs.hilt.work.compiler)
    implementation(libs.hilt.navigation.compose)

    testImplementation(libs.junit4)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.test.ext)
    testImplementation(libs.room.testing)
    testImplementation(libs.coroutines.test)
    testImplementation(libs.androidx.work.testing)
    testImplementation(composeBom)
    testImplementation(libs.compose.ui.test)
    debugImplementation(libs.compose.ui.test.manifest)
}
