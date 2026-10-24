package io.github.fyting.voicesearch;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;

/** The search address both apps open on their results. Plain Java, so it is tested on the JVM (src/test). */
final class Search {

    static final String RESULTS = "https://www.youtube.com/results?search_query=";

    private Search() { }

    /** The address for what was heard: trimmed, then encoded as a query value (UTF-8, a space as +). */
    static String url(String query) {
        try {
            return RESULTS + URLEncoder.encode(query.trim(), "UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException(e);   // every Java has UTF-8
        }
    }
}
