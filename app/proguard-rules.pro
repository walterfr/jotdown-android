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
