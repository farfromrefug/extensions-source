package eu.kanade.tachiyomi.extension.all.apimangas

import android.util.Base64
import eu.kanade.tachiyomi.lib.cryptoaes.CryptoAES
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
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
        val encryptedData = response.body.bytes()
        val contentType = response.body.contentType()

        return try {
            // Decrypt the image data
            val decryptedData = when (decryptionMethod.lowercase()) {
                "aes-256-cbc", "aes-128-cbc" -> {
                    decryptAES(encryptedData, decryptionKey, encryptionIv)
                }
                "xor" -> {
                    decryptXOR(encryptedData, decryptionKey)
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
     * Decrypt data using AES encryption
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
     * Alternative: Use CryptoAES library for CryptoJS-compatible decryption
     * This is useful if the API uses CryptoJS on the server side
     */
    private fun decryptWithCryptoAES(cipherText: String, password: String): String {
        return CryptoAES.decrypt(cipherText, password)
    }
}
