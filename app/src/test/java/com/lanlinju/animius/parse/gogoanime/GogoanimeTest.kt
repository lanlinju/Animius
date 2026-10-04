package com.lanlinju.animius.parse.gogoanime

import com.lanlinju.animius.data.remote.parse.parseEpisodes
import org.jsoup.Jsoup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Gogoanime 剧集解析离线单测(不联网,使用真实抓取片段)。
 *
 * 覆盖两点:
 *  1. href 提取 —— 选择器已定位到 `<a>`,直接 `it.attr("href")` 即可;
 *     历史上的 `it.select("a").attr("href")` 依赖 jsoup "select 包含自身"的行为,等价但绕。
 *  2. 顺序 —— 站点原始 HTML 是倒序(Episode 20 → Episode 1),解析后需为升序,
 *     与其他数据源(第01集 → 第N集)保持一致。
 *
 * 测试目标是顶层 [parseEpisodes](纯函数),不经过 [GogoanimeSource]:
 * 后者的 `baseUrl` 初始化依赖 Android Context,在 JVM 单测中不可用。
 */
class GogoanimeTest {

    private val detailHtml =
        "./src/test/java/com/lanlinju/animius/parse/gogoanime/html/detail.html"

    private fun document() = Jsoup.parse(File(detailHtml))

    @Test
    fun `episodes are parsed in ascending order`() {
        val episodes = parseEpisodes(document())

        assertEquals("应解析出全部 20 集", 20, episodes.size)
        assertEquals("首集应为第 1 集(升序)", "Episode 1", episodes.first().name)
        assertEquals("末集应为第 20 集(升序)", "Episode 20", episodes.last().name)
    }

    @Test
    fun `episode urls are extracted correctly`() {
        val episodes = parseEpisodes(document())

        // 直接 it.attr("href") 必须拿到真实地址,而不是空串
        assertEquals(
            "https://gogoanime.by/dragon-ball-daima-episode-1-english-subbed/",
            episodes.first().url
        )
        assertEquals(
            "https://gogoanime.by/dragon-ball-daima-episode-20-english-subbed/",
            episodes.last().url
        )
        assertTrue("不应出现空 URL", episodes.none { it.url.isBlank() })
        // 名称与 URL 的集数需一一对应(反转后不能错位)
        episodes.forEachIndexed { index, episode ->
            assertTrue(
                "[${episode.name}] 与 URL 不匹配: ${episode.url}",
                episode.url.endsWith("-episode-${index + 1}-english-subbed/")
            )
        }
    }

    @Test
    fun `empty container yields empty list`() {
        val episodes = parseEpisodes(
            Jsoup.parse("<html><body><div class=\"episodes-container\"></div></body></html>")
        )
        assertTrue("无剧集时不应抛异常", episodes.isEmpty())
    }
}
