package com.romanianguy.klover.data.backup

import com.romanianguy.klover.data.profiles.PROFILE_SHAPES
import com.romanianguy.klover.data.profiles.PROFILE_TONES
import com.romanianguy.klover.data.profiles.ProfileStore
import kotlinx.serialization.Serializable
import java.io.File
import java.util.Base64

// how a profile looks: its name, ring colour and shape, and the picture (a small JPEG, as base64)
@Serializable
data class ProfileBackup(
    val name: String = "",
    val tone: Int = 0,
    val shape: Int = 0,
    val photo: String? = null,
)

// a photo is a few tens of kB; anything much bigger is not one of ours
private const val MAX_PHOTO_BYTES = 2 * 1024 * 1024

internal fun ProfileStore.backupOf(key: String): ProfileBackup? {
    val profile = profiles.value.firstOrNull { it.key == key } ?: return null
    val photo = profile.photo
        ?.let { File(photoDir, it) }
        ?.takeIf { it.isFile && it.length() in 1..MAX_PHOTO_BYTES }
        ?.let { runCatching { Base64.getEncoder().encodeToString(it.readBytes()) }.getOrNull() }
    return ProfileBackup(profile.name, profile.tone, profile.shape, photo)
}

// puts the backed-up look on the profile [key]; a backup without a picture takes the picture off
internal fun ProfileStore.restore(key: String, backup: ProfileBackup, now: Long = System.currentTimeMillis()) {
    if (profiles.value.none { it.key == key }) return
    rename(key, backup.name)
    setTone(key, backup.tone.coerceIn(0, PROFILE_TONES - 1))
    setShape(key, backup.shape.coerceIn(0, PROFILE_SHAPES - 1))
    val bytes = backup.photo?.let { runCatching { Base64.getDecoder().decode(it) }.getOrNull() }
        ?.takeIf { it.size in 1..MAX_PHOTO_BYTES }
    if (bytes == null) {
        setPhoto(key, null)
        return
    }
    // a new name each time, so a changed picture is not shown from the cache of the old one
    val name = "${key.take(16)}-$now.jpg"
    photoDir.mkdirs()
    File(photoDir, name).writeBytes(bytes)
    setPhoto(key, name)
}
