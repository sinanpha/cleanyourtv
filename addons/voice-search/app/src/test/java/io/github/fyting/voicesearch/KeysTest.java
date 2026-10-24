package io.github.fyting.voicesearch;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.view.KeyEvent;

import org.junit.Test;

public class KeysTest {

    @Test
    public void theButtonsTheTvNeedsCannotBeBound() {
        for (int code : new int[]{KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_BACK,
                KeyEvent.KEYCODE_HOME, KeyEvent.KEYCODE_POWER, KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.KEYCODE_VOLUME_MUTE,
                KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_UNKNOWN}) {
            assertFalse(String.valueOf(code), Keys.bindable(code));
        }
    }

    @Test
    public void appAndColourButtonsCanBeBound() {
        assertTrue(Keys.bindable(KeyEvent.KEYCODE_BUTTON_3));    // 190, the YouTube button on Xiaomi remotes
        assertTrue(Keys.bindable(KeyEvent.KEYCODE_PROG_RED));
        assertTrue(Keys.bindable(KeyEvent.KEYCODE_SETTINGS));
        assertTrue(Keys.bindable(KeyEvent.KEYCODE_TV_INPUT));
        assertTrue(Keys.bindable(KeyEvent.KEYCODE_SEARCH));
    }

    @Test
    public void labelsReadLikeTheKeyNameWithTheCode() {
        assertEquals("BUTTON 3 (190)", Keys.label("KEYCODE_BUTTON_3", 190));
        assertEquals("PROG RED (183)", Keys.label("KEYCODE_PROG_RED", 183));
        assertEquals("300", Keys.label("300", 300));    // a code Android has no name for
        assertEquals("301", Keys.label(null, 301));
    }
}
