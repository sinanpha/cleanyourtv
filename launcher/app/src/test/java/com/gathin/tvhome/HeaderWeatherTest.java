package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class HeaderWeatherTest {

    @Test
    public void clearSkyIsTheSunByDayAndTheMoonAtNight() {
        assertEquals("☀️", HeaderWeather.icon(0, true));
        assertEquals("🌙", HeaderWeather.icon(0, false));
    }

    @Test
    public void wmoGroupsMapToOneIconEach() {
        assertEquals("☁️", HeaderWeather.icon(3, true));
        assertEquals("🌫️", HeaderWeather.icon(45, true));
        assertEquals("🌧️", HeaderWeather.icon(61, true));
        assertEquals("🌧️", HeaderWeather.icon(81, false));
        assertEquals("🌨️", HeaderWeather.icon(73, true));
        assertEquals("⛈️", HeaderWeather.icon(95, true));
    }

    @Test
    public void anUnknownCodeShowsTheTemperatureAlone() {
        assertEquals("", HeaderWeather.icon(42, true));
        assertEquals("27°", HeaderWeather.text(27.4, 42, true));
    }

    @Test
    public void theTemperatureIsRoundedToAWholeDegree() {
        assertEquals("☁️ 28°", HeaderWeather.text(27.5, 3, true));
        assertEquals("⛅ -2°", HeaderWeather.text(-2.4, 2, true));
    }
}
