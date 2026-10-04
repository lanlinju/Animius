package com.lanlinju.animius.data.remote.parse

import com.lanlinju.animius.data.remote.dto.AnimeBean
import com.lanlinju.animius.data.remote.dto.AnimeDetailBean
import com.lanlinju.animius.data.remote.dto.EpisodeBean
import com.lanlinju.animius.data.remote.dto.HomeBean
import com.lanlinju.animius.data.remote.dto.VideoBean
import com.lanlinju.animius.data.remote.parse.util.WebViewUtil
import com.lanlinju.animius.util.DefaultUserAgent
import com.lanlinju.animius.util.DownloadManager
import com.lanlinju.animius.util.getDefaultDomain
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.select.Elements

class GugufanSource : AnimeSource {
    override val DEFAULT_DOMAIN = "https://www.gugu3.com/"
    override var baseUrl: String = getDefaultDomain()

    private val webViewUtil: WebViewUtil by lazy { WebViewUtil() }

    override fun onExit() {
        webViewUtil.clearWeb()
    }

    private suspend fun getDocument(
        url: String,
        headers: Map<String, String> = emptyMap()
    ): Document {
        val source = DownloadManager.getHtml(url, headers)
        return Jsoup.parse(source)
    }

    override suspend fun getHomeData(): List<HomeBean> {
        val document = getDocument(baseUrl)

        val homeBeanList = mutableListOf<HomeBean>()
        document.select("div.box-width.wow")
            .apply {
                removeAt(0)
                removeAt(size - 1)
            }.forEach { element ->
                val title = element.select("h4").text()
                val homeItemBeanList = getAnimeList(element.select("div.public-list-box"))
                homeBeanList.add(HomeBean(title = title, animes = homeItemBeanList))
            }

        return homeBeanList
    }

    private fun getAnimeList(elements: Elements): List<AnimeBean> {
        val animeList = mutableListOf<AnimeBean>()
        elements.forEach { el ->
            val title = el.select("div.public-list-button > a").text()
            val url = el.select("a").attr("href")
            val imgUrl = el.select("img").attr("data-src")
            val episodeName = el.select("span.public-list-prb").text()
            animeList.add(AnimeBean(title = title, img = imgUrl, url = url, episodeName))
        }
        return animeList
    }

    override suspend fun getAnimeDetail(detailUrl: String): AnimeDetailBean {
        val document = getDocument("$baseUrl/$detailUrl")

        val detailInfo = document.select("div.detail-info")
        val title = detailInfo.select("h3").text()
        val desc = document.select("div#height_limit").text()
        val imgUrl = document.select("div.detail-pic > img").attr("data-src")
        val tags = detailInfo.select("span.slide-info-remarks").map { it.text() }
        val channels = getAnimeEpisodes(document)
        val relatedAnimes =
            getAnimeList(document.select("div.box-width.wow").select("div.public-list-box"))

        return AnimeDetailBean(title, imgUrl, desc, tags, relatedAnimes, channels = channels)
    }

    private fun getAnimeEpisodes(document: Document): Map<Int, List<EpisodeBean>> {
        val channels = mutableMapOf<Int, List<EpisodeBean>>()
        document.select("div.anthology-list.top20.select-a > div.anthology-list-box")
            .forEachIndexed { i, e ->
                val dramaElements = e.select("li").select("a")//剧集列表
                val episodes = mutableListOf<EpisodeBean>()
                dramaElements.forEach { el ->
                    val name = el.text()
                    val url = el.attr("href")
                    episodes.add(EpisodeBean(name, url))
                }
                channels[i] = episodes
            }
        return channels
    }

    override suspend fun getVideoData(episodeUrl: String): VideoBean {
        // 播放页: player_aaaa.url = vwnet-<hash> yunjie token
        // 通过 yunjie 解析 API 获取真实 m3u8,或 WebView 嗅探兜底
        val playPage = if (episodeUrl.startsWith("http")) episodeUrl else "$baseUrl${episodeUrl.removePrefix("/")}"
        val videoUrl = getVideoUrlFromPage(playPage)
        val headers = createHeaders()
        return VideoBean(videoUrl, headers)
    }

