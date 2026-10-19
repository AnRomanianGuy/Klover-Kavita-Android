package com.romanianguy.klover.data.repo

import com.romanianguy.klover.data.model.FilterComparison
import com.romanianguy.klover.data.model.FilterStatement
import com.romanianguy.klover.data.model.SeriesFilterField

enum class RefKind { Genre, Tag, Person }

data class FilterRef(val field: Int, val id: Int, val label: String) {
    val kind: RefKind
        get() = when (this.field) {
            SeriesFilterField.Genres -> RefKind.Genre
            SeriesFilterField.Tags -> RefKind.Tag
            else -> RefKind.Person
        }

    companion object {
        fun genre(id: Int, title: String) = FilterRef(SeriesFilterField.Genres, id, title)
        fun tag(id: Int, title: String) = FilterRef(SeriesFilterField.Tags, id, title)
        fun person(role: Int, id: Int, name: String) = FilterRef(PersonRoles.field(role), id, name)
    }
}

object PersonRoles {
    const val Writer = 3
    const val Penciller = 4
    const val Inker = 5
    const val Colorist = 6
    const val Letterer = 7
    const val CoverArtist = 8
    const val Editor = 9
    const val Publisher = 10
    const val Character = 11
    const val Translator = 12

    fun field(role: Int): Int = when (role) {
        Penciller -> SeriesFilterField.Penciller
        Inker -> SeriesFilterField.Inker
        Colorist -> SeriesFilterField.Colorist
        Letterer -> SeriesFilterField.Letterer
        CoverArtist -> SeriesFilterField.CoverArtist
        Editor -> SeriesFilterField.Editor
        Publisher -> SeriesFilterField.Publisher
        Character -> SeriesFilterField.Characters
        Translator -> SeriesFilterField.Translators
        else -> SeriesFilterField.Writers
    }
}

data class LibraryFilters(
    val refs: Set<FilterRef> = emptySet(),
    val statuses: Set<Int> = emptySet(),
    val unreadOnly: Boolean = false,
    val minRating: Int = 0,
) {
    val activeCount: Int
        get() = refs.size + statuses.size + (if (unreadOnly) 1 else 0) + (if (minRating > 0) 1 else 0)

    val isEmpty: Boolean get() = activeCount == 0

    fun toggle(ref: FilterRef): LibraryFilters = copy(refs = if (ref in refs) refs - ref else refs + ref)

    fun toggleStatus(status: Int): LibraryFilters = copy(statuses = if (status in statuses) statuses - status else statuses + status)

    // several of one kind mean any of them, different kinds all have to hold
    fun toStatements(): List<FilterStatement> = buildList {
        refs.groupBy { it.field }.forEach { (field, group) ->
            add(FilterStatement(FilterComparison.Contains, field, group.joinToString(",") { it.id.toString() }))
        }
        if (statuses.isNotEmpty()) {
            add(FilterStatement(FilterComparison.Contains, SeriesFilterField.PublicationStatus, statuses.sorted().joinToString(",")))
        }
        if (unreadOnly) add(FilterStatement(FilterComparison.Equal, SeriesFilterField.ReadProgress, "0"))
        if (minRating > 0) add(FilterStatement(FilterComparison.GreaterThanEqual, SeriesFilterField.UserRating, minRating.toString()))
    }
}
