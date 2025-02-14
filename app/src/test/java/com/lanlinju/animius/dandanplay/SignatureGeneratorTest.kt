package com.lanlinju.animius.dandanplay

import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import java.util.Base64
import java.util.Date

object SignatureGeneratorTest {
    @JvmStatic
    fun main(args: Array<String>) {
        val appId = "your_app_id"
        val appSecret = "your_app_secret"
        val timestamp = Date().time / 1000
        val path = "/api/v2/comment/123450001"
        val signature = generateSignature(appId, timestamp, path, appSecret)
        println("X-AppId: $appId")
        println("X-Signature: $signature")
        println("X-Timestamp: $timestamp")
    }

    private fun generateSignature(
        appId: String,
        timestamp: Long,
        path: String,
        appSecret: String
    ): String? {
        val data = appId + timestamp + path + appSecret
        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            val hash = digest.digest(data.toByteArray())
            Base64.getEncoder().encodeToString(hash)
        } catch (e: NoSuchAlgorithmException) {
            e.printStackTrace(); null
        }
    }
}