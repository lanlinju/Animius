package com.sakura.anime.data.remote.parse

import com.sakura.anime.data.remote.dto.AnimeBean
import com.sakura.anime.data.remote.dto.AnimeDetailBean
import com.sakura.anime.data.remote.dto.EpisodeBean
import com.sakura.anime.data.remote.dto.HomeBean
import com.sakura.anime.data.remote.dto.VideoBean
import com.sakura.anime.data.remote.parse.util.WebViewUtil
import com.sakura.anime.util.DownloadManager
import com.sakura.anime.util.getDefaultDomain
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.select.Elements

object NyafunSource : AnimeSource {
    override val DEFAULT_DOMAIN = "https://www.nyadm.org/"
    override var baseUrl: String = getDefaultDomain()

    private val webViewUtil: WebViewUtil by lazy { WebViewUtil() }

    override fun onExit() {
        webViewUtil.clearWeb()
    }

    override suspend fun getHomeData(): List<HomeBean> {
        val source = DownloadManager.getHtml(baseUrl)
        val document = Jsoup.parse(source)

        val homeBeanList = mutableListOf<HomeBean>()
        document.select("div.box-width.wow").takeLast(2).forEach { element ->
            val title = element.select("h4").text()
            val moreUrl = element.select("a").attr("href")
            val homeItemBeanList = getAnimeList(element.select("div.public-list-box"))
            homeBeanList.add(HomeBean(title = title, moreUrl = moreUrl, animes = homeItemBeanList))
        }

        return homeBeanList
    }

    override suspend fun getAnimeDetail(detailUrl: String): AnimeDetailBean {
        val source = DownloadManager.getHtml("$baseUrl/$detailUrl")
        val document = Jsoup.parse(source)

        val detailInfo = document.select("div.detail-info")
        val title = detailInfo.select("h3").text()
        val desc = document.select("div#height_limit").text()
        val imgUrl = document.select("div.detail-pic > img").attr("data-src")
        val tags = detailInfo.select("span.slide-info-remarks").map { it.text() }
        val episodes = getAnimeEpisodes(document)
        val relatedAnimes =
            getAnimeList(document.select("div.box-width.wow").select("div.public-list-box"))

        return AnimeDetailBean(title, imgUrl, desc, "", tags, "", episodes, relatedAnimes)
    }

    private fun getAnimeEpisodes(
        document: Document,
        action: (String) -> Unit = {}
    ): List<EpisodeBean> {
        return document.select("div.anthology-list.top20")
            .select("li").map {
                if (it.select("em").size > 0) {
                    action(it.text())
                }
                EpisodeBean(it.text(), it.select("a").attr("href"))
            }
    }

    override suspend fun getVideoData(episodeUrl: String): VideoBean {
        val source = DownloadManager.getHtml("$baseUrl/$episodeUrl")
        val document = Jsoup.parse(source)
        val title = document.select("div.list-body").select("h2").text()
        val videoUrl = getVideoUrl("$baseUrl/$episodeUrl")
        var episodeName = ""
        val episodes = getAnimeEpisodes(document, action = { episodeName = it })

        val headers = mapOf("Referer" to baseUrl)
        return VideoBean(title, videoUrl, episodeName, episodes, headers)
    }

    private suspend fun getVideoUrl(url: String): String {
        return webViewUtil.interceptRequest(
            url = url,
            regex = ".*\\.(mp4|mkv|m3u8).*\\?verify=.*",
            userAgent = "Mozilla/5.0 (iPhone; CPU iPhone OS 18_0 like Mac OS X)"
        )
    }

    override suspend fun getSearchData(query: String, page: Int): List<AnimeBean> {
        val source = DownloadManager.getHtml("$baseUrl/search/wd/$query/page/$page.html")
        val document = Jsoup.parse(source)
        val animeList = mutableListOf<AnimeBean>()
        document.select("div.vod-detail").forEach { el ->
            val title = el.select("div.detail-info > a").text()
            val url = el.select("div.detail-info > a").attr("href")
            val imgUrl = el.select("img").attr("data-src")
            animeList.add(AnimeBean(title = title, img = imgUrl, url = url))
        }
        return animeList
    }

    override suspend fun getWeekData(): Map<Int, List<AnimeBean>> {
        val source = DownloadManager.getHtml(baseUrl)
        val document = Jsoup.parse(source)
        val weekMap = mutableMapOf<Int, List<AnimeBean>>()
        document.select("div#week-module-box")
            .select("div.public-r").forEachIndexed { index, element ->
                val dayList = getAnimeList(element.select("div.public-list-box"))
                weekMap[index] = dayList
            }
        return weekMap
    }

    /*@OptIn(ExperimentalEncodingApi::class)
    fun getVideoUrl(document: Document): String {
        val urlTarget = document.select(".player-left > script")[1].data()
        val urlRegex = """"url":"(.*?)","url_next"""".toRegex()
        val url = urlRegex.find(urlTarget)!!.groupValues[1]

        val encodedVideoUrl = String(Base64.decode(url), Charsets.UTF_8)
        return URLDecoder.decode(encodedVideoUrl)
    }*/

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

}