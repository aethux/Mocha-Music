# Generated tree scan

Produced by `apple-sync/architecture/scan.py`. Names and locations only; the
curated pages in this directory explain behaviour.


## Gradle modules

- `app` — 1078 files, 253954 LOC
- `providers:canvas` — 7 files, 718 LOC
- `providers:innertube` — 106 files, 8409 LOC
- `providers:kugou` — 5 files, 250 LOC
- `providers:lrclib` — 2 files, 422 LOC
- `providers:lastfm` — 1 files, 173 LOC
- `providers:betterlyrics` — 3 files, 494 LOC
- `providers:simpmusic` — 2 files, 193 LOC
- `providers:youlyplus` — 2 files, 240 LOC
- `providers:shazamkit` — 2 files, 707 LOC
- `providers:artistvideo` — 2 files, 260 LOC
- `providers:applecanvas` — 1 files, 564 LOC
- `providers:paxsenixlyrics` — 2 files, 488 LOC
- `providers:directlyrics` — 11 files, 580 LOC
- `providers:musixmatch` — 6 files, 754 LOC
- `providers:genius` — 1 files, 157 LOC
- `providers:wikipedia` — 1 files, 211 LOC
- `modules:api` — 2 files, 453 LOC
- `modules:bridge` — 6 files, 1184 LOC
- `modules:applemusic` — 3 files, 795 LOC
- `modules:monochrome` — 2 files, 630 LOC
- `modules:jiosaavn` — 2 files, 335 LOC

## App packages and their declarations


### `(root)`
class App, class MainActivity, object AppContentNeverObscured

### `api`
object DeepLService, object MistralService, object OpenRouterService, object OpenRouterStreamingService

### `canvas`
object SpotifyCanvasProvider

### `car`
class CarApp, class CarSong, class CarCollection, class CarShelf, class CarCatalog, class CarImages, class CarLyrics, class CarLyricsPlayer, class CarLyricLines, class CarLyricsSource, class CarPlayback, class CarSession, class MochyCarAppService

### `car/screens`
class CarCollectionsScreen, class CarHomeTab, class CarLibraryTab, class CarLyricsScreen, class CarNowPlayingScreen, class CarRecentTab, class CarRemoteTab, class CarShelfScreen, class CarSongsScreen, interface CarTabPage, class CarDataTab, class CarTabsScreen

### `constants`
class HapticFocus, class HapticSensitivity, class HistorySource, class LibraryFilter, object MediaSessionConstants, class DensityScale, class HomeStyle, class PlaylistStyle, class SliderStyle, class StreamBackend, class AudioQuality, class DownloadQuality, class AudioPassMode, class CrossmixMode, class CrossmixLength, class CrossmixTempoMatch, class TransportEffect, class VideoQuality, class LibraryViewType, class SongFilter, class ArtistFilter, class AlbumFilter, class SongSortType, class PlaylistSongSortType, class LikedSongSortType, class AutoPlaylistSongSortType, class ArtistSortType, class ArtistSongSortType, class AlbumSortType, class PlaylistSortType, class MixSortType, class GridItemSize, class MyTopFilter, class QuickPicks, class PreferredLyricsProvider, class PlayerButtonsStyle, class PlayerBackgroundStyle, class PlayerCoverStyle, class PlayerStyle, class IpodTheme (+16 more)

### `core`
class AudioDevice, class AudioDeviceType

### `core/changelog`
class ChangelogSection, class ReleaseMetadata, class CachedChangelogData

### `core/commitscreen`
class CommitData

### `core/shapes`
class RoundedStarShape

### `core/updater`
class GithubRelease, interface UpdateState, class FullVersion, class ParsedRelease, class UpdateChannel, class ChangelogSection, class MochyUpdateStatus

### `core/updater/downloadmanager`
class MochyNotificationProvider, class UpdateDownloadWorker, class CustomDownloadManager, object DownloadNotificationManager

### `crossmix`
class TrackAnalysis, class LocalGrid, class Vibe, class StreamingTrackAnalyzer, object Harmony, class CrossmixAnalysisRepository, class MonoResampler, object CrossmixBars, class CrossmixPlan, class CrossmixTrackInput, object CrossmixPlanner, class CrossmixSettings, class FxParams, class CrossmixRole, class StyleUse, class CrossmixStyle

### `db`
class Converters, interface DatabaseDao, class MusicDatabase, class InternalDatabase, class Migration5To6, class Migration6To7, class Migration7To8, class Migration9To10, class Migration10To11, class Migration11To12, class Migration12To13, class Migration13To14, class Migration16To17, class Migration18To19, class Migration19To20, class Migration20To21, class Migration21To22, class Migration22To23, class Migration23To24, class Migration29To30, class RecapEventRow

### `db/daos`
interface SpeedDialDao

