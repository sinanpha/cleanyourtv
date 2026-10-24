package com.gathin.tvhome;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.View;
import android.widget.TextView;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Optional weather after the date (weather_authority): an icon for the sky now and the temperature, read from another
 * app's content provider ({@code content://<authority>/weather}, one row: temperature, code, is_day, updated). The
 * Video Shelf build serves Open-Meteo's current weather for the place it is set to; Home only reads it, so the
 * launcher keeps its no-network rule. Off while the authority is empty.
 */
final class HeaderWeather {

    /** The provider keeps a reading for half an hour, so a read every quarter hour on screen is plenty. */
    private static final long REREAD_MS = 15 * 60_000;
    /** After no answer (the network is still coming up after a boot), try again sooner. */
    private static final long RETRY_MS = 2 * 60_000;
    /** A reading older than this (the TV was offline) is not shown as the weather now. */
    private static final long STALE_MS = 3 * 60 * 60_000;

    private final ContentResolver resolver;
    private final TextView view;
    private final Uri uri;
    private final Handler main = new Handler(Looper.getMainLooper());
    // Background priority: Home's own UI and render threads come first on a four-core TV.
    private final ExecutorService io = Executors.newSingleThreadExecutor(task -> new Thread(() -> {
        android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND);
        task.run();
    }, "home-weather"));
    private long readAt = -REREAD_MS;

    HeaderWeather(Context context, TextView view, String authority) {
        this.resolver = context.getContentResolver();
        this.view = view;
        this.uri = Uri.parse("content://" + authority + "/weather");
    }

    /** Reads the weather again while Home is on screen, at most once a quarter hour; the view changes only on a change. */
    void refresh() {
        long now = SystemClock.elapsedRealtime();
        if (io.isShutdown() || now - readAt < REREAD_MS) return;
        readAt = now;
        io.execute(() -> {
            String text = null;
            try (Cursor c = resolver.query(uri, null, null, null, null)) {
                if (c != null && c.moveToFirst()) {
                    long updated = c.getLong(c.getColumnIndexOrThrow("updated"));
                    if (System.currentTimeMillis() - updated < STALE_MS) {
                        text = text(c.getDouble(c.getColumnIndexOrThrow("temperature")),
                                c.getInt(c.getColumnIndexOrThrow("code")), c.getInt(c.getColumnIndexOrThrow("is_day")) != 0);
                    }
                }
            } catch (RuntimeException e) {
                // The app behind the provider is missing, offline or refused us: the date alone, as without it.
            }
            String shown = text;
            main.post(() -> show(shown));
        });
    }

    void close() {
        io.shutdownNow();
    }

    private void show(String text) {
        if (text == null) {
            readAt = SystemClock.elapsedRealtime() - REREAD_MS + RETRY_MS;
            view.setVisibility(View.GONE);
            return;
        }
        if (!text.contentEquals(view.getText())) view.setText(text);
        view.setVisibility(View.VISIBLE);
    }

    /** "☁️ 27°": the icon for the conditions, then the temperature rounded to a whole degree. */
    static String text(double temperature, int code, boolean day) {
        String icon = icon(code, day);
        return (icon.isEmpty() ? "" : icon + " ") + Math.round(temperature) + "°";
    }

    /** An emoji (the TV's colour emoji font, no pictures in the APK) for a WMO weather code; empty for an unknown one. */
    static String icon(int code, boolean day) {
        if (code == 0) return day ? "☀️" : "🌙";                       // clear: sun, or moon
        if (code == 1 || code == 2) return day ? "⛅" : "☁️";                 // a few clouds
        if (code == 3) return "☁️";                                               // overcast
        if (code == 45 || code == 48) return "🌫️";                          // fog
        if ((code >= 51 && code <= 67) || (code >= 80 && code <= 82)) return "🌧️";   // drizzle, rain
        if ((code >= 71 && code <= 77) || code == 85 || code == 86) return "🌨️";     // snow
        if (code >= 95 && code <= 99) return "⛈️";                                // thunderstorm
        return "";
    }
}
