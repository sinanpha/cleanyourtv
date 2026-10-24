package io.github.fyting.videoshelf;

import android.content.ContentResolver;
import android.database.Cursor;
import android.media.tv.TvContract;
import android.net.Uri;
import android.provider.BaseColumns;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * Reads the TV provider. Without ACCESS_ALL_EPG_DATA (signature|privileged) a query may not carry a selection,
 * and it returns this app's own rows plus every row marked searchable, so everything is read whole and
 * filtered in {@link Shelf} (docs/watch-next.md).
 */
final class TvListings {

    private TvListings() {
    }

    /** Throws SecurityException when the channel table cannot be read at all. */
    static List<Shelf.Row> load(ContentResolver cr, Set<String> sources, String watchNextName, int limit) {
        List<Shelf.Channel> channels = new ArrayList<>();
        try (Cursor c = cr.query(TvContract.Channels.CONTENT_URI, null, null, null, null)) {
            if (c != null) {
                int id = c.getColumnIndex(BaseColumns._ID);
                int pkg = c.getColumnIndex(TvContract.Channels.COLUMN_PACKAGE_NAME);
                int name = c.getColumnIndex(TvContract.Channels.COLUMN_DISPLAY_NAME);
                while (c.moveToNext()) {
                    channels.add(new Shelf.Channel(c.getLong(id), text(c, pkg), text(c, name)));
                }
            }
        }
        List<Shelf.Item> previews = programs(cr, TvContract.PreviewPrograms.CONTENT_URI, true);
        List<Shelf.Item> watchNext = programs(cr, TvContract.WatchNextPrograms.CONTENT_URI, false);
        return Shelf.build(channels, previews, watchNext, watchNextName, sources, limit);
    }

    private static List<Shelf.Item> programs(ContentResolver cr, Uri uri, boolean preview) {
        List<Shelf.Item> items = new ArrayList<>();
        try (Cursor c = cr.query(uri, null, null, null, null)) {
            if (c == null) return items;
            int id = c.getColumnIndex(BaseColumns._ID);
            int channel = preview ? c.getColumnIndex(TvContract.PreviewPrograms.COLUMN_CHANNEL_ID) : -1;
            int pkg = c.getColumnIndex(TvContract.PreviewPrograms.COLUMN_PACKAGE_NAME);
            int title = c.getColumnIndex(TvContract.PreviewPrograms.COLUMN_TITLE);
            int poster = c.getColumnIndex(TvContract.PreviewPrograms.COLUMN_POSTER_ART_URI);
            int thumb = c.getColumnIndex(TvContract.PreviewPrograms.COLUMN_THUMBNAIL_URI);
            int intent = c.getColumnIndex(TvContract.PreviewPrograms.COLUMN_INTENT_URI);
            int browsable = c.getColumnIndex(TvContract.PreviewPrograms.COLUMN_BROWSABLE);
            int rank = c.getColumnIndex(preview ? TvContract.PreviewPrograms.COLUMN_WEIGHT
                    : TvContract.WatchNextPrograms.COLUMN_LAST_ENGAGEMENT_TIME_UTC_MILLIS);
            while (c.moveToNext()) {
                String art = text(c, poster);
                if (art == null || art.isEmpty()) art = text(c, thumb);
                items.add(new Shelf.Item(c.getLong(id), channel >= 0 ? c.getLong(channel) : Shelf.WATCH_NEXT,
                        text(c, pkg), text(c, title), art, text(c, intent),
                        rank >= 0 && !c.isNull(rank) ? c.getLong(rank) : 0,
                        browsable < 0 || c.isNull(browsable) || c.getInt(browsable) != 0));
            }
        } catch (SecurityException | IllegalArgumentException e) {
            // A table this build will not hand out (Watch Next on some firmware): show the rest.
            return Collections.emptyList();
        }
        return items;
    }

    private static String text(Cursor c, int column) {
        return column >= 0 && !c.isNull(column) ? c.getString(column) : null;
    }
}
