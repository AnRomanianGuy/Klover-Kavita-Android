package com.romanianguy.klover.data.update

// "v1.2.3", "1.2" or "1.2-beta1": the numbers, and whether it is a pre-release of them
internal class AppVersion(val parts: List<Int>, val preRelease: Boolean) : Comparable<AppVersion> {
    override fun compareTo(other: AppVersion): Int {
        for (i in 0 until maxOf(parts.size, other.parts.size)) {
            val a = parts.getOrElse(i) { 0 }
            val b = other.parts.getOrElse(i) { 0 }
            if (a != b) return a.compareTo(b)
        }
        // the same numbers: 1.2 comes after 1.2-beta1
        return other.preRelease.compareTo(preRelease)
    }
}

internal fun parseVersion(text: String): AppVersion? {
    val clean = text.trim().removePrefix("v").removePrefix("V")
    val numbers = clean.substringBefore('-').substringBefore('+')
    val parts = numbers.split('.').map { it.toIntOrNull() ?: return null }
    if (parts.isEmpty() || numbers.isBlank()) return null
    return AppVersion(parts, preRelease = '-' in clean.substringBefore('+'))
}

// whether [remote] is a later version than [local]; one that cannot be read is never later
fun isNewerVersion(remote: String, local: String): Boolean {
    val r = parseVersion(remote) ?: return false
    val l = parseVersion(local) ?: return false
    return r > l
}
