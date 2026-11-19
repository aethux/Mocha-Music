# 00. Overview

## What it is

An Android music player: Kotlin, Jetpack Compose, Hilt, Room, Media3/ExoPlayer, Coil, Ktor, Timber.
It streams from YouTube Music through `providers/innertube` (InnerTube, the web client's private
API), can also stream from installed modules (Apple Music, Monochrome, JioSaavn), plays local
files, and imports playlists from Spotify, Apple Music, Tidal, YouTube Music, CSV and other Mochy
installs.

Licence GPL-3.0. Fork lineage: ViviMusic → Metrolist → Mochy.

## Build facts

| | |
|---|---|
| `compileSdk` / `targetSdk` / `minSdk` | 37 / 37 / 24 |
| AGP / Gradle / Kotlin | 9.0.0 / 9.3.1 / 2.3.10 |
| JDK toolchain | 21 |
| NDK / CMake | 27.0.12077973 / 3.22.1 (native audio: `app/src/main/cpp`) |
| Flavours | `abi` (universal, arm64, armeabi, x86, x86_64) × `variant` (gms, foss); `fdroid` source set strips self-update |
| DB | Room `mochy.db`, version 43, 24 entities, 3 views |
| DI | Hilt everywhere; `di/*EntryPoint.kt` exposes singletons to non-Hilt contexts (workers, services) |

Build: see `apple-sync/ENVIRONMENT.md`. CI builds `assembleUniversalGmsDebug` with
`--no-configuration-cache`.

## Gradle modules

`app` is the whole application (1,078 files, 254k LOC). Subprojects are libraries:

- `providers/innertube` (106 files, 8.4k LOC) — YouTube/YouTube Music client, the app's default source.
- `providers/canvas`, `applecanvas`, `artistvideo` — animated artwork.
- Lyric providers: `lrclib`, `betterlyrics`, `simpmusic`, `youlyplus`, `paxsenixlyrics`, `directlyrics`, `musixmatch`, `genius`, `kugou`, `wikipedia`.
- `providers/lastfm`, `providers/shazamkit` — scrobbling and recognition.
- `modules/api` (453 LOC) — the module contract, plain Kotlin, no Android.
- `modules/bridge` — runs module code that arrives in a zip (`Wire.kt`, `BridgedModule.kt`).
- `modules/applemusic`, `modules/monochrome`, `modules/jiosaavn` — built-in source modules.

## App packages (by size)

`ui` 570 files · `playback` 63 · `viewmodels` 48 · `utils` 43 · `importer` 39 · `db` 39 ·
`lyrics` 34 · `link` 26 · `modules` 24 · `car` 21 · `eq` 19 · `discord` 15 · `core` 15 ·
`floating` 12 · `extensions` 10 · `di` 8 · `models`, `constants` 7 · `crossmix`, `songedit` 7 ·
`scrobble`, `api` 5 · `recognition` 4 · `widget` 3 · `playlistcover`, `i18n`, `app` 1-3.

## Entry points

| What | Where |
|---|---|
| Application + Hilt root | `App.kt` |
| Single activity, Compose host, nav | `MainActivity.kt`, `ui/screens/Screens.kt`, `ui/screens/NavigationBuilder.kt` |
| Playback service | `playback/MusicService.kt` (a Media3 `MediaSessionService`) |
| Download service | `playback/ExoDownloadService.kt` |
| Car app | `car/MochyCarAppService.kt`, `car/CarApp.kt` |
| TV | `ui/tv/TvActivity.kt` |
| Phone↔TV link service | `link/phone/PhoneLinkService.kt` |
| Widgets | `widget/MusicWidgetReceiver.kt`, `widget/TurntableWidgetReceiver.kt` |
| Crash screen | `ui/screens/CrashActivity.kt`, `utils/CrashHandler.kt` |

## Conventions that matter when editing

- Screens are `@Composable` functions in `ui/screens/**`, each with a `@HiltViewModel` in
  `viewmodels/`. Navigation destinations live in `Screens.kt`.
- Preferences are DataStore keys in `preferences/PreferenceKeys.kt`, read with helpers such as
  `enumPreference(context, Key, Default)`; secret material goes through `utils/SecretVault.kt`.
- Long strings of behaviour live in KDoc, in a conversational voice. Read them; they explain intent
  the code doesn't.
- `MainActivity` registers ~50 `activity-alias` entries for launcher icons; ignore them when
  hunting for real screens.

## Where to look first for the sync work

| Question | File |
|---|---|
| How are playlists stored and edited? | `db/daos/PlaylistDao.kt`, `db/entities/PlaylistEntity.kt` |
| How does YouTube two-way sync work today? | `utils/SyncUtils.kt`, `importer/YouTubePlaylistMirror.kt`, `importer/PlaylistAddedHere.kt` |
| How does an Apple import read the account? | `importer/account/AppleMusicAccount.kt` |
| How does a song become audio? | `playback/MusicService.kt`, `playback/custom/DynamicResolvingMediaSource.kt`, `modules/applemusic/AppleMusicModule.kt` |
| What can a module touch? | `modules/api/.../MochyModule.kt` |
