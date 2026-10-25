package com.gathin.tvhome;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class HomeVideosFitTest {
    // A MiTV-MOOR2 at 1080p: six cards across make 236 px pictures; about 550 px lie between the header and the dock.
    private static final int BASE = 236;

    @Test public void twoRowsThatFitKeepTheirSize() {
        // Two rows of 133 px pictures and 150 px of text and gaps each: 566 px, in 600 px of room.
        assertEquals(BASE, HomeVideos.fitCardWidth(BASE, BASE, 566, 2, 600));
    }

    @Test public void threeCompactRowsShrinkThePicturesOnly() {
        // Three compact rows of 92 px text and gaps plus 133 px pictures are 675 px; 550 px leaves 91 px a picture.
        int width = HomeVideos.fitCardWidth(BASE, BASE, 675, 3, 550);
        assertEquals(162, width);
        assertTrue(3 * (92 + width * 9 / 16) <= 550);
    }

    @Test public void cardsGrowBackWhenARowGoes() {
        // Back to two rows at the shrunk width: 2 x (150 + 91) = 482 px; 600 px of room fits the full size again.
        assertEquals(BASE, HomeVideos.fitCardWidth(BASE, 162, 482, 2, 600));
    }

    @Test public void neverBelowSixtyPercent() {
        assertEquals(Math.round(BASE * 0.6f), HomeVideos.fitCardWidth(BASE, BASE, 900, 4, 300));
    }
}
