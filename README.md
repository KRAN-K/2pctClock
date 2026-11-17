# 2%Clock

A feature-packed, customizable Android clock widget.

## Features
- Live ticking time, date and world clocks (plain styles use the system `TextClock`, so no wake-ups or services)
- **Date formats**: M/D/YYYY, MM/DD/YYYY, M/D/YY, D/M/YYYY, YYYY-MM-DD, Mon D, YYYY, Month D, optional weekday
- 12h / 24h / system time, optional seconds and AM/PM, next alarm, up to two world clocks
- **Fonts**: system families, 13 bundled open-license fonts (Rye, Sancreek, Butcherman, Eater, Metal Mania, Monoton, Creepster, Nosifer, Pacifico, Orbitron, Black Ops One, Permanent Marker, Special Elite), and **import your own** from a `.ttf`/`.otf`/`.zip` or a dafont.com link (e.g. `dafont.com/carta-bianca.font`)
- **Backgrounds**: solid, 8 gradient presets, or an image (photo picker or URL), with dim and opacity
- **Borders**: solid, double, neon glow (static); rainbow, pulse, **fire** (animated)
- **Digit effects** when the minute or second changes: roll, fade, pop, flicker, fire, burn, water
- **Tap action**: alarms (default), timers, calendar, any installed app, a system setting, or nothing
- **Gear icon** on the widget for quick access to settings (also long-press > Reconfigure on Android 12+)
- Settings screen has its own light / dark / AMOLED theme and accent colors
- Themes for the widget: Material You (auto light/dark), light, dark, custom colors
- Responsive: text scales to the widget's size

## How it works
Plain styles are RemoteViews with `TextClock`. Anything that needs custom drawing (custom fonts, backgrounds, borders, effects)
is drawn to a bitmap by `StyledRenderer`, and `ClockEngineService` pushes frames to the widget while the screen is on
(per-minute, per-second, or ~15 fps during an effect or animated border). It stops itself when the screen turns off or no styled widget remains.

## Fonts and licensing
Bundled fonts are SIL OFL (Permanent Marker and Special Elite are Apache 2.0). Fonts from dafont.com are often personal-use only, so they are not bundled; the app imports
them on demand for the user. Check each font's license.

## Build
```
./gradlew assembleRelease
```
APK lands in `app/build/outputs/apk/release/`. GitHub Actions builds it on every push and uploads it as an artifact.

### Stable signing key (so updates install over each other)
Add these repo secrets and CI signs releases with your key; without them it falls back to the debug key:
`KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`

## Roadmap
Weather, next calendar event, battery, analog/flip/word-clock faces, per-zone day/night markers.
