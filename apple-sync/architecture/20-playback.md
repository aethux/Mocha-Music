# 20. Playback

## The service

`playback/MusicService.kt` is a Media3 `MediaSessionService` (`class MusicService :` at line 308). It
owns one ExoPlayer (built around line 1416), publishes a `MediaSession` so notification, Bluetooth,
Android Auto, the TV app and the phone-link remote all control the same session, and hosts the
audio pipeline:

- `audioPass` (line 371) — USB DAC / exclusive output routing, see `playback/audio/pass/`.
- Audio processors from `eq/audio/`: parametric EQ, AutoEq headphone correction, loudness,
  replay gain, spatial, reverb, peak limiter, crossmix transition, haptics.
- `TransportFadingPlayer` and `TransportTransitions` for pause/play/resume fades.
- `SleepTimer`, `CallWatcher` (pause on call), `ProviderSwitch` (failover between sources),
  `StreamInspector` and `StreamFacts` (what is actually playing, for the UI and the log),
  `YouTubePlayReporter` (play counts back to the app's own stats).

`playQueue(...)` (line 1998) is the single entry point screens call. `PlayerConnection` is the
UI-side control wrapper: it binds to the service, exposes `StateFlow`s (`playbackState`,
`isPlaying`, queue, position) and survives activity recreation.

## Queues

`playback/queues/` implements `Queue` with: `YouTubeQueue` (InnerTube watch endpoint, paginates
`next`), `YouTubePlaylistQueue`, `YouTubeAlbumRadio`, `ListQueue` (a plain list, used for local
playlists and imports), `LocalAlbumRadio`, `EmptyQueue`. A queue yields `MediaMetadata` /
`MediaItem`s and can preload one item, which is how gapless and crossmix get their next track.

## How a song becomes audio

1. A `MediaItem` is queued with a media id and extras describing the track.
2. `playback/custom/DynamicResolvingMediaSource.kt` wraps the item and calls a `fetcherAction` when
   ExoPlayer asks for the real source. The fetcher asks the *installed source modules* in the user's
   order (`modules/SourceModules.kt`, `ModuleRegistry`), each answering
   `resolve(TrackQuery) -> SourceStream`.
3. A `SourceStream` carries a URL (progressive, HLS or DASH), headers, a codec/quality label,
   `sampleRateHz`, and optionally `StreamDrm.Widevine(license, pssh)`.
4. `playback/custom/WidevineDrm.kt` provides the `MediaDrmCallback`; the module's `LicenseServer`
   trades the CDM challenge for a license. This is the mechanism the Apple Music module uses
   against Apple's web playback license server.
5. If the app is offline or a download exists, the download cache is used instead
   (`playback/StoredKind`, `DownloadCache`).

Built-in sources today: YouTube (`providers/innertube`, the default and always-on), Apple Music,
Monochrome, JioSaavn, plus anything the user installs.

## Downloads

`playback/DownloadUtil.kt` is the whole pipeline: it picks a provider route via
`sourceModules.downloadRoute(track, failedProviders)`, streams through Media3's
`DownloadManager` into `ExoDownloadService`, records codecs/sizes, saves lyrics, and exposes
`downloads: StateFlow<Map<String, Download>>` plus a `batch` for the Library progress card. Details
worth knowing:

- Downloads bypass the player's cache on purpose: the player caches whatever it streamed, which may
  be a different format from the download's requested quality.
- Per-song URL cache with expiry, per-provider failure memory so a retry moves down the order, and
  per-song format pinning so a resumed download cannot change format.
- `playback/ExportTags.kt`, `DownloadExporter.kt`, `Mp4FlacRemux.kt`, `OggOpusRemux.kt` write tags,
  covers and containers for exported files.
- `AutoDownloads.kt` downloads new songs of chosen playlists and liked songs, tracking where each
  list started so old songs are not re-fetched.
- `StorageMover.kt` / `StoragePlaces.kt` move the download tree between storage locations.

## Audio lab

`eq/` is its own subsystem: `AudioFxService` (the effect chain), `AudioLabStore` (state),
`eq/audio/*` processors, `eq/data/*` EQ profiles, `eq/autoeq/*` headphone correction data.
UI lives in `ui/screens/audiolab/`. `playback/audio/` holds the device side: USB DAC probing
(`UsbDacReader`, `UsbAudioDescriptors`), exclusive/mixer passes (`AudioPass`, `UsbPass`,
`MixerPass`), Bluetooth codec watcher (LDAC etc.), AAudio exclusive mode via the native `cpp/`
library.

## Crossmix

`crossmix/` analyses two tracks (`TrackAnalysis`, `LocalGrid`, `Vibe`, `Harmony`), plans a blend
(`CrossmixPlanner`, `CrossmixPlan`) and hands it to `CrossmixDirector` and
`CrossmixTransitionAudioProcessor`. Room analysis is local; streaming analysis is
`StreamingTrackAnalyzer`.

## Playback-related utilities

`CachedSongs` (what is cached), `OriginCard` (the "where this plays from" card), `SleepTimer`,
`SongTuning` (pitch/speed?), `playback/voice/` (voice search and handoff), `utils/Sabr*` and
`utils/potoken/` (YouTube SABR streaming and PoToken), `utils/cipher/` (signature deciphering via a
WebView).

## What this means for the Apple sync

- Playback already goes through modules, so an Apple-backed library needs no player work: the Apple
  module resolves any `TrackQuery`. The only gap is that `TrackQuery` has no id, so the module
  searches by title/artist/duration even when the app knows the exact catalog id.
- `MediaItem` extras are how the app knows which song is playing; a sync engine can listen there for
  "currently playing" without touching the service.
- Downloads are source-module-driven, so Apple rows can be downloaded only if some module (e.g.
  Monochrome) resolves them; Apple streams themselves cannot be stored.
