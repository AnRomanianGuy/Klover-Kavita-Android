package com.romanianguy.klover.data.chapters

import org.junit.Assert.assertEquals
import org.junit.Test

class NumberedTitleTest {
    @Test
    fun aPlainNumberGetsTheLabel() {
        assertEquals("Volume 3", numberedTitle("Volume", "3"))
        assertEquals("Chapter 14.5", numberedTitle("Chapter", " 14.5 "))
    }

    @Test
    fun aNameThatAlreadyHasTheWordIsNotDoubled() {
        assertEquals("Volume 3", numberedTitle("Volume", "Volume 3"))
        assertEquals("Volume 3", numberedTitle("Volume", "volume 3"))
        assertEquals("Volume 3 - The End", numberedTitle("Volume", "Volume 3 - The End"))
        assertEquals("Chapter 7", numberedTitle("Chapter", "Chapter 7"))
    }

    @Test
    fun otherShortFormsBecomeTheLabel() {
        assertEquals("Volume 3", numberedTitle("Volume", "Vol. 3"))
        assertEquals("Volume 03", numberedTitle("Volume", "v03"))
        assertEquals("Volume 12", numberedTitle("Volume", "Vol12"))
        assertEquals("Chapter 5", numberedTitle("Chapter", "Ch. 5"))
    }

    @Test
    fun aNameThatIsNotANumberKeepsItsOwnWords() {
        assertEquals("Volume Vampire Hunters", numberedTitle("Volume", "Vampire Hunters"))
        assertEquals("Volume Cover 1", numberedTitle("Volume", "Cover 1"))
        assertEquals("Volume Volumes of Dusk", numberedTitle("Volume", "Volumes of Dusk"))
    }

    @Test
    fun nothingToAppendIsJustTheLabel() {
        assertEquals("Volume", numberedTitle("Volume", null))
        assertEquals("Volume", numberedTitle("Volume", "  "))
    }
}
