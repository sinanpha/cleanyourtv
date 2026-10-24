package io.github.fyting.videoshelf;

import android.content.Context;
import android.content.SharedPreferences;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The weather now at options.xml's place, for the launcher's header
 * ({@code content://io.github.fyting.videoshelf.shelf/weather}): Open-Meteo's current temperature, WMO weather code
 * and day or night, which needs no account or key. Fetched only when the launcher asks (it asks while Home is on
 * screen), at most once every 30 minutes for the same place, and kept in this app's preferences so Home has an
 * answer while the network is still coming up.
 */
final class Weather {

    static final String[] COLUMNS = {"temperature", "code", "is_day", "updated"};
    private static final long FRESH_MS = 30 * 60_000;
    /** Older than this, a reading is not "now" any more: no row, and Home shows the date alone. */
    private static final long KEEP_MS = 3 * 60 * 60_000;
    private static final Pattern CURRENT = Pattern.compile("\"current\"\\s*:\\s*\\{([^}]*)\\}");

    private Weather() { }

    /** One row of {@link #COLUMNS} for the place, or null while there is no reading from the last three hours. */
    static synchronized Object[] now(Context context, double lat, double lon) {
        SharedPreferences saved = context.getSharedPreferences("weather", Context.MODE_PRIVATE);
        String place = String.format(Locale.ROOT, "%.2f,%.2f", lat, lon);
        long now = System.currentTimeMillis();
        long updated = place.equals(saved.getString("place", "")) ? saved.getLong("updated", 0) : 0;
        if (updated == 0 || now < updated || now - updated >= FRESH_MS) {
            double[] fresh = fetch(lat, lon);
            if (fresh != null) {
                saved.edit().putString("place", place).putFloat("temperature", (float) fresh[0])
                        .putInt("code", (int) fresh[1]).putInt("is_day", (int) fresh[2]).putLong("updated", now).apply();
                updated = now;
            }
        }
        if (updated == 0 || now - updated >= KEEP_MS) return null;
        return new Object[]{(double) saved.getFloat("temperature", 0f), saved.getInt("code", -1),
                saved.getInt("is_day", 1), updated};
    }

    private static double[] fetch(double lat, double lon) {
        String url = String.format(Locale.ROOT, "https://api.open-meteo.com/v1/forecast?latitude=%.2f&longitude=%.2f"
                + "&current=temperature_2m,weather_code,is_day&timezone=auto", lat, lon);
        try {
            byte[] data = Thumbnails.download(url);
            return data == null ? null : parse(new String(data, StandardCharsets.UTF_8));
        } catch (IOException | RuntimeException e) {
            return null;   // offline or refused: keep the last reading
        }
    }

    /**
     * {temperature in °C, WMO code, 1 by day or 0 by night} from Open-Meteo's answer, read from its "current" object
     * (not "current_units"); null without a temperature or a code. Plain text matching, so it is tested on the JVM.
     */
    static double[] parse(String json) {
        Matcher current = CURRENT.matcher(json == null ? "" : json);
        if (!current.find()) return null;
        String fields = current.group(1);
        Double temperature = number(fields, "temperature_2m");
        Double code = number(fields, "weather_code");
        Double day = number(fields, "is_day");
        if (temperature == null || code == null) return null;
        return new double[]{temperature, code, day == null ? 1 : day};
    }

    private static Double number(String fields, String key) {
        Matcher m = Pattern.compile("\"" + key + "\"\\s*:\\s*(-?\\d+(?:\\.\\d+)?)").matcher(fields);
        return m.find() ? Double.valueOf(m.group(1)) : null;
    }
}
