package io.github.fyting.videoshelf;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Hands the shelf's rows to the home screen, so a launcher without network access can show them:
 * {@code content://io.github.fyting.videoshelf.shelf/videos} lists every video (row name, title, picture, intent,
 * package), {@code .../rows} just the row names (for the launcher's settings), and each picture URI opens a JPEG
 * this app downloaded into its cache. Only the packages in {@code R.array.shelf_readers} (the launcher) may read
 * any of them; the rows include the owner's watch history.
 * When the read has just asked the source apps to write their rows again (SourceRefresh), the cursor's extras hold
 * {@link #REREAD_MS}: read again after that many milliseconds for the fresh rows.
 */
public class ShelfProvider extends ContentProvider {

    static final String[] COLUMNS = {"_id", "row_name", "title", "image", "intent", "package"};
    static final String REREAD_MS = "reread_ms";

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        checkReader();
        if ("weather".equals(uri.getLastPathSegment())) return weather();
        if ("rows".equals(uri.getLastPathSegment())) return rowNames();
        if (!"videos".equals(uri.getLastPathSegment())) return null;
        Set<String> sources = sources();
        int limit = getContext().getResources().getInteger(R.integer.home_row_limit);
        long reread = SourceRefresh.ask(getContext());
        List<Shelf.Row> rows = TvListings.load(getContext().getContentResolver(), sources,
                getContext().getString(R.string.continue_watching), limit);
        MatrixCursor out = new MatrixCursor(COLUMNS);
        for (Shelf.Row row : rows) {
            for (Shelf.Item it : row.items) {
                String image = it.poster == null || it.poster.isEmpty() ? null
                        : new Uri.Builder().scheme("content").authority(uri.getAuthority())
                                .appendPath("image").appendQueryParameter("u", it.poster).build().toString();
                out.addRow(new Object[]{it.id, row.name, it.title, image, it.intentUri, row.pkg});
            }
        }
        if (reread > 0) {
            Bundle extras = new Bundle();
            extras.putLong(REREAD_MS, reread);
            out.setExtras(extras);
        }
        return out;
    }

    /**
     * {@code rows}: the names of the rows there are now, in this app's row_order (History, Subscriptions, Recommended
     * first), for the launcher's settings to switch on or off. Asks the source apps for nothing.
     */
    private Cursor rowNames() {
        List<Shelf.Row> rows = Shelf.order(TvListings.load(getContext().getContentResolver(), sources(),
                getContext().getString(R.string.continue_watching), 1),
                Arrays.asList(getContext().getResources().getStringArray(R.array.row_order)));
        MatrixCursor out = new MatrixCursor(new String[]{"row_name"});
        for (Shelf.Row row : rows) out.addRow(new Object[]{row.name});
        return out;
    }

    private Set<String> sources() {
        return new LinkedHashSet<>(Arrays.asList(getContext().getResources().getStringArray(R.array.source_packages)));
    }

    /** {@code weather}: one row, the weather now at options.xml's place (Weather), or none while it is unknown. */
    private Cursor weather() {
        MatrixCursor out = new MatrixCursor(Weather.COLUMNS);
        try {
            double latitude = Double.parseDouble(getContext().getString(R.string.weather_latitude).trim());
            double longitude = Double.parseDouble(getContext().getString(R.string.weather_longitude).trim());
            if (Math.abs(latitude) <= 90 && Math.abs(longitude) <= 180) {
                Object[] row = Weather.now(getContext(), latitude, longitude);
                if (row != null) out.addRow(row);
            }
        } catch (NumberFormatException e) {
            // no place set: no row, and the launcher shows the date alone
        }
        return out;
    }

    /** The picture behind an image URI from {@link #query}, from the cache this app's screen shares (ThumbCache). */
    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        checkReader();
        String url = uri.getQueryParameter("u");
        if (!"r".equals(mode) || url == null || !"image".equals(uri.getLastPathSegment())) {
            throw new FileNotFoundException(uri.toString());
        }
        File file = ThumbCache.get(getContext(), url);
        if (file == null) throw new FileNotFoundException(url);
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY);
    }

    private void checkReader() {
        String caller = getCallingPackage();
        if (caller == null) throw new SecurityException("no calling package");
        if (caller.equals(getContext().getPackageName())) return;
        for (String reader : getContext().getResources().getStringArray(R.array.shelf_readers)) {
            if (reader.equals(caller)) return;
        }
        throw new SecurityException(caller + " may not read the shelf");
    }

    @Override
    public String getType(Uri uri) {
        return "image".equals(uri.getLastPathSegment()) ? "image/jpeg" : null;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        throw new UnsupportedOperationException("read only");
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        throw new UnsupportedOperationException("read only");
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        throw new UnsupportedOperationException("read only");
    }
}
