# Add your own rules here. Keep Google Play Services and ML Kit models.
-keep class com.google.android.gms.** { *; }
-dontwarn com.google.android.gms.**

-keep class com.google.mlkit.** { *; }
-dontwarn com.google.mlkit.**

-keepattributes *Annotation*, InnerClasses, EnclosingMethod, Signature
-keepclassmembers class kotlinx.serialization.json.** { *; }
-keep @kotlinx.serialization.Serializable class com.archerypal.data.** { *; }
