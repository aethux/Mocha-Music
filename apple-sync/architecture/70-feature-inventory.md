# 70. Feature inventory

Every user-facing feature I could find, with the code that implements it. Grouped by area. If a row
names a directory, the entry point is the obvious screen or service inside it.

## Playback and audio

| Feature | Where |
|---|---|
| Stream from YouTube Music (default source) | `providers/innertube`, `playback/queues/YouTubeQueue`, `MusicService` |
| Stream from installed modules (Apple Music, Monochrome, JioSaavn, user modules) | `modules/*`, `modules/SourceModules`, `playback/custom/DynamicResolvingMediaSource` |
| Play local files | `importer/LocalImportService`, `playback/queues/ListQueue`, `LocalModule` |
| Gapless / next-track preload | `Queue.preloadItem`, `MusicService.playQueue` |
| Crossfade and harmonic crossmix | `crossmix/`, `playback/CrossmixDirector`, `CrossmixTransitionAudioProcessor` |
| Sleep timer | `playback/SleepTimer.kt` |
| Pause on call, resume on Bluetooth | `playback/CallWatcher.kt`, playback settings |
| Skip silence | `playback/audio/SilenceDetectorAudioProcessor.kt` |
| Volume, replay gain, loudness normalisation | `eq/audio/`, `eq/LoudnessSettings`, `ReplayGain*` |
| Parametric EQ with presets and saved profiles | `eq/AudioFxService`, `eq/data/*`, `ui/screens/equalizer/` |
| AutoEq headphone correction | `eq/autoeq/`, `ui/screens/equalizer/` |
| Spatial audio and reverb | `eq/audio/SpatialAudioProcessor`, `ReverbAudioProcessor` |
| Peak limiter | `eq/audio/PeakLimiter.kt` |
| Haptic audio (vibration from the mix) | `playback/audio/HapticAudioProcessor.kt` |
| USB DAC: exclusive and mixer passthrough | `playback/audio/pass/`, `AudioPass`, `UsbPass`, `MixerPass` |
| AAudio exclusive output | `app/src/main/cpp`, `pass/AudioPass` |
| Bluetooth codec reporting (LDAC etc.) | `playback/audio/BluetoothCodecWatcher.kt` |
| Stream inspector (what is really playing, bitrate/codec/DRM) | `playback/StreamInspector.kt`, `StreamFacts.kt` |
| Playback failure failover between sources | `playback/ProviderSwitch.kt`, `StreamFailure` |
| Video mode (YouTube video with video-lyrics) | `ui/player/video/`, `utils/VideoStreams.kt` |
| Queue management (reorder, play next, radio) | `playback/queues/*`, `ui/player/QueueScreen.kt` |
| Play/pause/resume fades | `playback/TransportFadingPlayer.kt`, `TransportTransitions.kt` |

## Downloads and files

