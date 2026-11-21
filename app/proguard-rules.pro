# Entidades Room e DTOs de rede (Gson/Retrofit)
-keep class br.com.jotdown.data.entity.** { *; }
-keep class br.com.jotdown.data.network.** { *; }
-keepattributes *Annotation*,Signature,Exceptions,InnerClasses,EnclosingMethod

# Room — mantém Database e DAOs gerados pelo KSP, permitindo ofuscar nomes internos
-keep class * extends androidx.room.RoomDatabase { *; }
-keep,allowobfuscation @androidx.room.Dao class * { *; }

# ML Kit — os AARs já trazem regras próprias para JNI/@KeepForSdk
-dontwarn com.google.mlkit.**

# Compose — os AARs do Compose já possuem consumer rules próprias
-dontwarn androidx.compose.**

# PDFBox-Android — mantém classes usadas e permite ofuscação/shrinking onde seguro
-keep,allowobfuscation,allowshrinking class com.tom_roush.pdfbox.** { *; }
-dontwarn com.tom_roush.pdfbox.**
-dontwarn com.gemalto.jp2.**
-dontwarn org.bouncycastle.**
-dontwarn org.apache.fontbox.**
-dontwarn org.apache.pdfbox.**

# Google API Client & Drive — preserva campos @Key refletidos pelo GenericJson
-keepclassmembers class * {
    @com.google.api.client.util.Key <fields>;
}
-keep,allowobfuscation,allowshrinking class * extends com.google.api.client.json.GenericJson {
    @com.google.api.client.util.Key <fields>;
    public <init>();
}
-dontwarn com.google.api.client.**
-dontwarn com.google.api.services.drive.**

# Retrofit & Gson — usam consumer rules embutidas nos AARs/JARs
-keep class sun.misc.Unsafe { *; }
-dontwarn com.google.gson.**
-dontwarn retrofit2.**
