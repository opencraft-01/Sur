# ---- PrimeBox / CloudStream extension compatibility ----
-keep class com.lagradost.cloudstream3.** { *; }
-keep class com.lagradost.api.** { *; }
-keep class com.lagradost.nicehttp.** { *; }
-dontwarn com.lagradost.**

# ---- JSON (Jackson) ----
-keep class com.fasterxml.jackson.** { *; }
-keepclassmembers class * { @com.fasterxml.jackson.annotation.* <fields>; @com.fasterxml.jackson.annotation.* <methods>; }
-dontwarn com.fasterxml.jackson.**

# ---- kotlinx.serialization ----
-keepattributes *Annotation*, InnerClasses, Signature, RuntimeVisibleAnnotations, AnnotationDefault
-keep,includedescriptorclasses class **$$serializer { *; }
-keepclassmembers class ** { *** Companion; }
-keepclasseswithmembers class ** { kotlinx.serialization.KSerializer serializer(...); }
-dontwarn kotlinx.serialization.**

# ---- misc deps ----
-dontwarn org.jsoup.**
-dontwarn org.mozilla.javascript.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn javax.annotation.**
-dontwarn kotlin.**
-dontwarn org.slf4j.**
-dontwarn io.ktor.**
-keep class org.mozilla.javascript.** { *; }

# keep our own entry points
-keep class com.primebox.** { *; }
