package io.github.fyting.voicesearch;

import android.view.KeyEvent;

/** Which remote buttons can be given a voice search, and what they are called. Plain Java, tested on the JVM. */
final class Keys {

    /** What a bound button opens: the listening bar for SmartTube, or for YouTube. */
    static final String SMARTTUBE = "smarttube";
    static final String YOUTUBE = "youtube";

    private Keys() { }

    /**
     * False for the buttons the TV cannot do without: moving around, OK, Back, Home, power and volume. Any other
     * button (an app button, a colour key, Settings, Input, a number...) can open a voice search instead.
     */
    static boolean bindable(int code) {
        switch (code) {
            case KeyEvent.KEYCODE_UNKNOWN:
            case KeyEvent.KEYCODE_DPAD_UP:
            case KeyEvent.KEYCODE_DPAD_DOWN:
            case KeyEvent.KEYCODE_DPAD_LEFT:
            case KeyEvent.KEYCODE_DPAD_RIGHT:
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_NUMPAD_ENTER:
            case KeyEvent.KEYCODE_BACK:
            case KeyEvent.KEYCODE_ESCAPE:
            case KeyEvent.KEYCODE_HOME:
            case KeyEvent.KEYCODE_POWER:
            case KeyEvent.KEYCODE_SLEEP:
            case KeyEvent.KEYCODE_WAKEUP:
            case KeyEvent.KEYCODE_VOLUME_UP:
            case KeyEvent.KEYCODE_VOLUME_DOWN:
            case KeyEvent.KEYCODE_VOLUME_MUTE:
                return false;
            default:
                return true;
        }
    }

    /**
     * A button's name from {@link KeyEvent#keyCodeToString}: "KEYCODE_BUTTON_3" with code 190 reads "BUTTON 3 (190)",
     * and a code Android has no name for (it answers with the number) reads just "300".
     */
    static String label(String keyCodeString, int code) {
        String name = keyCodeString == null ? "" : keyCodeString.replaceFirst("^KEYCODE_", "").replace('_', ' ').trim();
        return name.isEmpty() || name.equals(String.valueOf(code)) ? String.valueOf(code) : name + " (" + code + ")";
    }
}
