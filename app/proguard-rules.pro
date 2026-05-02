-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.ajsharm.imagen.**$$serializer { *; }
-keepclassmembers class com.ajsharm.imagen.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}

# Tink (used by androidx.security.crypto) references these annotations at runtime/reflection.
-dontwarn com.google.errorprone.annotations.**
-dontwarn javax.annotation.**
-dontwarn javax.annotation.concurrent.**
-keep class com.google.crypto.tink.** { *; }
-dontwarn com.google.api.client.**
-dontwarn org.joda.time.**

# OkHttp / Okio
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

