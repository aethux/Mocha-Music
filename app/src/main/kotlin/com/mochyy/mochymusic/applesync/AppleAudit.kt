// AppleAudit.kt

package com.mochyy.mochymusic.applesync

import android.content.Context
import com.mochyy.mochymusic.applesync.db.AppleAccountEntity
import com.mochyy.mochymusic.applesync.db.AppleAuditEntity
import com.mochyy.mochymusic.applesync.db.AppleEpochEntity
import com.mochyy.mochymusic.applesync.db.AppleSyncDao
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The read-only pass that takes the epoch and then checks the account against it.
 *
 * The first run records every library song and playlist the account holds into [AppleEpochEntity] and
 * says so in the audit log. Every later run compares, and reports anything that was there at the
 * start and is now gone. Nothing here writes to Apple; the only side effects are local rows.
 *
 * This is also the only code in the fork that has ever touched the account, which is why it lives
 * behind a screen that says "read only" and has no button for anything else.
 */
@Singleton
class AppleAudit @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dao: AppleSyncDao,
) {

    suspend fun run(): AppleAuditReport {
        val tokens = AppleTokens(context).read()
            ?: throw AppleSyncException("Sign in to Apple Music first.", signedOut = true)
        val client = AppleClient(tokens)

        val storefront = client.storefront()
        dao.saveAccount(
            AppleAccountEntity(
                storefront = storefront,
                signedInAt = System.currentTimeMillis(),
            ),
        )

        val playlists = client.playlists()
        val songs = client.librarySongs()
        val current = buildSet {
            playlists.forEach { add(AppleRef(AppleKind.PLAYLIST, it.id).key()) }
            songs.forEach { add(AppleRef(AppleKind.LIBRARY_SONG, it.libraryId).key()) }
        }

        val stored = dao.epoch()
        val epoch = if (stored.isEmpty()) {
            val now = System.currentTimeMillis()
            current.mapNotNull(::appleRefFromKey).forEach { ref ->
                dao.addToEpoch(listOf(AppleEpochEntity(ref.kind.name, ref.id, now)))
            }
            current
        } else {
            stored.map { AppleRef(AppleKind.valueOf(it.kind), it.remoteId).key() }.toSet()
        }

        val diff = AppleLibraryDiff.compare(epoch, current)
        dao.audit(
            AppleAuditEntity(
                at = System.currentTimeMillis(),
                op = if (stored.isEmpty()) "AUDIT_FIRST_RUN" else "AUDIT",
                target = "library",
                decision = "ALLOW",
                httpStatus = 200,
                verified = diff.trusted,
            ),
        )

        return AppleAuditReport(
            storefront = storefront,
            playlists = playlists.size,
            songs = songs.size,
            epochSize = epoch.size,
            addedSinceEpoch = diff.added,
            removedSinceEpoch = diff.removed,
            trusted = diff.trusted,
        )
    }
}