### `db/entities`
class Album, class AlbumArtistMap, class AlbumEntity, class AlbumSongRow, class AlbumWithSongs, class Artist, class ArtistEntity, class Event, class EventWithSong, class FormatEntity, class ImportSession, class ImportSessionSong, class ImportedPlaylist, class ImportedPlaylistTrack, class ImportedSong, class ImportedSourceCount, class ImportedAlbumRow, class LocalItem, class LyricsEntity, class PlayCountEntity, class Playlist, class PlaylistEntity, class PlaylistEvent, class PlaylistSong, class PlaylistSongMap, class PlaylistSongMapPreview, class RecognitionHistory, class RelatedSongMap, class SearchHistory, class SetVideoIdEntity, class Song, class SongAlbumMap, class SongArtistMap, class SongEdit, class EditedArtist, class SongEntity, class SongWithStats, class SortedSongAlbumMap, class SortedSongArtistMap, class SpeedDialItem

### `di`
object AppModule, interface AudioLabEntryPoint, interface DiscordEntryPoint, interface LyricsHelperEntryPoint, object NetworkModule, interface PlaylistResyncEntryPoint, class PlayerCache, class DownloadCache, class ApplicationScope, interface ScrobblerEntryPoint

### `discord`
interface DiscordLogin, class DiscordAuth, class DiscordGateway, object DiscordHttp, class DiscordLoginActivity, class PresenceConnection, class DiscordPresence, class DiscordProfile, class GifEncoder, class NowPlaying, object PictureHosts, class PresenceActivity, class SpinStyle, interface PresencePicture, object PresenceImages, class PresenceKind, class PresenceImage, class PresenceButtonTarget, class PresenceButton, class PresenceSettings, object PresenceText, class SpinningArtwork, class UploadedPictures

### `eq`
class AudioFxService, class AutoEqCorrection, class EqPreset, class EqSettings, class LoudnessSettings, class SpatialSettings, class NormalizeSettings, class PlaybackSettings, class VolumeSettings, class AudioLabStore, object EqPresets, class EqualizerService, class ReverbSettings, object ReverbPresets

### `eq/audio`
object AudioTap, class SpectrumAnalyzer, object Biquad, class ResponseCurve, class CustomEqualizerAudioProcessor, class LoudnessParams, class LoudnessAudioProcessor, class PeakLimiter, class ReplayGainMode, class ReplayGainParams, class ReplayGainTags, class ReverbParams, class ReverbAudioProcessor, class SpatialMode, class SpatialParams, class SpatialAudioProcessor, class VolumeCurve

### `eq/autoeq`
class AutoEqTuning, object AutoEqFitter, class HeadphoneForm, class AutoEqEntry, class AutoEqModel, class AutoEqMeasurement, class AutoEqRepository

### `eq/data`
class SavedEQProfile, class EQProfileRepository, class FilterType, class ParametricEQBand, class ParametricEQ, object ParametricEQParser

### `floating`
class FloatingLyrics, class SongLyrics, class PillShape, class PillMetrics, interface PillHost, class RingMorph, class PillDrop, class PillDropTargets, class FlightSpring, class PillFlight, class PillColors, class PillTint, class RowLook, class PreparedScript, class RowGeometry, class SweepBrush, class ReadingSettings, class PillRowKind, class PillRow, class PillReadings, class PillStop, class PillScript, class LineGroup, class PillState, interface PillWindowListener, class PillWindow, class OverlayOwner

### `i18n`
object AppLanguage

### `importer`
class CsvExportRow, class DeviceSong, object DeviceMusicScanner, class ImportHistory, class TrackMatch, class ImportMatches, class ImportedTrack, class ImportSource, class ParsedImport, class PlaylistOrigin, class ResyncSummary, class ResyncRead, class ResyncRequest, class ImportOptions, class UnmatchedTrack, class ImportProgress, object ImportNotices, class ImportSteps, object ImportedVersions, class LinkSource, class LinkImportException, object LinkImporter, object LocalArtwork, class LocalAudioMetadata, object LocalFileNames, object LocalFolders, class LocalImportService, class LocalFile, class NamedFile, object LocalSidecars, object LocalSongRepair, object MochyCsvExporter, class MusicImportService, object PendingYouTubeLikes, object PlaylistAddedHere, interface ResyncStart, class ResyncAllStart, class PlaylistResync, class PlaylistSourceRecord, object SyncUndo (+4 more)

### `importer/account`
object AccountHttp, interface AccountImportState, class ResyncTarget, class ResyncReads, class AccountImportSession, class AccountService, class RemotePlaylist, interface AccountLibrary, class AccountImportException, class AccountRetryLater, class WebPlayerQuery, class CapturedCredentials, interface AccountConnector, object AppleMusicConnector, class AppleMusicLibrary, object SpotifyConnector, class SpotifyLibrary, object SpotifyWebPlayer, class WebPlayerClient, class SpotifyWebPlayerLibrary, object TidalConnector, class TidalLibrary, class YouTubeMusicLibrary

### `importer/transfer`
object TransferArchive, class TransferManifest, class TransferExporter, class TransferException, class TransferImporter, object TransferRestore

### `link`
class DeviceKind, class LinkCredentials, object LinkAuth, class LinkControlRoutes, class LinkRequest, class LinkResponse, class LinkHttpServer, class PairingCode, class LinkPairingDoor, object LinkPlayer, object LinkRows, interface LinkSettingsEntryPoint, class LinkSettings, class LinkSetting, object LinkSongs, object LinkWire, object LinkIdentity, class LinkPeer, class LinkAdvert, object LocalNetworkAccess, class PhoneFile, object PhoneFiles

