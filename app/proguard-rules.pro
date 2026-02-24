# ──────────────────────────────────────────────────
# Phantom – ProGuard / R8 Rules
# ──────────────────────────────────────────────────

# ── Debugging ────────────────────────────────────
# Keep line numbers for crash reports (tiny size cost, huge debugging value)
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# ── OkHttp ───────────────────────────────────────
# OkHttp uses reflection for platform detection
-dontwarn okhttp3.internal.platform.**
-dontwarn org.bouncycastle.**
-dontwarn org.conscrypt.**
-dontwarn org.openjsse.**
-dontwarn okio.**

# ── Hilt / Dagger ────────────────────────────────
# Hilt-generated code relies on component/module class names
-keepclassmembers class * {
    @javax.inject.Inject <init>(...);
}
-keepclassmembers class * {
    @dagger.hilt.android.lifecycle.HiltViewModel <init>(...);
}

# ── ML Kit ───────────────────────────────────────
-dontwarn com.google.mlkit.**

# ── Kotlin Metadata (needed for Hilt) ────────────
-keep class kotlin.Metadata { *; }

# ── App: Keep BuildConfig for API keys ───────────
-keep class dev.korryr.phantom.BuildConfig { *; }

# ── Don't warn on common annotations ────────────
-dontwarn javax.annotation.**
-dontwarn org.codehaus.mojo.animal_sniffer.**