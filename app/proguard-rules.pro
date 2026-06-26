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
-keep @kotlinx.serialization.Serializable class com.archerypal.app.data.** { *; }

-keep class io.libp2p.** { *; }
-dontwarn io.libp2p.**
-keep class io.libp2p.protocol.circuit.** { *; }
-keep class io.netty.** { *; }
-dontwarn io.netty.**
