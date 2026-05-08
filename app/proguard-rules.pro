# Hilt
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }

# Room
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**

# Compose
-keep class androidx.compose.runtime.** { *; }

# hwplib / hwpxlib — 리플렉션 기반 객체 매핑 방어
-keep class kr.dogfoot.hwplib.** { *; }
-keep class kr.dogfoot.hwpxlib.** { *; }
-keepclassmembers class kr.dogfoot.** { *; }
-dontwarn kr.dogfoot.**

# Apache POI (hwplib 번들)
-keep class org.apache.poi.** { *; }
-dontwarn org.apache.poi.**
-dontwarn org.apache.xmlbeans.**
-dontwarn org.openxmlformats.**
-dontwarn schemaorg_apache_xmlbeans.**

# javax / w3c — POI가 참조하지만 안드로이드에 없는 항목
-dontwarn javax.xml.**
-dontwarn org.w3c.**
-dontwarn org.xml.**

# PdfBox-Android
-keep class com.tom_roush.pdfbox.** { *; }
-keep class org.apache.pdfbox.** { *; }
-dontwarn com.tom_roush.pdfbox.**
-dontwarn org.apache.pdfbox.**
