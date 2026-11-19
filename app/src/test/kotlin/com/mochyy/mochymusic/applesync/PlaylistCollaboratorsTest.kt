package com.mochyy.mochymusic.applesync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The page is HTML Apple changes when it likes, so the parser's job is to keep people and drop page
 * chrome - never to invent a name.
 */
class PlaylistCollaboratorsTest {

    @Test
    fun keepsThePeopleAndDropsTheChrome() {
        val raw = """
            {"href":"https://music.apple.com/library/playlist/x",
             "title":"Am I God? - Playlist",
             "names":["Faizaan","Disha Singh","My Account","Music controls","Open navigation"],
             "handles":["vrsfyd"],
             "evidence":["credit: Faizaan & 1 other"]}
        """.trimIndent()
        val read = PlaylistCollaborators.parse(raw)
        assertTrue("the second person is kept", read.names.contains("Disha Singh"))
        assertTrue("the owner is kept", read.names.contains("Faizaan"))
        assertTrue("chrome is dropped", read.names.none { it == "My Account" || it == "Music controls" })
        assertEquals(listOf("credit: Faizaan & 1 other"), read.evidence)
    }

    @Test
    fun aHandleIsShownAsAHandle() {
        val read = PlaylistCollaborators.parse("""{"names":[],"handles":["vrsfyd"],"evidence":[]}""")
        assertEquals(listOf("@vrsfyd"), read.names)
    }

    @Test
    fun nothingReadIsNotAnError() {
        assertEquals(0, PlaylistCollaborators.parse(null).names.size)
        assertEquals(0, PlaylistCollaborators.parse("not json").names.size)
    }
}
