# Switches on a TV

Every key a theme can set (the Keys table in [`themes/README.md`](../themes/README.md)) gets a
screenshot of what it does on a real TV. This table is where they are listed: the repo and
[cleanyour.tv/addons#switches](https://cleanyour.tv/addons#switches) both show them from here.

## Adding a key

A new key needs a screenshot. Put it in `docs/switches/<key>.jpg` (an existing screenshot in the
repo that shows the key at work also counts) and add a row below:

- From a real TV, with the key set, as a JPEG or PNG, 16:9 (1280×720 is plenty), under 300 KB.
- Show where the key changes something you can see. For a key that maps a remote button, show the
  screen the button opens; for one that changes behaviour only, show the setting's effect, such as
  the panel, the menu or the app that comes up.
- Blur anything personal: Wi-Fi names, people, addresses.

`tests/test_switches.py` checks that every key has a row here with an image that exists and fits
the rules. Keys from before this rule are on its wanted list; a screenshot for one of them is welcome.

## Screenshots

| Key | Screenshot |
|---|---|
| `columns` | <img src="../themes/six-columns/screenshot.jpg" width="320" alt="columns on a TV"> |
| `sky_intensity` | <img src="sky-sunset.jpg" width="320" alt="sky_intensity on a TV"> |
| `wallpaper_rotation_minutes` | <img src="../themes/scenes/screenshot.jpg" width="320" alt="wallpaper_rotation_minutes on a TV"> |
| `festival` | <img src="../themes/dussehra/screenshot.jpg" width="320" alt="festival on a TV"> |
| `settings_key_panel` | <img src="quick-settings-panel.jpg" width="320" alt="settings_key_panel on a TV"> |
| `home_idle_clock_minutes` | <img src="clock-screensaver.png" width="320" alt="home_idle_clock_minutes on a TV"> |
| `gamepad_passthrough` | <img src="switches/gamepad_passthrough.jpg" width="320" alt="gamepad_passthrough on a TV"> |
| `launcher_settings_icon` | <img src="switches/launcher_settings_icon.jpg" width="320" alt="launcher_settings_icon on a TV: the sliders button focused beside Settings"> |
| `wrap_rows` | <img src="switches/wrap_rows.jpg" width="320" alt="wrap_rows on a TV: Left on the first dock app lands on the last"> |
| `dock_fits_apps` | <img src="switches/dock_fits_apps.jpg" width="320" alt="dock_fits_apps on a TV: a dock of seven apps whose band ends at the last one, video rows blurred"> |
| `home_videos_authority` | <img src="switches/home_videos_authority.jpg" width="320" alt="home_videos_authority on a TV: video rows above the dock, thumbnails blurred"> |
| `home_videos_rows` | <img src="switches/home_videos_authority.jpg" width="320" alt="home_videos_rows on a TV: History above Recommended, thumbnails blurred"> |
| `home_videos_per_row` | <img src="switches/home_videos_authority.jpg" width="320" alt="home_videos_per_row on a TV: six videos per row, thumbnails blurred"> |
| `home_videos_max` | <img src="switches/home_videos_authority.jpg" width="320" alt="home_videos_max on a TV: rows of videos above the dock that scroll past the screen, thumbnails blurred"> |
| `weather_authority` | <img src="switches/weather_authority.jpg" width="320" alt="weather_authority on a TV: a sun behind a cloud and 31° after the date in the header, video rows blurred"> |
