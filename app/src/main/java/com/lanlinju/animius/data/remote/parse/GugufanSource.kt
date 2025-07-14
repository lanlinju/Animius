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
        val nestedUrl = getVideoUrl("${baseUrl}/$episodeUrl")
        val document = getDocument(nestedUrl)
        val data = document.body().select("script").last()!!.data()
        val videoUrl = extractUrlWithRegex(data)
        val headers = createHeaders(videoUrl)
        return VideoBean(videoUrl, headers)
    }

    private fun createHeaders(videoUrl: String): Map<String, String> {
        return if (videoUrl.contains("m3u8"))
            emptyMap()
        else
            mapOf("Referer" to baseUrl, "User-Agent" to DefaultUserAgent)
    }

    fun extractUrlWithRegex(input: String): String {
        val regex = """，*"url"\s*:\s*"(https?://[^"]+)",.*""".toRegex()
        return regex.find(input)?.groupValues?.get(1) ?: error("Failed to extract Url")
    }

    private suspend fun getVideoUrl(url: String): String {
        return webViewUtil.interceptRequest(
            url = url,
            regex = "player/dp\\.php\\?key=",
            userAgent = DefaultUserAgent,
            timeoutMs = 20_000
        )
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