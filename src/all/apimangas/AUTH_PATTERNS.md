# Authentication Patterns for API Extensions

This document provides concrete examples of different authentication methods you might encounter when building API-based manga extensions.

## Pattern 1: Basic Authentication (Username + Password)

**Use Case:** Simple APIs that use HTTP Basic Auth

```kotlin
override val client: OkHttpClient = network.cloudflareClient.newBuilder()
    .authenticator { _, response ->
        if (response.request.header("Authorization") != null) {
            null // Already tried, give up
        } else {
            response.request.newBuilder()
                .header("Authorization", Credentials.basic(username, password))
                .build()
        }
    }
    .build()
```

## Pattern 2: JWT Bearer Token

**Use Case:** Modern REST APIs using JWT tokens

```kotlin
private var accessToken: String? = null

private fun authenticateUser(username: String, password: String): String? {
    val body = FormBody.Builder()
        .add("username", username)
        .add("password", password)
        .build()

    val request = POST("$baseUrl/api/auth/login", headers, body)
    val response = client.newCall(request).execute()

    if (response.isSuccessful) {
        val data = json.decodeFromString<AuthResponse>(response.body.string())
        return data.accessToken
    }
    return null
}

override val client: OkHttpClient = network.cloudflareClient.newBuilder()
    .addInterceptor { chain ->
        val token = accessToken ?: getAuthToken()
        val request = if (token != null) {
            chain.request().newBuilder()
                .header("Authorization", "Bearer $token")
                .build()
        } else {
            chain.request()
        }
        chain.proceed(request)
    }
    .build()

@Serializable
data class AuthResponse(
    val accessToken: String,
    val tokenType: String = "Bearer",
    val expiresIn: Long,
)
```

## Pattern 3: OAuth2 with Refresh Token

**Use Case:** APIs using OAuth2 with token refresh

```kotlin
private var accessToken: String? = null
private var refreshToken: String? = null
private var tokenExpiry: Long = 0

override val client: OkHttpClient = network.cloudflareClient.newBuilder()
    .addInterceptor { chain ->
        var request = chain.request()
        
        // Check if token is expired
        if (System.currentTimeMillis() > tokenExpiry) {
            refreshAccessToken()
        }
        
        // Add token to request
        accessToken?.let { token ->
            request = request.newBuilder()
                .header("Authorization", "Bearer $token")
                .build()
        }
        
        val response = chain.proceed(request)
        
        // If we get 401, try refreshing token once
        if (response.code == 401 && refreshToken != null) {
            response.close()
            if (refreshAccessToken()) {
                // Retry request with new token
                request = request.newBuilder()
                    .header("Authorization", "Bearer $accessToken")
                    .build()
                return@addInterceptor chain.proceed(request)
            }
        }
        
        response
    }
    .build()

private fun authenticateUser(username: String, password: String): String? {
    val body = FormBody.Builder()
        .add("grant_type", "password")
        .add("username", username)
        .add("password", password)
        .add("client_id", "your_client_id")
        .build()

    val request = POST("$baseUrl/oauth/token", headers, body)
    val response = client.newCall(request).execute()

    if (response.isSuccessful) {
        val data = json.decodeFromString<OAuthTokenResponse>(response.body.string())
        accessToken = data.accessToken
        refreshToken = data.refreshToken
        tokenExpiry = System.currentTimeMillis() + (data.expiresIn * 1000)
        
        // Persist refresh token
        preferences.edit()
            .putString("refresh_token", refreshToken)
            .putLong("token_expiry", tokenExpiry)
            .apply()
        
        return data.accessToken
    }
    return null
}

private fun refreshAccessToken(): Boolean {
    val refresh = refreshToken ?: return false
    
    val body = FormBody.Builder()
        .add("grant_type", "refresh_token")
        .add("refresh_token", refresh)
        .add("client_id", "your_client_id")
        .build()

    val request = POST("$baseUrl/oauth/token", headers, body)
    val response = client.newCall(request).execute()

    if (response.isSuccessful) {
        val data = json.decodeFromString<OAuthTokenResponse>(response.body.string())
        accessToken = data.accessToken
        refreshToken = data.refreshToken
        tokenExpiry = System.currentTimeMillis() + (data.expiresIn * 1000)
        
        preferences.edit()
            .putString("refresh_token", refreshToken)
            .putLong("token_expiry", tokenExpiry)
            .apply()
        
        return true
    }
    return false
}

@Serializable
data class OAuthTokenResponse(
    val accessToken: String,
    val refreshToken: String,
    val expiresIn: Long,
    val tokenType: String = "Bearer",
)
```

## Pattern 4: API Key Authentication

**Use Case:** APIs that use static API keys

```kotlin
private val apiKey: String
    get() = preferences.getString(PREF_API_KEY, "")!!

override fun headersBuilder() = super.headersBuilder()
    .add("X-API-Key", apiKey)
    // or
    .add("Authorization", "ApiKey $apiKey")

override fun setupPreferenceScreen(screen: PreferenceScreen) {
    EditTextPreference(screen.context).apply {
        key = PREF_API_KEY
        title = "API Key"
        summary = if (apiKey.isEmpty()) "Not set" else "••••${apiKey.takeLast(4)}"
        setDefaultValue("")
        dialogTitle = "API Key"
    }.also(screen::addPreference)
}
```

