// AppleGuard.kt

package com.mochyy.mochymusic.applesync

/**
 * The rule that keeps this fork from touching the user's existing library.
 *
 * Every write is described as an [AppleOp] and put to [AppleGuard.decide] before a request is built.
 * The guard is pure: it takes an [AppleGuardContext] and answers Allowed or Refused(reason), so the
 * same rules run in a JVM test, in the app, and in any debug tool that wants to print the decision
 * table.
 *
 * The promise has two halves:
 *  - an object that existed before this app wrote anything (the epoch) is never modified;
 *  - an object this app did not create is never deleted.
 *
 * A refusal is not an error to retry. It means the caller asked for something the app must not do,
 * and it is meant to be shown to the user and logged.
 */
enum class AppleKind {
    LIBRARY_SONG,
    PLAYLIST,
    CATALOG_SONG,
}

data class AppleRef(val kind: AppleKind, val id: String)

/** The writes the app is allowed to describe. Reads have no op: they are always allowed. */
enum class AppleWrite {
    CREATE_PLAYLIST,
    ADD_TRACKS,
    REPLACE_TRACKS,
    RENAME_PLAYLIST,
    DELETE_PLAYLIST,
    ADD_TO_LIBRARY,
    REMOVE_FROM_LIBRARY,
    SET_RATING,
    CLEAR_RATING,
}

data class AppleOp(
    val write: AppleWrite,
    /** the local playlist id, for every playlist-scoped write */
    val playlistId: String? = null,
    val playlistName: String? = null,
    /** Apple's canEdit, false for playlists it will not let us change */
    val playlistEditable: Boolean = true,
    /** the remote objects this op acts on: a playlist id to delete, a library id to remove */
    val targets: List<AppleRef> = emptyList(),
    /**
     * A person said yes to this one, after being told what it does. Only a removal of something
     * already in their library can use it: the epoch still refuses deletes, renames and playlist
     * rewrites, and an id the app has never seen stays refused whatever is confirmed.
     */
    val confirmed: Boolean = false,
)

data class AppleGuardContext(
    /** the user's "allow Mochy to change my Apple Music" setting; false until they say otherwise */
    val writesEnabled: Boolean,
    val epoch: Set<AppleRef>,
    val owned: Set<AppleRef>,
    val linkedPlaylists: Set<String>,
)

sealed interface AppleDecision {
    data object Allowed : AppleDecision

    data class Refused(val reason: AppleRefusal) : AppleDecision
}

enum class AppleRefusal {
    WRITES_DISABLED,
    AUTO_MANAGED_PLAYLIST,
    UNLINKED_PLAYLIST,
    PLAYLIST_NOT_EDITABLE,
    EMPTY_REPLACEMENT,
    PRE_EXISTING_OBJECT,
    NOT_OWNED,
}

object AppleGuard {

    /**
     * Playlists Apple maintains itself. A write to one of these loops or loses data, so they are
     * refused by name before anything else is considered.
     */
    const val SandboxPrefix = "Mochy sandbox"
    val AutoManagedNames = setOf("favorite songs", "favourite songs", "loved songs")

    /**
     * Writes that change or destroy an object rather than referencing it.
     *
     * A rating is deliberately not here. It is the user's own mark on a song: it removes nothing
     * from the library, cannot cascade into a playlist, and can be cleared as easily as set. The
     * epoch is a promise about what the library holds, so a heart on a song that was already there
     * is allowed - otherwise every one of the account's own songs would have a dead heart.
     */
    private val Mutating = setOf(
        AppleWrite.DELETE_PLAYLIST,
        AppleWrite.RENAME_PLAYLIST,
        AppleWrite.REMOVE_FROM_LIBRARY,
    )

    fun decide(op: AppleOp, context: AppleGuardContext): AppleDecision {
        if (!context.writesEnabled) return AppleDecision.Refused(AppleRefusal.WRITES_DISABLED)

        if (op.playlistId != null) {
            val name = op.playlistName?.trim()?.lowercase()
            if (name != null && name in AutoManagedNames) {
                return AppleDecision.Refused(AppleRefusal.AUTO_MANAGED_PLAYLIST)
            }
            if (op.playlistId !in context.linkedPlaylists) {
                return AppleDecision.Refused(AppleRefusal.UNLINKED_PLAYLIST)
            }
            if (!op.playlistEditable) {
                return AppleDecision.Refused(AppleRefusal.PLAYLIST_NOT_EDITABLE)
            }
        }

        // a replace sends the whole list; an empty one is the call that wipes a playlist
        if (op.write == AppleWrite.REPLACE_TRACKS && op.targets.isEmpty()) {
            return AppleDecision.Refused(AppleRefusal.EMPTY_REPLACEMENT)
        }

        // a sandbox playlist this app made in an earlier run may not have reached the owned table
        // before that run died; its name is ours and the epoch proves it did not exist before
        val recoverableSandbox = op.write == AppleWrite.DELETE_PLAYLIST &&
            op.playlistName?.startsWith(SandboxPrefix) == true

        if (op.write in Mutating) {
            for (target in op.targets) {
                // the one exception, and only for the user's own tap: Mochy never removes a
                // pre-existing song by itself, but it must not refuse the person who owns it -
                // that goes for a song that predates the sync and for one only Apple ever added
                val removalConfirmed = op.confirmed && op.write == AppleWrite.REMOVE_FROM_LIBRARY
                if (target in context.epoch) {
                    if (!removalConfirmed) return AppleDecision.Refused(AppleRefusal.PRE_EXISTING_OBJECT)
                    continue
                }
                if (target !in context.owned && !recoverableSandbox && !removalConfirmed) {
                    return AppleDecision.Refused(AppleRefusal.NOT_OWNED)
                }
            }
        }

        return AppleDecision.Allowed
    }
}
