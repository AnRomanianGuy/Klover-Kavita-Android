package com.romanianguy.klover.data.chapters

private val WORD_AND_NUMBER = Regex("""^(?:volume|vol|chapter|chap|ch|v|c)\.?\s*(\d.*)$""", RegexOption.IGNORE_CASE)

// "Volume 3" from a label and a value; a name that already starts with the label ("Vol. 3", "v03") isn't doubled
fun numberedTitle(label: String, value: String?): String {
    val v = value.orEmpty().trim()
    if (v.isEmpty()) return label.trim()
    val word = label.trim()
    // The label as a whole word: "Volumes of Dusk" is a name of its own, not "Volume" with an s.
    if (word.isNotBlank() && v.startsWith(word, ignoreCase = true) && !v.getOrElse(word.length) { ' ' }.isLetter()) {
        return word + v.substring(word.length)
    }
    WORD_AND_NUMBER.matchEntire(v)?.let { return "${label.trim()} ${it.groupValues[1]}".trim() }
    return "${label.trim()} $v".trim()
}
