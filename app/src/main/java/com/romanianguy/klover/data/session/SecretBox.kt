package com.romanianguy.klover.data.session

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.GeneralSecurityException
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

// AES-256-GCM with a Keystore key, stored as base64(iv || ciphertext).
// decrypt returns null instead of throwing (a backup restored on another phone has no key); callers treat that as
// signed out
class SecretBox(private val alias: String = DEFAULT_ALIAS) {
    fun encrypt(plain: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val sealed = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(cipher.iv + sealed, Base64.NO_WRAP)
    }

    fun decrypt(encoded: String): String? = try {
        val raw = Base64.decode(encoded, Base64.NO_WRAP)
        if (raw.size <= IV_BYTES) {
            null
        } else {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(TAG_BITS, raw, 0, IV_BYTES))
            String(cipher.doFinal(raw, IV_BYTES, raw.size - IV_BYTES), Charsets.UTF_8)
        }
    } catch (_: GeneralSecurityException) {
        null // wrong key, corrupted or tampered data
    } catch (_: IllegalArgumentException) {
        null // not valid base64
    } catch (_: RuntimeException) {
        // Some devices' keystores throw a plain runtime exception (a ProviderException) for a key that has gone bad.
        // Reading the stored login must never take the app down: it just means signed out.
        null
    }

    private fun key(): SecretKey {
        val store = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (store.getKey(alias, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val DEFAULT_ALIAS = "klover.session.v1"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_BYTES = 12
        const val TAG_BITS = 128
    }
}
