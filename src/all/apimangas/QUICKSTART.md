# Quick Start Guide: Adapting the Skeleton to Your API

This guide walks through adapting the API Mangas skeleton to a real manga API.

## Example API Scenario

Let's say you have an API at `https://manga-api.example.com` with:
- JWT authentication via POST `/auth/login`
- Manga list at GET `/mangas?page=1&sort=popular`
- AES-encrypted images with keys returned per chapter
- Various filters (genre, status, year)

## Step-by-Step Adaptation

### 1. Update Basic Configuration

**In `ApiMangas.kt`:**

```kotlin
companion object {
    private const val DEFAULT_API_URL = "https://manga-api.example.com"
    private const val PREF_API_URL = "api_url"
    private const val PREF_USERNAME = "username"
    private const val PREF_PASSWORD = "password"
}
```

### 2. Implement Authentication

**Replace `authenticateUser()` with your API's auth:**

```kotlin
private fun authenticateUser(username: String, password: String): String? {
    return try {
        // Your API's authentication endpoint
        val body = FormBody.Builder()
            .add("email", username)  // Your API might use "email" instead
            .add("password", password)
            .build()

        val request = POST("$baseUrl/auth/login", headers, body)
        val response = client.newCall(request).execute()

        if (response.isSuccessful) {
            val responseBody = response.body.string()
            
            // Parse based on your API's response format
            // Example 1: Simple JSON with token field
            val tokenResponse = json.decodeFromString<Map<String, String>>(responseBody)
            tokenResponse["token"]
            
            // Example 2: Complex response with nested fields
            // val authResponse = json.decodeFromString<AuthResponse>(responseBody)
            // authResponse.data.accessToken
        } else {
            null
        }
    } catch (e: Exception) {
        null
    }
}
```

**For OAuth or complex auth:**

```kotlin
// If your API uses refresh tokens
private var refreshToken: String? = null

private fun authenticateUser(username: String, password: String): String? {
    // Get initial tokens
    val response = // ... make auth request
    val tokens = json.decodeFromString<TokenResponse>(response)
    
    // Store refresh token
    refreshToken = tokens.refreshToken
    preferences.edit().putString("refresh_token", refreshToken).apply()
    
    return tokens.accessToken
}

private fun refreshAuthToken(): String? {
    val refresh = refreshToken ?: return null
    // Call refresh endpoint
    // ...
}
```

### 3. Adapt DTOs to Your API

**Check your API's actual response format:**

```bash
curl -H "Authorization: Bearer YOUR_TOKEN" \
  https://manga-api.example.com/mangas?page=1
```

**Example Response:**
```json
{
  "data": [
    {
      "id": "manga-123",
      "attributes": {
        "title": "Example Manga",
        "cover": "https://cdn.example.com/covers/123.jpg",
        "description": "A great manga",
        "status": "ongoing",
        "tags": ["action", "adventure"]
      },
      "encryption": {
        "key": "base64EncodedKey==",
        "iv": "base64EncodedIV=="
      }
    }
  ],
  "hasMore": true
}
```

**Update `ApiMangasDto.kt`:**

```kotlin
@Serializable
data class MangaListResponse(
    val data: List<MangaWrapper>,
    val hasMore: Boolean,  // Match your API's pagination field
)

@Serializable
data class MangaWrapper(
    val id: String,
    val attributes: MangaAttributes,
    val encryption: EncryptionInfo? = null,
)

@Serializable
data class MangaAttributes(
    val title: String,
    val cover: String,
    val description: String? = null,
    val status: String? = null,
    val tags: List<String> = emptyList(),
    // Add other fields your API returns
)

@Serializable
data class EncryptionInfo(
    val key: String,
    val iv: String,
)
```

**Update parsing in `ApiMangas.kt`:**

