-keep class br.com.jotdown.data.entity.** { *; }
-keep class br.com.jotdown.data.dao.** { *; }
-keepattributes *Annotation*

# Room — evita remoção dos DAOs e Database pelo R8
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Dao class *

# ML Kit — mantém classes de reconhecimento de texto
-keep class com.google.mlkit.** { *; }
-dontwarn com.google.mlkit.**

# Compose — evita problemas com reflexão
-keep class androidx.compose.** { *; }
-dontwarn androidx.compose.**

# PDFBox-Android (Evitar erros com JP2Decoder e outras libs opcionais)
-keep class com.tom_roush.pdfbox.** { *; }
-dontwarn com.tom_roush.pdfbox.**
-dontwarn com.gemalto.jp2.**
-dontwarn org.bouncycastle.**
-dontwarn org.apache.fontbox.**
-dontwarn org.apache.pdfbox.**


# Google API Client & Drive
-keep class com.google.api.services.drive.** { *; }
-keep class com.google.api.client.** { *; }
-dontwarn com.google.api.client.**
-dontwarn com.google.api.services.drive.**

# Retrofit & Gson
-keep class sun.misc.Unsafe { *; }
-keep class com.google.gson.stream.** { *; }
-keep class com.google.gson.examples.android.model.** { *; }
-keep class com.google.gson.** { *; }
-dontwarn com.google.gson.**
-keep class retrofit2.** { *; }
-dontwarn retrofit2.**
-keepattributes Signature
-keepattributes Exceptions