### `link/phone`
class PairedDevice, class LiveDevice, object PairedDeviceStore, class PhoneLibraryRoutes, class PhoneLinkHost, class Accepted, class PairException, object PhoneLinkNotices, class PhoneLinkService, class PhoneLinkBootReceiver, class PhonePairing, class PhonePeers

### `link/remote`
interface RemoteControlEntryPoint, class RemoteControl, class RemoteTarget, class RemoteQueueItem, class RemoteNow, class RemoteSetting

### `link/tv`
object LibraryMirror, object TvControl, interface PairTarget, class TvPairing, class PhoneSyncState, class TvPhoneSync, class LinkedPhone, class TvProfile, object TvProfiles

### `lyrics`
class AudioPrints, class AudioPrinter, object AudioMatcher, object BetterLyricsProvider, object CaptionAligner, object DeezerLyricsProvider, object EmbeddedLyrics, class ChannelSource, class Fft, object GoogleTranslateEngine, object KuGouLyricsProvider, object LrcLibLyricsProvider, object LrcRedLyricsProvider, object LyricsAligner, class LyricsAutoSync, class WordTimestamp, class RubyToken, class LyricsEntry, class LyricsHelper, class LyricsResult, class LyricsWithProvider, interface LyricsProvider, object LyricsProviderRegistry, class LyricsProject, object LyricsTranslationHelper, object LyricsUtils, class ModuleLyricsProvider, object NetEaseLyricsProvider, object PaxSenixLyricsProvider, object QQMusicLyricsProvider, object SimpMusicLyricsProvider, class SongVideoMap, class VideoAudioSync, object VideoCaptionSource, object VideoLyricsSync, class VocalActivity, class VocalActivityAnalyzer, class StereoResampler, object YouLyPlusLyricsProvider, object YouTubeCaptionTranslation (+3 more)

### `models`
class ArtistGroup, class ItemsPage, class MediaMetadata, class PersistPlayerState, class PersistQueue, class QueueType, class QueueData, class SimilarRecommendation, class TransferState, class TransferItem, class TransferBatch

### `modules`
object DeviceDecoders, object GitHubReleases, class CatalogModule, class ModuleCatalog, class ModuleLooks, class ModuleFacts, object ModuleMetadata, interface ModuleOrigin, class ModuleEntry, class ModuleRegistry, interface ModuleSource, class ModuleUpdate, class ModuleUpdates, class SourceModules

### `modules/browser`
interface ModuleFeedEntryPoint, class ModuleBrowserLoginActivity, class FeedModule, class FeedKind, class FeedPerson, interface FeedResult, class ModuleFeed

### `modules/code`
object CodeModules

### `modules/local`
class LocalModule

### `modules/skin`
interface SkinSource, class ActiveGlass, class SkinProgram, class SkinParts, object ActiveSkin, class ModuleMenuAction, object ModuleMenu, class ModuleSkin, class SkinGlass, class GlassChange, interface SkinColor, class SkinAlign, class SkinText, class SkinFont, class SkinMenuButton, class MenuSide, class SkinPlayer, class SkinCustomise, interface SkinTarget, class SkinMenuItem, interface SkinBackground, class SkinShaderSource, class SkinLayer, class SkinMotion, class MotionKind, class SkinEnter, class EnterKind, class SkinCondition, class CoverShape, class ProgressStyle, class VisualizerStyle, class ButtonKind, class ShapeKind, class TimeKind, class StackAlign, class GradientKind, interface SkinBlock, class SkinPage, interface PageBackground, interface PageBlock (+17 more)

### `modules/web`
class ManifestSettings, class WebModule, class WebModuleManifest

### `playback`
object AutoDownloads, class CachedSongs, class CallWatcher, class CrossmixDirector, class ExportLayout, class ExportChoice, interface ExportStep, class DownloadExporter, class DownloadUtil, class ExoDownloadService, class ExportTags, class ExportCover, object ExportTagWriter, class MediaLibrarySessionCallback, object Mp4FlacRemux, class StreamFailure, class MusicService, class PassSettings, object OggOpusRemux, object OriginCard, class OriginCardPlayer, class OriginCardLink, class PlayerConnection, class PlayingFrom, class ForcedRoute, interface ProviderSwitchState, class ProviderSwitch, class SleepTimer, class SongTuning, interface MoveStep, class StorageMover, class StoredKind, class StoragePlace, object StoragePlaces, class StreamFacts, class StreamDetails, class StreamInspector, class TransportFadingPlayer, class TransportTransitions, class YouTubePlayReporter (+3 more)

### `playback/audio`
class AudioOutputs, object UsbMixer, class AudioSpectrum, class BluetoothCodecWatcher, class LdacQuality, class BluetoothCodec, class CrossmixClockSink, class CrossmixTransitionAudioProcessor, class HapticAudioProcessor, class PrecisionSink, class SilenceDetectorAudioProcessor, class SincResampler, object StreamPcmDecoder, class Media3MediaDataSource, class UsbDacDetails, class DacFormat, object UsbDacReader, object MixerFormats

