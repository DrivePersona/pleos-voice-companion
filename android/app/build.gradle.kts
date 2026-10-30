plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.example.voicecompanion"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.voicecompanion"
        minSdk = 28 // Pleos 제품 앱 요구사항: Android API 28 이상
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
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
        // 시뮬레이터·상세 로그는 개발 빌드에만 보이도록 BuildConfig.DEBUG를 사용한다.
        buildConfig = true
    }

    flavorDimensions += "distribution"
    productFlavors {
        create("review") {
            dimension = "distribution"
            applicationIdSuffix = ".review"
            versionNameSuffix = "-review"
        }
        create("internal") {
            dimension = "distribution"
        }
    }

    // CarUxRestrictions(주행 규제)용 android.car. 폰 이미지에는 없으니 런타임에서 확인한다.
    useLibrary("android.car")
}

dependencies {
    val voicePackVersion = providers.gradleProperty("drivepersona.voicepack.version").get()
    val characterPackVersion = providers.gradleProperty("drivepersona.characterpack.version").get()

    implementation("io.drivepersona.packs:voicepack-runtime:$voicePackVersion")
    implementation("io.drivepersona.packs:characterpack-runtime:$characterPackVersion")
    // Approved content belongs only in team builds. The public review flavor uses
    // the built-in TTS path and contains no licensed voice or character assets.
    add("internalImplementation", "io.drivepersona.packs:voicepack-content:$voicePackVersion")
    add("internalImplementation", "io.drivepersona.packs:characterpack-content:$characterPackVersion")
    implementation("ai.pleos.playground:Vehicle:2.3.2")
    implementation("ai.pleos.playground:TextToSpeech:3.3.1")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.compose.material3:material3:1.3.1")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.7")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303") // 디버그 톤 팩을 앱 검증기로 읽는 단위 테스트용
}
