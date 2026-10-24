# Voice Search

Press, then speak, to search SmartTube or YouTube: one app, one tile (**Voice Search**, "Tìm bằng giọng nói" on a
Vietnamese TV). Its screen has **SmartTube voice** and **YouTube voice**, and under **Remote buttons** you give any
spare remote button one of the two. Once that button is bound, pressing it anywhere, over a video, the home screen
or another app, opens the listening bar at once: speak, and that app's search results open. Build with JDK 17 and
Android SDK 34: `./gradlew testReleaseUnitTest lintRelease assembleRelease`. The small gradlew scripts reuse the
repository's pinned Gradle wrapper with this folder as the project. No binaries or prebuilt APKs are committed.

- **Listening** goes through the TV's speech recognition service (Google's on Google TV), the way SmartTube's own
  search does, so it starts at once. The system voice screen (`RECOGNIZE_SPEECH`) was tried first: on a MiTV-MOOR2
  it showed its bar and then waited for another press before it listened.
- **The search** is the address `https://www.youtube.com/results?search_query=<words>`, sent to the app; SmartTube
  (stable, else beta) and YouTube for Android TV both open it on their results.
- **The bar** sits over whatever was on screen, dimmed, and shows the words as they are heard. A soft ring behind
  the microphone grows with the voice; the microphone itself keeps its size (scaled with the voice, its glyph
  blurred and lost its shape). After a miss (nothing heard, or no network) OK listens again, Back closes, and it
  closes by itself after 8 seconds. Pressing the bound button again while it waits listens again.
- **Remote buttons:** **+ Bind a button**, press the button, choose SmartTube or YouTube. A bound button's row
  changes its target or removes it. Arrows, OK, Back, Home, power and volume cannot be bound. A press on a bound
  button is taken on press and release and acts on release, as the launcher's own remote buttons do.
- **The button service** (`KeyService`, "Voice Search buttons") is an accessibility service that reads key presses
  only: no accessibility events, nothing on screen. The screen says whether it is on and opens the TV's
  accessibility settings to switch it on; the install below switches it on from a shell.
- **One button, one place.** Android hands a key to every service that filters keys, so a button that the
  launcher's `app_keys` (or another key app) already takes still does that too: unbind it there first. On the
  MiTV-MOOR2 here, the YouTube button (`BUTTON 3`, 190) opens SmartTube through the launcher's `app_keys`.
- `language` in `app/src/main/res/values/options.xml` sets the language to listen in (`vi-VN`, `en-US`); empty
  follows the TV.

For a shell or a test, a `query` extra skips the listening:

```bash
TV_HOST=<tv ip> tools/venv/bin/python -I tools/tv.py 'am start -n io.github.fyting.voicesearch/.SmartTubeSearch --es query conan'
```

## Permissions

- `android.permission.RECORD_AUDIO` (runtime): the recognition service listens only for an app that holds it
  (without it the log says "call for recognition service without RECORD_AUDIO permissions" and nothing is heard).
  The first press asks with the system dialog, or grant it from a shell as below. The audio goes to the TV's
  recognition service, as with SmartTube's or YouTube's own voice search; this app keeps nothing and has no
  network permission.
- An accessibility service (`android.permission.BIND_ACCESSIBILITY_SERVICE` guards it, so only the system binds
  it), for the bound remote buttons. It asks for key filtering only.

No telemetry, ads or background work beyond that service, which only answers key presses; the microphone is on only
while the bar shows it listening.

## Install

```bash
TV_HOST=<tv ip> tools/venv/bin/python -I tools/tv.py --push addons/voice-search/app/build/outputs/apk/release/app-release.apk /data/local/tmp/addon.apk
TV_HOST=<tv ip> tools/venv/bin/python -I tools/tv.py 'pm install -r /data/local/tmp/addon.apk; rm -f /data/local/tmp/addon.apk; pm grant io.github.fyting.voicesearch android.permission.RECORD_AUDIO; cmd package compile -m speed -f io.github.fyting.voicesearch'
```

Then switch the button service on, in the TV's accessibility settings or from a shell. The shell line adds it to
the enabled list without dropping the services already there; Android takes a service off that list when its app
is reinstalled, so run it after every install (the launcher's build-and-install.sh does the same for its own):

```bash
TV_HOST=<tv ip> tools/venv/bin/python -I tools/tv.py 'svc=io.github.fyting.voicesearch/.KeyService; s=$(settings get secure enabled_accessibility_services); case "$s" in *voicesearch*) settings put secure enabled_accessibility_services ""; settings put secure accessibility_enabled 0; sleep 1; settings put secure enabled_accessibility_services "$s" ;; null|"") settings put secure enabled_accessibility_services $svc ;; *) settings put secure enabled_accessibility_services "$s:$svc" ;; esac; settings put secure accessibility_enabled 1'
```

On Windows Git Bash, set `MSYS_NO_PATHCONV=1` first so `/data/local/tmp` reaches the TV unchanged.

## Validation

Tested on a **Xiaomi MiTV-MOOR2 (Google TV 11, RTM5.220609.003.1174)** with SmartTube and YouTube for Android TV
7.21: the `query` extra opened "sushi" in SmartTube's results; the same address opened "conan" in YouTube's results,
while a request sent to a YouTube that had to start from cold stopped at YouTube's "who's watching" account screen
first. A listening bar started recording (`dumpsys audio`, VOICE_RECOGNITION by the recognition service) about a
second after it opened, and with nobody speaking the microphone stopped after about 9 seconds and the bar closed.
The button service showed as bound in `dumpsys accessibility` beside the launcher's two. The owner then bound a
button on the real remote and used it: the service opened the SmartTube bar (`START ... SmartTubeSearch`, from this
app), the spoken words went to SmartTube as a search, and a video from the results played. 6 JVM tests (the search
address, which buttons can be bound, their names), lintRelease (0 errors) and the release build (23 KB) passed.
Injected keys were not used to test the buttons: an injected key is no proof for a button that goes through an
accessibility service.

The screenshot is the app's screen on that TV.