### `playback/audio/pass`
class AudioPass, class ExclusivePass, class MixerPass, class PassBlocker, class LabSound, class PassBlockersBefore, object PassNative, object PassPcm, class PassRouterSink, class PassSink, interface PassTransport, class PassFailure, object UsbAudioDescriptors, class UsbDacAccess, interface UsbTransfer, class UsbDacVolume, class UsbPass

### `playback/custom`
class CustomStreamResult, class DynamicResolvingMediaSource, class NoProviderException, object WidevineDrm

### `playback/queues`
object EmptyQueue, class ListQueue, class LocalAlbumRadio, interface Queue, class YouTubeAlbumRadio, class YouTubePlaylistQueue, class YouTubeQueue

### `playback/voice`
class VoicePicker, class VoicePlayActivity, object VoiceHandoff, interface VoiceRequest, object VoiceSearch

### `playlistcover`
class PlaylistCoverStyle, interface CoverChoice, class CoverPalette, object PlaylistCoverGenerator, object PlaylistCoverStore, object PlaylistCovers

### `recognition`
class DecodedAudio, object AudioResampler, object MusicRecognitionService, object ShazamSignatureGenerator, object VibraSignature

### `scrobble`
class PendingScrobble, class ScrobbleQueue, class ScrobbleSendWorker, class ScrobbleSite, class ScrobbleTimer, class Scrobbler

### `songedit`
class CoverOption, object CoverSources, object LyricsFileFormats, class PickedLyrics, object LyricsFileReader, object SongCoverFiles, interface CoverPick, class SongEditDraft, object SongEditSaver, object SongEdits

### `ui/component`
class NavItemState, class TabOwnership, class FontSizeRange, object BlurEffectCache, class PillPress, class BottomSheetState, interface CollapsedSwipe, interface ExpandedSwipe, class ButtonStyle, class TransportColors, class TransportHold, object ContainerTransformRegistry, class MorphingCornerClip, class DeviceTilt, class DraggableLyricsProviderItem, class LinkSegment, class ChromeSurface, class GlassState, class FullSize, class MaskedEffectCache, class BlurDirection, object PeekPageSize, class HeroLabel, interface HeroCarouselItem, class KeepListeningItem, class DiscoverReason, class DiscoverItem, class RelatedPickItem, class ListeningPickItem, object GachaItem, class PlaylistPickItem, class StarterItem, class StarterSongItem, class HeroPillKind, class UnitShader, class GachaTint, class GachaPhase, class GachaState, class GachaSounds, object HomeGlowColor (+48 more)

### `ui/component/detail`
class DetailSecondaryAction, class DetailCollapseState, class CreatorUi, class FeaturedArtistUi, interface PlaylistModeBar, class PlaylistSideHeader

### `ui/launch`
object LaunchGate, class LaunchIntroState, class MarkPlacement, class MarkFrame, class LaunchMarkArt, class LaunchIconArt, class PreludeLook

### `ui/menu`
class Sections, class PlaylistResyncState

### `ui/menu/songedit`
class SongEditFlow, class SongEditState, interface VideoChoice, class VideoPickState

### `ui/player`
class BackdropBlurTransformation, class PlayerSwipe, class PlayerRects, object CanvasVideoCache, object CanvasVideoThread, class CanvasVideoSession, class CanvasTexture, object CanvasArtworkPlaybackCache, class ReflectionMesh, class LarpLevel, class LikeSlot, class LikeBurstState, class Particle, class LyricsLineState, class LyricsAlignment, class LyricsLineInsets, class RubyPlacement, class SweepProbe, class LyricLineParts, class SweepHeadGeometry, class WaveEffectCache, class KaraokeHead, class MotionCells, class TimedRange, class MotionLayout, class MotionLayoutCache, class LineReading, class ShownLyric, class LyricsListPadding, class LineLook, interface LyricRow, class LyricsStagger, class SteadyWrap, class TickerStack, class TickerSlot, class NoteLayout, class NoteLetter, class MorphEndpoints, class MorphEndpointsPx, class MorphElement (+37 more)

### `ui/player/bedside`
class PullToClose, class BedsideInsets, class BedsideTime, class BedsideSong, class BedsideProgress, class BedsideRest, class BedsideInk, class BedsideNight

### `ui/player/customize`
class DiscGeometry, class CoverArtBox, class CoverBoxF, interface CoverOption, class Finish

### `ui/player/flagship`
class AmbientColors, class CoverLook, class SpeakerMotion, class TransportShapes, class PlayGlyphState, class SkipCycleState

### `ui/player/info`
class AboutSource, class SongAbout, class AboutArtist, class StoreLink, object SongAboutLoader, class StatCard, class EffectsSnapshot, class StatsInput

### `ui/player/ipod`
interface IpodWheelActions, class RingZone, class RingStart, class Spinner, class IpodController, class IpodCoverFlowState, class IpodGeometry, class IpodBodyPaint, class IpodHaptics, object HapticsThread, class IpodLook, class IpodBodyColors, class IpodWheelColors, class IpodScreenColors, class IpodPage, interface IpodPick, class IpodRow, class IpodList, class IpodEntry, class IpodNavigator, class IpodBar, class IpodNowPlayingState, class IpodScale

