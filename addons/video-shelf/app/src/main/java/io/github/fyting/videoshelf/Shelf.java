package io.github.fyting.videoshelf;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Turns TV provider rows into the shelf's rows. Plain Java, so the rules are tested on the JVM (src/test). */
final class Shelf {

    /** Row id of the Continue watching row, which has no channel behind it. */
    static final long WATCH_NEXT = -1;

    static final class Channel {
        final long id;
        final String pkg;
        final String name;

        Channel(long id, String pkg, String name) {
            this.id = id;
            this.pkg = pkg;
            this.name = name;
        }
    }

    static final class Item {
        final long id;
        final long channelId;
        final String pkg;
        final String title;
        final String poster;
        final String intentUri;
        /** Larger first: a preview program's weight, a Watch Next program's last engagement time. */
        final long rank;
        final boolean browsable;

        Item(long id, long channelId, String pkg, String title, String poster, String intentUri, long rank,
             boolean browsable) {
            this.id = id;
            this.channelId = channelId;
            this.pkg = pkg;
            this.title = title;
            this.poster = poster;
            this.intentUri = intentUri;
            this.rank = rank;
            this.browsable = browsable;
        }
    }

    static final class Row {
        final long channelId;
        final String pkg;
        final String name;
        final List<Item> items;

        Row(long channelId, String pkg, String name, List<Item> items) {
            this.channelId = channelId;
            this.pkg = pkg;
            this.name = name;
            this.items = items;
        }
    }

    private static final Comparator<Item> ORDER = (a, b) -> {
        int byRank = Long.compare(b.rank, a.rank);
        return byRank != 0 ? byRank : Long.compare(a.id, b.id);
    };

    private Shelf() {
    }

    /**
     * Continue watching first (when a source app wrote any), then one row per source channel in the order the
     * channels were created. A program counts only when it belongs to its channel's own app: the provider hands
     * a reader every searchable row, whoever wrote it.
     */
    static List<Row> build(List<Channel> channels, List<Item> previews, List<Item> watchNext, String watchNextName,
                           Set<String> sources, int limit) {
        List<Row> rows = new ArrayList<>();

        List<Item> next = new ArrayList<>();
        for (Item it : watchNext) {
            if (it.browsable && usable(it) && fromSource(it.pkg, sources)) next.add(it);
        }
        if (!next.isEmpty()) rows.add(new Row(WATCH_NEXT, next.get(0).pkg, watchNextName, top(next, limit)));

        Map<Long, List<Item>> byChannel = new LinkedHashMap<>();
        for (Item it : previews) {
            if (!it.browsable || !usable(it)) continue;
            byChannel.computeIfAbsent(it.channelId, k -> new ArrayList<>()).add(it);
        }
        List<Channel> sorted = new ArrayList<>(channels);
        sorted.sort(Comparator.comparingLong(c -> c.id));
        for (Channel c : sorted) {
            if (!fromSource(c.pkg, sources)) continue;
            List<Item> items = byChannel.get(c.id);
            if (items == null) continue;
            List<Item> own = new ArrayList<>();
            for (Item it : items) {
                if (isEmpty(it.pkg) || it.pkg.equals(c.pkg)) own.add(it);
            }
            if (own.isEmpty()) continue;
            rows.add(new Row(c.id, c.pkg, isEmpty(c.name) ? c.pkg : c.name, top(own, limit)));
        }
        return rows;
    }

    /**
     * The rows named in {@code names} first, in that order (by the names the source app gives them, in any case), then
     * the others in the order they came. A name no row has is skipped.
     */
    static List<Row> order(List<Row> rows, List<String> names) {
        List<Row> ordered = new ArrayList<>(rows.size());
        for (String name : names) {
            for (Row r : rows) {
                if (r.name != null && r.name.equalsIgnoreCase(name.trim()) && !ordered.contains(r)) ordered.add(r);
            }
        }
        for (Row r : rows) if (!ordered.contains(r)) ordered.add(r);
        return ordered;
    }

    /** Changes whenever what the shelf would show changes, so an unchanged refresh keeps the focus where it is. */
    static String signature(List<Row> rows) {
        StringBuilder sb = new StringBuilder();
        for (Row r : rows) {
            sb.append(r.channelId).append(':').append(r.name).append('[');
            for (Item it : r.items) sb.append(it.id).append(',').append(it.title).append(',').append(it.poster).append(';');
            sb.append(']');
        }
        return sb.toString();
    }

    private static boolean fromSource(String pkg, Set<String> sources) {
        return sources.isEmpty() || (pkg != null && sources.contains(pkg));
    }

    private static boolean usable(Item it) {
        return !isEmpty(it.title) || !isEmpty(it.poster);
    }

    private static List<Item> top(List<Item> items, int limit) {
        List<Item> copy = new ArrayList<>(items);
        copy.sort(ORDER);
        if (limit > 0 && copy.size() > limit) copy = new ArrayList<>(copy.subList(0, limit));
        return Collections.unmodifiableList(copy);
    }

    private static boolean isEmpty(String s) {
        return s == null || s.isEmpty();
    }
}
