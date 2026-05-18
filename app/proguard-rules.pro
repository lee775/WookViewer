# Hilt
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keep,allowobfuscation @interface dagger.hilt.android.lifecycle.HiltViewModel
-keep @dagger.hilt.android.lifecycle.HiltViewModel class * { *; }

# Room
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao class * { *; }
-dontwarn androidx.room.paging.**

# Kotlin metadata + reflection
-keepattributes Signature, *Annotation*, InnerClasses, EnclosingMethod
-keep class kotlin.Metadata { *; }
-keep class kotlin.reflect.** { *; }

# Compose
-keep class androidx.compose.runtime.** { *; }
-keepclassmembers class androidx.compose.runtime.** { *; }

# Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}

# ----- LibreOfficeKit JNI (CRITICAL — JNI 메서드명 난독화 시 native crash) -----
-keep class org.libreoffice.kit.** { *; }
-keepclassmembers class org.libreoffice.kit.** {
    native <methods>;
    <init>(...);
}
# Document.MessageCallback 은 JNI 에서 호출 — 인터페이스 + 메서드 유지
-keep interface org.libreoffice.kit.Document$MessageCallback { *; }
-keepclassmembers class org.libreoffice.kit.Document {
    private void messageRetrieved(int, java.lang.String);
}
# DirectBufferAllocator 도 JNI 에서 직접 호출 가능
-keep class org.libreoffice.kit.DirectBufferAllocator { *; }

# hwplib / hwpxlib — 리플렉션 기반 객체 매핑
-keep class kr.dogfoot.hwplib.** { *; }
-keep class kr.dogfoot.hwpxlib.** { *; }
-keepclassmembers class kr.dogfoot.** { *; }
-dontwarn kr.dogfoot.**

# Apache POI — 리플렉션 + schema 클래스 다수
# R8 의 "Missing classes" 에러 방지: POI 가 디지털 서명/메타데이터 스키마를 참조하지만
# 우리는 그 기능 안 씀 → 누락 클래스 모두 무시.
-keep class org.apache.poi.** { *; }
-keep class org.openxmlformats.** { *; }
-keep class schemaorg_apache_xmlbeans.** { *; }
-keep class com.microsoft.schemas.** { *; }
-keep class org.etsi.uri.** { *; }
-keep class org.w3.x2000.** { *; }
-keep class org.w3.x2003.** { *; }
-dontwarn org.apache.poi.**
-dontwarn org.apache.xmlbeans.**
-dontwarn org.openxmlformats.**
-dontwarn schemaorg_apache_xmlbeans.**
-dontwarn com.microsoft.schemas.**
-dontwarn org.etsi.uri.**
-dontwarn org.w3.**
# POI 디지털 서명·외부 의존 (사용 안 함)
-dontwarn org.apache.xml.security.**
-dontwarn de.rototor.pdfbox.graphics2d.**
-dontwarn com.zaxxer.sparsebits.**
-dontwarn com.graphbuilder.**
-dontwarn org.osgi.framework.**

# R8 missing class 에러 전반적으로 무시 (POI 가 끌어오는 광범위한 미사용 의존)
-ignorewarnings

# xmlbeans
-keep class org.apache.xmlbeans.** { *; }

# Bouncy Castle (POI crypto)
-keep class org.bouncycastle.** { *; }
-dontwarn org.bouncycastle.**

# SLF4J / log4j — POI 의존
-dontwarn org.slf4j.**
-dontwarn org.apache.logging.log4j.**
-dontwarn org.apache.commons.logging.**

# javax / w3c — POI 가 참조하지만 안드로이드에 없는 항목
-dontwarn javax.xml.**
-dontwarn javax.annotation.**
-dontwarn org.w3c.**
-dontwarn org.xml.**
-dontwarn java.awt.**
-dontwarn java.beans.**

# PdfBox-Android
-keep class com.tom_roush.pdfbox.** { *; }
-keep class org.apache.pdfbox.** { *; }
-dontwarn com.tom_roush.pdfbox.**
-dontwarn org.apache.pdfbox.**

# Timber
-dontwarn org.jetbrains.annotations.**

# 라인 번호 유지 → 크래시 로그 의미 있게
-keepattributes SourceFile, LineNumberTable
-renamesourcefileattribute SourceFile
