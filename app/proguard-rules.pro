# Add your own rules here. Keep Google Play Services and ML Kit models.
-keep class com.google.android.gms.** { *; }
-dontwarn com.google.android.gms.**

-keep class com.google.android.gms.ads.** { *; }
-dontwarn com.google.android.gms.ads.**

-keep class com.android.vending.billing.** { *; }
-dontwarn com.android.vending.billing.**

-keep class com.google.mlkit.** { *; }
-dontwarn com.google.mlkit.**

-keepattributes *Annotation*, InnerClasses, EnclosingMethod, Signature
-keepclassmembers class kotlinx.serialization.json.** { *; }
-keep class com.google.android.libraries.identity.googleid.** { *; }
-dontwarn com.google.android.libraries.identity.googleid.**

-keep class androidx.credentials.** { *; }
-dontwarn androidx.credentials.**

-dontwarn android.os.ProfilingTrigger$Builder
-dontwarn android.os.ProfilingTrigger

