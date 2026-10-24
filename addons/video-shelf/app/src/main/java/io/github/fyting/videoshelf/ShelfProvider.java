package io.github.fyting.videoshelf;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
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
 * package), and each picture URI opens a JPEG this app downloaded into its cache. Only the packages in
 * {@code R.array.shelf_readers} (the launcher) may read either; the rows include the owner's watch history.
 */
public class ShelfProvider extends ContentProvider {

    static final String[] COLUMNS = {"_id", "row_name", "title", "image", "intent", "package"};

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        checkReader();
        if ("weather".equals(uri.getLastPathSegment())) return weather();
        if (!"videos".equals(uri.getLastPathSegment())) return null;
        Set<String> sources = new LinkedHashSet<>(Arrays.asList(
                getContext().getResources().getStringArray(R.array.source_packages)));
        int limit = getContext().getResources().getInteger(R.integer.home_row_limit);
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
        return out;
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
