-dontobfuscate
-dontoptimize
-keep class androidx.webkit.WebStorageCompat {
    public static java.lang.String deleteBrowsingDataForSite(android.webkit.WebStorage, java.lang.String, java.util.concurrent.Executor, java.lang.Runnable);
}
-keep class androidx.webkit.WebViewFeature {
    public static boolean isFeatureSupported(java.lang.String);
}
-keep class androidx.webkit.WebViewCompat {
    public static androidx.webkit.ScriptHandler addDocumentStartJavaScript(android.webkit.WebView, java.lang.String, java.util.Set);
}
-keep interface androidx.webkit.ScriptHandler { public void remove(); }
-keep interface org.chromium.support_lib_boundary.** { *; }
-dontwarn androidx.annotation.**
-dontwarn org.jspecify.annotations.**
