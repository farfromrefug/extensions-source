package eu.kanade.tachiyomi.extension.all.apimangas

import kotlinx.serialization.Serializable

/**
 * Data Transfer Objects for API responses
 */

@Serializable
data class MangaListResponse(
    val mangas: List<MangaDto>,
    val hasNextPage: Boolean,
)

@Serializable
data class MangaDto(
    val id: String,
    val title: String,
    val thumbnailUrl: String,
    val description: String? = null,
    val author: String? = null,
    val artist: String? = null,
    val status: String? = null,
    val genres: List<String> = emptyList(),
    // Custom metadata for decryption - this will be stored in SManga.url as JSON
    val decryptionKey: String? = null,
    val encryptionIv: String? = null,
)

@Serializable
data class ChapterListResponse(
    val chapters: List<ChapterDto>,
)

@Serializable
data class ChapterDto(
    val id: String,
    val name: String,
    val chapterNumber: Float? = null,
    val dateUpload: Long? = null,
    val scanlator: String? = null,
)

@Serializable
data class PageListResponse(
    val pages: List<PageDto>,
    // Decryption info that applies to all pages in this chapter
    val decryptionKey: String? = null,
    val encryptionIv: String? = null,
)

@Serializable
data class PageDto(
    val index: Int,
    val imageUrl: String,
)

/**
 * Custom metadata wrapper to store additional data alongside manga URL
 * This allows us to store decryption keys per manga
 */
@Serializable
data class MangaMetadata(
    val apiUrl: String,
    val decryptionKey: String? = null,
    val encryptionIv: String? = null,
)