## Pattern 5: Session-based Authentication

**Use Case:** Traditional session cookies

```kotlin
private var sessionCookie: String? = null

private fun authenticateUser(username: String, password: String): String? {
    val body = FormBody.Builder()
        .add("username", username)
        .add("password", password)
        .build()

    val request = POST("$baseUrl/login", headers, body)
    val response = client.newCall(request).execute()

    if (response.isSuccessful) {
        // Extract session cookie
        val cookies = response.headers("Set-Cookie")
        sessionCookie = cookies.firstOrNull { it.startsWith("session=") }
        return sessionCookie
    }
    return null
}

override val client: OkHttpClient = network.cloudflareClient.newBuilder()
    .addInterceptor { chain ->
        val request = if (sessionCookie != null) {
            chain.request().newBuilder()
                .header("Cookie", sessionCookie!!)
                .build()
        } else {
            chain.request()
        }
        chain.proceed(request)
    }
    .build()
```

## Pattern 6: Custom Authentication Header

**Use Case:** APIs with proprietary auth schemes

```kotlin
private fun generateAuthSignature(timestamp: String, nonce: String): String {
    val message = "$username:$timestamp:$nonce"
    val key = SecretKeySpec(password.toByteArray(), "HmacSHA256")
    val mac = Mac.getInstance("HmacSHA256")
    mac.init(key)
    val signature = mac.doFinal(message.toByteArray())
    return Base64.encodeToString(signature, Base64.NO_WRAP)
}

override val client: OkHttpClient = network.cloudflareClient.newBuilder()
    .addInterceptor { chain ->
        val timestamp = System.currentTimeMillis().toString()
        val nonce = UUID.randomUUID().toString()
        val signature = generateAuthSignature(timestamp, nonce)
        
        val request = chain.request().newBuilder()
            .header("X-Auth-Username", username)
            .header("X-Auth-Timestamp", timestamp)
            .header("X-Auth-Nonce", nonce)
            .header("X-Auth-Signature", signature)
            .build()
        
        chain.proceed(request)
    }
    .build()
```

## Pattern 7: Two-Factor Authentication (2FA)

**Use Case:** APIs requiring 2FA codes

```kotlin
private var authToken: String? = null
private var requires2FA: Boolean = false
private var tempAuthToken: String? = null

private fun authenticateUser(username: String, password: String): String? {
    val body = FormBody.Builder()
        .add("username", username)
        .add("password", password)
        .build()

    val request = POST("$baseUrl/api/auth/login", headers, body)
    val response = client.newCall(request).execute()

    if (response.code == 202) {
        // Requires 2FA
        val data = json.decodeFromString<AuthResponse>(response.body.string())
        requires2FA = true
        tempAuthToken = data.tempToken
        return null
    }
    
    if (response.isSuccessful) {
        val data = json.decodeFromString<AuthResponse>(response.body.string())
        return data.accessToken
    }
    
    return null
}

private fun authenticate2FA(code: String): String? {
    if (tempAuthToken == null) return null
    
    val body = FormBody.Builder()
        .add("temp_token", tempAuthToken!!)
        .add("code", code)
        .build()

    val request = POST("$baseUrl/api/auth/verify-2fa", headers, body)
    val response = client.newCall(request).execute()

    if (response.isSuccessful) {
        val data = json.decodeFromString<AuthResponse>(response.body.string())
        requires2FA = false
        tempAuthToken = null
        return data.accessToken
    }
    
    return null
}

override fun setupPreferenceScreen(screen: PreferenceScreen) {
    // Standard username/password fields
    // ...
    
    if (requires2FA) {
        EditTextPreference(screen.context).apply {
            key = "2fa_code"
            title = "2FA Code"
            summary = "Enter your two-factor authentication code"
            dialogTitle = "2FA Code"
            inputType = InputType.TYPE_CLASS_NUMBER
            
            setOnPreferenceChangeListener { _, newValue ->
                val code = newValue as String
                val token = authenticate2FA(code)
                if (token != null) {
                    authToken = token
                    Toast.makeText(context, "2FA verified", Toast.LENGTH_SHORT).show()
                    true
                } else {
                    Toast.makeText(context, "Invalid code", Toast.LENGTH_SHORT).show()
                    false
                }
            }
        }.also(screen::addPreference)
    }
}
```

## Pattern 8: Device Registration

**Use Case:** APIs that require device registration

