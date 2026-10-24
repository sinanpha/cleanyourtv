package io.github.fyting.voicesearch;

import android.accessibilityservice.AccessibilityService;
import android.content.Context;
import android.content.Intent;
import android.provider.Settings;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;

import java.util.function.IntConsumer;

/**
 * Turns a remote button given a voice search (Bindings) into one: a press anywhere opens the listening bar for
 * SmartTube or YouTube. Keys only, as the launcher's InputKeyService takes the remote's app buttons: no accessibility
 * events and nothing read from the screen. A bound button is taken on press and release, and acts on release. It is
 * switched on in the TV's accessibility settings, or from a shell (README).
 *
 * <p>Another service that takes the same button (the launcher's app_keys, say) still gets it too: Android hands a
 * key to every service that filters keys, so a button should be bound in one place only.
 */
public class KeyService extends AccessibilityService {

    /** While the screen waits for a button to bind, the next bindable press goes there instead (MainActivity). */
    static volatile IntConsumer capture;

    @Override
    protected boolean onKeyEvent(KeyEvent event) {
        int code = event.getKeyCode();
        IntConsumer waiting = capture;
        if (waiting != null && Keys.bindable(code)) {
            if (event.getAction() == KeyEvent.ACTION_UP && !event.isCanceled()) waiting.accept(code);
            return true;
        }
        String target = Bindings.target(this, code);
        if (target == null) return false;
        if (event.getAction() == KeyEvent.ACTION_UP && !event.isCanceled()) {
            Class<?> bar = Keys.YOUTUBE.equals(target) ? YouTubeSearch.class : SmartTubeSearch.class;
            startActivity(new Intent(this, bar).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        }
        return true;
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        // keys only: the service asks for no events
    }

    @Override
    public void onInterrupt() {
        // nothing spoken or shown to interrupt
    }

    /** Whether the TV has this service switched on (the accessibility list names it). */
    static boolean enabled(Context context) {
        String list = Settings.Secure.getString(context.getContentResolver(),
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        return list != null && list.contains(context.getPackageName() + "/");
    }
}
