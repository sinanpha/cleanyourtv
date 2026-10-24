package io.github.fyting.videoshelf;

import android.content.ContentResolver;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.Process;
import android.util.LruCache;
import android.widget.ImageView;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Loads thumbnails off the main thread, scaled to the card's width in RGB_565 (a 16:9 card at 360 px is about
 * 150 KB), and keeps the recent ones in a small memory cache that goes when the screen closes. The files come from
 * the disk cache the launcher's rows fill too (ThumbCache), so a picture Home already showed needs no download.
 * Two background-priority threads: the TV has four slow cores, and the UI and render threads come first.
 */
final class Thumbnails {

    private static final int MAX_BYTES = 2 * 1024 * 1024;

    private final Context context;
    private final ContentResolver resolver;
    private final int targetWidth;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService pool = Executors.newFixedThreadPool(2, task -> new Thread(() -> {
        Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND);
        task.run();
    }, "thumbnails"));
    private final LruCache<String, Bitmap> cache = new LruCache<String, Bitmap>(6 * 1024 * 1024) {
        @Override
        protected int sizeOf(String key, Bitmap value) {
            return value.getByteCount();
        }
    };

    Thumbnails(Context context, int targetWidth) {
        this.context = context.getApplicationContext();
        this.resolver = context.getContentResolver();
        this.targetWidth = targetWidth;
    }

    /** The view's tag holds the URI it was last asked for, so a late answer never lands on a reused view. */
    void load(ImageView view, String uri) {
        view.setTag(uri);
        Bitmap hit = cache.get(uri);
        if (hit != null) {
            view.setImageBitmap(hit);
            return;
        }
        view.setImageDrawable(null);
        if (pool.isShutdown()) return;
        pool.execute(() -> {
            Bitmap bmp = fetch(uri);
            if (bmp == null) return;
            cache.put(uri, bmp);
            main.post(() -> {
                if (uri.equals(view.getTag())) view.setImageBitmap(bmp);
            });
        });
    }

    void clear() {
        cache.evictAll();
    }

    void shutdown() {
        pool.shutdownNow();
        cache.evictAll();
    }

    private Bitmap fetch(String uri) {
        byte[] data;
        try {
            data = read(uri);
        } catch (IOException | RuntimeException e) {
            return null;
        }
        if (data == null) return null;
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(data, 0, data.length, bounds);
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null;
        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inPreferredConfig = Bitmap.Config.RGB_565;
        // Let the JPEG decoder skip pixels first (a 1280 px picture decodes at 640 for a 400 px card, a quarter of
        // the work), then scale what is left to the card's width in the same decode.
        int sample = 1;
        while (bounds.outWidth / (sample * 2) >= targetWidth) sample *= 2;
        opts.inSampleSize = sample;
        int sampled = bounds.outWidth / sample;
        if (sampled > targetWidth) {
            opts.inScaled = true;
            opts.inDensity = sampled;
            opts.inTargetDensity = targetWidth;
        }
        return BitmapFactory.decodeByteArray(data, 0, data.length, opts);
    }

    /** The picture's bytes from an http(s) address, or null for any other scheme, a non-200 answer or over 2 MB. */
    static byte[] download(String uri) throws IOException {
        String scheme = Uri.parse(uri).getScheme();
        if (!"https".equalsIgnoreCase(scheme) && !"http".equalsIgnoreCase(scheme)) return null;
        HttpURLConnection conn = (HttpURLConnection) new URL(uri).openConnection();
        conn.setConnectTimeout(8000);
        conn.setReadTimeout(8000);
        conn.setInstanceFollowRedirects(true);
        try {
            if (conn.getResponseCode() != HttpURLConnection.HTTP_OK) return null;
            try (InputStream in = conn.getInputStream()) {
                return drain(in);
            }
        } finally {
            conn.disconnect();
        }
    }

    private byte[] read(String uri) throws IOException {
        Uri parsed = Uri.parse(uri);
        String scheme = parsed.getScheme();
        if ("https".equalsIgnoreCase(scheme) || "http".equalsIgnoreCase(scheme)) {
            File file = ThumbCache.get(context, uri);
            return file == null || file.length() > MAX_BYTES ? null : Files.readAllBytes(file.toPath());
        }
        if (ContentResolver.SCHEME_CONTENT.equals(scheme) || ContentResolver.SCHEME_ANDROID_RESOURCE.equals(scheme)) {
            try (InputStream in = resolver.openInputStream(parsed)) {
                return in == null ? null : drain(in);
            }
        }
        return null;
    }

    private static byte[] drain(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream(64 * 1024);
        byte[] buf = new byte[16 * 1024];
        int n;
        while ((n = in.read(buf)) != -1) {
            out.write(buf, 0, n);
            if (out.size() > MAX_BYTES) return null;
        }
        return out.toByteArray();
    }
}
