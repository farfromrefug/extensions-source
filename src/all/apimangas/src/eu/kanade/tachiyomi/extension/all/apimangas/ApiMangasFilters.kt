package eu.kanade.tachiyomi.extension.all.apimangas

import eu.kanade.tachiyomi.source.model.Filter
import okhttp3.HttpUrl

/**
 * Custom filters for the API Mangas extension
 * These filters allow users to refine their search and browse experience
 */

interface UriFilter {
    fun addToUri(builder: HttpUrl.Builder)
}

/**
 * Sort filter with multiple sort options
 */
class SortFilter : Filter.Sort(
    "Sort",
    arrayOf(
        "Popular",
        "Latest Updates",
        "Alphabetically",
        "Rating",
        "Random",
    ),
    Selection(0, true),
)

/**
 * Status filter - allow filtering by manga status
 */
class StatusFilter : Filter.Select<String>(
    "Status",
    arrayOf(
        "All",
        "Ongoing",
        "Completed",
        "Hiatus",
        "Cancelled",
    ),
)

/**
 * Genre filter using multi-select checkboxes
 */
class GenreOption(name: String, val id: String = name) : Filter.CheckBox(name, false)

class GenreFilter(genres: List<GenreOption>) : Filter.Group<GenreOption>("Genres", genres), UriFilter {
    override fun addToUri(builder: HttpUrl.Builder) {
        val selectedGenres = state.filter { it.state }.map { it.id }
        if (selectedGenres.isNotEmpty()) {
            builder.addQueryParameter("genres", selectedGenres.joinToString(","))
        }
    }
}

/**
 * Custom text filter for advanced search
 */
class AdvancedSearchFilter : Filter.Text("Advanced Search")

/**
 * Custom tri-state filter for content ratings
 */
class RatingFilter(name: String) : Filter.TriState(name)

/**
 * Content rating group filter
 */
class ContentRatingFilter : Filter.Group<RatingFilter>(
    "Content Rating",
    listOf(
        RatingFilter("Safe"),
        RatingFilter("Suggestive"),
        RatingFilter("Erotica"),
        RatingFilter("Pornographic"),
    ),
), UriFilter {
    override fun addToUri(builder: HttpUrl.Builder) {
        val included = state.filter { it.state == Filter.TriState.STATE_INCLUDE }.map { it.name }
        val excluded = state.filter { it.state == Filter.TriState.STATE_EXCLUDE }.map { it.name }

        if (included.isNotEmpty()) {
            builder.addQueryParameter("rating_include", included.joinToString(","))
        }
        if (excluded.isNotEmpty()) {
            builder.addQueryParameter("rating_exclude", excluded.joinToString(","))
        }
    }
}

/**
 * Minimum chapters filter
 */
class MinChaptersFilter : Filter.Text("Minimum Chapters")

/**
 * Year filter
 */
class YearFilter : Filter.Text("Publication Year")

/**
 * Helper function to build filter list
 * This can be customized based on the API's available filters
 */
fun getFilterList(): List<Filter<*>> {
    return listOf(
        Filter.Header("Note: Filters may not work with text search"),
        Filter.Separator(),
        SortFilter(),
        StatusFilter(),
        Filter.Separator(),
        GenreFilter(
            listOf(
                GenreOption("Action"),
                GenreOption("Adventure"),
                GenreOption("Comedy"),
                GenreOption("Drama"),
                GenreOption("Fantasy"),
                GenreOption("Historical"),
                GenreOption("Horror"),
                GenreOption("Mystery"),
                GenreOption("Psychological"),
                GenreOption("Romance"),
                GenreOption("Sci-Fi", "sci_fi"),
                GenreOption("Slice of Life", "slice_of_life"),
                GenreOption("Sports"),
                GenreOption("Supernatural"),
                GenreOption("Thriller"),
            ),
        ),
        ContentRatingFilter(),
        Filter.Separator(),
        Filter.Header("Additional Filters"),
        MinChaptersFilter(),
        YearFilter(),
        AdvancedSearchFilter(),
    )
}
