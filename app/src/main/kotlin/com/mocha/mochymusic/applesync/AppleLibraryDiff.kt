// AppleLibraryDiff.kt

package com.mocha.mochymusic.applesync

/**
 * What changed between the snapshot taken before the app ever wrote anything (the epoch) and what
 * the account holds now.
 *
 * Pure set arithmetic over "kind:id" keys. [trusted] is the question the audit exists to answer: did
 * anything that was there at the start go missing?
 */
object AppleLibraryDiff {

    data class Result(
        val added: List<String>,
        val removed: List<String>,
        val common: Int,
    ) {
        val trusted: Boolean get() = removed.isEmpty()
    }

    fun compare(epoch: Set<String>, current: Set<String>): Result {
        val added = (current - epoch).sorted()
        val removed = (epoch - current).sorted()
        return Result(added = added, removed = removed, common = (epoch intersect current).size)
    }
}
