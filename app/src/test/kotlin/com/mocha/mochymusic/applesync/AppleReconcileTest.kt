// AppleReconcileTest.kt
//
// The rule that decides whether a song is still in the question Apple asks. It exists because the
// card kept saying "3 songs aren't on Apple Music" after all three had been matched by hand: a
// matched song is not an AM_ row, so the count had to stop reading ids alone.

package com.mocha.mochymusic.applesync

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppleReconcileTest {

    @Test
    fun `an apple row is never in question`() {
        assertFalse(AppleReconcile.stillInQuestion("AM_1440833098", emptySet(), emptySet()))
    }

    @Test
    fun `a local file is never in question`() {
        assertFalse(AppleReconcile.stillInQuestion("LOCAL_3f9c", emptySet(), emptySet()))
    }

    @Test
    fun `a plain youtube row is in question`() {
        assertTrue(AppleReconcile.stillInQuestion("dQw4w9WgXcQ", emptySet(), emptySet()))
    }

    @Test
    fun `a song matched by hand stops being in question`() {
        assertFalse(
            AppleReconcile.stillInQuestion(
                songId = "dQw4w9WgXcQ",
                decided = emptySet(),
                pinned = setOf("dQw4w9WgXcQ"),
            ),
        )
    }

    @Test
    fun `a song kept on youtube stops being in question`() {
        assertFalse(
            AppleReconcile.stillInQuestion(
                songId = "dQw4w9WgXcQ",
                decided = setOf("dQw4w9WgXcQ"),
                pinned = emptySet(),
            ),
        )
    }

    @Test
    fun `a pinned apple song is still excluded after a re-import makes it an AM row`() {
        assertFalse(AppleReconcile.stillInQuestion("AM_9", emptySet(), setOf("AM_9")))
    }
}