### `ui/player/menu`
class MenuSheetStack, class MenuSheetEntry

### `ui/player/models`
class TransportState, class TrackInfo, class QueueItemData, class QueueState, class ProgressState

### `ui/player/share`
class ShareSong, class ShareStyle, interface ShareBackground, class WordLook, class WordKey, class ShareFlightSource, class LyricsShareSession, class ShareRegions, class ShareChoreography, class SungWord, class CinematicFlow, class TimedPieces, class Spot, class ChasingBox, class WordMode

### `ui/player/share/song`
class ShareReadingChoice, class ShareLine, class Grab, object SharePreviewThread, class SongPreviewPlayer, object SongShare, class BackgroundPage, class ShareLayers, class SendState, class SongShareKind, interface ShareVideoSource, interface SongBackground, interface Fetched, class ShareMusicVideo, class ShareArt, class ShareLyricsLook, class SongShareSession

### `ui/player/share/song/export`
class FrameImage, class OffscreenFrames, class ViewPicture, object ShareEncoders, class ShareMediaSources, class ExportFrames, class ShareAudioFade, class CardFrost, class CardFrostEffect, class ForegroundEffect, class CardFrostProgram, class ForegroundProgram, class ShareApp, class ShareFile, class ShareRequest, class ShareQuality, class ShareTheme, class ShareExportJob, class VideoBackground, class CarrierUnderVideo

### `ui/player/studio`
class BarStage, class StudioParts, class TileHold, class TileInk, class MiddleStage, class PlayerMenuAction, class PlayerMenuLayout, class PlayerMenuExtras, class CardGeometry, class StudioStage, class StudioSheet, class Motion, class StudioAnchors, class PlayerStudioState, class PartTransform, class TileLayout, class StudioCapture, class LiftedPart, class StudioPieces, class StudioCamera, object StudioType

### `ui/player/styles`
class StyleTrack, class StyleLyrics, class StyleSong, class StyleInsets, class RingCircle, class CoverRingState, class RingSide, class RingGeometry, class BarLevels, class TitleFitKey, class TimedWord, class LineSide, class WordCell, class WordStage, class SubCell, class LineCells, class Interlude, class LineKey, class LineCache, class LetterPass, class SkinContext, class SkinLive, class SkinEvents, class TriggerRun, class BlockMotion, class PoseSum, class SkinAudio, class SkinPulse, class StyleTones, class LivePosition, class MenuItem, class StyleMenuExtra, class StyleActions, class StyleTurn, class StyleSplits, class Responsive, object TurnedFrames

### `ui/player/video`
object VideoDash, object VideoFrameReader, class VideoFrameSampler, class VideoLyrics, class VideoLyricLook, class RegularGeometry, class DragMode, class VideoAmbientState, class VideoPane, class SeekFlash, class VideoFrame, class VideoModeState, object YouTubeVideoThread, object YouTubeVideoHttp, class YouTubeVideoSession, object YouTubeVideoSessions, class VideoAmbient, class VideoTexture

### `ui/screens`
class HomeSection, typealias NavEnter, typealias NavExit, class Screens, class NavigationTab, class DarkMode, class LyricsPosition, class OptionStats

### `ui/screens/artist`
class ItemsKind, class ArtistShelves, class ArtistShelfTitles

### `ui/screens/audiolab`
class PictureClock, class LevelHistory, class Spin, class CrossfeedLevel, class AtmosSupport, class SpeedPreset, class CurveShape, object LabColors, class LabChip, class LevelFollower, class LabTitle, interface Shown, class QualityOption, class Effective, class ActiveEffect, class StackCard, class LabSpectrum, class RoomWaves

### `ui/screens/devices`
interface DevicesEntryPoint, class DevicesLink, interface PairUi, class PairAttempt

### `ui/screens/donate`
class MarqueeStrip

### `ui/screens/equalizer`
class Plot

### `ui/screens/gesture`
class CustomiseSection

### `ui/screens/library`
class PickerSong, class PickerSelection, interface IndexedRow, class LibraryGridEntry, class ImportStep, class ImportedService, class IndexScript, class IndexSection, interface RailSlot, class RailPositions, class LocalImportActions, class ManageAction, class TransferKind, class ShelfTabData, class LibraryTab, class LibraryCardEntry

### `ui/screens/playlist`
class AddMusicTab, class AddMusicPicks, class SongSearchResults, class PlaylistType

### `ui/screens/profile`
class ProfilePhase, class ProfileMorphState, object ProfileStore

### `ui/screens/recap`
class ScanVerdict, class ScratchState, object Code128, class StatsRange, class RecapSong, class RecapArtist, class ListeningStats, interface RecapAvailability, object RecapRepository, class GenreShare, object RecapGenres, class RecapSnippetPlayer, class RecapStoryData, class RecapAnchors, class PageAnim, class StoryGeometry, class StoryState, class StoryGesture, class StoryDirector, object ZigzagShape, object RecapClock, class RecapTime, class GiantRow, class GiantTextState, class GiantWordCache, class RecapPalette, object RecapPalettes, object MeshShader, class RankRow, interface GenreState, class PickerChoice

### `ui/screens/search`
class RecentSearch, object RecentSearches, object SearchColors

