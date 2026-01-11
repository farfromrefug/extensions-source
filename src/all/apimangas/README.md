# API Mangas Extension Skeleton

This is a **skeleton/template extension** that demonstrates how to create a manga extension for Koma/Tachiyomi that:
1. Communicates with an API using username/password authentication
2. Stores custom metadata (like decryption keys) per manga
3. Decrypts images after download
4. Provides custom filters for search/browse

## ⚠️ This is a Template

**This extension is NOT ready to use out of the box.** It's a skeleton that you need to customize for your specific API. Follow the setup instructions below.

## Features Demonstrated

### 1. Authentication with Username/Password
- The extension supports basic username/password authentication
- Credentials are stored in extension preferences
- Authentication can be done via:
  - Basic Auth (included in the authenticator)
  - Bearer Token (with token refresh)
  - Custom headers
- See `ApiMangas.kt` - `authenticateUser()` method

### 2. Custom Metadata Storage Per Manga
**Problem:** The standard `SManga` model doesn't have fields for storing custom data like decryption keys.

**Solution:** We serialize custom metadata as JSON and store it in the `SManga.url` field:

```kotlin
// Store metadata
val metadata = MangaMetadata(
    apiUrl = "/api/manga/$id",
    decryptionKey = "base64_encoded_key",
    encryptionIv = "base64_encoded_iv"
)
manga.url = json.encodeToString(metadata)

// Retrieve metadata later
val metadata = json.decodeFromString<MangaMetadata>(manga.url)
```

This allows you to:
- Store decryption keys per manga
- Store any other custom data needed for your API
- Maintain backward compatibility

See `ApiMangasDto.kt` for the `MangaMetadata` class.

### 3. Image Decryption
Images are decrypted using an OkHttp interceptor that:
- Detects decryption parameters in the image URL (as query parameters)
- Downloads the encrypted image
- Decrypts it before returning to the app
- Supports multiple encryption methods (AES, XOR, etc.)

The `ImageDecryptionInterceptor` supports:
- **AES-256-CBC** encryption
- **AES-128-CBC** encryption  
- **XOR** encryption (simple example)
- **CryptoJS-compatible** decryption (via CryptoAES library)

See `ImageDecryptionInterceptor.kt` for implementation details.

### 4. Custom Filters
The extension includes comprehensive filter support:
- Sort options (Popular, Latest, Alphabetically, Rating, Random)
- Status filter (Ongoing, Completed, Hiatus, Cancelled)
- Multi-select genre filters
- Tri-state content rating filters (Include/Exclude)
- Additional filters (Year, Minimum Chapters)
- Advanced search text filter

See `ApiMangasFilters.kt` for all filter implementations.

## Setup Instructions

### Step 1: Replace API Base URL
In `ApiMangas.kt`, update the `DEFAULT_API_URL`:
```kotlin
private const val DEFAULT_API_URL = "https://your-api.com"
```

### Step 2: Implement Authentication
Update the `authenticateUser()` method in `ApiMangas.kt` to match your API's authentication:

```kotlin
private fun authenticateUser(username: String, password: String): String? {
    // Example: Your API might use POST /api/auth/login
    val body = FormBody.Builder()
        .add("username", username)
        .add("password", password)
        .build()
    
    val request = POST("$baseUrl/api/auth/login", headers, body)
    val response = client.newCall(request).execute()
    
    // Parse and return the token
    // ...
}
```

### Step 3: Update DTOs
Modify the DTOs in `ApiMangasDto.kt` to match your API's response format:
- `MangaDto` - for manga metadata
- `ChapterDto` - for chapter metadata
- `PageDto` - for page/image metadata
- Add decryption key fields as needed

### Step 4: Customize Filters
Edit `ApiMangasFilters.kt` to match your API's filter capabilities:
- Update genre list
- Modify sort options
- Add/remove filters as needed

### Step 5: Update Decryption Logic
Modify `ImageDecryptionInterceptor.kt` to match your encryption:
- Update `decryptAES()` if using AES with different parameters
- Implement custom decryption methods as needed
- Update query parameter names if different

