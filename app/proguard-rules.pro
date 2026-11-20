# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class cc.toolcrate.telos.** { kotlinx.serialization.KSerializer serializer(...); }
# Bouncy Castle: only Argon2 is used.
-dontwarn org.bouncycastle.**
