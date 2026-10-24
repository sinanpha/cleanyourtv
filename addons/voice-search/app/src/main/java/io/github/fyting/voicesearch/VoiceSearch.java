package io.github.fyting.voicesearch;

import android.Manifest;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;

/**
 * Press, then speak: listens at once and hands what was heard to an app's search, as the address
 * {@code https://www.youtube.com/results?search_query=...}, which SmartTube and YouTube both open on their results.
 * It talks to the TV's speech recognition service directly (Google's on Google TV), as SmartTube's own search does:
 * the system voice screen (RECOGNIZE_SPEECH) waited for another press on a MiTV-MOOR2 before it listened. The service
 * listens only for an app that holds the microphone permission, so the first press asks for it. A small panel at the
 * top shows the words as they are heard; OK listens again after a miss, Back closes. A {@code query} string extra
 * skips the listening, for a shell or a test:
 * {@code am start -n io.github.fyting.voicesearch/.SmartTubeSearch --es query conan}.
 */
abstract class VoiceSearch extends Activity {

    private static final int ASK_MIC = 1;
    /** After a miss the panel waits this long for OK, then gets out of the way. */
    private static final long IDLE_CLOSE_MS = 8000;

    private final Handler main = new Handler(Looper.getMainLooper());
    private final Runnable close = this::finish;
    private SpeechRecognizer recognizer;
    private GradientDrawable orbColour;
    /** A soft ring behind the orb that follows the voice; the orb and its microphone keep their size. */
    private View halo;
    /** The voice level, smoothed so the ring breathes instead of jumping with every reading. */
    private float level;
    private TextView words;
    private boolean listening;

    /** The apps to search in, in order: the first one installed gets the search. */
    abstract String[] targets();

