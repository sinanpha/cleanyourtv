package io.github.fyting.videoshelf;

import android.content.Context;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Comparator;

/**
 * The thumbnails on disk, shared by this app's screen (Thumbnails) and the launcher's rows (ShelfProvider): a picture
 * downloaded for one is there at once for the other. One file per address, named by its SHA-1, in the app's cache
 * folder; each use marks the file as fresh, and only the {@link #MAX_FILES} used last are kept.
 */
final class ThumbCache {

    /** Room for every row Home holds (home_row_limit each), this screen's rows and some churn; about 30 KB each. */
    static final int MAX_FILES = 120;

    private ThumbCache() { }

    /** The picture behind an http(s) address as a file, downloaded on first use; null when it cannot be had. */
    static File get(Context context, String url) {
        File dir = new File(context.getCacheDir(), "shelf");
        File file = new File(dir, hash(url) + ".jpg");
        if (file.isFile()) {
            file.setLastModified(System.currentTimeMillis());
            return file;
        }
        byte[] data;
        try {
            data = Thumbnails.download(url);
        } catch (IOException | RuntimeException e) {
            return null;
        }
        if (data == null || (!dir.isDirectory() && !dir.mkdirs())) return null;
        // A part file per thread: the screen's two loaders and the provider can fetch the same picture at once.
        File part = new File(dir, file.getName() + "." + Thread.currentThread().getId() + ".part");
        try (FileOutputStream os = new FileOutputStream(part)) {
            os.write(data);
        } catch (IOException e) {
            part.delete();
            return null;
        }
        if (!part.renameTo(file)) {
            part.delete();
            if (!file.isFile()) return null;   // another thread's copy landed first: use it
        }
        prune(dir);
        return file;
    }

    /** Keeps the pictures used last; the rows change every few hours, so older ones are rarely asked for again. */
    private static void prune(File dir) {
        File[] files = dir.listFiles();
        if (files == null || files.length <= MAX_FILES) return;
        Arrays.sort(files, Comparator.comparingLong(File::lastModified));
        for (int i = 0; i < files.length - MAX_FILES; i++) files[i].delete();
    }

    static String hash(String s) {
        try {
            byte[] d = MessageDigest.getInstance("SHA-1").digest(s.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(d.length * 2);
            for (byte b : d) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            return Integer.toHexString(s.hashCode());
        }
    }
}
