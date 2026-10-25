package io.github.fyting.videoshelf;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.Button;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * One screen: a row per home screen channel of the source apps (SmartTube's Recommended, History and the like),
 * read when the screen resumes, the rows named in row_order first and cards_per_screen cards across. OK on a video
 * hands its intent back to the app that published it.
 */
public class ShelfActivity extends Activity {

    private static final String TAG = "VideoShelf";
    private static final String READ_TV_LISTINGS = "android.permission.READ_TV_LISTINGS";
    private static final int REQUEST_ACCESS = 1;

    private static final int GAP_DP = 14;
    private static final int SIDE_DP = 48;
    /** Cards added per frame after the first screenful. */
    private static final int CARDS_PER_FRAME = 3;

    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService io = Executors.newSingleThreadExecutor(task -> new Thread(() -> {
        android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND);
        task.run();
    }, "listings"));
    /** Coalesces the thumbnail check while a row scrolls: once the scroll settles, not on every frame of it. */
    private final Runnable loadVisibleTask = this::loadVisible;
    /** The second read after asking the source apps for fresh rows (SourceRefresh). */
    private final Runnable rereadTask = () -> refresh(false);
    private final List<HorizontalScrollView> rowScrolls = new ArrayList<>();

    private Thumbnails thumbs;
    /** A card's picture width: cards_per_screen of them fill the width between the side margins. */
    private int cardWidth;
    /** Cards per row built with the screen: a screenful and the one peeking in at its edge. */
    private int firstCards;
    private List<String> rowOrder;
    private ScrollView scroll;
    private LinearLayout rowsBox;
    private TextView status;
    private Button grant;
    private Set<String> sources;
    private String shown = "";
    private int generation;
    private boolean resumed;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        sources = new LinkedHashSet<>(Arrays.asList(getResources().getStringArray(R.array.source_packages)));
        // Each slot is a card's picture, its 6 dp padding on either side and the gap to the next card.
        int perScreen = Math.max(1, getResources().getInteger(R.integer.cards_per_screen));
        cardWidth = (getResources().getDisplayMetrics().widthPixels - dp(2 * SIDE_DP)) / perScreen - dp(GAP_DP + 12);
        firstCards = perScreen + 1;
        rowOrder = Arrays.asList(getResources().getStringArray(R.array.row_order));
        thumbs = new Thumbnails(this, cardWidth);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFF000000);
        root.setPadding(0, dp(14), 0, 0);

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.BOTTOM);
        header.setPadding(dp(SIDE_DP), 0, dp(SIDE_DP), dp(2));
        TextView title = text(getString(R.string.app_name), 22, Color.WHITE);
        title.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        header.addView(title);
        TextView hint = text(getString(R.string.hint), 12, 0x99FFFFFF);
        hint.setPadding(dp(16), 0, 0, dp(3));
        header.addView(hint);
        root.addView(header);

        status = text(getString(R.string.loading), 18, 0xCCFFFFFF);
        status.setPadding(dp(SIDE_DP), dp(24), dp(SIDE_DP), dp(12));
        status.setMaxWidth(dp(640));
        root.addView(status);

        grant = new Button(this);
        grant.setText(R.string.grant);
        grant.setAllCaps(false);
        grant.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        grant.setBackgroundResource(R.drawable.button_bg);
        grant.setPadding(dp(28), 0, dp(28), 0);
        grant.setOnFocusChangeListener((v, focused) -> grant.setTextColor(focused ? Color.BLACK : Color.WHITE));
        grant.setTextColor(Color.WHITE);
        grant.setOnClickListener(v -> requestPermissions(new String[]{READ_TV_LISTINGS}, REQUEST_ACCESS));
        LinearLayout.LayoutParams gp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(48));
        gp.leftMargin = dp(SIDE_DP);
        grant.setLayoutParams(gp);
        grant.setVisibility(View.GONE);
        root.addView(grant);

        scroll = new ScrollView(this);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setClipToPadding(false);
        scroll.setPadding(0, 0, 0, dp(48));
        scroll.setOnScrollChangeListener((v, x, y, ox, oy) -> scheduleLoad());
        rowsBox = new LinearLayout(this);
        rowsBox.setOrientation(LinearLayout.VERTICAL);
        rowsBox.setClipChildren(false);
        scroll.addView(rowsBox);
        root.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        setContentView(root);
    }

    @Override
    protected void onResume() {
        super.onResume();
        resumed = true;
        refresh(true);
    }

    @Override
    protected void onPause() {
        super.onPause();
        resumed = false;
        main.removeCallbacks(rereadTask);   // onResume reads again anyway
    }

    @Override
    protected void onDestroy() {
        main.removeCallbacks(loadVisibleTask);
        main.removeCallbacks(rereadTask);
        io.shutdownNow();
        thumbs.shutdown();
        super.onDestroy();
    }

    /** Hidden behind another app and Android wants memory back: drop the pictures; onResume builds the rows again. */
    @Override
    public void onTrimMemory(int level) {
        super.onTrimMemory(level);
        if (level >= TRIM_MEMORY_BACKGROUND) {
            rowsBox.removeAllViews();
            rowScrolls.clear();
            shown = "";
            thumbs.clear();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode == REQUEST_ACCESS) refresh(false);
    }

    /** Reads the rows; with askSources, also asks the source apps for fresh ones and reads again once they wrote them. */
    private void refresh(boolean askSources) {
        final int gen = ++generation;
        final String watchNextName = getString(R.string.continue_watching);
        final int limit = getResources().getInteger(R.integer.row_limit);
        io.execute(() -> {
            long reread = askSources ? SourceRefresh.ask(this) : 0;
            List<Shelf.Row> rows;
            try {
                rows = Shelf.order(TvListings.load(getContentResolver(), sources, watchNextName, limit), rowOrder);
            } catch (SecurityException | IllegalArgumentException e) {
                Log.w(TAG, "TV provider refused the channel table: " + e);
                rows = Collections.emptyList();
            }
            int videos = 0;
            for (Shelf.Row r : rows) videos += r.items.size();
            Log.i(TAG, rows.size() + " rows, " + videos + " videos");
            final List<Shelf.Row> result = rows;
            main.post(() -> {
                if (gen != generation || isDestroyed()) return;
                show(result);
                if (reread > 0 && resumed) main.postDelayed(rereadTask, reread);
            });
        });
    }

    private void show(List<Shelf.Row> rows) {
        boolean allowed = checkSelfPermission(READ_TV_LISTINGS) == PackageManager.PERMISSION_GRANTED;
        if (rows.isEmpty()) {
            rowsBox.removeAllViews();
            rowScrolls.clear();
            shown = "";
            status.setVisibility(View.VISIBLE);
            status.setText(allowed ? R.string.nothing : R.string.need_access);
            grant.setVisibility(allowed ? View.GONE : View.VISIBLE);
            if (!allowed) grant.requestFocus();
            return;
        }
        status.setVisibility(View.GONE);
        grant.setVisibility(View.GONE);
        String signature = Shelf.signature(rows);
        if (signature.equals(shown)) return;   // nothing new: keep the focus and the scroll where they are
        shown = signature;

        // New rows while the owner is in them (the second read after SourceRefresh): the focus stays at the same row
        // and card, not back on the first card.
        int focusRow = -1, focusCard = 0;
        View focused = getCurrentFocus();
        for (int i = 0; focused != null && i < rowScrolls.size(); i++) {
            ViewGroup line = (ViewGroup) rowScrolls.get(i).getChildAt(0);
            if (focused.getParent() == line) {
                focusRow = i;
                focusCard = line.indexOfChild(focused);
            }
        }
        rowsBox.removeAllViews();
        rowScrolls.clear();
        View first = null;
        for (Shelf.Row row : rows) {
            // Three rows and the header fit a 1080p screen without scrolling: about 300 px a row at density 2.
            TextView name = text(row.name, 16, Color.WHITE);
            name.setPadding(dp(SIDE_DP), dp(8), dp(SIDE_DP), 0);
            rowsBox.addView(name);

            HorizontalScrollView hsv = new HorizontalScrollView(this);
            hsv.setHorizontalScrollBarEnabled(false);
            hsv.setClipChildren(false);
            hsv.setClipToPadding(false);
            hsv.setOnScrollChangeListener((v, x, y, ox, oy) -> scheduleLoad());
            LinearLayout line = new LinearLayout(this);
            line.setOrientation(LinearLayout.HORIZONTAL);
            line.setClipChildren(false);
            line.setPadding(dp(SIDE_DP - GAP_DP / 2), dp(7), dp(SIDE_DP), dp(7));
            for (int i = 0; i < Math.min(firstCards, row.items.size()); i++) {
                View card = card(row.items.get(i), row);
                line.addView(card);
                if (first == null) first = card;
            }
            hsv.addView(line);
            rowsBox.addView(hsv);
            rowScrolls.add(hsv);
        }
        if (focusRow >= 0 && !rowScrolls.isEmpty()) {
            ViewGroup line = (ViewGroup) rowScrolls.get(Math.min(focusRow, rowScrolls.size() - 1)).getChildAt(0);
            if (line.getChildCount() > 0) first = line.getChildAt(Math.min(focusCard, line.getChildCount() - 1));
        }
        if (first != null) first.requestFocus();
        scroll.post(this::loadVisible);
        addRestLater(rows, signature);
    }

    /**
     * The cards beyond the first screen, a few per frame: building all sixty-odd at once held the UI thread for
     * about 300 ms on a MiTV-MOOR2, a visible stall right after the screen opened.
     */
    private void addRestLater(List<Shelf.Row> rows, String signature) {
        // postOnAnimation, not post: one batch per frame, so the frames in between still draw on time.
        scroll.postOnAnimation(new Runnable() {
            int row;
            int next = firstCards;

            @Override
            public void run() {
                if (!signature.equals(shown) || isDestroyed()) return;   // rebuilt meanwhile
                for (int added = 0; added < CARDS_PER_FRAME && row < rows.size(); ) {
                    Shelf.Row r = rows.get(row);
                    if (next >= r.items.size()) {
                        row++;
                        next = firstCards;
                        continue;
                    }
                    ((ViewGroup) rowScrolls.get(row).getChildAt(0)).addView(card(r.items.get(next++), r));
                    added++;
                }
                if (row < rows.size()) scroll.postOnAnimation(this);
            }
        });
    }

    private View card(Shelf.Item item, Shelf.Row row) {
        int width = cardWidth;
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setFocusable(true);
        card.setClickable(true);
        card.setBackgroundResource(R.drawable.card_bg);
        card.setPadding(dp(6), dp(5), dp(6), dp(6));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(width + dp(12), ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.leftMargin = dp(GAP_DP / 2);
        lp.rightMargin = dp(GAP_DP / 2);
        card.setLayoutParams(lp);
        card.setTag(item.poster);

        ImageView poster = new ImageView(this);
        poster.setScaleType(ImageView.ScaleType.CENTER_CROP);
        poster.setBackgroundResource(R.drawable.poster_bg);
        poster.setOutlineProvider(ViewOutlineProvider.BACKGROUND);
        poster.setClipToOutline(true);
        card.addView(poster, new LinearLayout.LayoutParams(width, width * 9 / 16));

        TextView title = text(item.title == null ? "" : item.title, 12, 0xB3FFFFFF);
        title.setMaxLines(2);
        title.setEllipsize(TextUtils.TruncateAt.END);
        title.setPadding(dp(2), dp(4), dp(2), 0);
        card.addView(title, new LinearLayout.LayoutParams(width, ViewGroup.LayoutParams.WRAP_CONTENT));

        card.setOnFocusChangeListener((v, focused) -> {
            v.animate().scaleX(focused ? 1.06f : 1f).scaleY(focused ? 1.06f : 1f).setDuration(120).start();
            title.setTextColor(focused ? Color.WHITE : 0xB3FFFFFF);
        });
        card.setOnClickListener(v -> open(item, row));
        // Left and Right move one card and stop at the row's ends, decided here rather than by the focus finder,
        // which at a row's end could jump to whatever lies beyond it on the screen.
        card.setOnKeyListener((v, keyCode, event) -> {
            if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
            if (keyCode != KeyEvent.KEYCODE_DPAD_LEFT && keyCode != KeyEvent.KEYCODE_DPAD_RIGHT) return false;
            ViewGroup line = (ViewGroup) v.getParent();
            boolean rtl = line.getLayoutDirection() == View.LAYOUT_DIRECTION_RTL;
            int next = line.indexOfChild(v) + ((keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) != rtl ? 1 : -1);
            if (next >= 0 && next < line.getChildCount()) line.getChildAt(next).requestFocus();
            return true;
        });
        return card;
    }

    private void scheduleLoad() {
        main.removeCallbacks(loadVisibleTask);
        main.postDelayed(loadVisibleTask, 120);
    }

    /** Asks for the thumbnails of the rows on screen and of about a screen beyond, nothing further away. */
    private void loadVisible() {
        int top = scroll.getScrollY() - scroll.getHeight() / 2;
        int bottom = scroll.getScrollY() + scroll.getHeight() * 3 / 2;
        for (HorizontalScrollView hsv : rowScrolls) {
            if (hsv.getBottom() < top || hsv.getTop() > bottom) continue;
            ViewGroup line = (ViewGroup) hsv.getChildAt(0);
            int left = hsv.getScrollX() - hsv.getWidth() / 2;
            int right = hsv.getScrollX() + hsv.getWidth() * 3 / 2;
            for (int i = 0; i < line.getChildCount(); i++) {
                View card = line.getChildAt(i);
                if (card.getRight() < left || card.getLeft() > right) continue;
                Object uri = card.getTag();
                ImageView poster = (ImageView) ((ViewGroup) card).getChildAt(0);
                if (uri instanceof String && !uri.equals(poster.getTag())) thumbs.load(poster, (String) uri);
            }
        }
    }

    /** Opens the program's own intent, kept inside the app that published the row; that app's start screen otherwise. */
    private void open(Shelf.Item item, Shelf.Row row) {
        Intent intent = null;
        if (!TextUtils.isEmpty(item.intentUri)) {
            try {
                intent = Intent.parseUri(item.intentUri, Intent.URI_INTENT_SCHEME);
                intent.setSelector(null);
                ComponentName target = intent.getComponent();
                if (target != null && !target.getPackageName().equals(row.pkg)) intent = null;
                else if (target == null) intent.setPackage(row.pkg);
            } catch (URISyntaxException e) {
                intent = null;
            }
        }
        if (intent != null && start(intent)) return;
        PackageManager pm = getPackageManager();
        Intent app = pm.getLeanbackLaunchIntentForPackage(row.pkg);
        if (app == null) app = pm.getLaunchIntentForPackage(row.pkg);
        if (app == null || !start(app)) Toast.makeText(this, R.string.cannot_open, Toast.LENGTH_SHORT).show();
    }

    private boolean start(Intent intent) {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            startActivity(intent);
            return true;
        } catch (ActivityNotFoundException | SecurityException e) {
            Log.w(TAG, "could not start " + intent + ": " + e);
            return false;
        }
    }

    private TextView text(String s, int sp, int color) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        t.setIncludeFontPadding(false);
        return t;
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