| Feature | Where |
|---|---|
| Download a song (best source in the user's order) | `playback/DownloadUtil.kt`, `ExoDownloadService` |
| Auto-download new songs of playlists and liked songs | `playback/AutoDownloads.kt` |
| Download quality and format choices | `constants/DownloadQuality`, `StreamFormat` |
| Container remux (MP4/FLAC, OGG/Opus) | `playback/Mp4FlacRemux.kt`, `OggOpusRemux.kt` |
| Tag writing and covers on export | `playback/ExportTags.kt`, `ExportCover.kt` |
| Export downloads to a folder/document tree | `playback/DownloadExporter.kt`, `utils/DocumentTree.kt` |
| Move the download tree between storages | `playback/StorageMover.kt`, `StoragePlaces.kt` |
| Cached-songs view | `playback/CachedSongs.kt`, `ui/screens/playlist/CachePlaylistScreen.kt` |
| Scan device audio, import metadata/covers | `importer/DeviceMusicScanner.kt`, `LocalAudioImport`, `LocalArtwork` |
| Fix local song metadata/sidecars | `importer/LocalSongRepair.kt`, `LocalSidecars.kt` |

## Library, playlists, favourites

| Feature | Where |
|---|---|
| Library tabs: songs, albums, artists, playlists | `ui/screens/library/*`, `LibraryTabs.kt` |
| Filters and sort orders | `constants/LibraryFilter`, `SongSortType`, `AlbumSortType`, … |
| Like / unlike, add to library, remove | `MusicService`, `db/DatabaseDao`, `utils/SyncUtils.likeSong` |
| Create, rename, reorder, delete playlists; add/remove songs | `db/daos/PlaylistDao`, `ui/screens/playlist/*`, `ui/menu/AddToPlaylistDialog` |
| Auto/generated playlists (mixes, rediscover, etc.) | `ui/screens/playlist/AutoPlaylistScreen.kt`, `viewmodels/AutoPlaylistViewModel` |
| Playlist covers (generated styles, custom image) | `playlistcover/`, `ui/menu/CustomThumbnailMenu.kt` |
| Speed dial pins on Home (capped) | `db/daos/SpeedDialDao`, `db/MusicDatabase.pinToSpeedDial` |
| Recent searches | `db/entities/SearchHistory`, `ui/screens/search/RecentSearches` |
| Song edits (title/artist/cover/video override) | `songedit/`, `db/entities/SongEdit`, `ui/menu/songedit/` |

## Discovery and search

| Feature | Where |
|---|---|
| Personalised Home (shelves, hero card, daily discover) | `ui/screens/HomeScreen.kt`, `ui/component/`, `viewmodels/HomeViewModel`, `HomeRecommender` |
| Home styles: Mochyy / Listen Now / Hop In | `constants/HomeStyle`, `ui/screens/Home*` |
| Moods, genres, charts, explore | `ui/screens/ExploreScreen.kt`, `GenreScreen.kt`, `ChartsViewModel` |
| Artist pages, albums, similar artists, videos | `ui/screens/artist/*`, `providers/artistvideo` |
| Album pages with credits and "about" | `ui/screens/AlbumScreen.kt`, `ui/player/info/` |
| Online search with suggestions | `ui/screens/search/*`, `suggestions/` (incl. `AppleMusicScraper`) |
| Local library search | `ui/screens/search/LocalSearchScreen.kt` |
| Voice search and voice handoff | `playback/voice/` |

## Lyrics

| Feature | Where |
|---|---|
| 10+ lyric providers with fallback order | `lyrics/*`, `providers/*lyrics`, `LyricsProviderRegistry` |
| Synced, word-by-word, duet and background vocals | `lyrics/LyricsAligner`, `LyricsEntry`, `RubyToken` |
| Translations | `lyrics/LyricsTranslationHelper`, `GoogleTranslateEngine` |
| Automatic lyric sync from audio | `lyrics/LyricsAutoSync`, `AudioPrinter`, `AudioMatcher` |
| Video lyrics (YouTube captions and audio sync) | `lyrics/VideoLyricsSync`, `VideoAudioSync` |
| Floating lyrics overlay | `floating/` |
| Per-song offset and romanisation | `song.lyricsOffset`, `song.romanizeLyrics`, settings |
| Lyric files for local songs | `songedit/LyricsFileReader`, `LyricsFileFormats` |

## Personalisation and UI

| Feature | Where |
|---|---|
| Player styles: Flagship, Classic, Turntable, Poster, Timeline, Lyrics, Bedside, Video, iPod | `ui/player/*`, `constants/PlayerStyle` |
| Home, playlist and bottom-bar styles | `constants/HomeStyle`, `PlaylistStyle`, `ui/component` |
| Blur quality/strength, progressive blur, pure black | `constants`, `ui/component/BlurEffectCache`, settings |
| Dynamic colours from artwork | `ui/theme/ArtworkPalette`, `CoverThemeColor` |
| Fonts, accent colour, animations, haptics, gestures | `ui/screens/settings/*`, `ui/screens/gesture/`, `constants/Haptic*` |
| Launcher icon switching (50 aliases) | `utils/AppIcons.kt`, manifest activity-aliases |
| Module looks (players, bars, pages, shaders) | `modules/skin/`, `ui/screens/settings/modules/` |
| Player studio (edit layout) | `ui/player/studio/` |
| Share cards and video export | `ui/player/share/` |
| Stats and year-style recap | `ui/screens/recap/` |
| "Larp mode" and other easter eggs | `ui/screens/settings/LarpModeSheet.kt`, `ui/player/LarpLevel` |

## Modules and extensions

| Feature | Where |
|---|---|
| Install modules from link, zip, GitHub, catalog | `modules/ModuleRegistry`, `ModuleCatalog`, `GitHubReleases` |
| Module browser and settings pages | `ui/screens/settings/modules/*`, `modules/browser/` |
| JS modules (separate project) | `modules/bridge/`, `docs/wiki` |
| Web modules (HTTP contract) | `modules/web/WebModule.kt` |
| Look modules | `modules/skin/`, `modules/ModuleLooks` |
| Built-in sources: Apple Music, Monochrome, JioSaavn | `modules/applemusic`, `modules/monochrome`, `modules/jiosaavn` |

## Import, export, sync

| Feature | Where |
|---|---|
| Import by link (Spotify embed, Apple page, Deezer, YouTube) | `importer/LinkImporter.kt` |
| Import by signed-in account (Spotify, Apple, Tidal, YouTube Music) | `importer/account/*`, `AccountImportSession` |
| Import CSV (TuneMyMusic and Mochy formats) | `importer/CsvImport.kt`, `MochyCsvExporter.kt` |
| Import local files | `importer/LocalImportService`, `DeviceMusicScanner` |
| Re-sync an imported playlist later | `importer/PlaylistResync.kt` |
| Undo the last sync | `importer/SyncUndo.kt` |
| YouTube Music full library sync | `utils/SyncUtils.kt` |
| Two-way auto-sync for a YouTube playlist | `YouTubePlaylistMirror`, `PlaylistAddedHere`, `playlist.isAutoSync` |
| Full-install transfer archive (backup/restore) | `importer/transfer/*`, `BackupRestoreViewModel` |

## Integrations and devices

| Feature | Where |
|---|---|
| Last.fm / Libre.fm scrobbling with offline queue | `scrobble/`, `providers/lastfm` |
| Discord rich presence with rotating artwork | `discord/` |
| Shazam-style recognition | `recognition/`, `providers/shazamkit` |
| Spotify Canvas and Apple Music canvas | `providers/canvas`, `providers/applecanvas` |
| Android Auto | `car/*`, `MochyCarAppService` |
| Android TV app with phone link | `ui/tv/*`, `link/tv/`, `link/phone/PhoneLinkService` |
| Remote control of a paired device | `link/remote/` |
| Home-screen widgets (music, turntable) | `widget/` |
| Media session, notification controls, Bluetooth | `MusicService`, `MediaSessionConstants` |

## System

| Feature | Where |
|---|---|
| In-app updater with channels and changelog | `core/updater/*`, `UpdateDownloadWorker` |
| Crash screen and handler | `utils/CrashHandler.kt`, `ui/screens/CrashActivity.kt` |
| Proxy support | `utils/AppProxy.kt`, `ProxySettingsScreen` |
| Data saver and offline mode | `utils/DataSaver.kt`, `OfflineMode.kt` |
| Languages (40+ locales) | `i18n/`, `res/values-*` |
| F-Droid flavour without self-update | `app/src/fdroid` |
| Onboarding with device capability probe | `ui/screens/setup/*` |