### Step 6: Replace Icons
Replace the placeholder icons in `res/mipmap-*/` directories:
- Use the [Android Asset Studio](https://romannurik.github.io/AndroidAssetStudio/icons-launcher.html)
- Generate icons for all densities (mdpi, hdpi, xhdpi, xxhdpi, xxxhdpi)
- Icons should have rounded corners per extension guidelines
- **Remove `web_hi_res_512.png` if generated**

### Step 7: Update Extension Info
In `build.gradle`, update:
```gradle
ext {
    extName = 'Your Source Name'  // Display name
    extClass = '.YourClassName'    // Main class name
    extVersionCode = 1
}
```

## Architecture Overview

```
ApiMangas.kt (Main Extension Class)
├── Authentication
│   ├── authenticateUser() - Get token from API
│   ├── getAuthToken() - Manage token state
│   └── OkHttpClient with authenticator
├── Manga Operations
│   ├── popularMangaRequest/Parse
│   ├── latestUpdatesRequest/Parse
│   ├── searchMangaRequest/Parse
│   ├── mangaDetailsRequest/Parse
│   └── MangaDto.toSManga() - Convert with metadata
├── Chapter Operations
│   ├── chapterListRequest/Parse
│   └── ChapterDto.toSChapter()
├── Page/Image Operations
│   ├── pageListRequest/Parse
│   └── Decryption params added to URLs
└── Configuration
    └── setupPreferenceScreen() - Settings UI

ImageDecryptionInterceptor.kt
├── Intercepts image requests
├── Detects decryption parameters
├── Downloads encrypted image
├── Decrypts using appropriate method
└── Returns decrypted image

ApiMangasFilters.kt
├── Filter definitions
├── UriFilter interface for URL building
└── getFilterList() builder

ApiMangasDto.kt
├── API response models
├── MangaMetadata (for storing extra data)
└── Serializable classes for JSON
```

## Testing

1. **Build the extension:**
   ```bash
   ./gradlew :src:all:apimangas:assembleDebug
   ```

2. **Install on device/emulator:**
   - The APK will be in `src/all/apimangas/build/outputs/apk/debug/`
   - Install via `adb install` or Android Studio

3. **Configure in app:**
   - Go to Browse → Extensions → API Mangas → Settings
   - Enter API URL, username, and password
   - Try browsing and searching

4. **Test features:**
   - Test authentication
   - Test manga browsing
   - Test filters
   - Test image decryption

## Metadata Storage Pattern

The key innovation in this skeleton is the metadata storage pattern:

```kotlin
// When fetching manga from API
val manga = SManga.create().apply {
    val metadata = MangaMetadata(
        apiUrl = "/api/manga/123",
        decryptionKey = "key_from_api",
        encryptionIv = "iv_from_api"
    )
    url = json.encodeToString(metadata)  // Store as JSON
    title = "Manga Title"
    // ... other fields
}

// Later, when fetching chapters
val metadata = json.decodeFromString<MangaMetadata>(manga.url)
val decryptionKey = metadata.decryptionKey  // Retrieved!
```

This pattern allows storing arbitrary custom data without modifying the `SManga` class.

## Proposed Changes to Koma/Extensions-Lib

If the metadata storage pattern proves useful, consider proposing these changes to Koma:

### Option 1: Add metadata field to SManga
```kotlin
class SManga {
    // ... existing fields
    var metadata: String? = null  // For storing JSON metadata
}
```

### Option 2: Typed metadata support
```kotlin
class SManga {
    // ... existing fields
    var customData: Map<String, String>? = null
}
```

### Option 3: Extension-specific data store
```kotlin
interface MetadataStore {
    fun put(mangaUrl: String, key: String, value: String)
    fun get(mangaUrl: String, key: String): String?
}
```

## Dependencies

This extension uses:
- `lib-cryptoaes` - For CryptoJS-compatible AES decryption
- Standard Kotlin serialization for JSON
- OkHttp for network requests

## Troubleshooting

### Authentication Issues
- Verify API URL is correct
- Check username/password are correct
- Look at Logcat for error messages
- Enable verbose logging in Koma settings

### Decryption Issues
- Verify decryption keys are being stored correctly
- Check encryption method matches your API
- Test with a single image first
- Add logging to ImageDecryptionInterceptor

### Filter Issues
- Check API documentation for supported filters
- Verify query parameter names match API
- Test filters individually

## Contributing

If you use this skeleton and make improvements:
1. Consider sharing your changes back
2. Document any patterns you discover
3. Help improve this template for others

## License

Same as Koma Extensions - Apache 2.0

## Credits

Based on the Booklore extension and other Koma extensions.
