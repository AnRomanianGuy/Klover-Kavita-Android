package com.romanianguy.klover.data.downloads

import com.romanianguy.klover.data.network.KavitaJson
import kotlinx.serialization.builtins.ListSerializer
import java.io.File

// written to a temp file and renamed, so a crash can't leave a half-written index
class DownloadStore(private val file: File) {
    private val serializer = ListSerializer(DownloadEntry.serializer())

    fun load(): List<DownloadEntry> = runCatching {
        if (file.isFile) KavitaJson.decodeFromString(serializer, file.readText()) else emptyList()
    }.getOrDefault(emptyList())

    // an unreadable index makes load answer "nothing", which anything that deletes unknown files must not take for
    // "nothing is downloaded"
    fun isReadable(): Boolean = runCatching {
        !file.isFile || KavitaJson.decodeFromString(serializer, file.readText()).let { true }
    }.getOrDefault(false)

    fun save(entries: List<DownloadEntry>) {
        file.parentFile?.mkdirs()
        val temp = File(file.path + ".tmp")
        temp.writeText(KavitaJson.encodeToString(serializer, entries))
        if (!temp.renameTo(file)) {
            temp.copyTo(file, overwrite = true)
            temp.delete()
        }
    }
}