### `ui/screens/search/suggestions`
object AppleMusicScraper, class SuggestionTrack, class SuggestionArtist, class SuggestionAlbum, class SuggestionsViewModel

### `ui/screens/settings`
class LanguageOption, object CipherRefreshRateLimiter, class SettingsEntry, object MochyGreeting, class HeaderStage, class ProxyDialog, interface ProxyCheck, class SettingsPane, class RecommendationState, class RecommendedSetting, class CardMotion, class TransitionStyle, class Shape, class PictureTools

### `ui/screens/settings/devspace`
class DevSocial, class DevPerson, class IssueFilter, class DevIssue, class DevSpaceStats, object DevSpaceRepository

### `ui/screens/settings/importsync`
class CaptureBridge

### `ui/screens/settings/integrations/discord`
object DiscordColors

### `ui/screens/settings/modules`
class BrowserAction, interface PageProgram, object PageShaderCache, class SettingsPageRow, class TestSong

### `ui/screens/settings/storage`
class ExportOptions, class DownloadSort, class DownloadPicks, class BarPart, class PlacePick

### `ui/screens/setup`
class Step, class WelcomeClock, class WelcomeLayout

### `ui/screens/setup/onboarding`
object BlurCapability, class FrameProbeRecorder, class CardInfo, class Confetti, object Onb, class OnbButtonStyle, class IdCardAnchors, class ProviderArt

### `ui/screens/setup/welcome`
class GirlLayer, object GirlArt, class GirlPose, class Drawstring, class BaseWeights, class GirlMotion, object WelcomeColors, class GirlBitmaps, class GirlPainter

### `ui/screens/update`
class LeadDevEntry, class CreditEntry, class LicenseEntry, class ContributorEntry, interface MdBlock

### `ui/theme`
class ArtworkPalette, object CoverThemeColor, object PlayerColorExtractor, object PlayerSliderColors, object LookFont

### `ui/tv`
class TvSongRef, class TvActions, class TvActivity, interface TvStage, class TvTab, class TvSongsKind, class TvSongsViewModel, object TvRoutes, interface TvHeroCard, interface TvHomeRow, object TvColors, object TvSpace, object TvText, class TvFocusLook, class TvLibraryTab, class TvShelfCard, class TvLinkNews, class TvMochyTile, class TvLink, class TvPlayerBar, class TvLook, class TvSearchViewModel, class TvInfoStart, class TvTrack

### `ui/utils`
class AppBarScrollBehavior, object AppMotion, class HighRateVoter, class VoterPixel, class ItemWrapper, object KeyUtils, class SoftBlurTransformation, object WindowTurning

### `utils`
class AppIconFamily, class AppIconColor, class AppIcon, object AppIcons, class ProxyConfig, object AppProxy, class ProxyRefusedException, object AppleMusicAboutAlbum, object ArtistImageResolver, class ArtistHeader, object ArtistPageCache, class FeedVideoKind, class HomeVideo, object ArtistVideos, object BotDetectionMitigator, class CoilBitmapLoader, object ComposeToImage, class CrashHandler, object DataSaver, object PreferencesCache, class DocumentTree, object ImageRetry, class InnerTubeXStreams, object ListeningGenres, class GenreFamily, class NetworkConnectivityObserver, object NotRecommended, object OfflineMode, class PlaybackLogLevel, class PlaybackLogEntry, object PlaybackLogManager, class SecretPreferences, object SecretVault, object SongVersions, class SyncOperation, class SyncStatus, class SyncState, class SyncUtils, class VideoStreams, class VideoTrack (+6 more)

### `utils/cipher`
object CipherDeobfuscator, class CipherWebView, class CipherException, object FunctionNameExtractor, object PlayerJsFetcher

### `utils/potoken`
class PoTokenException, class BadWebViewException, class PoTokenGenerator, class PoTokenResult, class PoTokenWebView

### `utils/sabr`
object EjsNTransformSolver, class SabrException

### `viewmodels`
class AccountImportViewModel, class AccountSettingsViewModel, class AccountContentType, class AccountViewModel, class AlbumViewModel, class ArtistAlbumsViewModel, class ArtistItemsViewModel, class SimilarGroups, class ArtistSimilarViewModel, class ArtistRelease, class ArtistViewModel, interface AutoEqIndex, class AudioLabViewModel, class LikedSourceSection, class AutoPlaylistSongs, class AutoPlaylistViewModel, class CsvImportState, class ConvertedSongLog, class BackupRestoreViewModel, class BrowseViewModel, class CachePlaylistViewModel, class ChartsViewModel, class DiscordViewModel, class ExploreViewModel, class GenreViewModel, class HistoryViewModel, class DateAgo, class HomeFeedCache, class HomeCategory, class TasteProfile, class HomeRecommender, class ListeningSignals, class DailyDiscoverItem, class CommunityPlaylistItem, class HomeViewModel, class StarterSong, interface PendingImport, class ImportSyncViewModel, class IntegrationsViewModel, class IpodAlbum (+48 more)

### `widget`
class MochyWidgetManager, class MusicWidgetReceiver, class TurntableWidgetReceiver

## Manifest components


