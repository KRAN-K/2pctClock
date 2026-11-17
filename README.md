# 2%Clock

A feature-packed, customizable Android clock widget.

## Features
- Live ticking time, date and world clocks (system `TextClock` views, so no wake-ups or services)
- **Date formats**: M/D/YYYY, MM/DD/YYYY, M/D/YY, D/M/YYYY, YYYY-MM-DD, Mon D, YYYY, Month D, with optional weekday
- 12h / 24h / system time, optional seconds and AM/PM
- Next-alarm line, up to two world clocks
- Themes: Material You (auto light/dark), light, dark, or custom colors with hex entry
- Background opacity, corner style, 5 fonts, alignment, text size
- Responsive: text scales to the widget's current size
- Tap time/alarm to open your clock app, tap the date to open your calendar, tap anywhere else for settings
- Long-press the widget (Android 12+) to reconfigure
- Opening the app from the launcher edits defaults and applies them to all widgets

## Build
```
./gradlew assembleRelease
```
APK lands in `app/build/outputs/apk/release/`. GitHub Actions builds it on every push and uploads it as an artifact.

### Stable signing key (so updates install over each other)
Add these repo secrets and CI signs releases with your key; without them it falls back to the debug key:
`KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`

## Roadmap
Weather, next calendar event, battery, more clock faces (flip, analog, word clock), per-zone day/night markers.
