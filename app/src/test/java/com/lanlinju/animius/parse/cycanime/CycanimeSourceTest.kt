package com.lanlinju.animius.parse.cycanime

import com.lanlinju.animius.data.remote.parse.shouldRefreshToken
import com.lanlinju.animius.data.remote.parse.toBearerValue
import com.lanlinju.animius.data.remote.parse.util.SourceAuthManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * 次元城的纯函数单测(JVM,不需要设备/网络)。
 *
 * 只放不依赖 Android 的逻辑;需要 WebView / Context / 网络的用例见
 * `app/src/androidTest/.../parse/cycanime/CycaniLoginFlowTest.kt`。
 */
class CycanimeSourceTest {

    /**
     * 回归:站点下发的 token 可能自带 `Bearer ` 前缀。
     * 若直接拼 `"Bearer $token"` 会得到 `Bearer Bearer eyJ...`,服务端返回 401。
     */
    @Test
    fun bearerPrefixIsNormalized() {
        val raw = "eyJhbGciOiJIUzI1NiJ9.payload.sig"

        assertEquals("裸 token 应补上前缀", "Bearer $raw", raw.toBearerValue())
    }

    @Test
    fun existingBearerPrefixIsNotDuplicated() {
        val prefixed = "Bearer eyJhbGciOiJIUzI1NiJ9.payload.sig"

        // 已带前缀:原样保留,不得叠加成 "Bearer Bearer ..."
        assertEquals(prefixed, prefixed.toBearerValue())
        // 大小写不敏感
        assertEquals(
            "bearer eyJhbGciOiJIUzI1NiJ9.payload.sig",
            "bearer eyJhbGciOiJIUzI1NiJ9.payload.sig".toBearerValue()
        )
        assertEquals(
            "BEARER eyJhbGciOiJIUzI1NiJ9.payload.sig",
            "BEARER eyJhbGciOiJIUzI1NiJ9.payload.sig".toBearerValue()
        )
    }

    @Test
    fun surroundingWhitespaceIsTrimmed() {
        val raw = "eyJhbGciOiJIUzI1NiJ9.payload.sig"

        assertEquals("Bearer $raw", "  $raw  ".toBearerValue())
        assertEquals(
            "bearer eyJhbGciOiJIUzI1NiJ9.payload.sig",
            "  bearer eyJhbGciOiJIUzI1NiJ9.payload.sig  ".toBearerValue()
        )
    }

    // ---------- 登录会话载荷解析 ----------

    /**
     * 站点真实的会话结构(2026-09-15 实测):token 自带 `Bearer ` 前缀,
     * expiresAt 是 **ISO 字符串**(9 位小数 + 偏移),不是时间戳数字。
     */
    @Test
    fun loginPayloadParsesTokenAndExpiry() {
        val payload = "Bearer eyJhbGciOiJIUzI1NiJ9.payload.sig|2026-09-22T00:42:18.332566444+08:00"

        val session = SourceAuthManager.parseLoginPayload(payload)

        assertEquals("Bearer eyJhbGciOiJIUzI1NiJ9.payload.sig", session?.token)
        assertEquals("2026-09-22T00:42:18.332566444+08:00", session?.expiresAt)
    }

    @Test
    fun loginPayloadWithoutExpiryIsAccepted() {
        val session = SourceAuthManager.parseLoginPayload("TEST_TOKEN_123")

        assertEquals("TEST_TOKEN_123", session?.token)
        assertEquals("站点未给过期时间时应留空,而不是抛异常", "", session?.expiresAt)
    }

    @Test
    fun loginPayloadWithoutTokenIsRejected() {
        // 没读到会话 / 会话里没有 token:脚本返回空串
        assertNull(SourceAuthManager.parseLoginPayload(""))
        assertNull(SourceAuthManager.parseLoginPayload("   "))
        assertNull("只有过期时间,没有 token,应视为没读到", SourceAuthManager.parseLoginPayload("|2026-09-22T00:42:18+08:00"))
    }

