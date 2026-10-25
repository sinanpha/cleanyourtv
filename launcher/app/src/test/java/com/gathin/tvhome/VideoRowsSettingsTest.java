package com.gathin.tvhome;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class VideoRowsSettingsTest {
    private static final List<String> CONFIGURED = Arrays.asList("Lịch sử", "Đề xuất");

    @Test public void nothingPickedOnTheTvKeepsConfigXml() {
        assertEquals(CONFIGURED, Config.homeVideosRows(CONFIGURED, null));
    }

    @Test public void rowsPickedOnTheTvWinInTheirOrder() {
        assertEquals(Arrays.asList("Lịch sử", "Kênh đăng ký"),
                Config.homeVideosRows(CONFIGURED, "Lịch sử\nKênh đăng ký"));
    }

    @Test public void everyRowOffMeansNoRowsAtAll() {
        // Null, not an empty list: config.xml's empty home_videos_rows means "the first row", this means none.
        assertNull(Config.homeVideosRows(CONFIGURED, ""));
        assertNull(Config.homeVideosRows(CONFIGURED, "\n \n"));
    }

    @Test public void settingsListTheProvidersRowsThenAnyOnRowItLacks() {
        List<String> offered = Arrays.asList("Lịch sử", "Kênh đăng ký", "Đề xuất");
        assertEquals(offered, Config.videoRowChoices(offered, CONFIGURED, CONFIGURED));
        // SmartTube stopped publishing Recommended for now: it stays listed while on, so it can still be turned off.
        assertEquals(Arrays.asList("Lịch sử", "Kênh đăng ký", "Đề xuất"),
                Config.videoRowChoices(Arrays.asList("Lịch sử", "Kênh đăng ký"), CONFIGURED, CONFIGURED));
    }

    @Test public void beforeTheProviderAnswersTheConfiguredRowsAreListed() {
        assertEquals(CONFIGURED, Config.videoRowChoices(Collections.emptyList(), CONFIGURED, Collections.emptySet()));
    }

    @Test public void theSavedChoiceFollowsTheListedOrder() {
        List<String> names = Arrays.asList("Lịch sử", "Kênh đăng ký", "Đề xuất");
        assertEquals("Lịch sử\nĐề xuất", Config.videoRowsChoice(names, new HashSet<>(Arrays.asList("Đề xuất", "Lịch sử"))));
        assertEquals("", Config.videoRowsChoice(names, Collections.emptySet()));
    }
}
