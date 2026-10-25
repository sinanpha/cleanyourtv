package com.gathin.tvhome;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Remote-friendly settings owned by the launcher; system settings stay one row away. */
public final class LauncherSettingsActivity extends Activity {
    static final String EXTRA_REORDER = "com.gathin.tvhome.extra.REORDER_FROM_SETTINGS";
    private SharedPreferences choices;
    private final TextView[] columnChoices = new TextView[4];
    private final TextView[] rowChoices = new TextView[4];
    private static final int[] DOCK_OPACITIES = {100, 70, 50, 30};
    private final TextView[] dockChoices = new TextView[DOCK_OPACITIES.length];
    private static final int[] ROTATION_MINUTES = {5, 15, 30, 60};
    private final TextView[] rotationChoices = new TextView[ROTATION_MINUTES.length];
    private static final int[] WALLPAPER_DIMS = {0, 20, 40, 60};
    private final TextView[] dimChoices = new TextView[WALLPAPER_DIMS.length];
    private TextView wallpaperChoice;
    /** The video rows listed here in screen order, and those switched on. */
    private final List<String> videoRowNames = new ArrayList<>();
    private final Set<String> videoRowsOn = new HashSet<>();
    private LinearLayout videoRowsBox;
    private boolean videoFirstRowDefault;
    private TextView animationsChoice;
    private TextView advancedChoice;
    private TextView followThemeChoice;
    private int columns;
    private int rowsBelowDock;
    private int dockOpacity;
    private int rotationMinutes;
    private int wallpaperDim;
    private boolean animations;
    private boolean advancedEffects;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_launcher_settings);
        choices = getSharedPreferences(Config.SETTINGS_NAME, MODE_PRIVATE);
        Config config = Config.load(this);
        View reorderChoice = findViewById(R.id.reorderChoice);
        reorderChoice.setVisibility(config.launcherSettingsButton ? View.VISIBLE : View.GONE);
        reorderChoice.setOnClickListener(v -> {
            setResult(RESULT_OK, new Intent().putExtra(EXTRA_REORDER, true));
            finish();
        });
        columns = config.columns;
        rowsBelowDock = config.rowsBelowDock;
        dockOpacity = config.dockOpacity;
        rotationMinutes = config.wallpaperRotationMinutes;
        wallpaperDim = config.wallpaperDim;
        animations = config.animations;
        advancedEffects = config.animations && config.advancedEffects;

        int[] ids = {R.id.columns4, R.id.columns5, R.id.columns6, R.id.columns7};
        for (int i = 0; i < ids.length; i++) {
            final int value = i + 4;
            columnChoices[i] = findViewById(ids[i]);
            columnChoices[i].setOnClickListener(v -> {
                if (columns == value) return;
                columns = value;
                choices.edit().putInt("columns", value).apply();
                changed();
                refresh();
            });
        }
        int[] rowIds = {R.id.rows0, R.id.rows1, R.id.rows2, R.id.rows3};
        for (int i = 0; i < rowIds.length; i++) {
            final int value = i;
            rowChoices[i] = findViewById(rowIds[i]);
            rowChoices[i].setOnClickListener(v -> {
                if (rowsBelowDock == value) return;
                rowsBelowDock = value;
                choices.edit().putInt(Config.ROWS_BELOW_DOCK_KEY, value).apply();
                changed();
                refresh();
            });
        }
        int[] dockIds = {R.id.dock100, R.id.dock70, R.id.dock50, R.id.dock30};
        for (int i = 0; i < dockIds.length; i++) {
            final int value = DOCK_OPACITIES[i];
            dockChoices[i] = findViewById(dockIds[i]);
            dockChoices[i].setOnClickListener(v -> {
                if (dockOpacity == value) return;
                dockOpacity = value;
                choices.edit().putInt(Config.DOCK_OPACITY_KEY, value).apply();
                changed();
                refresh();
            });
        }
        int[] rotationIds = {R.id.rotation5, R.id.rotation15, R.id.rotation30, R.id.rotation60};
        for (int i = 0; i < rotationIds.length; i++) {
            final int value = ROTATION_MINUTES[i];
            rotationChoices[i] = findViewById(rotationIds[i]);
            rotationChoices[i].setOnClickListener(v -> {
                // An interval means "rotate": it also drops a single picture (or None) picked in the Wallpaper row.
                if (rotationMinutes == value && !choices.contains(Config.WALLPAPER_CHOICE_KEY)) return;
                rotationMinutes = value;
                choices.edit().putInt(Config.WALLPAPER_ROTATION_KEY, value)
                        .remove(Config.WALLPAPER_CHOICE_KEY).apply();
                changed();
                refresh();
            });
        }
        int[] dimIds = {R.id.dim0, R.id.dim20, R.id.dim40, R.id.dim60};
        for (int i = 0; i < dimIds.length; i++) {
            final int value = WALLPAPER_DIMS[i];
            dimChoices[i] = findViewById(dimIds[i]);
            dimChoices[i].setOnClickListener(v -> {
                if (wallpaperDim == value) return;
                wallpaperDim = value;
                choices.edit().putInt(Config.WALLPAPER_DIM_KEY, value).apply();
                changed();
                refresh();
            });
        }
        // Keep the numeric choices increasing in the Hebrew reading direction. Reorder the
        // actual views so D-pad focus follows their positions without changing the saved values.
        if (getResources().getBoolean(R.bool.rtl_app_grid)) {
            reverseRow(columnChoices);
            reverseRow(rowChoices);
            reverseRow(dockChoices);
            reverseRow(rotationChoices);
            reverseRow(dimChoices);
        }
        wallpaperChoice = findViewById(R.id.wallpaperChoice);
        wallpaperChoice.setOnClickListener(v -> showWallpaperPicker());
        animationsChoice = findViewById(R.id.animationsChoice);
        animationsChoice.setOnClickListener(v -> {
            animations = !animations;
            if (!animations) advancedEffects = false;
            choices.edit().putBoolean("animations", animations)
                    .putBoolean("advanced_effects", advancedEffects).apply();
            changed();
            refresh();
        });
        advancedChoice = findViewById(R.id.advancedChoice);
        advancedChoice.setOnClickListener(v -> {
            if (advancedEffects) {
                setAdvanced(false);
            } else {
                new AlertDialog.Builder(this)
                        .setTitle(R.string.advanced_effects)
                        .setMessage(R.string.advanced_effects_warning)
                        .setPositiveButton(R.string.enable, (dialog, which) -> setAdvanced(true))
                        .setNegativeButton(android.R.string.cancel, null)
                        .show();
            }
        });
        followThemeChoice = findViewById(R.id.followThemeChoice);
        followThemeChoice.setOnClickListener(v -> {
            if (!hasOnTvChoices()) return;
            choices.edit().remove("columns").remove(Config.ROWS_BELOW_DOCK_KEY).remove("animations")
                    .remove("advanced_effects").remove(Config.WALLPAPER_CHOICE_KEY)
                    .remove(Config.DOCK_OPACITY_KEY).remove(Config.WALLPAPER_ROTATION_KEY)
                    .remove(Config.WALLPAPER_DIM_KEY).remove(Config.HOME_VIDEOS_ROWS_KEY).commit();
            loadVideoRows();
            Config current = Config.load(this);
            columns = current.columns;
            rowsBelowDock = current.rowsBelowDock;
            dockOpacity = current.dockOpacity;
            rotationMinutes = current.wallpaperRotationMinutes;
            wallpaperDim = current.wallpaperDim;
            animations = current.animations;
            advancedEffects = current.animations && current.advancedEffects;
            changed();
            refresh();
        });
        findViewById(R.id.defaultHomeChoice).setOnClickListener(v -> {
            Toast.makeText(this, R.string.home_settings_help, Toast.LENGTH_LONG).show();
            open(new Intent(Settings.ACTION_HOME_SETTINGS));
        });
        findViewById(R.id.tvSettingsChoice).setOnClickListener(v ->
                open(SystemSettings.intent(this, Settings.ACTION_SETTINGS)));
        videoRowsBox = findViewById(R.id.homeVideosRows);
        String videoAuthority = getString(R.string.home_videos_authority).trim();
        if (videoAuthority.isEmpty()) {
            for (int id : new int[]{R.id.homeVideosLabel, R.id.homeVideosRows, R.id.homeVideosHint}) {
                findViewById(id).setVisibility(View.GONE);
            }
        } else {
            loadVideoRows();
            showVideoRows(Collections.emptyList());
            readVideoRowNames(videoAuthority);
        }
        refresh();
        if (config.launcherSettingsButton) reorderChoice.requestFocus();
        else columnChoices[Math.max(0, Math.min(columns - 4, 3))].requestFocus();
    }

    /** The rows in force (config.xml, or switched here before) are the ones switched on. */
    private void loadVideoRows() {
        videoRowsOn.clear();
        List<String> rows = Config.homeVideosRows(this);
        if (rows != null) videoRowsOn.addAll(rows);
        // An empty home_videos_rows shows the provider's first row: it reads as switched on once the rows are known.
        videoFirstRowDefault = rows != null && rows.isEmpty();
    }

    /** One choice per row ({@link Config#videoRowChoices}); the focus stays on the same place if it was on one. */
    private void showVideoRows(List<String> offered) {
        if (videoFirstRowDefault && !offered.isEmpty()) {
            videoRowsOn.add(offered.get(0));
            videoFirstRowDefault = false;
        }
        int focused = videoRowsBox.indexOfChild(videoRowsBox.getFocusedChild());
        videoRowNames.clear();
        videoRowNames.addAll(Config.videoRowChoices(offered,
                Arrays.asList(getResources().getStringArray(R.array.home_videos_rows)), videoRowsOn));
        videoRowsBox.removeAllViews();
        for (String name : videoRowNames) {
            TextView cell = (TextView) getLayoutInflater().inflate(R.layout.settings_choice_cell, videoRowsBox, false);
            cell.setText(name);
            cell.setOnClickListener(v -> {
                if (!videoRowsOn.remove(name)) videoRowsOn.add(name);
                choices.edit().putString(Config.HOME_VIDEOS_ROWS_KEY,
                        Config.videoRowsChoice(videoRowNames, videoRowsOn)).apply();
                changed();
                refresh();
            });
            videoRowsBox.addView(cell);
        }
        if (focused >= 0 && videoRowsBox.getChildCount() > 0) {
            videoRowsBox.getChildAt(Math.min(focused, videoRowsBox.getChildCount() - 1)).requestFocus();
        }
        if (videoRowsBox.getChildCount() > 0) refresh();
    }

    /**
     * Asks the provider which rows it has now ({@code content://<authority>/rows}, Video Shelf 1.5 and later) off the
     * main thread, so a row SmartTube adds can be switched on too. Without an answer the configured rows stay listed.
     */
    private void readVideoRowNames(String authority) {
        Uri uri = Uri.parse("content://" + authority + "/rows");
        new Thread(() -> {
            List<String> offered = new ArrayList<>();
            try (Cursor c = getContentResolver().query(uri, null, null, null, null)) {
                int column = c == null ? -1 : c.getColumnIndex("row_name");
                while (column >= 0 && c.moveToNext()) {
                    String name = c.getString(column);
                    if (name != null && !name.trim().isEmpty() && !offered.contains(name.trim())) offered.add(name.trim());
                }
            } catch (RuntimeException e) {
                // The app behind the provider is missing, stopped answering or refused us: keep the configured rows.
            }
            if (!offered.isEmpty()) runOnUiThread(() -> {
                if (!isDestroyed()) showVideoRows(offered);
            });
        }, "video-rows").start();
    }

    private void reverseRow(TextView[] views) {
        LinearLayout row = (LinearLayout) views[0].getParent();
        row.removeAllViews();
        for (int i = views.length - 1; i >= 0; i--) row.addView(views[i]);
    }

    private void showWallpaperPicker() {
        List<File> images = wallpaperImages();
        List<String> names = wallpaperLabels(images);
        // None comes first: a collection can hold a hundred pictures, and None should not sit below all of them.
        String[] labels = new String[images.size() + 1];
        labels[0] = getString(R.string.wallpaper_none);
        for (int i = 0; i < images.size(); i++) labels[i + 1] = names.get(i);
        String current = choices.getString(Config.WALLPAPER_CHOICE_KEY, null);
        int index = indexOf(images, current);
        int checked = "none".equals(current) ? 0 : index < 0 ? -1 : index + 1;
        new AlertDialog.Builder(this)
                .setTitle(R.string.wallpaper_label)
                .setSingleChoiceItems(labels, checked, (dialog, which) -> {
                    dialog.dismiss();
                    if (which == 0) {
                        choices.edit().putString(Config.WALLPAPER_CHOICE_KEY, "none").apply();
                    } else {
                        choices.edit().putString(Config.WALLPAPER_CHOICE_KEY,
                                images.get(which - 1).getName()).apply();
                    }
                    changed();
                    Toast.makeText(this, R.string.wallpaper_applied, Toast.LENGTH_SHORT).show();
                    refresh();
                })
                .setNeutralButton(R.string.wallpaper_theme_default, (dialog, which) -> {
                    choices.edit().remove(Config.WALLPAPER_CHOICE_KEY).apply();
                    changed();
                    refresh();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    /** The pictures the Wallpaper row offers: a theme's single wallpaper.jpg first, then the wallpapers/ collection. */
    private List<File> wallpaperImages() {
        File dir = Config.dir(this);
        List<File> images = new java.util.ArrayList<>(WallpaperRotation.images(
                dir == null ? null : new File(dir, "wallpapers")));
        // A theme with a single wallpaper.jpg offers that picture too (Config maps the choice back to it).
        File single = dir == null ? null : new File(dir, Config.WALLPAPER_NAME);
        if (single != null && single.isFile()) images.add(0, single);
        return images;
    }

    /** Each picture's name for people: its caption, "Screensaver picture N" for a copied one, or the file name. */
    private List<String> wallpaperLabels(List<File> images) {
        File dir = Config.dir(this);
        List<String> files = new java.util.ArrayList<>(images.size());
        for (File image : images) files.add(image.getName());
        return WallpaperCaptions.labels(files, WallpaperCaptions.read(dir == null ? null : new File(dir, "wallpapers")),
                getString(R.string.wallpaper_screensaver_picture));
    }

    /** The picked picture's name as the picker shows it; the file name when it is no longer there. */
    private String wallpaperName(String file) {
        List<File> images = wallpaperImages();
        int index = indexOf(images, file);
        return index < 0 ? file : wallpaperLabels(images).get(index);
    }

    private static int indexOf(List<File> images, String name) {
        if (name == null) return -1;
        for (int i = 0; i < images.size(); i++) {
            if (images.get(i).getName().equals(name)) return i;
        }
        return -1;
    }

    /**
     * A choice was saved. Home rebuilds when it next resumes because the change count moved, whether these
     * settings are left with Back or with the remote's Home button (which cancels the activity result).
     */
    private void changed() {
        setResult(RESULT_OK);
        choices.edit().putLong(Config.SETTINGS_REVISION_KEY,
                choices.getLong(Config.SETTINGS_REVISION_KEY, 0) + 1).apply();
    }

    private void setAdvanced(boolean enabled) {
        advancedEffects = enabled;
        choices.edit().putBoolean("advanced_effects", enabled).apply();
        changed();
        refresh();
    }

    private void refresh() {
        for (int i = 0; i < columnChoices.length; i++) {
            columnChoices[i].setSelected(columns == i + 4);
        }
        for (int i = 0; i < rowChoices.length; i++) {
            rowChoices[i].setSelected(rowsBelowDock == i);
        }
        for (int i = 0; i < dockChoices.length; i++) {
            dockChoices[i].setSelected(dockOpacity == DOCK_OPACITIES[i]);
        }
        // A single picture (or None) picked in the Wallpaper row does not rotate, whatever the interval.
        boolean rotating = !choices.contains(Config.WALLPAPER_CHOICE_KEY);
        for (int i = 0; i < rotationChoices.length; i++) {
            rotationChoices[i].setSelected(rotating && rotationMinutes == ROTATION_MINUTES[i]);
        }
        for (int i = 0; i < dimChoices.length; i++) {
            dimChoices[i].setSelected(wallpaperDim == WALLPAPER_DIMS[i]);
        }
        for (int i = 0; i < videoRowsBox.getChildCount() && i < videoRowNames.size(); i++) {
            videoRowsBox.getChildAt(i).setSelected(videoRowsOn.contains(videoRowNames.get(i)));
        }
        animationsChoice.setText(getString(R.string.animations_state,
                getString(animations ? R.string.on : R.string.off)));
        advancedChoice.setText(getString(R.string.advanced_effects_state,
                getString(advancedEffects ? R.string.on : R.string.off)));
        advancedChoice.setEnabled(animations);
        advancedChoice.setAlpha(animations ? 1f : 0.5f);
        String wallpaperCurrent = choices.getString(Config.WALLPAPER_CHOICE_KEY, null);
        wallpaperChoice.setText(getString(R.string.wallpaper_state,
                "none".equals(wallpaperCurrent) ? getString(R.string.wallpaper_none)
                        : wallpaperCurrent != null ? wallpaperName(wallpaperCurrent)
                        : getString(R.string.wallpaper_theme_default)));
        followThemeChoice.setSelected(!hasOnTvChoices());
    }

    private boolean hasOnTvChoices() {
        return choices.contains("columns") || choices.contains(Config.ROWS_BELOW_DOCK_KEY)
                || choices.contains("animations") || choices.contains("advanced_effects")
                || choices.contains(Config.WALLPAPER_CHOICE_KEY) || choices.contains(Config.DOCK_OPACITY_KEY)
                || choices.contains(Config.WALLPAPER_ROTATION_KEY) || choices.contains(Config.WALLPAPER_DIM_KEY)
                || choices.contains(Config.HOME_VIDEOS_ROWS_KEY);
    }

    private void open(Intent intent) {
        try {
            startActivity(intent);
        } catch (ActivityNotFoundException | SecurityException e) {
            Toast.makeText(this, R.string.not_available, Toast.LENGTH_LONG).show();
        }
    }
}
