# 40. Modules

Modules are how Mochy gains sources, lyrics, metadata, looks and player UI without shipping them in
the APK. The contract is `modules/api/src/main/kotlin/com/mochyy/mochymusic/module/MochyModule.kt`,
plain Kotlin, `MODULE_API_VERSION = 3`.

## The contract

`MochyModule` (everything a module is):

| Member | Meaning |
|---|---|
| `info: ModuleInfo` | id, name, version, description, apiVersion, author, source URL |
| `adds: Set<ModuleFeature>` | what it contributes: AudioSource, Lyrics, Metadata, Look |
| `attach(host: ModuleHost)` | called once; gives storage, HTTP, logging, a private folder |
| `signIn: SignInSpec?` | how the app signs in: start URL, allowed origins, session origins, desktop site, title/subtitle |
| `onSignInHeader(name, value, url): Boolean` | every request header the service's page sends; true once the module has what it needs |
| `accountState()` / `verifyAccount()` / `signOut()` | signed out / signed in / expired |
| `choices: List<ModuleChoice>` | user-selectable options (quality, region) |

`ModuleHost` is the sandbox: `http(HttpRequest): HttpResponse`, `storage` (key/value),
`log(message)`, `folder: File`.

Type-specific interfaces:

- `SourceModule` — `resolve(TrackQuery, picks): ResolveResult` and `diagnose(...)` reporting
  `DiagnosticStep`s. Returns `SourceStream(url, format, headers, drm, sourceLabel, qualityLabel,
  matched, codec, sampleRateHz)` or `NotFound`/`Unavailable`.
- `LyricsModule.lyrics(TrackQuery): String?` (LRC or plain).
- `MetadataModule.metadata(TrackQuery): TrackMetadata?` (genre, release date, writers, producers,
  label, about, cover URL, **isrc**).
- `LookModule.look: Map<String, String>` — may change only the keys in `LookSetting` (player style,
  home style, cover rounding, blur, accent, backgrounds, iPod theme, bottom bar glass…).
- `ServerListModule` (user-addable servers, e.g. Monochrome), `ItemListModule` (zips/links of items).

`TrackQuery` is what the app knows about the song it wants: title, artists, album, duration, isrc.
**No id** — which is why every module re-searches by metadata, and the one-line change the Apple work
wants.

## Registry and lifecycle

`modules/ModuleRegistry.kt`: loads built-ins and installed modules, tracks enabled state
(`enabled_<id>` preference, only the YouTube source on by default), user ordering (first enabled wins
resolution), per-module choice picks (`picks`), field values (`ModuleFields.onFields`), and looks
(one look at a time, later ones covered by earlier ones). It also handles installing from a link,
a zip, a GitHub release or the catalog (`ModuleCatalog`, `GitHubReleases`, `ModuleUpdates`,
`modules.check(link)` / `checkZip` / `checkCode`).

`modules/SourceModules.kt` is the playback-side façade: which sources are enabled and in what order,
`resolve(track)` across them, and `downloadRoute(track, failed)` for downloads.

## Built-in modules

| Module | What it does |
|---|---|
| `modules/applemusic` | Signs into music.apple.com, captures the developer token + music-user-token, matches a track by ISRC then title/artist against `/v1/catalog/{sf}/search`, and plays Apple's **web playback** stream (AAC 256 or HE-AAC 64) through Widevine, licensed by `play.itunes.apple.com/…/acquireWebPlaybackLicense`. Explicitly cannot do lossless/Atmos (FairPlay) or downloads (no offline license). |
| `modules/monochrome` | FLAC streams from Monochrome servers; user-addable server list. |
| `modules/jiosaavn` | Another source. |
| `modules/bridge` | Runs module code delivered in a zip (`BridgedModule`, `Wire`, `ModuleSide`). |

## JS and web modules

- **JS Modules** live in a separate project (`mochy-js-modules`), added as a module; scripts are
  zips answering `searchTracks`, `getTrackStreamUrl`, `getTrackLyrics`, `getTrackMetadata`, sandboxed
  to songs/lyrics/info over HTTP.
- **Web modules** are URLs implementing `GET /manifest.json`, `/search?q=`, `/stream/{id}`,
  `/lyrics?…`, `/metadata?…` with `Accept: application/json`.
- **Zip modules** can carry looks, players, glass, pages (shader-based) and their own settings.
- **Code modules** (arbitrary Kotlin) must be signed with Mochy's key, so only the maintainer ships
  those.

## What a module cannot do

This is the reason the Apple sync is a fork rather than a module. From the contract:

- No access to `MusicDatabase` at all. A module never sees the library, playlists, play queue or
  settings beyond its own storage.
- It only ever receives one `TrackQuery` at a time — the song the player is about to resolve, or the
  one a lyrics/metadata request is for.
- No way to add, remove or reorder songs, or to create a playlist. There is no hook, not even a
  privileged one.

A module *can* hold the Apple session (via `SignInSpec` + `onSignInHeader`), call the Apple API
freely through `host.http`, and store tokens in `host.storage`. It just has nothing local to sync
against. That makes the useful module-shaped product "send this track / this playlist to Apple
Music", not two-way sync.
