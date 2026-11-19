// PlaylistBodyTest.kt
//
// The three pure pieces of a playlist push: the body, the comparison that proves a push, and the
// sentence a refusal is reported with.

package com.mochyy.mochymusic.applesync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaylistBodyTest {

    @Test
    fun `the body is the ordered library ids and nothing else`() {
        val body = PlaylistBody.of(listOf("i.abc123", "i.def456"))
        assertEquals(
            """{"data":[{"id":"i.abc123","type":"library-songs"},{"id":"i.def456","type":"library-songs"}]}""",
            body,
        )
    }

    @Test
    fun `the order sent is the order written, repeats included`() {
        val ids = listOf("i.b", "i.a", "i.b")
        val body = PlaylistBody.of(ids)
        // three entries, in that order, with the repeat kept: a playlist may hold a song twice
        assertEquals(3, body.split("\"library-songs\"").size - 1)
        assertTrue(body.indexOf("i.b") < body.indexOf("i.a"))
    }

    @Test
    fun `a read-back proves the push only when it is the same list in the same order`() {
        val sent = listOf("i.a", "i.b", "i.c")
        assertTrue(PlaylistBody.sameOrder(sent, listOf("i.a", "i.b", "i.c")))
        assertFalse(PlaylistBody.sameOrder(sent, listOf("i.c", "i.b", "i.a")))
        assertFalse(PlaylistBody.sameOrder(sent, listOf("i.a", "i.b")))
        assertFalse(PlaylistBody.sameOrder(sent, listOf("i.a", "i.b", "i.c", "i.d")))
        assertFalse(PlaylistBody.sameOrder(sent, emptyList()))
    }

    @Test
    fun `a failed answer becomes one line with Apple's own words`() {
        val said = PlaylistBody.sentence(500, "{\n  \"errors\": [\n    {\"title\": \"Unexpected\"}\n  ]\n}")
        assertTrue(said.startsWith("Apple answered 500: "))
        assertFalse("newlines are flattened", said.contains("\n"))
        assertTrue(said.contains("Unexpected"))
    }

    @Test
    fun `an empty answer still names the status`() {
        assertEquals("Apple answered 500", PlaylistBody.sentence(500, "   "))
        assertEquals("Apple answered -1", PlaylistBody.sentence(-1, ""))
    }

    @Test
    fun `a long answer is cut to something a toast can hold`() {
        val said = PlaylistBody.sentence(400, "x".repeat(500))
        assertTrue(said.length <= "Apple answered 400: ".length + 180)
    }

    @Test
    fun `the mismatch reason says which kind of mismatch it was`() {
        assertEquals("READ_BACK_FAILED", PlaylistBody.describeMismatch(listOf("i.a"), null))
        assertEquals("READ_BACK_COUNT 3->2", PlaylistBody.describeMismatch(listOf("i.a", "i.b", "i.c"), listOf("i.a", "i.b")))
        assertEquals("READ_BACK_ORDER", PlaylistBody.describeMismatch(listOf("i.a", "i.b"), listOf("i.b", "i.a")))
    }
}
