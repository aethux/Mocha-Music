// AppleAuditEntity.kt

package com.mocha.mochymusic.applesync.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Every decision the guard made and every write that followed, append-only.
 *
 * [decision] is ALLOW or REFUSE and [reason] is the refusal. [verified] is the answer to "did a read
 * afterwards show it happened", which is the only evidence Apple's 204s are worth.
 */
@Entity(tableName = "audit")
data class AppleAuditEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val at: Long,
    val op: String,
    val target: String? = null,
    val decision: String,
    val reason: String? = null,
    val httpStatus: Int? = null,
    val verified: Boolean? = null,
)
