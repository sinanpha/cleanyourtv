# Video Shelf

Optional standalone Android TV module card: SmartTube's home screen channels (Subscriptions, Recommended,
History and the like) as rows of video thumbnails, the way a launcher with channel rows shows them. OK on a
video opens it in SmartTube. Build with JDK 17 and Android SDK 34:
`./gradlew testReleaseUnitTest lintRelease assembleRelease`. The small gradlew scripts reuse the repository's
pinned Gradle wrapper with this folder as the project. No binaries or prebuilt APKs are committed.

How it reads the rows is in [`docs/watch-next.md`](../../docs/watch-next.md): SmartTube writes preview
channels to the TV provider, and an app holding `READ_TV_LISTINGS` can read every row marked searchable. The
rows are read each time the screen comes to the front; nothing runs while it is closed. Which apps' channels
are shown, how many videos per row, how many cards fit across the screen (`cards_per_screen`, 6) and which rows
come first (`row_order`: History, then Subscriptions, then Recommended) is set in
`app/src/main/res/values/options.xml` (empty `source_packages` shows every app's channels).

The same rows can sit on the home screen, above the dock: `ShelfProvider`
(`content://io.github.fyting.videoshelf.shelf/videos`) lists the videos with picture URIs that open JPEGs this
app downloads into its cache (at most 120 files), up to `home_row_limit` (24) videos a row, while this app's own
screen keeps `row_limit` (12); a launcher build with `home_videos_authority` set to that
authority reads them. The launcher itself needs no network permission that way. `.../rows` lists just the row
names (History, Subscriptions and Recommended first, as `row_order` puts them, without asking SmartTube for
anything), so Launcher settings can offer each row to switch on or off; with every row off Home stops reading the
provider. The card and tile can be hidden with the launcher's `hidden_apps` while the provider keeps serving. The provider answers only the
packages in `shelf_readers` (`com.gathin.tvhome`); any other caller, the shell included, gets a SecurityException,
since the rows include the watch history.

The provider also serves the weather now for the launcher's header: `content://io.github.fyting.videoshelf.shelf/weather`
is one row (`temperature` in °C, the WMO `code`, `is_day`, `updated`) for the place in `weather_latitude` and
`weather_longitude` (options.xml), from [Open-Meteo](https://open-meteo.com/), which needs no account or key. A
launcher build with `weather_authority` set to the same authority shows it after the date. It is fetched only when
the launcher asks, which it does while Home is on screen, at most once every 30 minutes, and a reading older than
three hours is not served. Empty coordinates serve no weather and send nothing.

SmartTube has to publish its channels: turn them on in SmartTube's settings if the screen says there are none.

SmartTube writes those channels again only every 15 minutes, the shortest period Android's WorkManager allows, so
a video watched a minute ago could take up to 15 minutes to reach History. When this screen opens or the launcher
reads the rows, Video Shelf asks SmartTube to write them now, through the receiver SmartTube keeps for the boot
broadcasts (`source_refresh` in options.xml, sent `QUICKBOOT_POWERON`). That takes about 6 s, and the reader reads
again 12 s later, keeping the focus on the same card. At most once every `source_refresh_gap_seconds` (120), since
each ask starts SmartTube if it is not running and rewrites about a hundred rows. Empty `source_refresh` never
asks.

## Permissions

- `android.permission.READ_TV_LISTINGS` (runtime) and `com.android.providers.tv.permission.READ_EPG_DATA`: read
  the home screen channels and Continue watching rows from the TV provider. The screen asks with the system
  dialog, or grant it from a shell: `pm grant io.github.fyting.videoshelf android.permission.READ_TV_LISTINGS`.
- `android.permission.INTERNET`: the thumbnails in those rows are `https://` addresses (i.ytimg.com for
  SmartTube), and the weather for the launcher's header comes from api.open-meteo.com, which is sent the place's
  latitude and longitude at two decimals. Nothing else is fetched or sent.

No telemetry, ads or background service. Thumbnails are downloaded once into the app's cache folder, which the
screen and the launcher's rows share (at most 120 files, the ones used last), so a picture Home already showed
appears at once here. On screen they are scaled to the card width in RGB_565 and held in a 6 MB memory cache only
while the screen is open, dropped when Android asks for memory back while the screen is hidden.

## Install

```bash
TV_HOST=<tv ip> tools/venv/bin/python -I tools/tv.py --push addons/video-shelf/app/build/outputs/apk/release/app-release.apk /data/local/tmp/addon.apk
TV_HOST=<tv ip> tools/venv/bin/python -I tools/tv.py 'pm install -r /data/local/tmp/addon.apk; rm -f /data/local/tmp/addon.apk; pm grant io.github.fyting.videoshelf android.permission.READ_TV_LISTINGS; cmd package compile -m speed -f io.github.fyting.videoshelf'
```

The last command compiles the app ahead of time, as `launcher/build-and-install.sh` does for the launcher. A
sideloaded APK otherwise starts interpreted, and on a MiTV-MOOR2 that made the rows stutter for its first runs.

On Windows Git Bash, set `MSYS_NO_PATHCONV=1` first so `/data/local/tmp` reaches the TV unchanged.
Press Home and answer Show card on the launcher's prompt.

## Validation

Tested on a **Xiaomi MiTV-MOOR2 (Google TV 11, RTM5.220609.003.1174)** with SmartTube 32.68: three rows
(Subscriptions, Recommended, History), 12 videos each, cold start 0.2-0.4 s, about 50 MB PSS while open; D-pad
across and between rows, OK opened the video in SmartTube's player, and Left and Right stop at a row's ends.
Moving across the rows: under 1 % janky frames (`dumpsys gfxinfo`, compiled with `-m speed`); the first second
after opening still drops some frames while the remaining cards and thumbnails arrive. 6 JVM tests, lintRelease
(0 errors) and the release build passed.

The screenshot is from that TV, with the thumbnails and titles blurred: they show the owner's history.