    @Test
    fun loginPayloadTrimsWhitespace() {
        val session = SourceAuthManager.parseLoginPayload("  TEST_TOKEN_123 | 2026-09-22T00:42:18+08:00  ")

        assertEquals("TEST_TOKEN_123", session?.token)
        assertEquals("2026-09-22T00:42:18+08:00", session?.expiresAt)
    }

    // ---------- 主动续期时机 ----------

    private val savedAt = 1_000_000_000_000L
    private val sevenDays = ChronoUnit.DAYS.duration.toMillis() * 7

    private fun expiresAtIsoAfter(duration: Long): String =
        Instant.ofEpochMilli(savedAt + duration).toString()

    private fun shouldRefresh(nowOffset: Long): Boolean = shouldRefreshToken(
        expiresAtIso = expiresAtIsoAfter(sevenDays),
        savedAtMillis = savedAt,
        nowMillis = savedAt + nowOffset,
    )

    @Test
    fun refreshIsNotDueWhenSessionIsFresh() {
        assertFalse("刚保存的会话不该续期", shouldRefresh(0L))
        assertFalse("还剩 3 天(不足一半阈值未到)不该续期", shouldRefresh(ChronoUnit.DAYS.duration.toMillis() * 3))
    }

    @Test
    fun refreshIsDueWhenRemainingDropsToHalf() {
        // 7 天的会话,剩 3.5 天正好是 50% 阈值
        val threeAndAHalfDays = ChronoUnit.DAYS.duration.toMillis() * 7 / 2

        assertTrue("剩余刚好一半时应开始续期", shouldRefresh(threeAndAHalfDays))
        assertTrue("临近过期更应续期", shouldRefresh(sevenDays - ChronoUnit.HOURS.duration.toMillis()))
    }

    @Test
    fun refreshIsNotDueWhenAlreadyExpired() {
        // 已经过期时续期只会拿到 1001 Invalid Token,应交给 401 分支引导重新登录
        assertFalse("已过期不该再发续期请求", shouldRefresh(sevenDays + 1))
    }

    @Test
    fun refreshIsNotDueWhenExpiryIsUnknownOrInvalid() {
        val now = savedAt + sevenDays

        assertFalse(
            "站点没给过期时间时无法预判,只能等 401",
            shouldRefreshToken(expiresAtIso = "", savedAtMillis = savedAt, nowMillis = now)
        )
        assertFalse(
            "过期时间格式不认识时不应误判",
            shouldRefreshToken(expiresAtIso = "not-a-time", savedAtMillis = savedAt, nowMillis = now)
        )
        assertFalse(
            "没有保存时刻就无法推算总时长",
            shouldRefreshToken(
                expiresAtIso = expiresAtIsoAfter(sevenDays),
                savedAtMillis = 0L,
                nowMillis = now,
            )
        )
    }

    @Test
    fun refreshIsNotDueWhenSavedAtIsLaterThanExpiry() {
        // 设备时间被改过 / 站点返回异常:总时长算出来是负的,不能每次请求都续期
        val expiresAt = Instant.ofEpochMilli(savedAt).toString()

        assertFalse(
            shouldRefreshToken(
                expiresAtIso = expiresAt,
                savedAtMillis = savedAt,
                nowMillis = savedAt - 1,
            )
        )
    }

    @Test
    fun refreshThresholdScalesWithSiteLifetime() {
        // 站点把有效期从 7 天改成 1 天时,阈值应自动跟着缩,而不是每次都续期
        val oneDay = ChronoUnit.DAYS.duration.toMillis()

        assertFalse(
            "刚续期完(剩满 1 天)不该立刻再续",
            shouldRefreshToken(
                expiresAtIso = Instant.ofEpochMilli(savedAt + oneDay).toString(),
                savedAtMillis = savedAt,
                nowMillis = savedAt,
            )
        )
        assertTrue(
            "1 天有效期剩不足半天时应续期",
            shouldRefreshToken(
                expiresAtIso = Instant.ofEpochMilli(savedAt + oneDay).toString(),
                savedAtMillis = savedAt,
                nowMillis = savedAt + oneDay * 3 / 4,
            )
        )
    }
}
