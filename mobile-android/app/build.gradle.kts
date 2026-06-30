import com.android.build.gradle.internal.api.BaseVariantOutputImpl
import groovy.json.JsonSlurper
import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp") version "2.3.7"
    // Firebase: google-services.json → BuildConfig; init in SquadRelayApplication (no gms plugin:
    // devDebug uses applicationIdSuffix ".debug" and needs a second Firebase Android app).
}

val squadRelayAndroidPackage = "com.lastasylum.alliance"

val squadRelayLocalProperties = Properties().apply {
    // Монорепо: часто local.properties лежит в корне репозитория; приоритет у mobile-android/local.properties.
    listOf(
        rootProject.file("../local.properties"),
        rootProject.file("local.properties"),
    ).filter { it.exists() }.forEach { f ->
        f.inputStream().use { load(it) }
    }
}

fun propEsc(key: String): String =
    squadRelayLocalProperties.getProperty(key)?.trim().orEmpty()
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")

/** Values from app/google-services.json when present (see google-services.json.example). */
data class FirebaseClientConfig(
    val projectId: String,
    val appId: String,
    val apiKey: String,
)

@Suppress("UNCHECKED_CAST")
fun firebaseFromGoogleServicesJson(): FirebaseClientConfig {
    val jsonFile = file("google-services.json").takeIf { it.exists() } ?: return FirebaseClientConfig("", "", "")
    return runCatching {
        val root = JsonSlurper().parseText(jsonFile.readText()) as Map<String, Any?>
        val projectInfo = root["project_info"] as? Map<String, Any?> ?: return FirebaseClientConfig("", "", "")
        val projectId = projectInfo["project_id"]?.toString().orEmpty()
        val clients = root["client"] as? List<*> ?: return FirebaseClientConfig("", "", "")
        for (entry in clients) {
            val client = entry as? Map<String, Any?> ?: continue
            val clientInfo = client["client_info"] as? Map<String, Any?> ?: continue
            val androidInfo = clientInfo["android_client_info"] as? Map<String, Any?> ?: continue
            val pkg = androidInfo["package_name"]?.toString().orEmpty()
            if (pkg != squadRelayAndroidPackage) continue
            val appId = clientInfo["mobilesdk_app_id"]?.toString().orEmpty()
            val apiKeys = client["api_key"] as? List<*> ?: continue
            val apiKey = apiKeys.firstOrNull()?.let { row ->
                (row as? Map<*, *>)?.get("current_key")?.toString()
            }.orEmpty()
            return FirebaseClientConfig(projectId, appId, apiKey)
        }
        FirebaseClientConfig("", "", "")
    }.getOrDefault(FirebaseClientConfig("", "", ""))
}

fun firebaseBuildConfigString(localKey: String, fromJson: String): String {
    val fromProps = propEsc(localKey)
    val value = fromProps.ifEmpty { fromJson.trim() }
    return value.replace("\\", "\\\\").replace("\"", "\\\"")
}

val firebaseFromJson = firebaseFromGoogleServicesJson()

/** Monorepo git root (LastAsylum); falls back to mobile-android module dir. */
fun gitRepositoryRoot(): java.io.File {
    val parent = rootProject.file("..")
    return if (parent.resolve(".git").exists()) parent else rootProject.rootDir
}

/** Short SHA of HEAD at build time; shown in Settings for sideload traceability. */
fun gitCommitShort(): String = runCatching {
    val repo = gitRepositoryRoot()
    ProcessBuilder("git", "-C", repo.absolutePath, "rev-parse", "--short", "HEAD")
        .redirectErrorStream(true)
        .start()
        .let { process ->
            val output = process.inputStream.bufferedReader().readText().trim()
            val ok = process.waitFor() == 0
            if (ok && output.isNotEmpty()) output else "unknown"
        }
}.getOrDefault("unknown")

/** Monotonic build number from git history; CI may override via SQUADRELAY_VERSION_CODE. */
fun gitCommitCount(): Int = runCatching {
    val repo = gitRepositoryRoot()
    ProcessBuilder("git", "-C", repo.absolutePath, "rev-list", "--count", "HEAD")
        .redirectErrorStream(true)
        .start()
        .let { process ->
            val output = process.inputStream.bufferedReader().readText().trim()
            val ok = process.waitFor() == 0
            if (ok) output.toIntOrNull()?.coerceAtLeast(1) else null
        }
}.getOrNull() ?: 2

val squadRelayGitCommit = gitCommitShort()
/** Публичное имя версии (APK, релизы); без -debug/-benchmark — это только в UI приложения. */
val squadRelayVersionName = "0.1.0"
/** Game client version the bundled map bridge is built for (see .tmp-tools/patch-frida-gadget.ps1). */
val squadRelayMapBridgeGameVersion = "1.0.81"
/** Map bridge logic version expected by this app build; bump together with BRIDGE_VERSION in
 * .tmp-tools/frida/map_fly_bridge.js so an installed patch with an older bridge shows as outdated. */
val squadRelayMapBridgeVersion = "54"
val squadRelayVersionCode = System.getenv("SQUADRELAY_VERSION_CODE")?.trim()?.toIntOrNull()
    ?.coerceAtLeast(1)
    ?: gitCommitCount()

/** Публичный бэкенд (Render). И prod, и dev по умолчанию — чтобы телефон работал без LAN. */
val squadRelayPublicBackendUrl = "https://lastasylum-backend-jbaa.onrender.com/"

