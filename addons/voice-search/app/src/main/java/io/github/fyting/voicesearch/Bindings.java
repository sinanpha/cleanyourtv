package io.github.fyting.voicesearch;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.Map;
import java.util.TreeMap;

/**
 * The remote buttons given a voice search: key code to Keys.SMARTTUBE or Keys.YOUTUBE, in this app's preferences.
 * The screen writes them and KeyService reads them in the same process, so a change counts from the next press.
 */
final class Bindings {

    private static final String PREFIX = "key_";

    private Bindings() { }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences("bindings", Context.MODE_PRIVATE);
    }

    /** What the button opens, or null when it is not bound. */
    static String target(Context context, int code) {
        return prefs(context).getString(PREFIX + code, null);
    }

    static void set(Context context, int code, String target) {
        prefs(context).edit().putString(PREFIX + code, target).apply();
    }

    static void remove(Context context, int code) {
        prefs(context).edit().remove(PREFIX + code).apply();
    }

    /** Every bound button, by key code. */
    static Map<Integer, String> all(Context context) {
        Map<Integer, String> all = new TreeMap<>();
        for (Map.Entry<String, ?> e : prefs(context).getAll().entrySet()) {
            if (!e.getKey().startsWith(PREFIX) || !(e.getValue() instanceof String)) continue;
            try {
                all.put(Integer.parseInt(e.getKey().substring(PREFIX.length())), (String) e.getValue());
            } catch (NumberFormatException ignored) {
                // not a key code: skip it
            }
        }
        return all;
    }
}
