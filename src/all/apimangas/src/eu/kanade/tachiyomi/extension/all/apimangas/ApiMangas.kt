package eu.kanade.tachiyomi.extension.all.apimangas

import android.app.Application
import android.content.SharedPreferences
import android.text.InputType
import android.widget.Toast
import androidx.preference.EditTextPreference
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import keiyoushi.utils.getPreferencesLazy
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.Credentials
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import uy.kohesive.injekt.injectLazy
import java.security.MessageDigest

/**
 * API Mangas Extension Skeleton
 *
 * This extension demonstrates:
 * 1. API authentication with username/password
 * 2. Storing custom metadata (decryption keys) per manga
 * 3. Image decryption using interceptors
 * 4. Custom filters for advanced search
 *
 * SETUP INSTRUCTIONS:
 * 1. Replace API_BASE_URL with your actual API endpoint
 * 2. Implement authentication mechanism in authenticateUser()
 * 3. Adjust DTOs to match your API response format
 * 4. Customize filters in ApiMangasFilters.kt
 * 5. Update ImageDecryptionInterceptor with your encryption method
 * 6. Replace placeholder icons in res/ directories
 */
class ApiMangas : ConfigurableSource, HttpSource() {

    override val name = "API Mangas"
    override val lang = "all"
    override val supportsLatest = true

    // TODO: Replace with your actual API base URL
    override val baseUrl by lazy {
        preferences.getString(PREF_API_URL, DEFAULT_API_URL)!!
    }

    // Preferences for storing user credentials and API URL
    internal val preferences: SharedPreferences by getPreferencesLazy()

    private val username: String
        get() = preferences.getString(PREF_USERNAME, "")!!

    private val password: String
        get() = preferences.getString(PREF_PASSWORD, "")!!

    // JSON serializer for parsing API responses and storing metadata
    private val json: Json = Json { ignoreUnknownKeys = true }

    /**
     * Custom OkHttpClient with authentication and image decryption interceptor
     */
    override val client: OkHttpClient = network.cloudflareClient.newBuilder()
        .authenticator { _, response ->
            // Handle authentication challenges
            // Return null if we already tried to authenticate or don't have credentials
            if (response.request.header("Authorization") != null || username.isEmpty()) {
                null
            } else {
                response.request.newBuilder()
                    .header("Authorization", Credentials.basic(username, password))
                    .build()
            }
        }
        .addInterceptor { chain ->
            // Add authentication token to all requests
            val token = getAuthToken()
            val request = if (token != null) {
                chain.request().newBuilder()
                    .addHeader("Authorization", "Bearer $token")
                    .build()
            } else {
                chain.request()
            }
            chain.proceed(request)
        }
        .addInterceptor(ImageDecryptionInterceptor()) // Add image decryption interceptor
        .build()

    // Store authentication token in memory (in production, use preferences)
    private var authToken: String? = null

    /**
     * Get or refresh authentication token
     */
    private fun getAuthToken(): String? {
        if (authToken != null) {
            return authToken
        }

        // Authenticate if we have credentials
        if (username.isNotEmpty() && password.isNotEmpty()) {
            authToken = authenticateUser(username, password)
        }

        return authToken
    }