android {
    namespace = "com.lastasylum.alliance"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.lastasylum.alliance"
        minSdk = 28
        // Android 15+: overlay при targetSdk < 35 — системный диалог «не оптимизировано»
        // и ломает прохождение тапов в игру (стрелка «назад» в чате и т.п.).
        targetSdk = 35
        versionCode = squadRelayVersionCode
        versionName = squadRelayVersionName

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
        buildConfigField("long", "BUILD_TIME_MS", "${System.currentTimeMillis()}L")
        buildConfigField("String", "GIT_COMMIT", "\"$squadRelayGitCommit\"")
        buildConfigField(
            "String",
            "MAP_BRIDGE_GAME_VERSION",
            "\"$squadRelayMapBridgeGameVersion\"",
        )
        buildConfigField(
            "String",
            "MAP_BRIDGE_VERSION",
            "\"$squadRelayMapBridgeVersion\"",
        )
        buildConfigField(
            "String",
            "FIREBASE_PROJECT_ID",
            "\"${firebaseBuildConfigString("squadrelay.firebase.projectId", firebaseFromJson.projectId)}\"",
        )
        buildConfigField(
            "String",
            "FIREBASE_APP_ID",
            "\"${firebaseBuildConfigString("squadrelay.firebase.appId", firebaseFromJson.appId)}\"",
        )
        buildConfigField(
            "String",
            "FIREBASE_API_KEY",
            "\"${firebaseBuildConfigString("squadrelay.firebase.apiKey", firebaseFromJson.apiKey)}\"",
        )
        buildConfigField("String", "CERT_PINS", "\"${propEsc("squadrelay.certPins")}\"")
    }

    flavorDimensions += "env"
    productFlavors {
        create("dev") {
            dimension = "env"
            // По умолчанию — Render (удобно с реального телефона). Локальный Nest:
            // в local.properties: squadrelay.api.baseUrl=http://10.0.2.2:3000/ (эмулятор)
            // или http://IP_ПК_LAN:3000/ (телефон в той же Wi‑Fi).
            val overrideUrl = squadRelayLocalProperties.getProperty("squadrelay.api.baseUrl")?.trim().orEmpty()
            val devApiUrl = if (overrideUrl.isNotEmpty()) {
                overrideUrl.trimEnd('/') + "/"
            } else {
                squadRelayPublicBackendUrl
            }
            buildConfigField("String", "API_BASE_URL", "\"$devApiUrl\"")
        }
        create("prod") {
            dimension = "env"
            buildConfigField(
                "String",
                "API_BASE_URL",
                "\"$squadRelayPublicBackendUrl\"",
            )
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
        debug {
            // No applicationIdSuffix: one Firebase Android app (google-services.json) + FCM on devDebug.
            versionNameSuffix = "-debug"
        }
        create("benchmark") {
            // Macrobenchmark target must not be debuggable; keep dev API config from debug.
            initWith(getByName("debug"))
            isDebuggable = false
            isMinifyEnabled = false
            matchingFallbacks += listOf("debug")
            versionNameSuffix = "-benchmark"
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

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
        // Moshi @Json and Android @DrawableRes on data-class ctor params (KT-73255).
        freeCompilerArgs.add("-Xannotation-default-target=param-property")
    }
}

/** APK для раздачи: SquadRelay-0.1.0-552.apk (без суффиксов buildType). */
@Suppress("DEPRECATION")
android.applicationVariants.configureEach {
    val apkFileName = "SquadRelay-$squadRelayVersionName-$versionCode.apk"
    outputs.configureEach {
        (this as BaseVariantOutputImpl).outputFileName = apkFileName
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2025.12.01")

    implementation("androidx.core:core-ktx:1.16.0")
    implementation("com.google.android.material:material:1.13.0")
    // Explicit base artifacts: required for ViewTree*Owner classes (overlay ComposeView owners).
    implementation("androidx.activity:activity-ktx:1.10.1")
    implementation("androidx.lifecycle:lifecycle-runtime:2.9.4")
    implementation("androidx.savedstate:savedstate:1.2.1")
    implementation("androidx.savedstate:savedstate-compose:1.3.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.9.4")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.9.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.4")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.navigation:navigation-compose:2.9.0")
    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-moshi:2.11.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
    implementation("com.squareup.moshi:moshi-kotlin:1.15.1")
    ksp("com.squareup.moshi:moshi-kotlin-codegen:1.15.1")
    implementation("io.socket:socket.io-client:2.1.1") {
        exclude(group = "org.json", module = "json")
    }
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.9.0")

    implementation("androidx.room:room-runtime:2.7.1")
    implementation("androidx.room:room-ktx:2.7.1")
    ksp("androidx.room:room-compiler:2.7.1")

    implementation("androidx.work:work-runtime-ktx:2.10.0")

    implementation(platform("com.google.firebase:firebase-bom:33.5.1"))
    implementation("com.google.firebase:firebase-messaging-ktx")
    implementation("com.google.firebase:firebase-crashlytics-ktx")
    implementation("com.google.firebase:firebase-analytics-ktx")

    implementation("androidx.profileinstaller:profileinstaller:1.4.1")

    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("io.coil-kt.coil3:coil-compose:3.4.0")
    implementation("io.coil-kt.coil3:coil-network-okhttp:3.4.0")
    implementation("io.coil-kt.coil3:coil-gif:3.4.0")
    implementation("com.airbnb.android:lottie:6.4.1")

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
    testImplementation("androidx.test:core:1.5.0")
    testImplementation("org.robolectric:robolectric:4.12.2")
    testImplementation("androidx.room:room-testing:2.7.1")
    testImplementation("androidx.work:work-testing:2.10.0")

    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
}

