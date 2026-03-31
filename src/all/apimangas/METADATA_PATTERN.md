# Metadata Storage Pattern for Extensions

## Problem Statement

The current `SManga` model in the extensions library doesn't provide a way to store custom metadata that extensions might need, such as:
- Decryption keys for encrypted content
- API-specific identifiers
- Tokens or session data per manga
- Custom configuration per manga

## Current Solution (Workaround)

This extension demonstrates a workaround by storing custom metadata as JSON in the `SManga.url` field:

```kotlin
// Define custom metadata structure
@Serializable
data class MangaMetadata(
    val apiUrl: String,              // The actual API URL
    val decryptionKey: String? = null,  // Encryption key
    val encryptionIv: String? = null,   // Encryption IV
    // Add any custom fields you need
)

// Store metadata
manga.url = json.encodeToString(MangaMetadata(
    apiUrl = "/api/manga/123",
    decryptionKey = "base64Key",
    encryptionIv = "base64IV"
))

// Retrieve metadata later
val metadata = json.decodeFromString<MangaMetadata>(manga.url)
val key = metadata.decryptionKey
```

### Advantages
✅ Works with current extension API  
✅ No changes needed to Koma or extensions-lib  
✅ Flexible - can store any serializable data  
✅ Backward compatible

### Disadvantages
❌ Misuses the `url` field  
❌ Requires JSON serialization/deserialization overhead  
❌ Error-prone if not careful with encoding  
❌ Not discoverable - other developers won't know about this pattern  
❌ URL field can't be used for its original purpose directly

## Recommended Changes to Koma/Extensions-Lib

To properly support custom metadata in extensions, we recommend adding official support. Here are three options:

### Option 1: Simple String Metadata Field (Minimal Change)

Add a dedicated metadata field to `SManga`:

```kotlin
class SManga {
    // ... existing fields
    var url: String = ""
    var title: String = ""
    // ... other fields
    
    // NEW: For extension-specific metadata
    var metadata: String? = null
}
```

**Usage:**
```kotlin
manga.url = "/api/manga/123"  // Clean URL
manga.metadata = json.encodeToString(CustomMetadata(...))  // Custom data
```

**Pros:**
- Minimal change to existing code
- Flexible - extensions can store any string
- Clear separation of concerns

**Cons:**
- Still requires JSON serialization
- No type safety

### Option 2: Key-Value Metadata Map (Medium Change)

Add a typed map for metadata:

```kotlin
class SManga {
    // ... existing fields
    
    // NEW: Structured metadata storage
    var customData: Map<String, String>? = null
}
```

**Usage:**
```kotlin
manga.customData = mapOf(
    "decryption_key" to "base64Key",
    "encryption_iv" to "base64IV",
    "api_version" to "v2"
)
```

**Pros:**
- Type-safe keys
- Easy to query individual values
- No serialization needed for simple values

**Cons:**
- Only supports String values (could use `Map<String, Any>` but loses type safety)
- Slightly more complex

### Option 3: Extension Metadata Store (Major Change)

Provide a centralized metadata store service:

```kotlin
interface MangaMetadataStore {
    fun put(mangaUrl: String, key: String, value: String)
    fun get(mangaUrl: String, key: String): String?
    fun putAll(mangaUrl: String, data: Map<String, String>)
    fun getAll(mangaUrl: String): Map<String, String>
    fun remove(mangaUrl: String, key: String)
    fun clearAll(mangaUrl: String)
}

// Injected into extensions
class MyExtension : HttpSource() {
    private val metadataStore: MangaMetadataStore by inject()
    
    // Usage
    metadataStore.put(manga.url, "decryption_key", "base64Key")
    val key = metadataStore.get(manga.url, "decryption_key")
}
```

**Pros:**
- Clean separation from SManga model
- Centralized management
- Could be backed by database for persistence
- Easy to add features like expiration, encryption, etc.

**Cons:**
- Requires significant implementation work
- More complex architecture
- Needs dependency injection setup

## Recommendation

For Koma and extensions-lib, we recommend **Option 1** as the best balance:

1. **Implement Option 1 (metadata field) first** - it's simple and solves 90% of use cases
2. Consider Option 3 (metadata store) for a future version if more advanced features are needed
3. Skip Option 2 as it doesn't provide enough benefit over Option 1

### Implementation Plan for Option 1

1. Add `metadata: String?` field to `SManga` class in extensions-lib
2. Update database schema in Koma to include the metadata column
3. Update backup/restore logic to include metadata
4. Update serialization to include metadata
5. Document the field in extension development guides

### Migration Path

For extensions using the workaround pattern (like this one):

```kotlin
// Before (workaround)
manga.url = json.encodeToString(MangaMetadata(apiUrl = "/api/manga/123", ...))

// After (with metadata field)
manga.url = "/api/manga/123"
manga.metadata = json.encodeToString(CustomMetadata(...))
```

The migration is straightforward and can be done incrementally.

## Security Considerations

When storing metadata:

1. **Don't store sensitive data unencrypted** - especially user credentials
2. **Validate metadata on read** - handle corrupt or missing data gracefully
3. **Consider size limits** - don't store megabytes of data
4. **Clean up old metadata** - implement expiration if needed
5. **Document what's stored** - help users understand what data is kept

## Example Use Cases

Extensions that would benefit from metadata storage:

1. **Encrypted content sources**
   - Store decryption keys per manga
   - Store encryption algorithm details

2. **API-based sources**
   - Store API-specific IDs
   - Store pagination tokens
   - Store rate limit info

3. **DRM sources**
   - Store license keys
   - Store expiration times

4. **Multi-format sources**
   - Store preferred format per manga
   - Store quality settings

5. **Progressive web sources**
   - Store session tokens
   - Store user preferences per manga

## Testing

Extensions using metadata should test:

1. Metadata survives app restart
2. Metadata survives backup/restore
3. Handles missing/corrupt metadata gracefully
4. Doesn't break when metadata format changes
5. Migration from old format works

## Documentation Needs

If metadata field is added, document:

1. Purpose and use cases
2. Size recommendations
3. Security best practices
4. Serialization examples
5. Migration guide
6. Common patterns (like this extension demonstrates)

## Conclusion

The metadata storage pattern demonstrated in this extension is a working solution that can be used **right now** with the current extensions API. However, proper support through a dedicated field in `SManga` would be cleaner and more maintainable long-term.

We recommend the Koma team consider implementing Option 1 (metadata field) in a future release.