    /**
     * Authenticate with the API
     * TODO: Implement based on your API's authentication mechanism
     */
    private fun authenticateUser(username: String, password: String): String? {
        return try {
            // Example: POST to /api/auth/login with username and password
            val body = FormBody.Builder()
                .add("username", username)
                .add("password", password)
                .build()

            val request = POST("$baseUrl/api/auth/login", headers, body)
            val response = client.newCall(request).execute()

            if (response.isSuccessful) {
                // Parse token from response
                val responseBody = response.body.string()
                val tokenResponse = json.decodeFromString<Map<String, String>>(responseBody)
                tokenResponse["token"] ?: tokenResponse["access_token"]
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    // ============================
    // Popular Manga
    // ============================

    override fun popularMangaRequest(page: Int): Request {
        val url = "$baseUrl/api/manga/popular".toHttpUrl().newBuilder()
            .addQueryParameter("page", page.toString())
            .addQueryParameter("limit", "20")
            .build()

        return GET(url, headers)
    }

    override fun popularMangaParse(response: Response): MangasPage {
        val result = json.decodeFromString<MangaListResponse>(response.body.string())
        val mangas = result.mangas.map { it.toSManga() }
        return MangasPage(mangas, result.hasNextPage)
    }

    // ============================
    // Latest Manga
    // ============================

    override fun latestUpdatesRequest(page: Int): Request {
        val url = "$baseUrl/api/manga/latest".toHttpUrl().newBuilder()
            .addQueryParameter("page", page.toString())
            .addQueryParameter("limit", "20")
            .build()

        return GET(url, headers)
    }

    override fun latestUpdatesParse(response: Response): MangasPage {
        return popularMangaParse(response)
    }

    // ============================
    // Search Manga
    // ============================

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = "$baseUrl/api/manga/search".toHttpUrl().newBuilder()
            .addQueryParameter("page", page.toString())
            .addQueryParameter("limit", "20")

        if (query.isNotEmpty()) {
            url.addQueryParameter("q", query)
        }

        // Apply filters
        filters.forEach { filter ->
            when (filter) {
                is SortFilter -> {
                    val sortValue = when (filter.state?.index) {
                        0 -> "popular"
                        1 -> "latest"
                        2 -> "alphabetical"
                        3 -> "rating"
                        4 -> "random"
                        else -> "popular"
                    }
                    url.addQueryParameter("sort", sortValue)
                    url.addQueryParameter("order", if (filter.state?.ascending == true) "asc" else "desc")
                }
                is StatusFilter -> {
                    if (filter.state != 0) {
                        val status = filter.values[filter.state].lowercase()
                        url.addQueryParameter("status", status)
                    }
                }
                is UriFilter -> filter.addToUri(url)
                is AdvancedSearchFilter -> {
                    if (filter.state.isNotEmpty()) {
                        url.addQueryParameter("advanced", filter.state)
                    }
                }
                is MinChaptersFilter -> {
                    if (filter.state.isNotEmpty()) {
                        url.addQueryParameter("min_chapters", filter.state)
                    }
                }
                is YearFilter -> {
                    if (filter.state.isNotEmpty()) {
                        url.addQueryParameter("year", filter.state)
                    }
                }
                else -> {}
            }
        }

        return GET(url.build(), headers)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        return popularMangaParse(response)
    }

    override fun getFilterList(): FilterList {
        return FilterList(eu.kanade.tachiyomi.extension.all.apimangas.getFilterList())
    }

    // ============================
    // Manga Details
    // ============================

    override fun mangaDetailsRequest(manga: SManga): Request {
        // Parse metadata from manga.url
        val metadata = parseMangaMetadata(manga.url)
        return GET("${baseUrl}${metadata.apiUrl}", headers)
    }

    override fun mangaDetailsParse(response: Response): SManga {
        val dto = json.decodeFromString<MangaDto>(response.body.string())
        return dto.toSManga()
    }

    override fun getMangaUrl(manga: SManga): String {
        // Extract the actual URL for opening in browser
        val metadata = parseMangaMetadata(manga.url)
        return "${baseUrl.replace("/api", "")}${metadata.apiUrl}"
    }

    // ============================
    // Chapter List
    // ============================

    override fun chapterListRequest(manga: SManga): Request {
        val metadata = parseMangaMetadata(manga.url)
        return GET("${baseUrl}${metadata.apiUrl}/chapters", headers)
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val result = json.decodeFromString<ChapterListResponse>(response.body.string())
        return result.chapters.map { it.toSChapter() }
    }

    override fun getChapterUrl(chapter: SChapter): String {
        return "${baseUrl.replace("/api", "")}${chapter.url}"
    }

    // ============================
    // Page List
    // ============================

    override fun pageListRequest(chapter: SChapter): Request {
        return GET("${baseUrl}${chapter.url}/pages", headers)
    }

    override fun pageListParse(response: Response): List<Page> {
        val result = json.decodeFromString<PageListResponse>(response.body.string())

        // Store decryption info for use in image requests
        return result.pages.map { pageDto ->
            // Append decryption parameters to image URL if encryption is used
            val imageUrl = if (result.decryptionKey != null) {
                pageDto.imageUrl.toHttpUrl().newBuilder()
                    .addQueryParameter("decrypt_key", result.decryptionKey)
                    .apply {
                        if (result.encryptionIv != null) {
                            addQueryParameter("decrypt_iv", result.encryptionIv)
                        }
                    }
                    .build()
                    .toString()
            } else {
                pageDto.imageUrl
            }

            Page(pageDto.index, imageUrl = imageUrl)
        }
    }

    override fun imageUrlParse(response: Response): String {
        throw UnsupportedOperationException()
    }

    // ============================
    // Preferences / Settings
    // ============================

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        EditTextPreference(screen.context).apply {
            key = PREF_API_URL
            title = "API URL"
            summary = "The base URL of your manga API"
            setDefaultValue(DEFAULT_API_URL)
            dialogTitle = "API URL"
            dialogMessage = "Enter the base URL (e.g., https://api.example.com)"

            setOnPreferenceChangeListener { _, newValue ->
                try {
                    val url = newValue as String
                    url.toHttpUrl() // Validate URL
                    Toast.makeText(screen.context, "API URL updated. Restart to apply.", Toast.LENGTH_SHORT).show()
                    true
                } catch (e: Exception) {
                    Toast.makeText(screen.context, "Invalid URL", Toast.LENGTH_SHORT).show()
                    false
                }
            }
        }.also(screen::addPreference)

        EditTextPreference(screen.context).apply {
            key = PREF_USERNAME
            title = "Username"
            summary = username.ifEmpty { "Not set" }
            setDefaultValue("")
            dialogTitle = "Username"

            setOnPreferenceChangeListener { _, _ ->
                // Clear auth token when credentials change
                authToken = null
                Toast.makeText(screen.context, "Credentials updated", Toast.LENGTH_SHORT).show()
                true
            }
        }.also(screen::addPreference)

        EditTextPreference(screen.context).apply {
            key = PREF_PASSWORD
            title = "Password"
            summary = if (password.isEmpty()) "Not set" else "••••••••"
            setDefaultValue("")
            dialogTitle = "Password"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD

            setOnPreferenceChangeListener { _, _ ->
                // Clear auth token when credentials change
                authToken = null
                Toast.makeText(screen.context, "Credentials updated", Toast.LENGTH_SHORT).show()
                true
            }
        }.also(screen::addPreference)
    }

    // ============================
    // Helper Functions
    // ============================

    /**
     * Convert MangaDto to SManga with embedded metadata
     * 
     * IMPORTANT: This demonstrates how to store custom metadata (decryption keys)
     * alongside the manga URL. The metadata is JSON-encoded in the URL field.
     */
    private fun MangaDto.toSManga(): SManga {
        return SManga.create().apply {
            // Create metadata with decryption info
            val metadata = MangaMetadata(
                apiUrl = "/api/manga/$id",
                decryptionKey = decryptionKey,
                encryptionIv = encryptionIv,
            )

            // Store metadata as JSON in the URL field
            // This allows us to retrieve decryption keys later
            url = json.encodeToString(metadata)

            title = this@toSManga.title
            thumbnail_url = thumbnailUrl
            description = this@toSManga.description
            author = this@toSManga.author
            artist = this@toSManga.artist
            status = when (this@toSManga.status?.lowercase()) {
                "ongoing" -> SManga.ONGOING
                "completed" -> SManga.COMPLETED
                "hiatus" -> SManga.ON_HIATUS
                "cancelled" -> SManga.CANCELLED
                else -> SManga.UNKNOWN
            }
            genre = genres.joinToString(", ")
            initialized = true
        }
    }

    /**
     * Parse metadata from SManga.url
     */
    private fun parseMangaMetadata(url: String): MangaMetadata {
        return try {
            json.decodeFromString(url)
        } catch (e: Exception) {
            // Fallback if URL is not JSON (for backward compatibility)
            MangaMetadata(apiUrl = url)
        }
    }

    /**
     * Convert ChapterDto to SChapter
     */
    private fun ChapterDto.toSChapter(): SChapter {
        return SChapter.create().apply {
            url = "/api/chapter/$id"
            name = this@toSChapter.name
            chapter_number = chapterNumber ?: -1f
            date_upload = dateUpload ?: 0L
            scanlator = this@toSChapter.scanlator
        }
    }

    // ============================
    // ID Generation
    // ============================

    /**
     * Generate unique source ID
     * Keep this stable across versions to maintain user's library
     */
    override val id: Long by lazy {
        val key = "apimangas/all/1"
        val bytes = MessageDigest.getInstance("MD5").digest(key.toByteArray())
        (0..7).map { bytes[it].toLong() and 0xff shl 8 * (7 - it) }.reduce(Long::or) and Long.MAX_VALUE
    }

    companion object {
        private const val DEFAULT_API_URL = "https://api.example.com"
        private const val PREF_API_URL = "api_url"
        private const val PREF_USERNAME = "username"
        private const val PREF_PASSWORD = "password"
    }
}
