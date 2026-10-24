package com.gathin.tvhome;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Names for a wallpaper collection (wallpaper_captions): {@code wallpapers/captions.txt}, one
 * "filename<TAB>name" per line. Blank lines, lines starting with # and lines without a tab are skipped; a name
 * is cut to {@link #MAX_LENGTH} characters. No Android types, so it is tested on the JVM.
 */
final class WallpaperCaptions {
    static final String FILE_NAME = "captions.txt";
    static final int MAX_LENGTH = 80;
    /** Pictures copied from the TV's screensaver cache over ADB are named ss-&lt;16 hex digits&gt;.webp. */
    static final String SCREENSAVER_PREFIX = "ss-";

    private WallpaperCaptions() { }

    static Map<String, String> parse(List<String> lines) {
        Map<String, String> names = new HashMap<>();
        for (String line : lines) {
            if (line == null || line.trim().isEmpty() || line.trim().startsWith("#")) continue;
            int tab = line.indexOf('\t');
            if (tab <= 0) continue;
            String file = line.substring(0, tab).trim();
            String name = line.substring(tab + 1).trim();
            if (file.isEmpty() || name.isEmpty()) continue;
            if (name.length() > MAX_LENGTH) name = name.substring(0, MAX_LENGTH).trim();
            names.put(file, name);
        }
        return names;
    }

    /**
     * What the Wallpaper picker shows for each file, in order: its caption, {@code screensaverFormat} with the
     * picture's number among the screensaver's copies (ss- files, which have only a hash for a name), or the file
     * name without its extension.
     */
    static List<String> labels(List<String> files, Map<String, String> captions, String screensaverFormat) {
        List<String> labels = new ArrayList<>(files.size());
        int screensaver = 0;
        for (String file : files) {
            boolean copied = file.startsWith(SCREENSAVER_PREFIX);
            if (copied) screensaver++;
            String caption = captions.get(file);
            int dot = file.lastIndexOf('.');
            if (caption != null) labels.add(caption);
            else if (copied) labels.add(String.format(Locale.getDefault(), screensaverFormat, screensaver));
            else labels.add(dot > 0 ? file.substring(0, dot) : file);
        }
        return labels;
    }

    /** The names in {@code folder}/captions.txt, or none when the file is missing or unreadable. */
    static Map<String, String> read(File folder) {
        File file = folder == null ? null : new File(folder, FILE_NAME);
        if (file == null || !file.isFile() || file.length() > 256 * 1024) return Collections.emptyMap();
        try {
            return parse(Files.readAllLines(file.toPath(), StandardCharsets.UTF_8));
        } catch (Exception e) {
            return Collections.emptyMap();
        }
    }
}
