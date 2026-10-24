package io.github.fyting.voicesearch;

/** The SmartTube tile: listens, then shows SmartTube's results (the stable build, or the beta). */
public class SmartTubeSearch extends VoiceSearch {

    @Override
    String[] targets() {
        return new String[]{"org.smarttube.stable", "org.smarttube.beta"};
    }

    @Override
    int prompt() {
        return R.string.prompt_smarttube;
    }
}