    private suspend fun getVideoUrlFromPage(playPage: String): String {
        // 1. 解析播放页,提取 player_aaaa 对象中的 url(yunjie token,如 vwnet-xxx)
        val source = DownloadManager.getHtml(playPage)
        val token = extractPlayerAaaaUrl(source)
        if (!token.isNullOrBlank() && !token.startsWith("http")) {
            // 2. GET 播放器页面(带 Referer + Sec-Fetch 头),提取 time/key/vkey
            val playerHtml = runCatching {
                DownloadManager.getHtml(
                    "https://player.gugu3.com/?url=$token",
                    mapOf(
                        "Referer" to baseUrl,
                        "Sec-Fetch-Dest" to "iframe",
                        "Sec-Fetch-Mode" to "navigate",
                        "Sec-Fetch-Site" to "same-site"
                    )
                )
            }.getOrNull()
            if (!playerHtml.isNullOrBlank()) {
                val time = Regex("\"time\"\\s*:\\s*\"?([0-9]+)\"?").find(playerHtml)?.groupValues?.get(1)
                val key = Regex("\"key\"\\s*:\\s*\"([^\"]*)\"").find(playerHtml)?.groupValues?.get(1)
                val vkey = Regex("\"vkey\"\\s*:\\s*\"([^\"]*)\"").find(playerHtml)?.groupValues?.get(1)
                if (time != null && vkey != null) {
                    // 3. POST mizhi_json.php 获取真实 m3u8
                    val apiJson = DownloadManager.postForm(
                        url = "https://player.gugu3.com/admin/mizhi_json.php",
                        form = mapOf(
                            "url" to token,
                            "time" to time,
                            "key" to (key ?: ""),
                            "vkey" to vkey
                        ),
                        headers = mapOf(
                            "Accept" to "application/json, text/javascript, */*; q=0.01",
                            "X-Requested-With" to "XMLHttpRequest",
                            "Origin" to "https://player.gugu3.com",
                            "Referer" to "https://player.gugu3.com/?url=$token"
                        )
                    )
                    // 提取 json.url 中的 m3u8/mp4
                    // 处理转义斜杠: https:\/\/xxx 或 https:\/\/xxx\/yyy
                    val decodedJson = apiJson.replace("\\/", "/")
                    Regex("\"url\"\\s*:\\s*\"(https?://[^\"]+)\"").find(decodedJson)?.groupValues?.get(1)
                        ?.let { return it }
                    Regex("https?://[^\"'\\s]+\\.(?:m3u8|mp4)(?:[?#][^\"'\\s]*)?")
                        .find(decodedJson)?.value?.let { return it }
                }
            }
        }
        // 4. 兜底:WebView 嗅探 m3u8/mp4
        return webViewUtil.interceptRequest(
            url = playPage,
            regex = ".*\\.(m3u8|mp4|flv|mkv).*(\\?.*)?$",
            userAgent = DefaultUserAgent,
            timeoutMs = 30_000
        )
    }

    /** 从播放页 HTML 提取 player_aaaa 对象中的 url 字段 */
    private fun extractPlayerAaaaUrl(html: String): String? {
        // 先定位 player_aaaa 对象
        val start = html.indexOf("player_aaaa")
        if (start == -1) return null
        val paSub = html.substring(start, (start + 5000).coerceAtMost(html.length))
        return Regex("\"url\"\\s*:\\s*\"([^\"]+)\"").find(paSub)?.groupValues?.get(1)
    }

    private fun createHeaders(): Map<String, String> {
        // 曾经对 m3u8 返回 emptyMap():因为 headers 非空时播放器走 setMediaSource(),
        // 而当时 mediaSourceCreator 固定用 ProgressiveMediaSource,无法解析 HLS。
        // 该 bug 已在 video-player 修复(m3u8 改用 HlsMediaSource),
        // 因此不再需要这个特例,统一带 Referer/UA(部分 CDN 需要 UA 才放行)。
        return mapOf("Referer" to baseUrl, "User-Agent" to DefaultUserAgent)
    }

    override suspend fun getSearchData(
        query: String,
        page: Int
    ): List<AnimeBean> {
        val document = getDocument("${baseUrl}/index.php/vod/search/page/$page/wd/$query.html")
        val animeList = mutableListOf<AnimeBean>()
        document.select("div.public-list-box").forEach { el ->
            val title = el.select("div.thumb-txt").text()
            val url = el.select("a.public-list-exp").attr("href")
            val imgUrl = el.select("img").attr("data-src")
            animeList.add(AnimeBean(title = title, img = imgUrl, url = url))
        }
        return animeList
    }

    override suspend fun getWeekData(): Map<Int, List<AnimeBean>> {
        val document = getDocument(baseUrl)
        val weekMap = mutableMapOf<Int, List<AnimeBean>>()
        document.select("div#week-module-box")
            .select("div.public-r").forEachIndexed { index, element ->
                val dayList = getAnimeList(element.select("div.public-list-box"))
                weekMap[index] = dayList
            }
        return weekMap
    }

}