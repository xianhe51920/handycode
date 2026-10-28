package com.fairycrane.handycode;

import android.os.Handler;
import android.os.Looper;
import android.webkit.WebStorage;
import androidx.webkit.WebStorageCompat;
import androidx.webkit.WebViewFeature;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** No cookie reads and no global clearing. UI-thread callbacks survive rotation. */
public final class SiteDataClearer {
    public interface Observer {
        void onStarted(int page);
        void onFinished(int page, boolean success);
    }
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final List<Observer> OBSERVERS = new ArrayList<>();
    private static int busyPage = -1;
    private SiteDataClearer() {}
    public static boolean isSupported() {
        try { return WebViewFeature.isFeatureSupported("DELETE_BROWSING_DATA"); }
        catch (RuntimeException | LinkageError unavailable) { return false; }
    }
    public static boolean isBusy() { return busyPage != -1; }
    public static void observe(Observer observer) {
        OBSERVERS.add(observer);
        if (isBusy()) observer.onStarted(busyPage);
    }
    public static void removeObserver(Observer observer) { OBSERVERS.remove(observer); }
    public static void start(int page) {
        if (isBusy()) return;
        final String[] sites = LogoutSites.forPage(page);
        if (!isSupported()) throw new UnsupportedOperationException("Site deletion unavailable");
        busyPage = page;
        for (Observer observer : new ArrayList<>(OBSERVERS)) observer.onStarted(page);
        clearSites(sites, () -> finish(page, true), error -> finish(page, false));
    }
    private static void finish(int page, boolean success) {
        busyPage = -1;
        for (Observer observer : new ArrayList<>(OBSERVERS)) observer.onFinished(page, success);
    }
    /** Also exercised against reserved .invalid fixture sites by device tests. */
    public static void clearSites(String[] sites, Runnable success, Consumer<Throwable> failure) {
        SiteClearQueue.run(sites, (site, complete) -> WebStorageCompat.deleteBrowsingDataForSite(
            WebStorage.getInstance(), site, command -> MAIN.post(command), complete), success, failure);
    }
}
