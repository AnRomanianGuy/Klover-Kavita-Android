package com.romanianguy.klover.data.downloads

import kotlinx.serialization.Serializable
import java.io.ByteArrayOutputStream

@Serializable
data class ResourceRef(val name: String, val mime: String)

private val RESOURCE_URL = Regex("""book-resources\?[^"'\s)<>]*""", RegexOption.IGNORE_CASE)

// the book files an HTML or CSS text refers to through book-resources?file=... URLs, decoded, no repeats
fun extractBookResourceFiles(text: String): List<String> =
    RESOURCE_URL.findAll(text)
        .mapNotNull { fileParam(it.value.substringAfter('?').replace("&amp;", "&")) }
        .distinct()
        .toList()

// the decoded value of the `file` parameter of a raw query string, or `null` when there is none
fun fileParam(rawQuery: String?): String? =
    rawQuery?.split('&')
        ?.firstOrNull { it.startsWith("file=") }
        ?.substringAfter("file=")
        ?.let(::percentDecode)
        ?.takeIf { it.isNotBlank() }

// unlike form decoding, + stays as is: Kavita escapes spaces as %20
fun percentDecode(value: String): String {
    if ('%' !in value) return value
    val out = ByteArrayOutputStream(value.length)
    var i = 0
    while (i < value.length) {
        if (value[i] == '%' && i + 2 <= value.lastIndex) {
            val digits = value.substring(i + 1, i + 3)
            // Two real hex digits: toIntOrNull alone also takes a sign, so "%+1" or "%-1" would turn into a stray
            // byte.
            val hex = if (digits.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }) digits.toInt(16) else null
            if (hex != null) {
                out.write(hex)
                i += 3
                continue
            }
        }
        // Copy whole code points so characters outside the BMP are not split into two broken halves.
        val codePoint = value.codePointAt(i)
        out.write(String(Character.toChars(codePoint)).toByteArray(Charsets.UTF_8))
        i += Character.charCount(codePoint)
    }
    return out.toString(Charsets.UTF_8.name())
}

// best guess of a mime type from a book file name, for resources the server did not label
fun mimeForBookFile(file: String): String = when (file.substringAfterLast('.', "").lowercase()) {
    "png" -> "image/png"
    "jpg", "jpeg" -> "image/jpeg"
    "gif" -> "image/gif"
    "webp" -> "image/webp"
    "svg" -> "image/svg+xml"
    "css" -> "text/css"
    "ttf" -> "font/ttf"
    "otf" -> "font/otf"
    "woff" -> "font/woff"
    "woff2" -> "font/woff2"
    "xhtml", "html", "htm" -> "text/html"
    else -> "application/octet-stream"
}
