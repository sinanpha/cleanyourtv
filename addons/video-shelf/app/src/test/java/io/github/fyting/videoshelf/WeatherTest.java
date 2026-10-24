package io.github.fyting.videoshelf;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class WeatherTest {

    /** The shape Open-Meteo answers with: the units first, under a name that also starts with "current". */
    private static final String ANSWER = "{\"latitude\":21.0,\"longitude\":105.875,\"timezone\":\"Asia/Bangkok\","
            + "\"current_units\":{\"time\":\"iso8601\",\"interval\":\"seconds\",\"temperature_2m\":\"°C\","
            + "\"weather_code\":\"wmo code\",\"is_day\":\"\"},"
            + "\"current\":{\"time\":\"2026-10-24T16:45\",\"interval\":900,\"temperature_2m\":27.4,"
            + "\"weather_code\":3,\"is_day\":1}}";

    @Test
    public void readsTheCurrentObjectNotItsUnits() {
        assertArrayEquals(new double[]{27.4, 3, 1}, Weather.parse(ANSWER), 0.001);
    }

    @Test
    public void readsBelowZeroAtNight() {
        assertArrayEquals(new double[]{-3, 71, 0}, Weather.parse(
                "{\"current\":{\"temperature_2m\":-3,\"weather_code\":71,\"is_day\":0}}"), 0.001);
    }

    @Test
    public void anAnswerWithoutTemperatureOrCodeIsNoReading() {
        assertNull(Weather.parse("{\"current\":{\"weather_code\":3}}"));
        assertNull(Weather.parse("{\"error\":true,\"reason\":\"Latitude must be in range\"}"));
        assertNull(Weather.parse(null));
    }
}
