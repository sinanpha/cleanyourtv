package com.gathin.tvhome;

import static org.junit.Assert.assertArrayEquals;

import org.junit.Test;

public class LunarTest {

    @Test
    public void tetFallsOnTheFirstDayOfTheFirstMonth() {
        assertArrayEquals(new int[]{2024, 1, 1, 0}, Lunar.toLunar(2024, 2, 10));
        assertArrayEquals(new int[]{2025, 1, 1, 0}, Lunar.toLunar(2025, 1, 29));
        assertArrayEquals(new int[]{2026, 1, 1, 0}, Lunar.toLunar(2026, 2, 17));
    }

    @Test
    public void theDayBeforeTetIsStillTheOldYear() {
        assertArrayEquals(new int[]{2025, 12, 29, 0}, Lunar.toLunar(2026, 2, 16));
    }

    @Test
    public void midAutumnIsTheFifteenthOfTheEighthMonth() {
        assertArrayEquals(new int[]{2026, 8, 15, 0}, Lunar.toLunar(2026, 9, 25));
    }

    @Test
    public void leapMonthsAreMarked() {
        assertArrayEquals(new int[]{2023, 2, 1, 1}, Lunar.toLunar(2023, 3, 22));
        assertArrayEquals(new int[]{2025, 6, 1, 1}, Lunar.toLunar(2025, 7, 25));
        assertArrayEquals(new int[]{2025, 6, 30, 0}, Lunar.toLunar(2025, 7, 24));
    }

    @Test
    public void vietnamReckonsAtUtcPlus7NotChinasUtcPlus8() {
        // Tết 1985 and 2007 came a month and a day earlier in Vietnam than in China.
        assertArrayEquals(new int[]{1985, 1, 1, 0}, Lunar.toLunar(1985, 1, 21));
        assertArrayEquals(new int[]{2007, 1, 1, 0}, Lunar.toLunar(2007, 2, 17));
    }
}
