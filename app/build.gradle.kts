import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.zhixing"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.zhixing"
        minSdk = 29
        targetSdk = 35
        versionCode = 3
        versionName = "0.3.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // 签名配置从 local.properties 读取：密码不进版本库，构建时动态注入
    signingConfigs {
        create("release") {
            val props = Properties()
            rootProject.file("local.properties").inputStream().use { input -> props.load(input) }
            storeFile = rootProject.file(props.getProperty("ZHIXING_KEYSTORE_FILE") ?: "app/zhixing-release.keystore")
            storePassword = props.getProperty("ZHIXING_STORE_PASSWORD") ?: ""
            keyAlias = props.getProperty("ZHIXING_KEY_ALIAS") ?: "zhixing"
            keyPassword = props.getProperty("ZHIXING_KEY_PASSWORD") ?: ""
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            // 已签名 release 包：密码通过 local.properties 注入，不进版本库
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // 默认值留空：UI 输入框不预填假占位，与 debug 保持一致。
            buildConfigField("String", "DECOMPOSE_BASE_URL", "\"\"")
            buildConfigField("String", "DECOMPOSE_TOKEN", "\"\"")
            buildConfigField("String", "DECOMPOSE_MODEL", "\"\"")
        }

        // 后端代理配置（ADR-0001：app 不直调大模型）；token 通过 local.properties 注入，不进版本库
        debug {
            // 默认值留空：UI 输入框不预填假占位，避免用户每次都要手动删除。
            // 保存校验由 SettingsViewModel.save 负责非空 + 格式检查，不依赖 BuildConfig 兜底。
            buildConfigField("String", "DECOMPOSE_BASE_URL", "\"\"")
            buildConfigField("String", "DECOMPOSE_TOKEN", "\"\"")
            buildConfigField("String", "DECOMPOSE_MODEL", "\"\"")
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
    // Compose BOM
    val composeBom = platform("androidx.compose:compose-bom:2024.10.01")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.6")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6")
    implementation("androidx.activity:activity-compose:1.9.3")

    // Compose UI
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    // Material Icons Extended：逾期排期块需要 Alarm 小闹钟图标（core 仅含 ~10 个基础图标）。
    implementation("androidx.compose.material:material-icons-extended")

    // Navigation
    implementation("androidx.navigation:navigation-compose:2.8.4")

    // Room
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // Networking (Retrofit + OkHttp + Moshi) — AI 拆解走后端代理（ADR-0001）
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-moshi:2.11.0")
    implementation("com.squareup.moshi:moshi-kotlin:1.15.1")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // Unit tests
    testImplementation("junit:junit:4.13.2")
    testImplementation("androidx.test:core:1.6.1")
    testImplementation("org.assertj:assertj-core:3.26.3")
    testImplementation("androidx.room:room-testing:2.6.1")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")

    // Instrumented tests (on-device: Room persistence, DAOs)
    androidTestImplementation("androidx.test:core:1.6.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test:rules:1.6.1")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.room:room-testing:2.6.1")
    androidTestImplementation("org.assertj:assertj-core:3.26.3")
    androidTestImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")

    // Compose UI tests
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