```kotlin
override fun popularMangaParse(response: Response): MangasPage {
    val result = json.decodeFromString<MangaListResponse>(response.body.string())
    val mangas = result.data.map { wrapper ->
        SManga.create().apply {
            // Store metadata with encryption info
            val metadata = MangaMetadata(
                apiUrl = "/mangas/${wrapper.id}",
                decryptionKey = wrapper.encryption?.key,
                encryptionIv = wrapper.encryption?.iv,
            )
            url = json.encodeToString(metadata)
            
            title = wrapper.attributes.title
            thumbnail_url = wrapper.attributes.cover
            description = wrapper.attributes.description
            status = when (wrapper.attributes.status?.lowercase()) {
                "ongoing" -> SManga.ONGOING
                "completed" -> SManga.COMPLETED
                else -> SManga.UNKNOWN
            }
            genre = wrapper.attributes.tags.joinToString(", ")
            initialized = true
        }
    }
    return MangasPage(mangas, result.hasMore)
}
```

### 4. Adjust API Endpoints

**Update all request builders to match your API:**

```kotlin
override fun popularMangaRequest(page: Int): Request {
    // Your API's actual endpoint structure
    val url = "$baseUrl/mangas".toHttpUrl().newBuilder()
        .addQueryParameter("page", page.toString())
        .addQueryParameter("per_page", "20")  // Your API's param name
        .addQueryParameter("sort", "popular")
        .build()
    return GET(url, headers)
}

override fun latestUpdatesRequest(page: Int): Request {
    val url = "$baseUrl/mangas".toHttpUrl().newBuilder()
        .addQueryParameter("page", page.toString())
        .addQueryParameter("per_page", "20")
        .addQueryParameter("sort", "updated")  // Your API's sort value
        .addQueryParameter("order", "desc")
        .build()
    return GET(url, headers)
}

override fun mangaDetailsRequest(manga: SManga): Request {
    val metadata = parseMangaMetadata(manga.url)
    // Your API might have a different endpoint structure
    return GET("${baseUrl}/mangas/${metadata.apiUrl.substringAfterLast("/")}", headers)
}
```

### 5. Customize Filters

**Match filters to your API's capabilities:**

In `ApiMangasFilters.kt`, update the genre list:

```kotlin
GenreFilter(
    listOf(
        // Use YOUR API's actual genre IDs/names
        GenreOption("Action", "1"),
        GenreOption("Romance", "2"),
        GenreOption("Comedy", "3"),
        // ... check your API documentation
    ),
)
```

**Update filter application in `searchMangaRequest()`:**

```kotlin
override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
    val url = "$baseUrl/mangas/search".toHttpUrl().newBuilder()
        .addQueryParameter("page", page.toString())
        .addQueryParameter("per_page", "20")

    if (query.isNotEmpty()) {
        url.addQueryParameter("q", query)  // Your API's query param
    }

    filters.forEach { filter ->
        when (filter) {
            is GenreFilter -> {
                val genres = filter.state.filter { it.state }.map { it.id }
                if (genres.isNotEmpty()) {
                    // Use your API's genre parameter format
                    url.addQueryParameter("genre_ids[]", genres.joinToString(","))
                }
            }
            is StatusFilter -> {
                if (filter.state != 0) {
                    // Map to your API's status values
                    val statusMap = mapOf(
                        1 to "ONGOING",
                        2 to "COMPLETED",
                        3 to "HIATUS",
                        4 to "CANCELLED"
                    )
                    url.addQueryParameter("status", statusMap[filter.state])
                }
            }
            // ... handle other filters
        }
    }

    return GET(url.build(), headers)
}
```

### 6. Configure Image Decryption

**Test your API's encryption:**

```bash
# Download an encrypted image
curl -H "Authorization: Bearer TOKEN" \
  https://cdn.example.com/images/encrypted/page1.jpg \
  -o encrypted.jpg

# Check if it's actually encrypted (won't display as image)
file encrypted.jpg
```

**Update `ImageDecryptionInterceptor.kt` if needed:**

```kotlin
// If your API uses a different encryption method
private fun decryptCustom(data: ByteArray, key: String, iv: String): ByteArray {
    // Your specific decryption implementation
    // Example: if using a custom XOR with salt
    val keyBytes = Base64.decode(key, Base64.DEFAULT)
    val ivBytes = Base64.decode(iv, Base64.DEFAULT)
    
    // Implement your decryption logic
    // ...
}
```

**If images aren't encrypted:**

Simply don't add decryption parameters to the URLs:

```kotlin
override fun pageListParse(response: Response): List<Page> {
    val result = json.decodeFromString<PageListResponse>(response.body.string())
    
    // No encryption - just use URLs directly
    return result.pages.map { pageDto ->
        Page(pageDto.index, imageUrl = pageDto.imageUrl)
    }
}
```