```kotlin
private var deviceId: String? = null
private var deviceToken: String? = null

init {
    // Load or generate device ID
    deviceId = preferences.getString("device_id", null) ?: run {
        val newId = UUID.randomUUID().toString()
        preferences.edit().putString("device_id", newId).apply()
        newId
    }
}

private fun registerDevice(): Boolean {
    val body = FormBody.Builder()
        .add("device_id", deviceId!!)
        .add("device_name", "Koma Extension")
        .add("device_type", "android")
        .build()

    val request = POST("$baseUrl/api/devices/register", headers, body)
    val response = client.newCall(request).execute()

    if (response.isSuccessful) {
        val data = json.decodeFromString<DeviceResponse>(response.body.string())
        deviceToken = data.token
        preferences.edit().putString("device_token", deviceToken).apply()
        return true
    }
    return false
}

private fun authenticateUser(username: String, password: String): String? {
    // Ensure device is registered
    if (deviceToken == null) {
        if (!registerDevice()) {
            return null
        }
    }
    
    val body = FormBody.Builder()
        .add("username", username)
        .add("password", password)
        .add("device_id", deviceId!!)
        .add("device_token", deviceToken!!)
        .build()

    val request = POST("$baseUrl/api/auth/login", headers, body)
    val response = client.newCall(request).execute()

    if (response.isSuccessful) {
        val data = json.decodeFromString<AuthResponse>(response.body.string())
        return data.accessToken
    }
    return null
}

@Serializable
data class DeviceResponse(
    val token: String,
    val deviceId: String,
)
```

## Pattern 9: Rate Limiting with Backoff

**Use Case:** APIs with rate limits

```kotlin
private var lastRequestTime = 0L
private val minRequestInterval = 1000L // 1 second between requests

override val client: OkHttpClient = network.cloudflareClient.newBuilder()
    .addInterceptor { chain ->
        // Enforce minimum time between requests
        val now = System.currentTimeMillis()
        val timeSinceLastRequest = now - lastRequestTime
        if (timeSinceLastRequest < minRequestInterval) {
            Thread.sleep(minRequestInterval - timeSinceLastRequest)
        }
        lastRequestTime = System.currentTimeMillis()
        
        val response = chain.proceed(chain.request())
        
        // Handle rate limit responses
        if (response.code == 429) {
            val retryAfter = response.header("Retry-After")?.toLongOrNull() ?: 60
            Log.w("ApiMangas", "Rate limited, waiting $retryAfter seconds")
            response.close()
            
            Thread.sleep(retryAfter * 1000)
            return@addInterceptor chain.proceed(chain.request())
        }
        
        response
    }
    .build()
```

## Testing Authentication

Here's a helper function to test authentication:

```kotlin
fun testAuthentication(context: Context): Boolean {
    return try {
        val token = authenticateUser(username, password)
        if (token != null) {
            Toast.makeText(context, "Authentication successful", Toast.LENGTH_SHORT).show()
            true
        } else {
            Toast.makeText(context, "Authentication failed", Toast.LENGTH_SHORT).show()
            false
        }
    } catch (e: Exception) {
        Log.e("ApiMangas", "Auth test failed", e)
        Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_LONG).show()
        false
    }
}

// Add to preferences screen
override fun setupPreferenceScreen(screen: PreferenceScreen) {
    // ... other preferences
    
    Preference(screen.context).apply {
        key = "test_auth"
        title = "Test Authentication"
        summary = "Verify your credentials work"
        
        setOnPreferenceClickListener {
            testAuthentication(screen.context)
            true
        }
    }.also(screen::addPreference)
}
```

## Security Best Practices

1. **Never log credentials or tokens:**
```kotlin
// BAD
Log.d("Auth", "Password: $password")

// GOOD
Log.d("Auth", "Attempting authentication...")
```

2. **Clear sensitive data on logout:**
```kotlin
fun logout() {
    accessToken = null
    refreshToken = null
    preferences.edit()
        .remove(PREF_USERNAME)
        .remove(PREF_PASSWORD)
        .remove("refresh_token")
        .remove("access_token")
        .apply()
}
```

3. **Use HTTPS only:**
```kotlin
init {
    require(baseUrl.startsWith("https://")) {
        "API must use HTTPS for security"
    }
}
```

4. **Handle token expiry gracefully:**
```kotlin
private fun isTokenExpired(): Boolean {
    val expiry = preferences.getLong("token_expiry", 0)
    return System.currentTimeMillis() > expiry
}
```

5. **Implement proper error handling:**
```kotlin
private fun authenticateUser(username: String, password: String): String? {
    if (username.isEmpty() || password.isEmpty()) {
        return null
    }
    
    return try {
        // Authentication logic
    } catch (e: IOException) {
        Log.e("Auth", "Network error", e)
        null
    } catch (e: Exception) {
        Log.e("Auth", "Unexpected error", e)
        null
    }
}
```

## Choose the Right Pattern

- **Basic Auth:** Simple but less secure, good for private/internal APIs
- **JWT/Bearer:** Modern standard, good for most REST APIs
- **OAuth2:** Complex but secure, good for public APIs
- **API Key:** Simple for read-only or service accounts
- **Session:** Traditional but requires cookie management
- **Custom:** When API has unique requirements
- **2FA:** When security is critical
- **Device Registration:** When API wants to track devices

Pick the pattern that matches your target API's authentication method.
