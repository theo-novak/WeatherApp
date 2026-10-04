# WeatherApp — Simple Weather

A bare-bones, location-aware Android weather app built from scratch: no
Android Studio, no frameworks beyond Jetpack Compose — one Kotlin file you
can read in a sitting, edited and tested on your own phone over USB.

## What it does

Opens, finds your location (network/Wi-Fi fix — city-level, battery-friendly,
with a labeled fallback if you decline), reverse-geocodes it to a place name,
then shows live conditions + a 5-day rain-chance list from Open-Meteo
(free, no API key, no account).

## Repository map

| file | what it is |
|---|---|
| `app/src/main/java/.../MainActivity.kt` | the whole app: location pipeline, Open-Meteo fetch with explicit timeouts, Compose UI. The 4 edit spots are marked inline. |
| `app/src/main/AndroidManifest.xml` | permissions (INTERNET + location) and app identity |
| `app/src/main/res/values/colors.xml` | theme colors — edit hex codes, rebuild |
| `app/src/main/res/values/strings.xml` | app label (name under the icon) |
| `app/src/main/res/values/themes.xml` | dark-slate Material theme |
| `app/src/main/res/drawable/ic_launcher_*.xml` | adaptive launcher icon as vector XML (no image assets) |
| `app/build.gradle.kts` | module config: SDK 36, Compose BOM (2025.06.00 — pinned to the compileSdk 36 / AGP 8.13 era) |
| `gradle/libs.versions.toml` | all dependency versions in one place |
| `gradle.properties` | `android.useAndroidX=true` (the build refuses without it) |
| `EDITING.md` | the guide: edit spots, build loop, troubleshooting |
| `build.bat` | one command: `assembleDebug` + `adb install` |
| `local.properties` | machine-local SDK path (single-machine personal backup) |

## Build & install

```
build.bat     # builds the APK and installs it on the connected phone
```

First build downloads dependencies (~3 min); later builds take ~3 s.
Requires: JDK 17, Android SDK (platform 36 + build-tools 36), USB debugging
enabled, and on Xiaomi/HyperOS: Developer options → "Install via USB" ON.

## Hard-won lessons (kept in file comments where they apply)

- Windows PyPI torch is CPU-only — but Android's equivalents bite too:
  the Compose BOM needs `implementation(platform(...))`, and the newest BOM
  demands an SDK two versions ahead of stable AGP. Pin the toolchain era.
- A loading-state guard (`if (loading) return`) plus `loading = true`
  initialization silently cancels the first fetch — spinner forever.
- `INSTALL_FAILED_USER_RESTRICTED` on Xiaomi means "Install via USB" is off,
  not a broken build.