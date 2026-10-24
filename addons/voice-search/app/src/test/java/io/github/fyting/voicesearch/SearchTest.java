package io.github.fyting.voicesearch;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class SearchTest {

    @Test
    public void vietnameseWordsAreEncodedAsUtf8() {
        assertEquals("https://www.youtube.com/results?search_query=th%C3%A1m+t%E1%BB%AD+conan",
                Search.url("thám tử conan"));
    }

    @Test
    public void spokenSymbolsCannotBreakOutOfTheQuery() {
        // "&", "#" and "=" stay inside the one search_query value.
        assertEquals("https://www.youtube.com/results?search_query=tom+%26+jerry+%23shorts+a%3Db",
                Search.url("tom & jerry #shorts a=b"));
    }

    @Test
    public void surroundingSpacesAreDropped() {
        assertEquals("https://www.youtube.com/results?search_query=sushi", Search.url("  sushi "));
    }
}