**activitys** (57)

- `.ui.screens.CrashActivity`
- `.ui.tv.TvActivity`
- `.MainActivity`
- `.IconDefault`
- `.IconMarkWhite`
- `.IconMarkBlack`
- `.IconMarkMaterial`
- `.IconMarkTransparent`
- `.IconOutline`
- `.IconOutlineWhite`
- `.IconOutlineBlack`
- `.IconOutlineMaterial`
- `.IconOutlineTransparent`
- `.IconAnimeWhite`
- `.IconAnimeDark`
- `.IconAnimeMaterial`
- `.IconAnimeTransparent`
- `.IconMarkRed`
- `.IconMarkOrange`
- `.IconMarkYellow`
- `.IconMarkGreen`
- `.IconMarkTeal`
- `.IconMarkSky`
- `.IconMarkBlue`
- `.IconMarkIndigo`
- `.IconMarkPurple`
- `.IconMarkPink`
- `.IconMarkGraphite`
- `.IconMarkCream`
- `.IconOutlineRed`
- `.IconOutlineOrange`
- `.IconOutlineYellow`
- `.IconOutlineGreen`
- `.IconOutlineTeal`
- `.IconOutlineSky`
- `.IconOutlineBlue`
- `.IconOutlineIndigo`
- `.IconOutlinePurple`
- `.IconOutlinePink`
- `.IconOutlineGraphite`
- `.IconOutlineCream`
- `.IconAnimeRed`
- `.IconAnimeOrange`
- `.IconAnimeYellow`
- `.IconAnimeGreen`
- `.IconAnimeTeal`
- `.IconAnimeSky`
- `.IconAnimeBlue`
- `.IconAnimeIndigo`
- `.IconAnimePurple`
- `.IconAnimePink`
- `.IconAnimeGraphite`
- `.IconAnimeCream`
- `.discord.DiscordLoginActivity`
- `.modules.browser.ModuleBrowserLoginActivity`
- `com.yalantis.ucrop.UCropActivity`
- `.playback.voice.VoicePlayActivity`

**activity-aliass** (50)

- `.IconDefault`
- `.IconMarkWhite`
- `.IconMarkBlack`
- `.IconMarkMaterial`
- `.IconMarkTransparent`
- `.IconOutline`
- `.IconOutlineWhite`
- `.IconOutlineBlack`
- `.IconOutlineMaterial`
- `.IconOutlineTransparent`
- `.IconAnimeWhite`
- `.IconAnimeDark`
- `.IconAnimeMaterial`
- `.IconAnimeTransparent`
- `.IconMarkRed`
- `.IconMarkOrange`
- `.IconMarkYellow`
- `.IconMarkGreen`
- `.IconMarkTeal`
- `.IconMarkSky`
- `.IconMarkBlue`
- `.IconMarkIndigo`
- `.IconMarkPurple`
- `.IconMarkPink`
- `.IconMarkGraphite`
- `.IconMarkCream`
- `.IconOutlineRed`
- `.IconOutlineOrange`
- `.IconOutlineYellow`
- `.IconOutlineGreen`
- `.IconOutlineTeal`
- `.IconOutlineSky`
- `.IconOutlineBlue`
- `.IconOutlineIndigo`
- `.IconOutlinePurple`
- `.IconOutlinePink`
- `.IconOutlineGraphite`
- `.IconOutlineCream`
- `.IconAnimeRed`
- `.IconAnimeOrange`
- `.IconAnimeYellow`
- `.IconAnimeGreen`
- `.IconAnimeTeal`
- `.IconAnimeSky`
- `.IconAnimeBlue`
- `.IconAnimeIndigo`
- `.IconAnimePurple`
- `.IconAnimePink`
- `.IconAnimeGraphite`
- `.IconAnimeCream`

**services** (4)

- `.playback.MusicService`
- `.car.MochyCarAppService`
- `.link.phone.PhoneLinkService`
- `.playback.ExoDownloadService`

**receivers** (4)

- `.link.phone.PhoneLinkBootReceiver`
- `androidx.media3.session.MediaButtonReceiver`
- `.widget.MusicWidgetReceiver`
- `.widget.TurntableWidgetReceiver`

**providers** (2)

- `androidx.core.content.FileProvider`
- `com.dpi.DensityScaler`

**permissions**

- `android.permission.ACCESS_LOCAL_NETWORK`
- `android.permission.ACCESS_NETWORK_STATE`
- `android.permission.BLUETOOTH`
- `android.permission.BLUETOOTH_ADMIN`
- `android.permission.BLUETOOTH_CONNECT`
- `android.permission.CHANGE_WIFI_MULTICAST_STATE`
- `android.permission.FOREGROUND_SERVICE`
- `android.permission.FOREGROUND_SERVICE_CONNECTED_DEVICE`
- `android.permission.FOREGROUND_SERVICE_DATA_SYNC`
- `android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK`
- `android.permission.INTERNET`
- `android.permission.MANAGE_EXTERNAL_STORAGE`
- `android.permission.MODIFY_AUDIO_SETTINGS`
- `android.permission.POST_NOTIFICATIONS`
- `android.permission.READ_EXTERNAL_STORAGE`
- `android.permission.READ_MEDIA_AUDIO`
- `android.permission.RECEIVE_BOOT_COMPLETED`
- `android.permission.RECORD_AUDIO`
- `android.permission.REQUEST_INSTALL_PACKAGES`
- `android.permission.SYSTEM_ALERT_WINDOW`
- `android.permission.VIBRATE`
- `android.permission.WAKE_LOCK`
- `android.permission.WRITE_EXTERNAL_STORAGE`
- `androidx.car.app.MEDIA_TEMPLATES`

