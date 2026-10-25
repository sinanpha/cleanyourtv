package io.github.fyting.videoshelf;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import org.junit.Test;

public class ShelfTest {

    private static final String TUBE = "org.smarttube.stable";
    private static final Set<String> SOURCES = Collections.singleton(TUBE);

    private static Shelf.Item preview(long id, long channel, String pkg, String title, long weight) {
        return new Shelf.Item(id, channel, pkg, title, "https://i.ytimg.com/vi/" + id + "/hq.jpg",
                "intent:#Intent;end", weight, true);
    }

    @Test
    public void keepsOnlySourceChannelsInCreationOrder() {
        List<Shelf.Channel> channels = Arrays.asList(
                new Shelf.Channel(7, TUBE, "History"),
                new Shelf.Channel(3, TUBE, "Recommended"),
                new Shelf.Channel(5, "com.spotify.tv.android", "Made for you"));
        List<Shelf.Item> previews = Arrays.asList(
                preview(1, 7, TUBE, "watched", 0),
                preview(2, 3, TUBE, "suggested", 0),
                preview(3, 5, "com.spotify.tv.android", "song", 0));
        List<Shelf.Row> rows = Shelf.build(channels, previews, Collections.emptyList(), "Continue", SOURCES, 10);
        assertEquals(2, rows.size());
        assertEquals("Recommended", rows.get(0).name);
        assertEquals("History", rows.get(1).name);
    }

    @Test
    public void dropsProgramsAnotherAppPutInTheChannel() {
        List<Shelf.Channel> channels = Collections.singletonList(new Shelf.Channel(3, TUBE, "Recommended"));
        List<Shelf.Item> previews = Arrays.asList(
                preview(1, 3, TUBE, "own", 0),
                preview(2, 3, "com.example.other", "foreign", 0));
        List<Shelf.Row> rows = Shelf.build(channels, previews, Collections.emptyList(), "Continue", SOURCES, 10);
        assertEquals(1, rows.get(0).items.size());
        assertEquals("own", rows.get(0).items.get(0).title);
    }

    @Test
    public void ordersByWeightThenIdAndCapsTheRow() {
        List<Shelf.Channel> channels = Collections.singletonList(new Shelf.Channel(3, TUBE, "Recommended"));
        List<Shelf.Item> previews = Arrays.asList(
                preview(4, 3, TUBE, "d", 0),
                preview(2, 3, TUBE, "b", 5),
                preview(3, 3, TUBE, "c", 0),
                preview(1, 3, TUBE, "a", 9));
        List<Shelf.Row> rows = Shelf.build(channels, previews, Collections.emptyList(), "Continue", SOURCES, 3);
        List<Shelf.Item> items = rows.get(0).items;
        assertEquals(3, items.size());
        assertEquals("a", items.get(0).title);
        assertEquals("b", items.get(1).title);
        assertEquals("c", items.get(2).title);
    }

    @Test
    public void watchNextComesFirstAndSkipsHiddenOrEmptyRows() {
        List<Shelf.Channel> channels = Arrays.asList(
                new Shelf.Channel(3, TUBE, "Recommended"),
                new Shelf.Channel(4, TUBE, "Empty"));
        Shelf.Item hidden = new Shelf.Item(9, 4, TUBE, "hidden", null, null, 0, false);
        List<Shelf.Item> previews = Arrays.asList(preview(1, 3, TUBE, "suggested", 0), hidden);
        List<Shelf.Item> next = Collections.singletonList(
                new Shelf.Item(20, Shelf.WATCH_NEXT, TUBE, "half watched", null, null, 1000, true));
        List<Shelf.Row> rows = Shelf.build(channels, previews, next, "Continue watching", SOURCES, 10);
        assertEquals(2, rows.size());
        assertEquals(Shelf.WATCH_NEXT, rows.get(0).channelId);
        assertEquals("Continue watching", rows.get(0).name);
        assertEquals("Recommended", rows.get(1).name);
    }

