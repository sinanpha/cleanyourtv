package io.github.fyting.voicesearch;

/** The YouTube tile: listens, then shows the results in YouTube for Android TV. */
public class YouTubeSearch extends VoiceSearch {

    @Override
    String[] targets() {
        return new String[]{"com.google.android.youtube.tv"};
    }

    @Override
    int prompt() {
        return R.string.prompt_youtube;
    }
}
