package com.romanianguy.klover.data.stats

import com.romanianguy.klover.data.session.SingleFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.time.LocalDate

class ReadingStatsFlushTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val today = LocalDate.of(2026, 10, 17)

    @Test
    fun whatWasCountedIsOnDiskAtOnceAfterAFlush() {
        val file = folder.newFile("stats.json")
        file.delete()
        // The writer that waits a moment before saving never gets to run in this test: only flush() can have written.
        val stats = ReadingStats(SingleFile(file), CoroutineScope(Job() + Dispatchers.Unconfined), today = { today })
        stats.addSeconds(90)
        stats.addPages(3)
        stats.flush()

        val reopened = ReadingStats(SingleFile(file), CoroutineScope(Job() + Dispatchers.Unconfined), today = { today })
        assertEquals(DayStat(seconds = 90, pages = 3), reopened.days.value[today.toString()])
    }
}