## Room schema


`InternalDatabase` version 34 (17 entities)

- **song**: id, title, duration, thumbnailUrl, albumId, albumName, explicit, year, date, dateModified, liked, likedDate, totalPlayTime, inLibrary, dateDownload, isLocal, libraryAddToken, libraryRemoveToken, lyricsOffset, romanizeLyrics, isDownloaded, isUploaded, isVideo
- **artist**: id, name, thumbnailUrl, channelId, lastUpdateTime, bookmarkedAt, isLocal
- **album**: id, playlistId, title, year, thumbnailUrl, themeColor, songCount, duration, explicit, lastUpdateTime, bookmarkedAt, likedDate, inLibrary, description, isLocal, isUploaded
- **playlist**: id, name, browseId, createdAt, lastUpdateTime, isEditable, bookmarkedAt, remoteSongCount, playEndpointParams, thumbnailUrl, shuffleEndpointParams, radioEndpointParams, isLocal, isAutoSync
- **song_artist_map**: songId, artistId, position
- **song_album_map**: songId, albumId, index
- **album_artist_map**: albumId, artistId, order
- **playlist_song_map**: id, playlistId, songId, position, setVideoId
- **search_history**: id, query
- **format**: id, itag, mimeType, codecs, bitrate, sampleRate, contentLength, loudnessDb, perceptualLoudnessDb, playbackUrl
- **lyrics**: id, lyrics, provider, translatedLyrics, translationLanguage, translationMode
- **event**: id, songId, timestamp, playTime
- **related_song_map**: id, songId, relatedSongId
- **set_video_id**: videoId, setVideoId
- **playCount**: song, year, month, count
- **recognition_history**: id, trackId, title, artist, album, coverArtUrl, coverArtHqUrl, genre, releaseDate, label, shazamUrl, appleMusicUrl, spotifyUrl, isrc, youtubeVideoId, recognizedAt, liked
- **speed_dial_item**: id, secondaryId, title, subtitle, thumbnailUrl, type, explicit, createDate

## Navigation destinations (`Screens.kt`)

- Screens
- Home
- Search
- Library
- Settings
- NavigationTab
- DarkMode
- LyricsPosition
- OptionStats

## Background work

- `MainActivity.kt` — android
- `car/CarPlayback.kt` — binds
- `core/updater/downloadmanager/UpdateDownloadWorker.kt` — Context
- `link/phone/PhoneLinkService.kt` — Service
- `scrobble/ScrobbleSendWorker.kt` — Context
- `ui/tv/TvActivity.kt` — launched

## Built-in modules

- **modules/api**: no ModuleInfo found
- **modules/applemusic**: `Apple Music` (Id)
- **modules/bridge**: no ModuleInfo found
- **modules/jiosaavn**: `JioSaavn` (Id)
- **modules/monochrome**: `Monochrome` (Id)

## Providers (subprojects)

- **providers/applecanvas** — 1 files, 564 LOC
- **providers/artistvideo** — 2 files, 260 LOC
- **providers/betterlyrics** — 3 files, 494 LOC
- **providers/canvas** — 7 files, 718 LOC
- **providers/directlyrics** — 11 files, 580 LOC
- **providers/genius** — 1 files, 157 LOC
- **providers/innertube** — 106 files, 8409 LOC
- **providers/kugou** — 5 files, 250 LOC
- **providers/lastfm** — 1 files, 173 LOC
- **providers/lrclib** — 2 files, 422 LOC
- **providers/musixmatch** — 6 files, 754 LOC
- **providers/paxsenixlyrics** — 2 files, 488 LOC
- **providers/shazamkit** — 2 files, 707 LOC
- **providers/simpmusic** — 2 files, 193 LOC
- **providers/wikipedia** — 1 files, 211 LOC
- **providers/youlyplus** — 2 files, 240 LOC

## DataStore preference keys (constants)

- `TOGGLE_LIBRARY` (ACTION_TOGGLE_LIBRARY) — MediaSessionConstants.kt
- `TOGGLE_START_RADIO` (ACTION_TOGGLE_START_RADIO) — MediaSessionConstants.kt
- `TOGGLE_LIKE` (ACTION_TOGGLE_LIKE) — MediaSessionConstants.kt
- `TOGGLE_SHUFFLE` (ACTION_TOGGLE_SHUFFLE) — MediaSessionConstants.kt
- `TOGGLE_REPEAT_MODE` (ACTION_TOGGLE_REPEAT_MODE) — MediaSessionConstants.kt
- `SYSTEM_DEFAULT` (SYSTEM_DEFAULT) — PreferenceKeys.kt
- `AUTO` (CrossmixStyleAuto) — PreferenceKeys.kt
