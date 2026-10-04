# Editing Simple Weather

You need exactly one command loop. From `Projects\WeatherApp\`:

```
build.bat          # builds the APK and installs it on your phone (USB)
```

(or type it out: `gradlew.bat assembleDebug && adb install -r app\build\outputs\apk\debug\app-debug.apk`)

## The 4 spots worth editing

| # | What | Where |
|---|------|-------|
| 1 | **Location** — name shown on screen + lat/long of the city | `app\src\main\java\...\MainActivity.kt`, top: `LOCATION_NAME` / `LATITUDE` / `LONGITUDE` |
| 2 | **Wording & emoji** for weather conditions | same file, `weatherCodeToText` / `weatherCodeToEmoji` |
| 3 | **Colors** — background, text, accent | `app\src\main\res\values\colors.xml` (hex codes) |
| 4 | **App name** (label under the icon) | `app\src\main\res\values\strings.xml` |

## Finding coordinates for a city
Google Maps: right-click the spot → the lat/long is the first two numbers
(e.g. `51.5074, -0.1278` → `LATITUDE = 51.5074`, `LONGITUDE = -0.1278`).

## What a full build does
`gradlew assembleDebug` fetches libraries once, compiles Kotlin, packages
the APK at `app\build\outputs\apk\debug\app-debug.apk`. First build is slow
(downloads); later builds are 30–90 seconds.

## Seeing the API yourself
The weather comes from Open-Meteo. Paste this in a browser:
`https://api.open-meteo.com/v1/forecast?latitude=51.5074&longitude=-0.1278&current=temperature_2m,apparent_temperature,wind_speed_10m,weather_code&timezone=auto`
That JSON is exactly what the app reads. Docs: https://open-meteo.com/en/docs

## If the phone isn't found
- `adb devices` should list it as `device` (not `unauthorized`).
- Replug the cable; if it says unauthorized, check the phone screen for the
  "Allow USB debugging?" dialog.
- USB mode must not be "charging only" — switch to File transfer/MTP if weird.