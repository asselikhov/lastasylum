import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp") version "1.9.24-1.0.20"
}

val squadRelayLocalProperties = Properties().apply {
    // Монорепо: часто local.properties лежит в корне репозитория; приоритет у mobile-android/local.properties.
    listOf(
        rootProject.file("../local.properties"),
        rootProject.file("local.properties"),
    ).filter { it.exists() }.forEach { f ->
        f.inputStream().use { load(it) }
    }
}

android {
    namespace = "com.lastasylum.alliance"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.lastasylum.alliance"
        minSdk = 28
        targetSdk = 34
        versionCode = 2
        versionName = "0.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
        buildConfigField("long", "BUILD_TIME_MS", "${System.currentTimeMillis()}L")
    }

    flavorDimensions += "env"
    productFlavors {
        create("dev") {
            dimension = "env"
            // Эмулятор: 10.0.2.2. Физическое устройство: в mobile-android/local.properties задать
            // squadrelay.api.baseUrl=http://ВАШ_IP:3000/
            val overrideUrl = squadRelayLocalProperties.getProperty("squadrelay.api.baseUrl")?.trim().orEmpty()
            val devApiUrl = if (overrideUrl.isNotEmpty()) {
                overrideUrl.trimEnd('/') + "/"
            } else {
                "http://10.0.2.2:3000/"
            }
            buildConfigField("String", "API_BASE_URL", "\"$devApiUrl\"")
        }
        create("prod") {
            dimension = "env"
            buildConfigField(
                "String",
                "API_BASE_URL",
                "\"https://lastasylum-backend.onrender.com/\"",
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
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
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

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.09.00")

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.4")
    implementation("androidx.activity:activity-compose:1.9.1")
    implementation("androidx.navigation:navigation-compose:2.7.7")
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
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}

