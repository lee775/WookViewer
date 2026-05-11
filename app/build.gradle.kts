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
        versionCode = 27
        versionName = "0.7.5"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables { useSupportLibrary = true }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
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

    // PDF 텍스트 추출 (Android PdfRenderer는 비트맵만 제공 — 텍스트 검색/복사 불가)
    // PdfBox-Android: Apache 2.0, ~10MB 추가
    implementation(libs.pdfbox.android)

    // OOXML 암호 해제 (DOCX/PPTX/XLSX의 Agile/Standard encryption)
    // POI 3.17 — Apache 2.0, hwplib(POI 3.9)와 POIFS API 호환.
    // xmlbeans 트랜지티브 ~3MB 포함, 전체 추가 ~10-15MB APK 사이즈.
    implementation(libs.poi.ooxml)

    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
}