    /** The line the panel shows while it waits for the first word. */
    abstract int prompt();

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        String query = getIntent().getStringExtra("query");
        if (query != null) {
            search(query);
            return;
        }
        setContentView(panel());
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            Toast.makeText(this, R.string.no_voice_input, Toast.LENGTH_LONG).show();
            finish();
        } else if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            listen();
        } else {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, ASK_MIC);
        }
    }

    @Override
    public void onRequestPermissionsResult(int request, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(request, permissions, results);
        if (request != ASK_MIC) return;
        if (results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) {
            listen();
        } else {
            Toast.makeText(this, R.string.no_mic, Toast.LENGTH_LONG).show();
            finish();
        }
    }

    /**
     * The bound remote button (or the tile) pressed again while the bar is up: one bar (singleTask), which listens
     * again after a miss and otherwise keeps listening.
     */
    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        String query = intent.getStringExtra("query");
        if (query != null) search(query);
        else if (!listening && recognizer != null) listen();
    }

    /** OK (or Enter) listens again after a miss; Back closes, as everywhere. */
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if ((keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER) && !listening) {
            listen();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    /** Home or another app took over: the microphone stops with the panel. */
    @Override
    protected void onStop() {
        super.onStop();
        if (!isFinishing()) finish();
    }

    @Override
    protected void onDestroy() {
        main.removeCallbacks(close);
        if (recognizer != null) recognizer.destroy();
        super.onDestroy();
    }

    private void listen() {
        main.removeCallbacks(close);
        if (recognizer == null) {
            recognizer = SpeechRecognizer.createSpeechRecognizer(this);
            recognizer.setRecognitionListener(new Listener());
        }
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_WEB_SEARCH)
                .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                .putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, getPackageName());
        String language = getString(R.string.language).trim();
        if (!language.isEmpty()) intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, language);
        listening = true;
        show(getString(prompt()), false);
        orbColour.setColor(0xFFE0483E);
        halo.setVisibility(View.VISIBLE);
        recognizer.startListening(intent);
    }

    private final class Listener implements RecognitionListener {
        @Override public void onReadyForSpeech(Bundle params) { }
        @Override public void onBeginningOfSpeech() { }
        @Override public void onBufferReceived(byte[] buffer) { }
        @Override public void onEndOfSpeech() { }
        @Override public void onEvent(int eventType, Bundle params) { }

        /**
         * The ring grows with the voice: about -2 dB in a quiet room, 10 dB when someone speaks up. Only the ring
         * scales; scaling the orb blew its microphone (a vector drawn once at its own size) up into a blurred,
         * misshapen glyph on every reading.
         */
        @Override
        public void onRmsChanged(float rms) {
            float target = Math.max(0f, Math.min(1f, (rms + 2f) / 12f));
            level = level * 0.6f + target * 0.4f;
            halo.setScaleX(1f + level * 0.35f);
            halo.setScaleY(1f + level * 0.35f);
        }

        @Override
        public void onPartialResults(Bundle partial) {
            String heard = first(partial);
            if (heard != null) show(heard, true);
        }

        @Override
        public void onResults(Bundle results) {
            listening = false;
            String heard = first(results);
            if (heard == null) {
                missed(R.string.retry);
                return;
            }
            show(heard, true);
            search(heard);
        }

        @Override
        public void onError(int error) {
            listening = false;
            if (error == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) {
                Toast.makeText(VoiceSearch.this, R.string.no_mic, Toast.LENGTH_LONG).show();
                finish();
            } else if (error == SpeechRecognizer.ERROR_NETWORK || error == SpeechRecognizer.ERROR_NETWORK_TIMEOUT
                    || error == SpeechRecognizer.ERROR_SERVER) {
                missed(R.string.no_network);
            } else {
                missed(R.string.retry);   // nothing heard, or not understood
            }
        }
    }

    private void missed(int message) {
        level = 0f;
        halo.setScaleX(1f);
        halo.setScaleY(1f);
        halo.setVisibility(View.INVISIBLE);
        orbColour.setColor(0xFF5A5A5A);
        show(getString(message), false);
        main.postDelayed(close, IDLE_CLOSE_MS);
    }

    private static String first(Bundle bundle) {
        List<String> list = bundle == null ? null : bundle.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
        String heard = list == null || list.isEmpty() ? null : list.get(0);
        return TextUtils.isEmpty(heard) || heard.trim().isEmpty() ? null : heard.trim();
    }

    private void show(String text, boolean heard) {
        words.setText(text);
        words.setTextColor(heard ? Color.WHITE : 0xB3FFFFFF);
    }

    private void search(String query) {
        Intent results = new Intent(Intent.ACTION_VIEW, Uri.parse(Search.url(query)))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        for (String pkg : targets()) {
            try {
                startActivity(results.setPackage(pkg));
                finish();
                return;
            } catch (ActivityNotFoundException ignored) {
                // not installed (or disabled): the next one
            }
        }
        Toast.makeText(this, R.string.app_missing, Toast.LENGTH_LONG).show();
        finish();
    }

    /** A dimmed screen with one rounded bar at the top: the microphone orb and the words. */
    private FrameLayout panel() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(0x99000000);

        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(8), dp(4), dp(36), dp(4));
        GradientDrawable barShape = new GradientDrawable();
        barShape.setColor(0xF2202020);
        barShape.setCornerRadius(dp(48));
        bar.setBackground(barShape);

        // Room around the 64 dp orb for the ring at its largest (64 x 1.35, about 86 dp), so it is never clipped.
        FrameLayout mic = new FrameLayout(this);
        halo = new View(this);
        GradientDrawable haloShape = new GradientDrawable();
        haloShape.setShape(GradientDrawable.OVAL);
        haloShape.setColor(0x66E0483E);
        halo.setBackground(haloShape);
        mic.addView(halo, new FrameLayout.LayoutParams(dp(64), dp(64), Gravity.CENTER));
        ImageView orb = new ImageView(this);
        orbColour = new GradientDrawable();
        orbColour.setShape(GradientDrawable.OVAL);
        orbColour.setColor(0xFFE0483E);
        orb.setBackground(orbColour);
        orb.setImageResource(R.drawable.mic);
        orb.setScaleType(ImageView.ScaleType.CENTER);
        mic.addView(orb, new FrameLayout.LayoutParams(dp(64), dp(64), Gravity.CENTER));
        bar.addView(mic, new LinearLayout.LayoutParams(dp(88), dp(88)));

        words = new TextView(this);
        words.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
        words.setSingleLine(true);
        words.setEllipsize(TextUtils.TruncateAt.START);   // the newest words stay in view
        LinearLayout.LayoutParams wp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        wp.leftMargin = dp(12);
        bar.addView(words, wp);

        FrameLayout.LayoutParams bp = new FrameLayout.LayoutParams(
                getResources().getDisplayMetrics().widthPixels * 2 / 3, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        bp.topMargin = dp(48);
        root.addView(bar, bp);
        return root;
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
