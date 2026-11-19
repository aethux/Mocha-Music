// AppleAccountEntity.kt

package com.mochyy.mochymusic.applesync.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * The Apple Music account this install syncs with, one row.
 *
 * The tokens are not here. They are sealed by SecretVault and kept in preferences, the way the
 * importer and the Apple source module keep theirs. This row holds only what is safe to read
 * anywhere: the storefront the account is in, what to call it, and the reason the pair was last
 * found expired.
 */
@Entity(tableName = "account")
data class AppleAccountEntity(
    @PrimaryKey val id: Int = 1,
    val storefront: String = "us",
    val label: String? = null,
    val signedInAt: Long = 0L,
    val expiredReason: String? = null,
)
