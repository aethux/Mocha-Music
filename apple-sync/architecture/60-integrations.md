# 60. Integrations and side systems

## Scrobbling

`scrobble/` — `Scrobbler` (Last.fm and Libre.fm), `ScrobbleTimer` (when a play counts),
`ScrobbleQueue` + `PendingScrobble` (offline queue), `ScrobbleSendWorker` (WorkManager), `ScrobbleSite`.
API credentials come from `local.properties` (`LASTFM_API_KEY`/`LASTFM_SECRET`) or are absent, in
which case the feature switches off. `providers/lastfm` holds the client.

## Discord presence

`discord/` — a full gateway client (`DiscordGateway`, `DiscordAuth`, `DiscordLoginActivity`),
presence model (`NowPlaying`, `PresenceActivity`, `PresenceKind`, `PresenceText`, `PresenceButton`,
`PresenceSettings`), image hosting (`PictureHosts`, `PresenceImages`, `SpinningArtwork`, `GifEncoder`)
and an upload cache. `DISCORD_CLIENT_ID` is an optional build key.

## Lyrics

`lyrics/` is a subsystem of its own:

- Providers: LrcLib, BetterLyrics, SimpMusic, YouLyPlus, PaxSenix, KuGou, NetEase, QQ Music, Deezer,
  embedded tags, plus module providers (`ModuleLyricsProvider`) and `LyricsProviderRegistry`.
- Alignment: `LyricsAligner`, `CaptionAligner`, `LyricsAutoSync`, word timestamps, ruby tokens
  (duets and background vocals).
- Video: `VideoLyricsSync`, `VideoAudioSync`, `SongVideoMap`, `YouTubeCaptionTranslation`.
- Translation: `LyricsTranslationHelper`, `GoogleTranslateEngine`.
- Floating lyrics overlay: `floating/`.

## Canvas and artist visuals

- `providers/canvas` — Spotify Canvas lookup; `providers/applecanvas` — Apple Music editorial video
  (needs Apple tokens; it was initialising in logcat during the emulator run).
- `providers/artistvideo` — artist background videos.
- `ui/player/CanvasArtwork*`, `CanvasVideoSession`, `utils/ArtistVideos`.

## Recognition

`recognition/` — Shazam-style matching: `MusicRecognitionService`, `ShazamSignatureGenerator`,
`VibraSignature`, `AudioPrinter`/`AudioMatcher` (local fingerprints). `providers/shazamkit` is the
client. `ui/player/info/` shows the result; `recognition_history` stores it, including ISRC.

## Device linking (phone ↔ TV ↔ remote)

`link/` is a small network stack:

- `LinkHttpServer`, `LinkPairingDoor`, `PairingCode`, `LinkAuth`, `LinkCredentials` — pairing and
  auth over the local network.
- `link/phone/` — `PhoneLinkService` (foreground service), `PhoneLinkHost`, `PhoneLinkBootReceiver`,
  `PairedDeviceStore`, `PhoneLibraryRoutes` (serves the library to a paired TV).
- `link/tv/` — `TvPairing`, `TvPhoneSync`, `LibraryMirror`, `TvControl`, `TvProfiles` (the TV side).
- `link/remote/` — `RemoteControl`, `RemoteTarget`, `RemoteQueueItem` (remote control of a peer).
- `LocalNetworkAccess`, `LinkAdvert`, `LinkPeer` handle discovery.
- Requires `ACCESS_LOCAL_NETWORK`, multicast state, and a foreground service type.

## Updating and crash handling

`core/updater/` — GitHub release polling, channels (`UpdateChannel`), changelog parsing,
`UpdateDownloadWorker` + `CustomDownloadManager` + notification provider, `UpdateState`. The F-Droid
flavour strips the self-update permission. `utils/CrashHandler.kt` + `ui/screens/CrashActivity.kt`
catch and display crashes; `core/changelog/` caches release notes.

## Sharing and export

- `ui/player/share/` — share cards (song, lyrics, art) and video export with encoders and an
  offscreen frame renderer.
- `playback/DownloadExporter.kt`, `ExportTags.kt`, `ExportCover.kt`, `ExportLayout` — exporting
  downloads out of the app's private storage (`ui/screens/settings/storage/ExportDownloadsSheet`).
- `utils/ComposeToImage.kt` — Compose → bitmap for shares.

## Proxy and networking

`utils/AppProxy.kt`, `ProxyConfig`, `ProxyRefusedException`, settings in `ProxySettingsScreen`, plus
`utils/DataSaver`, `OfflineMode`, `NetworkConnectivityObserver`, `BotDetectionMitigator` (for
YouTube's bot checks), `utils/cipher/` (signature deciphering in a WebView), `utils/potoken/`
(PoToken generation in a WebView), `utils/sabr/` (SABR streaming transforms).

## Stats and recap

`ui/screens/recap/` — listening statistics, story-style recap with shaders, genre share, snippet
player. Backed by `event`, `playCount` and `RecapRepository`. `ui/screens/recap/StatsScreen.kt` is
the entry point; `constants/OptionStats` controls the range.

## What the sync work touches here

Almost nothing, which is the point of keeping it additive. The two integrations worth knowing:

- `discord/` and `scrobble/` observe the player, so an Apple-sourced play behaves exactly like any
  other play; no changes needed.
- `providers/applecanvas` already holds Apple tokens for artwork. It and the Apple source module are
  two independent token owners today; the sync layer makes a third, which is the wart noted in the
  plan.
