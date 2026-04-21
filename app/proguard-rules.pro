# Jotdown ProGuard Rules
# O app usa WebView com HTML/JS — não há código Kotlin para ofuscar além da Activity

# Mantém a Activity principal
-keep class br.com.jotdown.** { *; }

# Mantém interfaces do WebView
-keepclassmembers class * extends android.webkit.WebViewClient { *; }
-keepclassmembers class * extends android.webkit.WebChromeClient { *; }

# Suprime avisos de libs não usadas
-dontwarn java.lang.invoke.**
