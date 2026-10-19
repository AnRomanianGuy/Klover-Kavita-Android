package com.romanianguy.klover.data.session

import android.util.Base64
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SecretBoxTest {
    private val box = SecretBox("klover.test.key")

    @Test
    fun roundTripsTextIncludingUnicode() {
        val secret = "eyJhbGciOiJIUzI1NiJ9.token-ÄÖ-密钥"
        assertEquals(secret, box.decrypt(box.encrypt(secret)))
    }

    @Test
    fun ciphertextIsNotThePlaintextAndDiffersEveryTime() {
        val a = box.encrypt("same secret")
        val b = box.encrypt("same secret")
        assertNotEquals("same secret", a)
        assertNotEquals("a fresh IV per message", a, b)
    }

    @Test
    fun tamperedDataIsRejectedInsteadOfDecryptedToGarbage() {
        val raw = Base64.decode(box.encrypt("secret"), Base64.NO_WRAP)
        raw[raw.size - 1] = (raw[raw.size - 1].toInt() xor 1).toByte()
        assertNull(box.decrypt(Base64.encodeToString(raw, Base64.NO_WRAP)))
    }

    @Test
    fun garbageAndEmptyInputGiveNullNotACrash() {
        assertNull(box.decrypt(""))
        assertNull(box.decrypt("not base64 at all!!"))
        assertNull(box.decrypt(Base64.encodeToString(ByteArray(5), Base64.NO_WRAP)))
    }
}
