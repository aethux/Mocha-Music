package com.mocha.mochymusic.applesync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppleLibraryDiffTest {

    private val epoch = setOf(
        "PLAYLIST:p.ONE",
        "PLAYLIST:p.TWO",
        "LIBRARY_SONG:i.A",
        "LIBRARY_SONG:i.B",
    )

    @Test
    fun anUnchangedLibraryIsTrusted() {
        val result = AppleLibraryDiff.compare(epoch, epoch)
        assertTrue(result.added.isEmpty())
        assertTrue(result.removed.isEmpty())
        assertEquals(4, result.common)
        assertTrue(result.trusted)
    }

    @Test
    fun addedObjectsAreReportedButDoNotBreakTrust() {
        val current = epoch + "PLAYLIST:p.NEW"
        val result = AppleLibraryDiff.compare(epoch, current)
        assertEquals(listOf("PLAYLIST:p.NEW"), result.added)
        assertTrue(result.removed.isEmpty())
        assertTrue("adding is allowed, removing is not", result.trusted)
    }

    @Test
    fun aMissingPreExistingObjectBreaksTrust() {
        val current = epoch - "LIBRARY_SONG:i.B"
        val result = AppleLibraryDiff.compare(epoch, current)
        assertEquals(listOf("LIBRARY_SONG:i.B"), result.removed)
        assertFalse(result.trusted)
    }

    @Test
    fun additionsAndRemovalsTogetherAreBothReported() {
        val current = (epoch - "PLAYLIST:p.TWO") + "LIBRARY_SONG:i.C"
        val result = AppleLibraryDiff.compare(epoch, current)
        assertEquals(listOf("LIBRARY_SONG:i.C"), result.added)
        assertEquals(listOf("PLAYLIST:p.TWO"), result.removed)
        assertEquals(3, result.common)
        assertFalse(result.trusted)
    }

    @Test
    fun theFirstRunComparesAgainstItself() {
        // epoch is empty only before it is written; the audit writes it and then compares equal sets
        val current = setOf("PLAYLIST:p.ONE", "LIBRARY_SONG:i.A")
        val result = AppleLibraryDiff.compare(emptySet(), current)
        assertEquals(current.sorted(), result.added)
        assertTrue(result.removed.isEmpty())
    }
}
