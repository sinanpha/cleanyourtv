package io.github.fyting.voicesearch;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Bundle;
import android.provider.Settings;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Map;

/**
 * The app's one tile: speak to SmartTube or YouTube from here, and give remote buttons a voice search. A bound button
 * works anywhere once KeyService is switched on; this screen says whether it is and opens the setting.
 */
public class MainActivity extends Activity {

    private static final int SIDE_DP = 56;

    private TextView status;
    private View enable;
    private LinearLayout buttons;
    private AlertDialog waiting;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(SIDE_DP), dp(36), dp(SIDE_DP), dp(36));

        TextView title = text(getString(R.string.app_name), 28, Color.WHITE);
        title.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        page.addView(title);
        TextView hint = text(getString(R.string.main_hint), 14, 0x99FFFFFF);
        hint.setPadding(0, dp(6), 0, dp(20));
        page.addView(hint);

        LinearLayout speak = new LinearLayout(this);
        speak.setOrientation(LinearLayout.HORIZONTAL);
        speak.addView(button(getString(R.string.smarttube_label), v -> startActivity(new Intent(this, SmartTubeSearch.class))));
        speak.addView(button(getString(R.string.youtube_label), v -> startActivity(new Intent(this, YouTubeSearch.class))));
        page.addView(speak);

        TextView section = text(getString(R.string.remote_buttons), 20, Color.WHITE);
        section.setPadding(0, dp(32), 0, dp(4));
        page.addView(section);
        status = text("", 14, 0xB3FFFFFF);
        status.setPadding(0, 0, 0, dp(8));
        page.addView(status);
        enable = button(getString(R.string.enable_service), v -> openAccessibilitySettings());
        page.addView(enable);

        buttons = new LinearLayout(this);
        buttons.setOrientation(LinearLayout.VERTICAL);
        page.addView(buttons);
        page.addView(button(getString(R.string.add_button), v -> waitForButton()));
        TextView note = text(getString(R.string.add_note), 13, 0x80FFFFFF);
        note.setPadding(0, dp(10), 0, 0);
        page.addView(note);

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(0xFF101010);
        scroll.addView(page);
        setContentView(scroll);
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    @Override
    protected void onPause() {
        super.onPause();
        KeyService.capture = null;
        if (waiting != null) waiting.dismiss();
    }

    /** The service's state and the bound buttons, each a row that changes or removes it. */
    private void refresh() {
        boolean on = KeyService.enabled(this);
        status.setText(on ? R.string.service_on : R.string.service_off);
        enable.setVisibility(on ? View.GONE : View.VISIBLE);
        buttons.removeAllViews();
        Map<Integer, String> all = Bindings.all(this);
        if (all.isEmpty()) {
            TextView none = text(getString(R.string.no_buttons), 15, 0x99FFFFFF);
            none.setPadding(0, dp(8), 0, dp(8));
            buttons.addView(none);
        }
        for (Map.Entry<Integer, String> e : all.entrySet()) {
            int code = e.getKey();
            buttons.addView(button(getString(R.string.binding_row, label(code), targetName(e.getValue())),
                    v -> change(code)));
        }
    }

    private void change(int code) {
        String[] choices = {getString(R.string.smarttube_label), getString(R.string.youtube_label),
                getString(R.string.remove_button)};
        new AlertDialog.Builder(this)
                .setTitle(label(code))
                .setItems(choices, (dialog, which) -> {
                    if (which == 2) Bindings.remove(this, code);
                    else Bindings.set(this, code, which == 0 ? Keys.SMARTTUBE : Keys.YOUTUBE);
                    refresh();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    /**
     * Asks for the button. With KeyService on, the service hands over the press (it sees buttons another service
     * would take before this screen does); without it, the dialog catches what reaches it.
     */
    private void waitForButton() {
        waiting = new AlertDialog.Builder(this)
                .setTitle(R.string.press_title)
                .setMessage(R.string.press_message)
                .setNegativeButton(android.R.string.cancel, null)
                .setOnDismissListener(d -> KeyService.capture = null)
                .create();
        waiting.setOnKeyListener((dialog, keyCode, event) -> {
            if (!Keys.bindable(keyCode)) return false;   // move, OK and Back work as usual
            if (event.getAction() == KeyEvent.ACTION_UP) pressed(keyCode);
            return true;
        });
        KeyService.capture = code -> runOnUiThread(() -> pressed(code));
        waiting.show();
    }

    private void pressed(int code) {
        if (waiting == null || !waiting.isShowing()) return;
        waiting.dismiss();
        KeyService.capture = null;
        String[] choices = {getString(R.string.smarttube_label), getString(R.string.youtube_label)};
        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.choose_target, label(code)))
                .setItems(choices, (dialog, which) -> {
                    String target = which == 0 ? Keys.SMARTTUBE : Keys.YOUTUBE;
                    Bindings.set(this, code, target);
                    Toast.makeText(this, getString(R.string.binding_row, label(code), targetName(target)),
                            Toast.LENGTH_SHORT).show();
                    refresh();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void openAccessibilitySettings() {
        try {
            startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, R.string.no_accessibility_settings, Toast.LENGTH_LONG).show();
        }
    }

    private static String label(int code) {
        return Keys.label(KeyEvent.keyCodeToString(code), code);
    }

    private String targetName(String target) {
        return getString(Keys.YOUTUBE.equals(target) ? R.string.youtube_label : R.string.smarttube_label);
    }

    /** A rounded row that turns white with black text while focused, as the launcher's settings rows do. */
    private TextView button(String label, View.OnClickListener click) {
        TextView b = text(label, 18, Color.WHITE);
        b.setFocusable(true);
        b.setClickable(true);
        b.setGravity(Gravity.CENTER_VERTICAL);
        b.setPadding(dp(22), dp(12), dp(22), dp(12));
        StateListDrawable bg = new StateListDrawable();
        bg.addState(new int[]{android.R.attr.state_focused}, rounded(0xFFFFFFFF));
        bg.addState(new int[]{}, rounded(0xFF262626));
        b.setBackground(bg);
        b.setOnFocusChangeListener((v, focused) -> b.setTextColor(focused ? Color.BLACK : Color.WHITE));
        b.setOnClickListener(click);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, dp(6), dp(14), dp(6));
        b.setLayoutParams(lp);
        return b;
    }

    private GradientDrawable rounded(int colour) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(colour);
        d.setCornerRadius(dp(24));
        return d;
    }

    private TextView text(String s, int sp, int colour) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(colour);
        return t;
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
