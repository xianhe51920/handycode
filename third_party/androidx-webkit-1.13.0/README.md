# AndroidX WebKit site deletion API

AndroidX WebKit 1.13.0, Apache-2.0 (`LICENSE.txt`). The APK also includes the license at `assets/licenses/androidx-webkit.txt`.

Official inputs (unaltered):

- [webkit-1.13.0.aar](https://dl.google.com/dl/android/maven2/androidx/webkit/webkit/1.13.0/webkit-1.13.0.aar), SHA256 `001c836ea612e835991288a7a66877f25288e4cf5ec89729ee7ae066e3491a35`.
- [core-1.1.0.aar](https://dl.google.com/dl/android/maven2/androidx/core/core/1.1.0/core-1.1.0.aar), SHA256 `76c7cfbe596fe3c09a6983bf1c89e889299c08ac9a3b52ce5182a088d056647e`. Compile-only input for R8; no Core classes enter the app.
- Original WebKit POM retained. No downloaded executable or runtime network dependency.

`site-data-compat.jar` retains the official `WebStorageCompat.deleteBrowsingDataForSite(WebStorage,String,Executor,Runnable)` and `WebViewFeature.isFeatureSupported(String)` entry points, their reachable helpers and Chromium boundary interfaces. `site-data.pro` only removes unused library classes; no optimization or obfuscation. The application's own classes are compiled normally by javac/D8. Library checksum is checked before each app build.

Rebuild offline with SDK build-tools 36.0.0/R8 and JDK21:

```powershell
./tools/prepare-site-data-library.ps1 -AndroidSdk <SDK> -JavaHome <JDK>
```

[API documentation](https://developer.android.com/reference/androidx/webkit/WebStorageCompat): site deletion clears cookies, JavaScript storage and network cache for the selected registrable site. The app checks `DELETE_BROWSING_DATA`; unsupported WebViews show an update message. There is no global deletion fallback. Production policy is fixed in `LogoutSites.java`; tests use two reserved `.invalid` sites and exercise cookies, localStorage and IndexedDB isolation without touching account sites.
