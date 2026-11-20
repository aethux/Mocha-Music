package com.mocha.mochymusic.applesync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The guard is the promise that the user's existing Apple Music library is never touched. Each test
 * here is one way that promise could be broken, and the refusal that stops it.
 */
class AppleGuardTest {

    private val playlist = "local-playlist-uuid"
    private val remotePlaylist = AppleRef(AppleKind.PLAYLIST, "p.ABC123")
    private val remoteSong = AppleRef(AppleKind.LIBRARY_SONG, "i.XYZ789")
    private val catalogSong = AppleRef(AppleKind.CATALOG_SONG, "1440857781")

    private fun context(
        writesEnabled: Boolean = true,
        epoch: Set<AppleRef> = emptySet(),
        owned: Set<AppleRef> = emptySet(),
        linked: Set<String> = setOf(playlist),
    ) = AppleGuardContext(writesEnabled, epoch, owned, linked)

    private fun op(
        write: AppleWrite,
        playlistId: String? = playlist,
        name: String? = "Road trip",
        editable: Boolean = true,
        targets: List<AppleRef> = emptyList(),
        confirmed: Boolean = false,
    ) = AppleOp(write, playlistId, name, editable, targets, confirmed)

    private fun refusal(decision: AppleDecision): AppleRefusal? =
        (decision as? AppleDecision.Refused)?.reason

    @Test
    fun writesDisabledRefusesEverything() {
        val decision = AppleGuard.decide(
            op(AppleWrite.ADD_TRACKS, targets = listOf(catalogSong)),
            context(writesEnabled = false),
        )
        assertEquals(AppleRefusal.WRITES_DISABLED, refusal(decision))
    }

    @Test
    fun unlinkedPlaylistIsRefused() {
        val decision = AppleGuard.decide(
            op(AppleWrite.ADD_TRACKS, targets = listOf(catalogSong)),
            context(linked = emptySet()),
        )
        assertEquals(AppleRefusal.UNLINKED_PLAYLIST, refusal(decision))
    }

    @Test
    fun autoManagedPlaylistsAreRefusedByName() {
        for (name in listOf("Favorite Songs", "favourite songs", "Loved Songs")) {
            val decision = AppleGuard.decide(
                op(AppleWrite.ADD_TRACKS, name = name, targets = listOf(catalogSong)),
                context(owned = setOf(remotePlaylist)),
            )
            assertEquals(name, AppleRefusal.AUTO_MANAGED_PLAYLIST, refusal(decision))
        }
    }

    @Test
    fun playlistAppleWillNotLetUsEditIsRefused() {
        val decision = AppleGuard.decide(
            op(AppleWrite.ADD_TRACKS, editable = false, targets = listOf(catalogSong)),
            context(),
        )
        assertEquals(AppleRefusal.PLAYLIST_NOT_EDITABLE, refusal(decision))
    }

    @Test
    fun replacingWithNothingIsRefused() {
        // the PUT that removes tracks is also the one that empties a playlist when data is missing
        val decision = AppleGuard.decide(
            op(AppleWrite.REPLACE_TRACKS, targets = emptyList()),
            context(),
        )
        assertEquals(AppleRefusal.EMPTY_REPLACEMENT, refusal(decision))
    }

    @Test
    fun addingTracksToALinkedPlaylistIsAllowed() {
        val decision = AppleGuard.decide(
            op(AppleWrite.ADD_TRACKS, targets = listOf(catalogSong)),
            context(),
        )
        assertEquals(AppleDecision.Allowed, decision)
    }

    @Test
    fun replacingWithATrackListIsAllowed() {
        val decision = AppleGuard.decide(
            op(AppleWrite.REPLACE_TRACKS, targets = listOf(remoteSong)),
            context(),
        )
        assertEquals(AppleDecision.Allowed, decision)
    }

    @Test
    fun creatingAPlaylistNeedsNoOwnership() {
        val decision = AppleGuard.decide(
            op(AppleWrite.CREATE_PLAYLIST, playlistId = null, name = "New list"),
            context(),
        )
        assertEquals(AppleDecision.Allowed, decision)
    }

    @Test
    fun addingToTheLibraryNeedsNoOwnership() {
        val decision = AppleGuard.decide(
            op(AppleWrite.ADD_TO_LIBRARY, playlistId = null, targets = listOf(catalogSong)),
            context(),
        )
        assertEquals(AppleDecision.Allowed, decision)
    }

    @Test
    fun deletingAPlaylistThisAppCreatedIsAllowed() {
        val decision = AppleGuard.decide(
            op(AppleWrite.DELETE_PLAYLIST, targets = listOf(remotePlaylist)),
            context(owned = setOf(remotePlaylist)),
        )
        assertEquals(AppleDecision.Allowed, decision)
    }

    @Test
    fun deletingAPlaylistThisAppDidNotCreateIsRefused() {
        val decision = AppleGuard.decide(
            op(AppleWrite.DELETE_PLAYLIST, targets = listOf(remotePlaylist)),
            context(),
        )
        assertEquals(AppleRefusal.NOT_OWNED, refusal(decision))
    }

    @Test
    fun deletingSomethingThatPredatesTheAppIsRefusedEvenWhenMarkedOwned() {
        // the epoch wins over ownership: a bug that records a pre-existing id as ours must still lose
        val decision = AppleGuard.decide(
            op(AppleWrite.DELETE_PLAYLIST, targets = listOf(remotePlaylist)),
            context(epoch = setOf(remotePlaylist), owned = setOf(remotePlaylist)),
        )
        assertEquals(AppleRefusal.PRE_EXISTING_OBJECT, refusal(decision))
    }

