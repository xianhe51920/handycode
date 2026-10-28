-dontobfuscate
-dontoptimize
-keep class androidx.webkit.WebStorageCompat {
    public static java.lang.String deleteBrowsingDataForSite(android.webkit.WebStorage, java.lang.String, java.util.concurrent.Executor, java.lang.Runnable);
}
-keep class androidx.webkit.WebViewFeature {
    public static boolean isFeatureSupported(java.lang.String);
}
-keep interface org.chromium.support_lib_boundary.** { *; }
-dontwarn androidx.annotation.**
-dontwarn org.jspecify.annotations.**
