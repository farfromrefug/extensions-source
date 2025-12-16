package eu.kanade.tachiyomi.extension.all.booklore.dto

import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.Serializable
import org.apache.commons.text.StringSubstitutor

interface ConvertibleToSManga {
    fun toSManga(baseUrl: String): SManga
}

@Serializable
class LibraryDto(
    val id: String,
    val name: String,
)

@Serializable
class SeriesDto(
    val id: String,
    val libraryId: String,
    val name: String,
    val language: String?,
    val created: String?,
    val lastModified: String?,
    val fileLastModified: String,
    val booksCount: Int,
    val metadata: SeriesMetadataDto,
    val booksMetadata: BookMetadataAggregationDto,
) : ConvertibleToSManga {
    override fun toSManga(baseUrl: String) = SManga.create().apply {
        title = metadata.title
        url = "$baseUrl/api/v1/series/$id"
        thumbnail_url = "$url/thumbnail"
        status = when {
            metadata.status == "ENDED" && metadata.totalBookCount != null && booksCount < metadata.totalBookCount -> SManga.PUBLISHING_FINISHED
            metadata.status == "ENDED" -> SManga.COMPLETED
            metadata.status == "ONGOING" -> SManga.ONGOING
            metadata.status == "ABANDONED" -> SManga.CANCELLED
            metadata.status == "HIATUS" -> SManga.ON_HIATUS
            else -> SManga.UNKNOWN
        }
        language = metadata.language
        genre = (metadata.genres).sorted().distinct().joinToString(", ")
        tags = (metadata.tags + booksMetadata.tags).sorted().distinct().joinToString(", ")
        description = metadata.summary?.ifBlank { booksMetadata.summary }
        booksMetadata.authors.groupBy({ it.role }, { it.name }).let { map ->
            author = map["writer"]?.distinct()?.joinToString()
            artist = map["penciller"]?.distinct()?.joinToString()
        }
    }
}

@Serializable
class SeriesMetadataDto(
    val status: String,
    val title: String,
    val summary: String?,
    val language: String?,
    val genres: Set<String>,
    val tags: Set<String>,
    val totalBookCount: Int? = null,
)

@Serializable
class BookMetadataAggregationDto(
    val authors: List<AuthorDto> = emptyList(),
    val tags: Set<String> = emptySet(),
    val summary: String?,
)

@Serializable
class BookDto(
    val id: String,
    val seriesTitle: String,
    val created: String?,
    val fileLastModified: String,
    val sizeBytes: Long,
    val size: String,
    val media: MediaDto,
    val metadata: BookMetadataDto,
) : ConvertibleToSManga {
    fun getChapterName(template: String, isFromReadList: Boolean): String {
        val values = hashMapOf(
            "title" to metadata.title,
            "seriesTitle" to seriesTitle,
            "number" to metadata.number,
            "createdDate" to created,
            "releaseDate" to metadata.releaseDate,
            "size" to size,
            "sizeBytes" to sizeBytes.toString(),
        )
        val sub = StringSubstitutor(values, "{", "}")

        return buildString {
            if (isFromReadList) {
                append(seriesTitle)
                append(" ")
            }

            append(sub.replace(template))
        }
    }

    override fun toSManga(baseUrl: String) = SManga.create().apply {
        title = metadata.title
        url = "$baseUrl/api/v1/books/$id"
        thumbnail_url = "$url/thumbnail"
        status = SManga.UNKNOWN
        genre = metadata.tags.distinct().joinToString(", ")
        description = metadata.summary
        author = metadata.authors.joinToString { it.name }
        artist = author
    }
}

@Serializable
class MediaDto(
    val status: String,
    val mediaType: String,
    val pagesCount: Int,
    val mediaProfile: String = "DIVINA",
    val epubDivinaCompatible: Boolean = false,
)

@Serializable
class PageDto(
    val number: Int,
    val mediaType: String,
)

@Serializable
class BookMetadataDto(
    val title: String,
    val summary: String?,
    val number: String?,
    val numberSort: Float?,
    val releaseDate: String?,
    val authors: List<AuthorDto>,
    val tags: Set<String>,
)

@Serializable
class AuthorDto(
    val name: String,
    val role: String,
)

@Serializable
class CollectionDto(
    val id: String,
    val name: String,
    val ordered: Boolean,
    val seriesIds: List<String>,
    val createdDate: String,
    val lastModifiedDate: String,
    val filtered: Boolean,
)

@Serializable
class ReadListDto(
    val id: String,
    val name: String,
    val summary: String?,
    val bookIds: List<String>,
    val createdDate: String,
    val lastModifiedDate: String,
    val filtered: Boolean,
) : ConvertibleToSManga {
    override fun toSManga(baseUrl: String) = SManga.create().apply {
        title = name
        description = summary
        url = "$baseUrl/api/v1/readlists/$id"
        thumbnail_url = "$url/thumbnail"
        status = SManga.UNKNOWN
    }
}
