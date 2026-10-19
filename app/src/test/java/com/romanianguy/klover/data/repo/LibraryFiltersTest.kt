package com.romanianguy.klover.data.repo

import com.romanianguy.klover.data.model.FilterComparison
import com.romanianguy.klover.data.model.FilterStatement
import com.romanianguy.klover.data.model.SeriesFilterField
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryFiltersTest {
    @Test
    fun noFiltersMeansNoStatements() {
        val filters = LibraryFilters()
        assertTrue(filters.isEmpty)
        assertEquals(0, filters.activeCount)
        assertEquals(emptyList<FilterStatement>(), filters.toStatements())
    }

    @Test
    fun severalGenresAreOneAnyOfStatement() {
        val filters = LibraryFilters().toggle(FilterRef.genre(2, "Drama")).toggle(FilterRef.genre(5, "Sci-Fi"))
        assertEquals(
            listOf(FilterStatement(FilterComparison.Contains, SeriesFilterField.Genres, "2,5")),
            filters.toStatements(),
        )
        assertEquals(2, filters.activeCount)
    }

    @Test
    fun differentKindsAllHaveToHold() {
        val filters = LibraryFilters(unreadOnly = true, minRating = 4)
            .toggle(FilterRef.tag(3, "Cozy"))
            .toggleStatus(2)
            .toggleStatus(0)
        val statements = filters.toStatements()
        assertTrue(FilterStatement(FilterComparison.Contains, SeriesFilterField.Tags, "3") in statements)
        assertTrue(FilterStatement(FilterComparison.Contains, SeriesFilterField.PublicationStatus, "0,2") in statements)
        assertTrue(FilterStatement(FilterComparison.Equal, SeriesFilterField.ReadProgress, "0") in statements)
        assertTrue(FilterStatement(FilterComparison.GreaterThanEqual, SeriesFilterField.UserRating, "4") in statements)
        assertEquals(5, filters.activeCount)
    }

    @Test
    fun aRatingOfZeroIsNotAFilter() {
        assertTrue(LibraryFilters(minRating = 0).toStatements().isEmpty())
    }

    @Test
    fun togglingTwiceTurnsItOff() {
        val genre = FilterRef.genre(1, "Fantasy")
        val on = LibraryFilters().toggle(genre)
        assertFalse(on.isEmpty)
        assertTrue(on.toggle(genre).isEmpty)
        assertTrue(LibraryFilters().toggleStatus(1).toggleStatus(1).isEmpty)
    }

    @Test
    fun peopleAreSearchedThroughTheFieldOfTheirRole() {
        assertEquals(SeriesFilterField.Writers, FilterRef.person(PersonRoles.Writer, 1, "A").field)
        assertEquals(SeriesFilterField.CoverArtist, FilterRef.person(PersonRoles.CoverArtist, 2, "B").field)
        assertEquals(SeriesFilterField.Penciller, FilterRef.person(PersonRoles.Penciller, 3, "C").field)
        val writer = FilterRef.person(PersonRoles.Writer, 1, "A")
        val artist = FilterRef.person(PersonRoles.CoverArtist, 2, "B")
        // Two roles are two statements, since each is its own field on the server.
        assertEquals(2, LibraryFilters().toggle(writer).toggle(artist).toStatements().size)
        assertEquals(RefKind.Person, writer.kind)
        assertEquals(RefKind.Genre, FilterRef.genre(1, "x").kind)
        assertEquals(RefKind.Tag, FilterRef.tag(1, "x").kind)
    }
}
