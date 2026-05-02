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
