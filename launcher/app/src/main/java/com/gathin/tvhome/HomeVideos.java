package com.gathin.tvhome;

import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Rect;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Optional rows of videos above the dock (home_videos_authority), read from another app's content provider:
 * the Video Shelf module app serves SmartTube's rows and their pictures. Home only reads it; the app behind the provider
 * fetches the pictures, so the launcher keeps its no-network rule. Off while the authority is empty.
 */
final class HomeVideos {

    /** One row on screen: its videos (title, image, intent, package) and its views. */
    private final class Row {
        final String name;
        /** Replaced in place when the same rows come back with other videos (sameShape). */
        List<String[]> items;
        final LinearLayout line = new LinearLayout(activity);
        final HorizontalScrollView scroll = new HorizontalScrollView(activity);
        int lastIndex;

        Row(String name, List<String[]> items) {
            this.name = name;
            this.items = items;
        }
    }

    private static final long REREAD_MS = 60_000;
    /**
     * Cursor extra: the provider just asked its source app for fresh rows; read again after this many ms (Video Shelf
     * asks SmartTube, which otherwise rewrites its rows only every 15 minutes).
     */
    private static final String REREAD_EXTRA = "reread_ms";

    private final MainActivity activity;
    private final Uri uri;
    private final List<String> rowNames;
    private final int perRow;
    /** home_videos_max: the videos a row holds in all; past perRow the row scrolls. */
    private final int maxPerRow;
    private final float density;
    /** A card's picture width for perRow across the screen, and the width in use (fit() shrinks it for more rows). */
    private final int baseCardWidth;
    private int cardWidth;
    /**
     * Three rows or more: smaller row titles and names, tighter gaps. At the two-row sizes a row's text and gaps alone
     * are about 150 px on a 1080p TV, so three of them left almost no room for the pictures between header and dock.
     */
    private boolean compact;
    /** Home's header (greeting, clock, date and the top buttons): the rows fit below it. */
    private final View header;
    private final LinearLayout panel;
    private final Handler main = new Handler(Looper.getMainLooper());
    // Background priority: Home's own UI and render threads come first on a four-core TV.
    private final ExecutorService io = Executors.newSingleThreadExecutor(task -> new Thread(() -> {
        android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND);
        task.run();
    }, "home-videos"));
    private final List<Row> rows = new ArrayList<>();
    private String shownKey = "";
    /** When the rows were last read; Home comes back often, and SmartTube's rows change far less often. */
    private long readAt = -REREAD_MS;
    /** Home is in front (between refresh and pause), so a read the provider asked for is still worth doing. */
    private boolean active;
    private final Runnable reread = this::read;
    private boolean wanted;
    private float wantedBottom;

    HomeVideos(MainActivity activity, FrameLayout root, View below, View header, String authority, String[] rowNames,
               int perRow, int maxPerRow) {
        this.activity = activity;
        this.header = header;
        this.uri = Uri.parse("content://" + authority + "/videos");
        this.rowNames = Arrays.asList(rowNames);
        this.perRow = Math.max(1, perRow);
        this.maxPerRow = Math.max(this.perRow, maxPerRow);
        this.density = activity.getResources().getDisplayMetrics().density;
        // perRow cards fill the width between the 60 dp margins: each slot is a card, its 5 dp padding and 6 dp gaps.
        int slot = (activity.getResources().getDisplayMetrics().widthPixels - dp(120)) / this.perRow;
        this.baseCardWidth = slot - dp(22);
        this.cardWidth = baseCardWidth;

        panel = new LinearLayout(activity);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setClipChildren(false);
        panel.setVisibility(View.GONE);
        root.addView(panel, root.indexOfChild(below) + 1, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        // Rows added or dropped change the panel's height: place it again once laid out (posted, not in the layout pass).
        panel.addOnLayoutChangeListener((v, l, t, r, b, oldL, oldT, oldR, oldB) -> {
            if (b - t != oldB - oldT) panel.post(() -> {
                if (wanted && !rows.isEmpty()) place();
            });
        });
    }

    /** Reads the rows again (Home came to the front, at most once a minute); cards are rebuilt only on a change. */
    void refresh() {
        active = true;
        long now = SystemClock.elapsedRealtime();
        // Each read wakes the provider's app and queries the TV provider: once a minute is plenty, unless the
        // rows are missing (first start, or dropped by release()).
        if (io.isShutdown() || (!rows.isEmpty() && now - readAt < REREAD_MS)) return;
        read();
    }

    /** Home went behind another app: a second read the provider asked for waits for the next refresh. */
    void pause() {
        active = false;
        main.removeCallbacks(reread);
    }

    private void read() {
        if (io.isShutdown()) return;
        readAt = SystemClock.elapsedRealtime();
        io.execute(() -> {
            Map<String, List<String[]>> byName = new LinkedHashMap<>();
            long again = 0;
            try (Cursor c = activity.getContentResolver().query(uri, null, null, null, null)) {
                if (c != null) {
                    Bundle extras = c.getExtras();
                    if (extras != null) again = Math.min(extras.getLong(REREAD_EXTRA, 0), REREAD_MS);
                    int r = c.getColumnIndex("row_name"), t = c.getColumnIndex("title"), im = c.getColumnIndex("image");
                    int in = c.getColumnIndex("intent"), p = c.getColumnIndex("package");
                    while (c.moveToNext()) {
                        List<String[]> items = byName.computeIfAbsent(String.valueOf(c.getString(r)), k -> new ArrayList<>());
                        if (items.size() < maxPerRow) {
                            items.add(new String[]{c.getString(t), c.getString(im), c.getString(in), c.getString(p)});
                        }
                    }
                }
            } catch (RuntimeException e) {
                // The app behind the provider is missing, stopped answering or refused us: no rows, Home as usual.
            }
            Map<String, List<String[]>> picked = new LinkedHashMap<>();
            for (String name : rowNames) {
                for (Map.Entry<String, List<String[]>> e : byName.entrySet()) {
                    if (e.getKey().equalsIgnoreCase(name) && !picked.containsKey(e.getKey())) picked.put(e.getKey(), e.getValue());
                }
            }
            if (rowNames.isEmpty() && !byName.isEmpty()) {
                Map.Entry<String, List<String[]>> first = byName.entrySet().iterator().next();
                picked.put(first.getKey(), first.getValue());
            }
            long delay = again;
            main.post(() -> {
                show(picked);
                if (delay > 0 && active) {
                    main.removeCallbacks(reread);
                    main.postDelayed(reread, delay);
                }
            });
        });
    }

    private void show(Map<String, List<String[]>> picked) {
        if (activity.isDestroyed()) return;
        StringBuilder key = new StringBuilder();
        for (Map.Entry<String, List<String[]>> e : picked.entrySet()) {
            key.append(e.getKey()).append('[');
            for (String[] it : e.getValue()) key.append(it[0]).append(it[1]).append('|');
        }
        if (key.toString().equals(shownKey)) return;
        shownKey = key.toString();
        if (sameShape(picked)) {
            // The same rows with other videos (one just watched moved History along): the cards take the new videos
            // where they stand, so the focus and the scroll stay put and only the pictures that changed are read.
            int r = 0;
            for (List<String[]> items : picked.values()) {
                if (items.isEmpty()) continue;
                Row row = rows.get(r++);
                row.items = items;
                for (int i = 0; i < items.size(); i++) {
                    ViewGroup card = (ViewGroup) row.line.getChildAt(i);
                    ImageView image = (ImageView) card.getChildAt(0);
                    String src = items.get(i)[1];
                    card.setContentDescription(items.get(i)[0]);
                    ((TextView) card.getChildAt(1)).setText(items.get(i)[0]);
                    if (src == null) {
                        image.setTag(null);
                        image.setImageDrawable(null);
                    } else if (!src.equals(image.getTag())) {
                        load(row, i);
                    }
                }
            }
            return;
        }
        // Rows that grew, shrank or were renamed are built again. When the focus was in them it waits on the panel
        // meanwhile and comes back to the same row and column, instead of jumping to the dock or the top bar.
        int focusRow = -1, focusColumn = 0;
        for (int i = 0; i < rows.size(); i++) {
            if (rows.get(i).line.hasFocus()) {
                focusRow = i;
                focusColumn = rows.get(i).lastIndex;
            }
        }
        if (focusRow >= 0) {
            panel.setFocusable(true);
            panel.requestFocus();
        }
        panel.removeAllViews();
        rows.clear();
        int shown = 0;
        for (List<String[]> items : picked.values()) if (!items.isEmpty()) shown++;
        compact = shown >= 3;
        for (Map.Entry<String, List<String[]>> e : picked.entrySet()) {
            if (e.getValue().isEmpty()) continue;
            Row row = new Row(e.getKey(), e.getValue());
            TextView title = new TextView(activity);
            title.setText(e.getKey());
            title.setTextColor(0xCCFFFFFF);
            title.setTextSize(TypedValue.COMPLEX_UNIT_SP, compact ? 14 : 17);
            title.setPadding(dp(68), dp(rows.isEmpty() ? 0 : compact ? 2 : 4), 0, 0);
            panel.addView(title);
            row.scroll.setHorizontalScrollBarEnabled(false);
            row.scroll.setClipChildren(false);
            row.scroll.setClipToPadding(false);
            row.line.setClipChildren(false);
            row.line.setPadding(dp(60), dp(compact ? 4 : 8), dp(60), dp(compact ? 4 : 8));
            row.scroll.addView(row.line);
            panel.addView(row.scroll);
            rows.add(row);
            for (int i = 0; i < row.items.size(); i++) row.line.addView(card(rows.size() - 1, i));
        }
        loadAll();
        if (rows.isEmpty()) {
            if (focusRow >= 0) activity.videosDown();
            panel.setVisibility(View.GONE);
        } else {
            if (focusRow >= 0) focusRow(Math.min(focusRow, rows.size() - 1), focusColumn);
            if (wanted) showAbove(wantedBottom);
        }
        panel.setFocusable(false);
    }

    /** The rows on screen have the names and lengths of these, so their cards can take the new videos in place. */
    private boolean sameShape(Map<String, List<String[]>> picked) {
        int r = 0;
        for (Map.Entry<String, List<String[]>> e : picked.entrySet()) {
            if (e.getValue().isEmpty()) continue;
            if (r >= rows.size()) return false;
            Row row = rows.get(r++);
            if (!row.name.equals(e.getKey()) || row.line.getChildCount() != e.getValue().size()) return false;
        }
        return r > 0 && r == rows.size();
    }

    private View card(int rowIndex, int index) {
        Row row = rows.get(rowIndex);
        String[] it = row.items.get(index);
        LinearLayout card = new LinearLayout(activity);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setFocusable(true);
        card.setPadding(dp(compact ? 4 : 5), dp(compact ? 4 : 5), dp(compact ? 4 : 5), dp(compact ? 4 : 6));
        StateListDrawable bg = new StateListDrawable();
        bg.addState(new int[]{android.R.attr.state_focused}, rounded(0x26FFFFFF, 10, true));
        bg.addState(new int[]{}, rounded(0, 10, false));
        card.setBackground(bg);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(cardWidth + dp(10), ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.leftMargin = lp.rightMargin = dp(6);
        card.setContentDescription(it[0]);

        ImageView image = new ImageView(activity);
        image.setScaleType(ImageView.ScaleType.CENTER_CROP);
        image.setBackground(rounded(0xFF1E1E1E, 7, false));
        image.setOutlineProvider(ViewOutlineProvider.BACKGROUND);
        image.setClipToOutline(true);
        card.addView(image, new LinearLayout.LayoutParams(cardWidth, cardWidth * 9 / 16));
        TextView name = new TextView(activity);
        name.setText(it[0]);
        name.setSingleLine(true);
        name.setEllipsize(TextUtils.TruncateAt.END);
        name.setTextColor(0xB3FFFFFF);
        name.setTextSize(TypedValue.COMPLEX_UNIT_SP, compact ? 11 : 12);
        name.setPadding(dp(2), dp(compact ? 3 : 5), dp(2), 0);
        card.addView(name, new LinearLayout.LayoutParams(cardWidth, ViewGroup.LayoutParams.WRAP_CONTENT));
        card.setLayoutParams(lp);

        card.setOnFocusChangeListener((v, focused) -> {
            v.animate().scaleX(focused ? 1.06f : 1f).scaleY(focused ? 1.06f : 1f).setDuration(120).start();
            name.setTextColor(focused ? 0xFFFFFFFF : 0xB3FFFFFF);
            if (focused) {
                if (index == 0) row.scroll.smoothScrollTo(0, 0);   // the row's start lines up with its title again
                else keepInView(row.scroll, v);
                row.lastIndex = index;
            }
        });
        // Every direction is decided here, never by the focus finder: Up and Down move one row, then on to the
        // top bar and the dock; Left and Right move one card and wrap around at the row's ends. The focus finder took
        // the Wi-Fi button above over the last card beside it, since the button starts a pixel right of the card before.
        card.setOnKeyListener((v, keyCode, event) -> {
            if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
            if (keyCode == KeyEvent.KEYCODE_DPAD_UP) {
                if (rowIndex == 0) activity.videosUp();
                else focusRow(rowIndex - 1, index);
            } else if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                if (rowIndex == rows.size() - 1) activity.videosDown();
                else focusRow(rowIndex + 1, index);
            } else if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                boolean rtl = row.line.getLayoutDirection() == View.LAYOUT_DIRECTION_RTL;
                int count = row.line.getChildCount();
                int next = (index + ((keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) != rtl ? 1 : -1) + count) % count;
                row.line.getChildAt(next).requestFocus();
            } else {
                return false;
            }
            return true;
        });
        card.setOnClickListener(v -> {
            String[] now = row.items.get(index);   // the video this card holds now, after any in-place update
            open(now[2], now[3]);
        });
        return card;
    }

    /**
     * A row longer than the screen scrolls so the focused card keeps the row's 60 dp margin from either edge, as
     * the first screenful has; the scroll view on its own stops with the card, and its zoomed outline, at the edge.
     */
    private void keepInView(HorizontalScrollView scroll, View card) {
        int margin = dp(60), x = scroll.getScrollX(), width = scroll.getWidth();
        if (card.getLeft() - margin < x) scroll.smoothScrollTo(card.getLeft() - margin, 0);
        else if (card.getRight() + margin > x + width) scroll.smoothScrollTo(card.getRight() + margin - width, 0);
    }

    /**
     * Every thumbnail, in the order they come into view: column by column across the rows, so each row's first
     * screenful arrives before the cards past the screen's edge (the provider downloads each one on first use).
     */
    private void loadAll() {
        int longest = 0;
        for (Row row : rows) longest = Math.max(longest, row.line.getChildCount());
        for (int i = 0; i < longest; i++) {
            for (Row row : rows) {
                if (i < row.line.getChildCount()) load(row, i);
            }
        }
    }

    private void load(Row row, int index) {
        ImageView image = (ImageView) ((ViewGroup) row.line.getChildAt(index)).getChildAt(0);
        String src = row.items.get(index)[1];
        if (src == null || io.isShutdown()) return;
        image.setTag(src);
        io.execute(() -> {
            Bitmap bmp = decode(src);
            if (bmp != null) main.post(() -> {
                if (src.equals(image.getTag())) image.setImageBitmap(bmp);
            });
        });
    }

    /**
     * The picture scaled to the full card width in RGB_565, from the provider's file; null when it cannot be read.
     * The full width, not the one fit() picked: off the main thread, and still sharp when fewer rows let cards grow.
     */
    private Bitmap decode(String src) {
        try (InputStream in = activity.getContentResolver().openInputStream(Uri.parse(src))) {
            if (in == null) return null;
            ByteArrayOutputStream out = new ByteArrayOutputStream(64 * 1024);
            byte[] buf = new byte[16 * 1024];
            for (int n; (n = in.read(buf)) != -1; ) {
                out.write(buf, 0, n);
                if (out.size() > 2 * 1024 * 1024) return null;
            }
            byte[] data = out.toByteArray();
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            BitmapFactory.decodeByteArray(data, 0, data.length, o);
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inPreferredConfig = Bitmap.Config.RGB_565;
            // The JPEG decoder skips pixels first (cheap), then the rest is scaled to the card's width.
            int sample = 1;
            while (o.outWidth / (sample * 2) >= baseCardWidth) sample *= 2;
            opts.inSampleSize = sample;
            if (o.outWidth / sample > baseCardWidth) {
                opts.inScaled = true;
                opts.inDensity = o.outWidth / sample;
                opts.inTargetDensity = baseCardWidth;
            }
            return BitmapFactory.decodeByteArray(data, 0, data.length, opts);
        } catch (Exception | OutOfMemoryError e) {
            return null;
        }
    }

    /** Opens the video's own intent, kept inside the app that published it; that app otherwise. */
    private void open(String intentUri, String pkg) {
        if (pkg == null) return;
        Intent intent = null;
        if (!TextUtils.isEmpty(intentUri)) {
            try {
                intent = Intent.parseUri(intentUri, Intent.URI_INTENT_SCHEME);
                intent.setSelector(null);
                ComponentName target = intent.getComponent();
                if (target == null) intent.setPackage(pkg);
                else if (!target.getPackageName().equals(pkg)) intent = null;
            } catch (Exception e) {
                intent = null;
            }
        }
        if (intent == null || !start(intent)) {
            Intent app = activity.getPackageManager().getLeanbackLaunchIntentForPackage(pkg);
            if (app != null) start(app);
        }
    }

    private boolean start(Intent intent) {
        try {
            activity.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            return true;
        } catch (ActivityNotFoundException | SecurityException e) {
            return false;
        }
    }

    /** Shows the rows with their bottom at bottomY (root coordinates), just above the dock. */
    void showAbove(float bottomY) {
        wanted = true;
        wantedBottom = bottomY;
        if (rows.isEmpty()) return;
        panel.setVisibility(View.VISIBLE);
        if (panel.getHeight() == 0) {
            panel.post(() -> {
                if (wanted) place();
            });
        }
        place();
    }

    private void place() {
        if (fit()) return;   // the cards changed size: the layout listener places the panel once it is laid out
        panel.setY(Math.max(0f, wantedBottom - panel.getHeight()));
        panel.animate().alpha(1f).setDuration(200).start();
    }

    /**
     * Sizes the cards so every row fits between the header and the dock: three rows at the full size ran over the
     * clock and the top buttons. Two rows keep home_videos_per_row's size. Returns true when the sizes changed.
     */
    private boolean fit() {
        if (rows.isEmpty() || panel.getHeight() == 0 || header == null || header.getHeight() == 0) return false;
        Rect box = new Rect(0, 0, header.getWidth(), header.getHeight());
        ((ViewGroup) panel.getParent()).offsetDescendantRectToMyCoords(header, box);
        int width = fitCardWidth(baseCardWidth, cardWidth, panel.getHeight(), rows.size(),
                wantedBottom - box.bottom - dp(12));
        if (Math.abs(width - cardWidth) <= 2) return false;
        cardWidth = width;
        for (Row row : rows) {
            for (int i = 0; i < row.line.getChildCount(); i++) {
                ViewGroup card = (ViewGroup) row.line.getChildAt(i);
                card.getLayoutParams().width = width + dp(10);
                card.getChildAt(0).getLayoutParams().width = width;
                card.getChildAt(0).getLayoutParams().height = width * 9 / 16;
                card.getChildAt(1).getLayoutParams().width = width;
                card.requestLayout();
            }
        }
        return true;
    }

    /**
     * The picture width that lets {@code rows} rows fit {@code room} px: only the 16:9 pictures scale, the names,
     * row titles and gaps keep their size (measured as the panel's height less its pictures at {@code current}).
     * Never wider than {@code base}, and not below 60% of it, where the titles under the cards stop being readable.
     */
    static int fitCardWidth(int base, int current, int panelHeight, int rows, float room) {
        float fixed = panelHeight - rows * (current * 9 / 16f);
        float width = (room - fixed) * 16f / (9f * rows);
        return Math.round(Math.max(base * 0.6f, Math.min(base, width)));
    }

    /** Away from the dock (the grid scrolled down): out of the way, unless the owner is in a row. */
    void hide() {
        wanted = false;
        if (panel.hasFocus()) return;
        panel.animate().cancel();
        panel.setAlpha(0f);
        panel.setVisibility(View.GONE);
    }

    boolean shown() {
        return panel.getVisibility() == View.VISIBLE && !rows.isEmpty();
    }

    /** From the dock: the row nearest to it, the last one. */
    void focusFromBelow() {
        if (!rows.isEmpty()) focusRow(rows.size() - 1, rows.get(rows.size() - 1).lastIndex);
    }

    /** From the top bar: the first row. */
    void focusFromAbove() {
        if (!rows.isEmpty()) focusRow(0, rows.get(0).lastIndex);
    }

    /** Focuses a row at the given column (the one the focus came from), or its last card when the row is shorter. */
    private void focusRow(int index, int column) {
        if (index < 0 || index >= rows.size()) return;
        Row row = rows.get(index);
        View card = row.line.getChildAt(Math.min(column, row.line.getChildCount() - 1));
        if (card != null) card.requestFocus();
    }

    /** Home went to the background and Android wants memory: drop the pictures; the next refresh rebuilds them. */
    void release() {
        panel.removeAllViews();
        rows.clear();
        shownKey = "";
    }

    void close() {
        main.removeCallbacks(reread);
        io.shutdownNow();
    }

    private GradientDrawable rounded(int color, int radiusDp, boolean outline) {
        GradientDrawable d = new GradientDrawable();
        d.setCornerRadius(dp(radiusDp));
        d.setColor(color);
        if (outline) d.setStroke(dp(3), 0xFFFFFFFF);
        return d;
    }

    private int dp(int v) {
        return Math.round(v * density);
    }
}
