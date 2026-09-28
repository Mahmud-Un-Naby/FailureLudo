import java.io.File
import java.net.URI
import java.io.FileInputStream
import java.util.Properties
import java.security.KeyStore
import java.security.MessageDigest

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.ksp)
    alias(libs.plugins.google.services)
}

// Load release-signing credentials from keystore.properties (gitignored).
// Absent on machines that only need debug builds — release stays unsigned there.
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.exists()) load(FileInputStream(keystorePropertiesFile))
}
val hasReleaseSigning = keystorePropertiesFile.exists()


// Public upload-certificate identity supplied by Google Play. Never accept a replacement
// key silently; update this only after an intentional Play upload-key reset.
val verifyPlayUploadKey = tasks.register("verifyPlayUploadKey") {
    val credentialsFile = rootProject.layout.projectDirectory.file("keystore.properties")
    // Always inspect the actual key, even when release compilation is up to date.
    doLast {
        check(credentialsFile.asFile.isFile) {
            "Release signing is missing. See docs/release-signing.md."
        }
        val credentials = Properties().apply {
            credentialsFile.asFile.inputStream().use { load(it) }
        }
        val configuredPath = credentials.getProperty("storeFile")
            ?: error("Missing release storeFile. See docs/release-signing.md.")
        val keyFile = File(configuredPath).let {
            if (it.isAbsolute) it else File(credentialsFile.asFile.parentFile, configuredPath)
        }
        val certificate = try {
            val store = KeyStore.getInstance(keyFile, credentials.getProperty("storePassword").toCharArray())
            store.getCertificate(credentials.getProperty("keyAlias"))
                ?: error("Missing signing certificate")
        } catch (error: Exception) {
            throw GradleException("Cannot read release signing certificate. Check keystore.properties; see docs/release-signing.md.")
        }
        val fingerprint = MessageDigest.getInstance("SHA-1").digest(certificate.encoded)
            .joinToString(":") { "%02X".format(it.toInt() and 0xff) }
        check(fingerprint == "F7:95:24:B5:1C:24:B8:93:BE:1D:34:B3:2A:D1:25:83:E9:46:06:0A") {
            "Wrong Google Play upload key ($fingerprint). See docs/release-signing.md."
        }
        logger.lifecycle("Verified Google Play upload certificate: $fingerprint")
    }
}
tasks.matching { it.name == "preReleaseBuild" || it.name == "validateSigningRelease" }
    .configureEach { dependsOn(verifyPlayUploadKey) }

// Deployment supplies a public HTTPS service URL; an unset URL shows an unavailable screen.
val onlineApiUrl = providers.gradleProperty("onlineApiUrl").orElse("").get().trim().trimEnd('/')
require(onlineApiUrl.isEmpty() || runCatching {
    val uri = URI(onlineApiUrl)
    uri.scheme == "https" && !uri.host.isNullOrBlank() && uri.rawUserInfo == null &&
        uri.rawQuery == null && uri.rawFragment == null && uri.path.isNullOrEmpty()
}.getOrDefault(false)) { "onlineApiUrl must be an HTTPS origin without credentials, path or query" }

android {
    namespace = "com.failureludo"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.failureludo"
        minSdk = 26
        targetSdk = 36
        versionCode = 13
        versionName = "1.0.9"
        buildConfigField("String", "ONLINE_API_URL", "\"$onlineApiUrl\"")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = rootProject.file(keystoreProperties["storeFile"] as String)
                storePassword = keystoreProperties["storePassword"] as String
                keyAlias = keystoreProperties["keyAlias"] as String
                keyPassword = keystoreProperties["keyPassword"] as String
            }
        }
    }

    buildTypes {
        release {
            ndk { debugSymbolLevel = "FULL" }
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation(project(":game-engine"))
    implementation(project(":online-protocol"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.coroutines.android)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)

    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.firestore)
    implementation(libs.firebase.auth)
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services.auth)
    implementation(libs.googleid)

    testImplementation(libs.junit)
    testImplementation("org.json:json:20240303")
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}
