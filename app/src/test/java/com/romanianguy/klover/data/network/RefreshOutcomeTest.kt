package com.romanianguy.klover.data.network

import com.romanianguy.klover.data.model.UserDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RefreshOutcomeTest {
    private val user = UserDto(token = "new", refreshToken = "r")

    @Test
    fun theFirstTryThatRenewsWinsAndNothingAfterItIsAsked() {
        var asked = 0
        val tries = sequence {
            asked++; yield(RefreshAttempt.Refused)
            asked++; yield(RefreshAttempt.Renewed(user))
            asked++; yield(RefreshAttempt.Failed)
        }
        assertEquals(RefreshOutcome.Renewed(user), resolveRefresh(tries))
        assertEquals(2, asked)
    }

    @Test
    fun theSessionEndsOnlyWhenTheServerRefusedEveryTime() {
        assertEquals(
            RefreshOutcome.Rejected,
            resolveRefresh(sequenceOf(RefreshAttempt.Refused, RefreshAttempt.Refused)),
        )
    }

    @Test
    fun aDroppedConnectionIsNotAReasonToSignOut() {
        assertEquals(RefreshOutcome.Unavailable, resolveRefresh(sequenceOf(RefreshAttempt.Failed)))
        // One refusal and one try that got no answer: not known to be over.
        assertEquals(
            RefreshOutcome.Unavailable,
            resolveRefresh(sequenceOf(RefreshAttempt.Refused, RefreshAttempt.Failed)),
        )
        assertEquals(
            RefreshOutcome.Unavailable,
            resolveRefresh(sequenceOf(RefreshAttempt.Failed, RefreshAttempt.Refused)),
        )
    }

    @Test
    fun withNothingToRenewWithTheSessionIsOver() {
        assertTrue(resolveRefresh(emptySequence()) is RefreshOutcome.Rejected)
    }
}
