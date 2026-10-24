package com.gathin.tvhome;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class WallpaperSettingsTest {
    @Test public void anIntervalPickedOnTheTvWinsOverTheTheme() {
        // config.xml or the theme says 15 minutes, the owner picked 60 in Launcher settings.
        assertEquals(60, Config.chosen(60, 1, 1440, 15));
    }

    @Test public void noIntervalSavedKeepsTheThemeValue() {
        assertEquals(15, Config.chosen(-1, 1, 1440, 15));
    }

    @Test public void aSavedZeroIntervalIsIgnored() {
        // Settings never save 0 (no rotation is the Wallpaper row's job), so a 0 cannot stop a theme's rotation.
        assertEquals(15, Config.chosen(0, 1, 1440, 15));
    }

    @Test public void dimmingCanBeTurnedOffOnTheTv() {
        assertEquals(0, Config.chosen(0, 0, 90, 40));
        assertEquals(40, Config.chosen(-1, 0, 90, 40));
        assertEquals(40, Config.chosen(91, 0, 90, 40));
    }
}
