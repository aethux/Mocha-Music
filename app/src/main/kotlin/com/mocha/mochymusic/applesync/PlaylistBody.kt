// PlaylistBody.kt
//
// The three things a playlist push needs to say, kept pure so they can be tested without a device:
// the body Apple's replace-tracks call takes, whether Apple's list afterwards is the one that was
// sent, and Apple's own sentence when it says no.

package com.mocha.mochymusic.applesync

object PlaylistBody {

    /**
     * The ordered list, as `PUT …/tracks` wants it: library ids (`i.…`) and nothing else. A
     * catalogue id here is a 400, and a list that is not the whole playlist is a partial rewrite.
     */
    fun of(libraryIds: List<String>): String = libraryIds.joinToString(
        separator = ",",
        prefix = """{"data":[""",
        postfix = "]}",
    ) { """{"id":"$it","type":"library-songs"}""" }

    /**
     * Whether a read of the playlist is the list that was just sent - the same ids, the same order,
     * the same repeats. Apple answers 204 to a replacement it did not make, so this is the only
     * evidence a push worked.
     */
    fun sameOrder(sent: List<String>, readBack: List<String>): Boolean = sent == readBack

    /**
     * Apple's own words about a failed call, flattened to one short line: an audit row and a toast
     * both want a sentence, and a raw JSON error is neither.
     */
    fun sentence(status: Int, body: String): String {
        val said = body.replace(Regex("\\s+"), " ").trim().take(180).ifBlank { null }
        return if (said == null) "Apple answered $status" else "Apple answered $status: $said"
    }

    /** How far a read-back got, for a message that has to be honest about a partial result. */
    fun describeMismatch(sent: List<String>, readBack: List<String>?): String = when {
        readBack == null -> "READ_BACK_FAILED"
        readBack.size != sent.size -> "READ_BACK_COUNT ${sent.size}->${readBack.size}"
        else -> "READ_BACK_ORDER"
    }
}