### 7. Handle Chapter Lists

**Update for your API's chapter format:**

```kotlin
override fun chapterListParse(response: Response): List<SChapter> {
    // Your API might nest chapters differently
    val responseData = json.decodeFromString<ChapterListResponse>(response.body.string())
    
    return responseData.chapters.mapIndexed { index, chapterDto ->
        SChapter.create().apply {
            url = "/chapters/${chapterDto.id}"
            name = buildChapterName(chapterDto)  // Custom formatting
            chapter_number = chapterDto.number?.toFloat() ?: -1f
            date_upload = parseApiDate(chapterDto.publishedAt)  // Parse your date format
            scanlator = chapterDto.scanlationGroup?.name
        }
    }
}

private fun buildChapterName(dto: ChapterDto): String {
    // Customize based on your API's data
    return when {
        dto.title != null -> "Ch. ${dto.number}: ${dto.title}"
        dto.volume != null -> "Vol. ${dto.volume} Ch. ${dto.number}"
        else -> "Chapter ${dto.number}"
    }
}
```

### 8. Testing Checklist

- [ ] Can log in with valid credentials
- [ ] Can browse popular manga
- [ ] Can see latest updates
- [ ] Search returns results
- [ ] Filters work correctly
- [ ] Manga details load
- [ ] Chapter list appears
- [ ] Images load (and decrypt if needed)
- [ ] Settings persist after app restart
- [ ] Handles network errors gracefully

### 9. Common Issues & Solutions

**Problem: "Unauthorized" errors**
```kotlin
// Add better error handling in authenticator
override val client: OkHttpClient = network.cloudflareClient.newBuilder()
    .authenticator { _, response ->
        if (response.request.header("Authorization") != null) {
            // Already tried auth, clear token and fail
            authToken = null
            null
        } else {
            // Try to authenticate
            val token = getAuthToken()
            if (token != null) {
                response.request.newBuilder()
                    .header("Authorization", "Bearer $token")
                    .build()
            } else {
                null
            }
        }
    }
    .build()
```

**Problem: Images not decrypting**
```kotlin
// Add logging to ImageDecryptionInterceptor
override fun intercept(chain: Interceptor.Chain): Response {
    val request = chain.request()
    val decryptionKey = request.url.queryParameter("decrypt_key")
    
    Log.d("ImageDecryption", "Processing: ${request.url}")
    Log.d("ImageDecryption", "Has key: ${decryptionKey != null}")
    
    // ... rest of implementation
}
```

**Problem: JSON parsing errors**
```kotlin
// Add error handling
override fun popularMangaParse(response: Response): MangasPage {
    return try {
        val body = response.body.string()
        Log.d("ApiMangas", "Response: $body")
        val result = json.decodeFromString<MangaListResponse>(body)
        // ... parse
    } catch (e: Exception) {
        Log.e("ApiMangas", "Parse error", e)
        MangasPage(emptyList(), false)
    }
}
```

## Real-World Example Template

Here's a minimal working version for a generic REST API:

```kotlin
class MyMangaApi : HttpSource(), ConfigurableSource {
    override val name = "My Manga API"
    override val baseUrl = "https://api.example.com"
    override val lang = "en"
    override val supportsLatest = true
    
    private val json = Json { ignoreUnknownKeys = true }
    
    override fun popularMangaRequest(page: Int) = 
        GET("$baseUrl/manga?sort=popular&page=$page")
    
    override fun popularMangaParse(response: Response): MangasPage {
        val data = json.decodeFromString<ApiResponse>(response.body.string())
        return MangasPage(
            data.results.map { it.toSManga() },
            data.hasNext
        )
    }
    
    // Implement other required methods...
}
```

## Next Steps

1. Test with real API endpoints
2. Handle edge cases (no results, network errors, etc.)
3. Add proper error messages
4. Optimize performance (caching, rate limiting)
5. Create proper icons (see README.md)
6. Submit as a pull request

## Need Help?

- Check the [Contributing Guide](../../../CONTRIBUTING.md)
- Look at existing extensions for examples
- Ask in Discord #programming channel
- Review the [Extension API docs](https://github.com/tachiyomiorg/extensions-lib)
