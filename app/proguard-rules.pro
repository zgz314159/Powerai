# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.

# Hilt rules
-keep class dagger.hilt.android.internal.managers.ViewComponentManager$FragmentContextWrapper { *; }
-keep class * extends androidx.lifecycle.ViewModel

# Room rules
-keep class * extends androidx.room.RoomDatabase
-keep class * extends androidx.room.Dao

# Gson rules
-keepattributes Signature
-keep class com.google.gson.reflect.TypeToken { *; }
-keep class * extends com.google.gson.reflect.TypeToken
-keep public class * implements com.google.gson.TypeAdapterFactory
-keep public class * implements com.google.gson.JsonSerializer
-keep public class * implements com.google.gson.JsonDeserializer

# JNI Bridge rules - Keep the interface and its implementations
-keep class com.example.powerai.core.model.PowerAIEngine { *; }
-keep class com.example.powerai.engine.ai.** { *; }
-keep class com.example.powerai.core.model.GenerationMetrics { *; }

# Keep native methods
-keepclasseswithmembernames class * {
    native <methods>;
}

# Remove Log calls in release
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
}

# PowerAi specific
-keep class com.example.powerai.core.model.** { *; }
-keep class com.example.powerai.data.importer.** { *; }
