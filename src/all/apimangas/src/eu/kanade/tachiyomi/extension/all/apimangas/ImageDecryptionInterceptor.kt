package eu.kanade.tachiyomi.extension.all.apimangas

import android.util.Base64
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Image Decryption Interceptor
 *
 * This interceptor handles decryption of images downloaded from the API.
 * It checks for special headers or query parameters containing decryption keys
 * and applies the appropriate decryption algorithm.
 *
 * Uses standard Android crypto APIs (javax.crypto) - no external dependencies needed.
 *
 * Usage:
 * 1. Include decryption info in the image URL as query parameters
 * 2. This interceptor will detect and decrypt the image before returning it
 */
class ImageDecryptionInterceptor : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val url = request.url

        // Check if this request contains decryption parameters
        val decryptionKey = url.queryParameter("decrypt_key")
        val encryptionIv = url.queryParameter("decrypt_iv")
        val decryptionMethod = url.queryParameter("decrypt_method") ?: "aes-256-cbc"

        // If no decryption info, proceed normally
        if (decryptionKey == null) {
            return chain.proceed(request)
        }

        // Build a clean request without decryption parameters
        val cleanUrl = url.newBuilder()
            .removeAllQueryParameters("decrypt_key")
            .removeAllQueryParameters("decrypt_iv")
            .removeAllQueryParameters("decrypt_method")
            .build()

        val cleanRequest = request.newBuilder()
            .url(cleanUrl)
            .build()

        // Get the encrypted response
        val response = chain.proceed(cleanRequest)
        if (!response.isSuccessful) {
            return response
        }

        // Get the encrypted image data
        val responseBody = response.body
        val encryptedData = responseBody.bytes()
        val contentType = responseBody.contentType()

        return try {
            // Decrypt the image data
            val decryptedData = when (decryptionMethod.lowercase()) {
                "aes-256-cbc", "aes-128-cbc" -> {
                    decryptAES(encryptedData, decryptionKey, encryptionIv)
                }
                "xor" -> {
                    decryptXOR(encryptedData, decryptionKey)
                }
                "cryptojs-aes" -> {
                    // CryptoJS-compatible AES decryption
                    decryptCryptoJSAES(encryptedData, decryptionKey)
                }
                else -> {
                    // Unknown method, return encrypted data
                    encryptedData
                }
            }

            // Build response with decrypted data
            Response.Builder()
                .request(request)
                .protocol(response.protocol)
                .code(200)
                .message(response.message)
                .headers(response.headers)
                .body(decryptedData.toResponseBody(contentType))
                .build()
        } catch (e: Exception) {
            // If decryption fails, return original response
            // Log the error in production code
            response
        } finally {
            response.close()
        }
    }

    /**
     * Decrypt data using AES encryption with explicit key and IV
     */
    private fun decryptAES(data: ByteArray, keyBase64: String, ivBase64: String?): ByteArray {
        val keyBytes = Base64.decode(keyBase64, Base64.DEFAULT)
        val ivBytes = ivBase64?.let { Base64.decode(it, Base64.DEFAULT) }
            ?: ByteArray(16) // Default IV if not provided

        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        val secretKey = SecretKeySpec(keyBytes, "AES")
        val ivSpec = IvParameterSpec(ivBytes)

        cipher.init(Cipher.DECRYPT_MODE, secretKey, ivSpec)
        return cipher.doFinal(data)
    }

    /**
     * Decrypt data using simple XOR encryption
     * This is a simple example - real implementations would be more complex
     */
    private fun decryptXOR(data: ByteArray, keyBase64: String): ByteArray {
        val keyBytes = Base64.decode(keyBase64, Base64.DEFAULT)
        val result = ByteArray(data.size)

        for (i in data.indices) {
            result[i] = (data[i].toInt() xor keyBytes[i % keyBytes.size].toInt()).toByte()
        }

        return result
    }

    /**
     * Decrypt using CryptoJS-compatible AES method
     * Uses KDF equivalent to OpenSSL's EVP_BytesToKey function
     * Compatible with CryptoJS.AES.decrypt() on the server side
     *
     * @param encryptedData Base64-encoded encrypted data (with "Salted__" prefix)
     * @param password The password/passphrase used for encryption
     * @throws IllegalArgumentException if data format is invalid
     */
    private fun decryptCryptoJSAES(encryptedData: ByteArray, password: String): ByteArray {
        // Decode base64 - throw if invalid
        val ctBytes = Base64.decode(encryptedData, Base64.DEFAULT)

        // Validate minimum length (must have "Salted__" (8 bytes) + salt (8 bytes) + at least 1 byte of data)
        if (ctBytes.size < 17) {
            throw IllegalArgumentException("CryptoJS data too short: ${ctBytes.size} bytes")
        }

        // Validate "Salted__" prefix
        val prefix = ctBytes.sliceArray(0..7)
        if (!prefix.contentEquals("Salted__".toByteArray(Charsets.US_ASCII))) {
            throw IllegalArgumentException("CryptoJS data missing 'Salted__' prefix")
        }

        // Extract salt (bytes 8-16 after "Salted__" prefix)
        val saltBytes = ctBytes.copyOfRange(8, 16)
        val cipherTextBytes = ctBytes.copyOfRange(16, ctBytes.size)

        // Generate key and IV using MD5 (OpenSSL EVP_BytesToKey)
        val md5 = MessageDigest.getInstance("MD5")
        val keyAndIV = generateKeyAndIV(32, 16, 1, saltBytes, password.toByteArray(Charsets.UTF_8), md5)
            ?: throw IllegalStateException("Failed to generate encryption key and IV")

        // Decrypt
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        val secretKey = SecretKeySpec(keyAndIV[0], "AES")
        val ivSpec = IvParameterSpec(keyAndIV[1])

        cipher.init(Cipher.DECRYPT_MODE, secretKey, ivSpec)
        return cipher.doFinal(cipherTextBytes)
    }

    /**
     * Generates a key and an initialization vector (IV) with the given salt and password.
     * This is equivalent to OpenSSL's EVP_BytesToKey function.
     *
     * @param keyLength the length of the generated key (in bytes)
     * @param ivLength the length of the generated IV (in bytes)
     * @param iterations the number of digestion rounds
     * @param salt the salt data (8 bytes of data)
     * @param password the password data
     * @param md the message digest algorithm to use
     * @return an two-element array with the generated key and IV
     */
    @Suppress("SameParameterValue")
    private fun generateKeyAndIV(
        keyLength: Int,
        ivLength: Int,
        iterations: Int,
        salt: ByteArray,
        password: ByteArray,
        md: MessageDigest,
    ): Array<ByteArray?>? {
        val digestLength = md.digestLength
        val requiredLength = (keyLength + ivLength + digestLength - 1) / digestLength * digestLength
        val generatedData = ByteArray(requiredLength)
        var generatedLength = 0

        return try {
            md.reset()

            // Repeat process until sufficient data has been generated
            while (generatedLength < keyLength + ivLength) {
                // Digest data (last digest if available, password data, salt if available)
                if (generatedLength > 0) {
                    md.update(generatedData, generatedLength - digestLength, digestLength)
                }
                md.update(password)
                md.update(salt, 0, 8)
                md.digest(generatedData, generatedLength, digestLength)

                // additional rounds
                for (i in 1 until iterations) {
                    md.update(generatedData, generatedLength, digestLength)
                    md.digest(generatedData, generatedLength, digestLength)
                }
                generatedLength += digestLength
            }

            // Copy key and IV into separate byte arrays
            val result = arrayOfNulls<ByteArray>(2)
            result[0] = generatedData.copyOfRange(0, keyLength)
            if (ivLength > 0) result[1] = generatedData.copyOfRange(keyLength, keyLength + ivLength)

            // Clean out temporary data
            generatedData.fill(0)

            result
        } catch (e: Exception) {
            null
        }
    }
}
