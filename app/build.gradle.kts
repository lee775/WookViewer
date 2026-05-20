plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.wook.viewer"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.wook.viewer"
        minSdk = 26
        targetSdk = 35
        versionCode = 102
        versionName = "1.0.2"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables { useSupportLibrary = true }
    }

    // 릴리즈 서명 — 환경 변수에서 keystore 경로/암호 읽기.
    // CI: build-release 워크플로가 secret 에서 디코드한 jks 경로/암호를 env 로 전달.
    // 로컬: keystore/wookviewer-release.jks 파일을 두고 환경 변수 RELEASE_KEYSTORE_PASSWORD 등 설정.
    val releaseKeystorePath: String? = System.getenv("RELEASE_KEYSTORE_PATH")
    val releaseKeystorePassword: String? = System.getenv("RELEASE_KEYSTORE_PASSWORD")
    val releaseKeyAlias: String? = System.getenv("RELEASE_KEY_ALIAS") ?: "wookviewer"
    val releaseKeyPassword: String? = System.getenv("RELEASE_KEY_PASSWORD") ?: releaseKeystorePassword
    val keystoreFile = releaseKeystorePath?.let { file(it) }
    val hasReleaseSigning = keystoreFile?.exists() == true &&
        !releaseKeystorePassword.isNullOrBlank()

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = keystoreFile
                storePassword = releaseKeystorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
        }
        release {
            // POI 5.x + xmlbeans 가 광범위한 디지털 서명/XML 스키마 클래스를 참조 →
            // R8 strict mode (AGP 8+) 가 미사용 누락 클래스 모두 error. 실용적으로 minify 비활성.
            // (서명·압축은 별도 — APK 사이즈 영향만 있음)
            isMinifyEnabled = false
            isShrinkResources = false
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
        freeCompilerArgs += listOf(
            "-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi",
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api"
        )
    }

    buildFeatures {
        compose = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            // hwplib(POI 3.9)이 함께 들고 오는 메타파일 충돌 방지
            excludes += "/META-INF/DEPENDENCIES"
            excludes += "/META-INF/LICENSE"
            excludes += "/META-INF/LICENSE.txt"
            excludes += "/META-INF/license.txt"
            excludes += "/META-INF/NOTICE"
            excludes += "/META-INF/NOTICE.txt"
            excludes += "/META-INF/notice.txt"
            excludes += "/META-INF/ASL2.0"
            excludes += "/META-INF/*.kotlin_module"
            // POI 3.17의 추가 META-INF 충돌
            excludes += "/META-INF/maven/**"
            excludes += "/META-INF/services/javax.xml.parsers.*"
            excludes += "/META-INF/services/javax.xml.stream.*"
            excludes += "/META-INF/services/javax.xml.transform.*"
            excludes += "/META-INF/services/org.xml.sax.*"
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)

    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.documentfile)
    implementation(libs.androidx.exifinterface)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.coil.compose)
    implementation(libs.timber)

    // HWP/HWPX 렌더 모듈 (PoC)
    // 주의: hwplib는 Apache POI 3.9를 번들 — APK 사이즈 영향 있음 (~10-15MB 추가 예상)
    //       프로덕션에서는 Dynamic Feature Module로 분리 검토
    implementation(libs.hwplib)
    implementation(libs.hwpxlib)
    // hwpxlib_ext: 암호화된 HWPX 복호화. JitPack 빌드.
    // 트랜지티브로 hwpxlib 1.0.3을 가져오는데 우리는 1.0.8 사용 — 충돌 방지 위해 exclude.
    implementation(libs.hwpxlib.ext) {
        exclude(group = "kr.dogfoot", module = "hwpxlib")
        exclude(group = "junit", module = "junit")
    }

    // PDF 텍스트 추출 (Android PdfRenderer는 비트맵만 제공 — 텍스트 검색/복사 불가)
    // PdfBox-Android: Apache 2.0, ~10MB 추가
    implementation(libs.pdfbox.android)

    // OOXML 암호 해제 (DOCX/PPTX/XLSX의 Agile/Standard encryption)
    // POI 5.2.5 + xmlbeans 5.x. APK 사이즈 ~10-15MB↑.
    // log4j2 트랜지티브는 SLF4J no-op 브리지로 처리.
    implementation(libs.poi.ooxml) {
        exclude(group = "stax", module = "stax-api")
        exclude(group = "javax.xml.stream", module = "stax-api")
        // POI 5는 log4j2 사용 — Android에서는 no-op로 처리
        exclude(group = "org.apache.logging.log4j", module = "log4j-core")
    }

    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
}

