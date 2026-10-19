package com.romanianguy.klover.data.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ServerUrlsTest {
    @Test
    fun explicitSchemeIsRespectedAndTrailingSlashDropped() {
        assertEquals(listOf("https://kavita.example.com"), candidateServerUrls("https://kavita.example.com/"))
        assertEquals(listOf("http://192.168.1.20:5000"), candidateServerUrls(" http://192.168.1.20:5000 "))
    }

    @Test
    fun subPathIsKept() {
        assertEquals(listOf("https://example.com/kavita"), candidateServerUrls("https://example.com/kavita/"))
    }

    @Test
    fun publicHostTriesHttpsThenHttp() {
        assertEquals(
            listOf("https://kavita.example.com", "http://kavita.example.com"),
            candidateServerUrls("kavita.example.com"),
        )
    }

    @Test
    fun lanHostTriesHttpFirst() {
        assertEquals(
            listOf("http://192.168.1.20:5000", "https://192.168.1.20:5000"),
            candidateServerUrls("192.168.1.20:5000"),
        )
        assertEquals("http://10.0.2.2:5055", candidateServerUrls("10.0.2.2:5055").first())
        assertEquals("http://nas.local", candidateServerUrls("nas.local").first())
        assertEquals("http://nas", candidateServerUrls("nas").first())
    }

    @Test
    fun blankInputGivesNoCandidates() {
        assertTrue(candidateServerUrls("   ").isEmpty())
    }

    @Test
    fun localHostDetection() {
        assertTrue(isLikelyLocalHost("172.16.0.5"))
        assertTrue(isLikelyLocalHost("172.31.255.1:8080"))
        assertFalse(isLikelyLocalHost("172.32.0.1"))
        assertFalse(isLikelyLocalHost("8.8.8.8"))
        assertFalse(isLikelyLocalHost("kavita.example.com"))
    }
}
