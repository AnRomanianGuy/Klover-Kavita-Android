package com.romanianguy.klover.data.backup

import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.romanianguy.klover.data.network.KavitaJson
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

const val BACKUP_APP = "klover"
const val BACKUP_FORMAT = 1

// one file with everything that belongs to a reader: the per-account data files, the settings and the per-series
// reader choices. Never the sign-in, the downloads or what is waiting to be sent to the server
@Serializable
data class BackupFile(
    val app: String = BACKUP_APP,
    val format: Int = BACKUP_FORMAT,
    val exportedAt: Long = 0,
    val appVersion: String = "",
    val files: Map<String, JsonElement> = emptyMap(),
    val settings: Map<String, PrefEntry> = emptyMap(),
    val series: Map<String, String> = emptyMap(),
)

@Serializable
data class PrefEntry(val type: String, val value: JsonElement)

enum class BackupProblem { NotABackup, TooNew, Unreadable, SignedOut }

class BackupException(val problem: BackupProblem) : Exception(problem.name)

private val pretty = Json(KavitaJson) { prettyPrint = true }

fun BackupFile.encode(): String = pretty.encodeToString(BackupFile.serializer(), this)

fun decodeBackup(text: String): BackupFile {
    val root = try {
        KavitaJson.parseToJsonElement(text)
    } catch (e: Exception) {
        throw BackupException(BackupProblem.NotABackup)
    }
    val app = (root as? JsonObject)?.get("app")?.jsonPrimitive?.contentOrNull
    if (app != BACKUP_APP) throw BackupException(BackupProblem.NotABackup)
    val file = try {
        KavitaJson.decodeFromJsonElement(BackupFile.serializer(), root)
    } catch (e: Exception) {
        throw BackupException(BackupProblem.Unreadable)
    }
    if (file.format > BACKUP_FORMAT) throw BackupException(BackupProblem.TooNew)
    return file
}

internal fun Map<Preferences.Key<*>, Any>.toEntries(skip: Set<String>): Map<String, PrefEntry> {
    val out = sortedMapOf<String, PrefEntry>()
    for ((key, value) in this) {
        if (key.name in skip) continue
        val entry = when (value) {
            is String -> PrefEntry("string", JsonPrimitive(value))
            is Boolean -> PrefEntry("boolean", JsonPrimitive(value))
            is Int -> PrefEntry("int", JsonPrimitive(value))
            is Long -> PrefEntry("long", JsonPrimitive(value))
            is Float -> PrefEntry("float", JsonPrimitive(value))
            is Double -> PrefEntry("double", JsonPrimitive(value))
            is Set<*> -> PrefEntry("stringSet", JsonArray(value.map { JsonPrimitive(it.toString()) }))
            else -> continue
        }
        out[key.name] = entry
    }
    return out
}

// puts the entries that make sense into [prefs]; returns how many were taken (a value of the wrong type is skipped)
internal fun MutablePreferences.putEntries(entries: Map<String, PrefEntry>, skip: Set<String>): Int {
    var taken = 0
    for ((name, entry) in entries) {
        if (name in skip || name.isBlank()) continue
        val v = entry.value
        val ok = runCatching { when (entry.type) {
            "string" -> v.jsonPrimitive.takeIf { it.isString }?.content?.also { this[stringPreferencesKey(name)] = it } != null
            "boolean" -> v.jsonPrimitive.booleanOrNull?.also { this[booleanPreferencesKey(name)] = it } != null
            "int" -> v.jsonPrimitive.intOrNull?.also { this[intPreferencesKey(name)] = it } != null
            "long" -> v.jsonPrimitive.longOrNull?.also { this[longPreferencesKey(name)] = it } != null
            "float" -> v.jsonPrimitive.floatOrNull?.also { this[floatPreferencesKey(name)] = it } != null
            "double" -> v.jsonPrimitive.doubleOrNull?.also { this[doublePreferencesKey(name)] = it } != null
            "stringSet" -> (v as? JsonArray)?.map { it.jsonPrimitive.content }?.toSet()
                ?.also { this[stringSetPreferencesKey(name)] = it } != null
            else -> false
        } }.getOrDefault(false)
        if (ok) taken++
    }
    return taken
}