    @Test
    public void emptySourcesShowEveryApp() {
        List<Shelf.Channel> channels = Arrays.asList(
                new Shelf.Channel(3, TUBE, "Recommended"),
                new Shelf.Channel(5, "com.spotify.tv.android", ""));
        List<Shelf.Item> previews = Arrays.asList(
                preview(1, 3, TUBE, "suggested", 0),
                preview(2, 5, "com.spotify.tv.android", "song", 0));
        List<Shelf.Row> rows = Shelf.build(channels, previews, Collections.emptyList(), "Continue",
                Collections.emptySet(), 10);
        assertEquals(2, rows.size());
        assertEquals("com.spotify.tv.android", rows.get(1).name);
    }

    @Test
    public void signatureFollowsContent() {
        List<Shelf.Channel> channels = Collections.singletonList(new Shelf.Channel(3, TUBE, "Recommended"));
        List<Shelf.Row> a = Shelf.build(channels, Collections.singletonList(preview(1, 3, TUBE, "x", 0)),
                Collections.emptyList(), "Continue", SOURCES, 10);
        List<Shelf.Row> same = Shelf.build(channels, Collections.singletonList(preview(1, 3, TUBE, "x", 0)),
                Collections.emptyList(), "Continue", SOURCES, 10);
        List<Shelf.Row> other = Shelf.build(channels, Collections.singletonList(preview(2, 3, TUBE, "y", 0)),
                Collections.emptyList(), "Continue", SOURCES, 10);
        assertEquals(Shelf.signature(a), Shelf.signature(same));
        assertNotEquals(Shelf.signature(a), Shelf.signature(other));
        assertTrue(Shelf.signature(Collections.emptyList()).isEmpty());
    }

    @Test
    public void signatureIgnoresProgramIds() {
        // SmartTube writes its rows again every 15 minutes with new program ids for the same videos.
        List<Shelf.Channel> channels = Collections.singletonList(new Shelf.Channel(3, TUBE, "History"));
        Shelf.Item before = new Shelf.Item(417486, 3, TUBE, "x", "https://i.ytimg.com/vi/a/hq.jpg", "intent:a", 29, true);
        Shelf.Item after = new Shelf.Item(417624, 3, TUBE, "x", "https://i.ytimg.com/vi/a/hq.jpg", "intent:a", 29, true);
        assertEquals(
                Shelf.signature(Shelf.build(channels, Collections.singletonList(before), Collections.emptyList(),
                        "Continue", SOURCES, 10)),
                Shelf.signature(Shelf.build(channels, Collections.singletonList(after), Collections.emptyList(),
                        "Continue", SOURCES, 10)));
    }

    @Test
    public void asksAgainOnlyAfterTheGap() {
        assertTrue(SourceRefresh.due(SourceRefresh.NEVER, 5_000, 120_000));
        assertFalse(SourceRefresh.due(10_000, 129_999, 120_000));
        assertTrue(SourceRefresh.due(10_000, 130_000, 120_000));
    }

    private static Shelf.Row row(String name) {
        return new Shelf.Row(name.hashCode(), TUBE, name, Collections.emptyList());
    }

    private static List<String> names(List<Shelf.Row> rows) {
        List<String> out = new java.util.ArrayList<>();
        for (Shelf.Row r : rows) out.add(r.name);
        return out;
    }

    @Test
    public void namedRowsComeFirstInTheGivenOrder() {
        // SmartTube publishes Subscriptions, Recommended, History; the owner wants History first.
        List<Shelf.Row> rows = Arrays.asList(row("Kênh đăng ký"), row("Đề xuất"), row("Lịch sử"), row("Continue"));
        assertEquals(Arrays.asList("Lịch sử", "Kênh đăng ký", "Đề xuất", "Continue"),
                names(Shelf.order(rows, Arrays.asList("Lịch sử", "Kênh đăng ký", "Đề xuất"))));
    }

    @Test
    public void orderIgnoresCaseAndNamesNoRowHas() {
        List<Shelf.Row> rows = Arrays.asList(row("Recommended"), row("History"));
        assertEquals(Arrays.asList("History", "Recommended"),
                names(Shelf.order(rows, Arrays.asList("Lịch sử", "history", "Subscriptions"))));
        assertEquals(Arrays.asList("Recommended", "History"), names(Shelf.order(rows, Collections.emptyList())));
    }
}
