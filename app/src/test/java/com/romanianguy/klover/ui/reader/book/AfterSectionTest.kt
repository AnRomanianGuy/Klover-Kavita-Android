package com.romanianguy.klover.ui.reader.book

import org.junit.Assert.assertEquals
import org.junit.Test

class AfterSectionTest {
    @Test
    fun aVoiceThatIsNotOnDoesNothing() {
        assertEquals(AfterSection.Idle, afterSection(active = false, sleepAtEnd = false, autoNext = true, section = 2, sections = 10))
    }

    @Test
    fun itGoesOnToTheNextSectionWhenAsked() {
        assertEquals(AfterSection.Advance, afterSection(active = true, sleepAtEnd = false, autoNext = true, section = 2, sections = 10))
        assertEquals(AfterSection.Advance, afterSection(active = true, sleepAtEnd = false, autoNext = true, section = 0, sections = 2))
    }

    @Test
    fun itStopsAtTheEndOfTheBook() {
        assertEquals(AfterSection.Stop, afterSection(active = true, sleepAtEnd = false, autoNext = true, section = 9, sections = 10))
        assertEquals(AfterSection.Stop, afterSection(active = true, sleepAtEnd = false, autoNext = true, section = 0, sections = 1))
    }

    @Test
    fun itStopsAfterTheSectionWithoutAutoNextOrWithTheSleepTimerAtTheEnd() {
        assertEquals(AfterSection.Stop, afterSection(active = true, sleepAtEnd = false, autoNext = false, section = 2, sections = 10))
        assertEquals(AfterSection.Stop, afterSection(active = true, sleepAtEnd = true, autoNext = true, section = 2, sections = 10))
    }

    @Test
    fun aVoiceWithoutTextStopsInsteadOfWanderingOn() {
        assertEquals(AfterSection.Stop, afterSection(active = true, sleepAtEnd = false, autoNext = true, section = -1, sections = 10))
    }
}