    @Test
    fun removingALibrarySongThisAppDidNotAddIsRefused() {
        val decision = AppleGuard.decide(
            op(AppleWrite.REMOVE_FROM_LIBRARY, playlistId = null, targets = listOf(remoteSong)),
            context(),
        )
        assertEquals(AppleRefusal.NOT_OWNED, refusal(decision))
    }

    @Test
    fun removingALibrarySongThisAppAddedIsAllowed() {
        val decision = AppleGuard.decide(
            op(AppleWrite.REMOVE_FROM_LIBRARY, playlistId = null, targets = listOf(remoteSong)),
            context(owned = setOf(remoteSong)),
        )
        assertEquals(AppleDecision.Allowed, decision)
    }

    @Test
    fun removingAPreExistingLibrarySongAsksFirst() {
        // the button has to work for the person who owns the library, but never silently
        val decision = AppleGuard.decide(
            op(AppleWrite.REMOVE_FROM_LIBRARY, playlistId = null, targets = listOf(remoteSong)),
            context(epoch = setOf(remoteSong)),
        )
        assertEquals(AppleRefusal.PRE_EXISTING_OBJECT, refusal(decision))
    }

    @Test
    fun removingAPreExistingLibrarySongAfterAYesIsAllowed() {
        val decision = AppleGuard.decide(
            AppleOp(
                write = AppleWrite.REMOVE_FROM_LIBRARY,
                targets = listOf(remoteSong),
                confirmed = true,
            ),
            context(epoch = setOf(remoteSong)),
        )
        assertEquals(AppleDecision.Allowed, decision)
    }

    @Test
    fun aSongOnlyAppleEverAddedStillAsksBeforeItGoes() {
        // added to the library in the Apple app after the epoch was taken: not ours, not recorded
        val decision = AppleGuard.decide(
            AppleOp(write = AppleWrite.REMOVE_FROM_LIBRARY, targets = listOf(remoteSong)),
            context(epoch = setOf(AppleRef(AppleKind.LIBRARY_SONG, "i.SOMETHING_ELSE"))),
        )
        assertEquals(AppleRefusal.NOT_OWNED, refusal(decision))
    }

    @Test
    fun aYesRemovesASongOnlyAppleEverAdded() {
        val decision = AppleGuard.decide(
            AppleOp(write = AppleWrite.REMOVE_FROM_LIBRARY, targets = listOf(remoteSong), confirmed = true),
            context(epoch = setOf(AppleRef(AppleKind.LIBRARY_SONG, "i.SOMETHING_ELSE"))),
        )
        assertEquals(AppleDecision.Allowed, decision)
    }

    @Test
    fun aYesIsNotAPlaylistDeletion() {
        val decision = AppleGuard.decide(
            op(AppleWrite.DELETE_PLAYLIST, targets = listOf(remotePlaylist), confirmed = true),
            context(epoch = setOf(remotePlaylist), owned = setOf(remotePlaylist)),
        )
        assertEquals(AppleRefusal.PRE_EXISTING_OBJECT, refusal(decision))
    }

    @Test
    fun renamingAPreExistingPlaylistIsRefused() {
        val decision = AppleGuard.decide(
            op(AppleWrite.RENAME_PLAYLIST, targets = listOf(remotePlaylist)),
            context(epoch = setOf(remotePlaylist), owned = setOf(remotePlaylist)),
        )
        assertEquals(AppleRefusal.PRE_EXISTING_OBJECT, refusal(decision))
    }

    @Test
    fun refusalsCarryAReason() {
        val decision = AppleGuard.decide(
            op(AppleWrite.DELETE_PLAYLIST, targets = listOf(remotePlaylist)),
            context(),
        )
        assertTrue(decision is AppleDecision.Refused)
        assertEquals(AppleRefusal.NOT_OWNED, refusal(decision))
    }

    @Test
    fun aHeartOnASongThatWasAlreadyInTheLibraryIsAllowed() {
        // the library's own songs have to be likeable: a rating removes nothing and cascades nowhere,
        // so the epoch protects the library's contents, not the user's taste
        for (write in listOf(AppleWrite.SET_RATING, AppleWrite.CLEAR_RATING)) {
            val decision = AppleGuard.decide(
                op(write, playlistId = null, targets = listOf(remoteSong)),
                context(epoch = setOf(remoteSong)),
            )
            assertEquals(AppleDecision.Allowed, decision)
        }
    }

    @Test
    fun aHeartStillNeedsTheWritesSwitch() {
        val decision = AppleGuard.decide(
            op(AppleWrite.SET_RATING, playlistId = null, targets = listOf(remoteSong)),
            context(writesEnabled = false, epoch = setOf(remoteSong)),
        )
        assertEquals(AppleRefusal.WRITES_DISABLED, refusal(decision))
    }

    @Test
    fun addingASongToTheLibraryIsNotBlockedByTheEpoch() {
        val decision = AppleGuard.decide(
            op(AppleWrite.ADD_TO_LIBRARY, playlistId = null, targets = listOf(catalogSong)),
            context(epoch = setOf(remoteSong)),
        )
        assertEquals(AppleDecision.Allowed, decision)
    }
}
