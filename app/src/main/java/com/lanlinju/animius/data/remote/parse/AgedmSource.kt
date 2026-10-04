package com.lanlinju.animius.data.remote.parse

import com.lanlinju.animius.data.remote.dto.AnimeBean
import com.lanlinju.animius.data.remote.dto.AnimeDetailBean
import com.lanlinju.animius.data.remote.dto.EpisodeBean
import com.lanlinju.animius.data.remote.dto.HomeBean
import com.lanlinju.animius.data.remote.dto.VideoBean
import com.lanlinju.animius.data.remote.parse.util.WebViewUtil
import com.lanlinju.animius.util.DownloadManager
import com.lanlinju.animius.util.getDefaultDomain
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.select.Elements

object AgedmSource : AnimeSource {

    override val DEFAULT_DOMAIN: String = "https://www.agedm.io/"

    override var baseUrl: String = getDefaultDomain()

    private val webViewUtil: WebViewUtil by lazy { WebViewUtil() }

    override fun onExit() {
        webViewUtil.clearWeb()
    }

    override suspend fun getWeekData(): MutableMap<Int, List<AnimeBean>> {
        val source = DownloadManager.getHtml(baseUrl)
        val document = Jsoup.parse(source)
        val weekMap = mutableMapOf<Int, List<AnimeBean>>()
        document.select("div.text_list_box").select("div.tab-pane")
            .forEachIndexed { index, element ->
                val dayList = mutableListOf<AnimeBean>()
                element.select("li").forEach { el ->
                    val title = el.select("a").text()
                    val episodeName = el.select("div.title_sub").text()
                    val url = el.select("a").attr("href")
                    dayList.add(AnimeBean(title = title, img = "", url = url, episodeName))
                }
                weekMap[index] = dayList
            }
        return weekMap
    }

    override suspend fun getSearchData(query: String, page: Int): List<AnimeBean> {
        val source = DownloadManager.getHtml("$baseUrl/search?query=$query&page=$page")
        val document = Jsoup.parse(source)
        val animeList = mutableListOf<AnimeBean>()
        document.select("div.card").forEach { el ->
            val title = el.select("h5").text()
            val url = el.select("h5 > a").attr("href")
            val imgUrl = el.select("img").attr("data-original")
            animeList.add(AnimeBean(title = title, img = imgUrl, url = url))
        }
        return animeList
    }

    override suspend fun getHomeData(): List<HomeBean> {
        val source = DownloadManager.getHtml(baseUrl)
        val document = Jsoup.parse(source)

        val homeBeanList = mutableListOf<HomeBean>()
        document.select("div.container").select("div.video_list_box").forEach { element ->
            val title = element.select("h6").text().replace("更多 »", "")
            val moreUrl = element.select("a").attr("href")
            val homeItemBeanList = getAnimeList(element.select("div.video_item"))
            homeBeanList.add(HomeBean(title = title, moreUrl = moreUrl, animes = homeItemBeanList))
        }

        return homeBeanList
    }

    override suspend fun getAnimeDetail(detailUrl: String): AnimeDetailBean {
        val source = DownloadManager.getHtml(detailUrl.toHttps())
        val document = Jsoup.parse(source)
        val videoDetailRight = document.select("div.video_detail_right")
        val title = videoDetailRight.select("h2").text()
        val desc = videoDetailRight.select("div.video_detail_desc").text()
        val imgUrl = document.select("div.video_detail_cover > img").attr("data-original")
        val detailBoxList = document.select("div.video_detail_box").select("li")
        val tags = detailBoxList[9].text().split("：")[1].split(" ").toMutableList()
        tags.add(detailBoxList[0].text().split("：")[1])
        tags.add(detailBoxList[1].text().split("：")[1])
        val playlist = document.select("div.tab-content").select("div.tab-pane")
        val channels = getAnimeEpisodes(playlist)
        val relatedAnimes =
            getAnimeList(document.select("div.video_list_box").select("div.video_item"))
        val animeDetailBean =
            AnimeDetailBean(title, imgUrl, desc, tags, relatedAnimes, channels = channels)

        return animeDetailBean
    }

    override suspend fun getVideoData(episodeUrl: String): VideoBean {
        val source = DownloadManager.getHtml("${baseUrl}/$episodeUrl")
        val document = Jsoup.parse(source)
        /*val elements = document.select("div.cata_video_item")
        val title = elements.select("h5").text()
        var episodeName = ""
        val playlist = document.select("div.playlist-source-tab").select("div.tab-pane")
        val episodes = getAnimeEpisodes(playlist, action = { episodeName = it })*/
        val videoUrl = getVideoUrl(document)

        return VideoBean(videoUrl)
    }

    private suspend fun getAnimeList(elements: Elements): List<AnimeBean> {
        val animeList = mutableListOf<AnimeBean>()
        elements.forEach { el ->
            val title = el.select("a").text()
            val url = el.select("a").attr("href")
            val imgUrl = el.select("img").attr("data-original")
            val episodeName = el.select("span.video_item--info").text()
            animeList.add(AnimeBean(title = title, img = imgUrl, url = url, episodeName))
        }
        return animeList
    }

    private suspend fun getAnimeEpisodes(elements: Elements): Map<Int, List<EpisodeBean>> {
        val channels = mutableMapOf<Int, List<EpisodeBean>>()
        elements.forEachIndexed { i, e ->
            val episodes = mutableListOf<EpisodeBean>()
            e.select("li").forEach { el ->
                val name = el.text()
                val url = el.select("a").attr("href")
                episodes.add(EpisodeBean(name, url))
            }
            channels[i] = episodes
        }

        return channels
    }

    private suspend fun getVideoUrl(document: Document): String {

        val videoUrl = document.select("#iframeForVideo").attr("src")
        if (videoUrl.isBlank()) throw IllegalStateException("播放页没有解析到播放器地址(iframeForVideo)")

        return webViewUtil.interceptRequest(
            url = videoUrl,
            // 两种地址形态都要覆盖:
            //  1. 带扩展名:https://vip.ffzy-plays.com/.../index.m3u8
            //  2. 不带扩展名:sid=1 走 ByteDance CDN,地址形如
            //     https://v16.akamaized.net/<hash>/<hash>/video/tos/alisg/<hash>/
            //     (24 分钟正片,无扩展名,靠 /video/tos/ 路径标识)
            // 扩展名后面的 (?|#|$) 是必须的:否则 https://jx.wuzhoupai.com:8443/m3u8/?url=...
            // 这种播放器页地址会被当成媒体地址,把 HTML 交给播放器。
            regex = "\\.(m3u8|mp4|flv|mkv)(\\?|#|$)|/video/tos/",
        )
    }

    private fun String.toHttps(): String = replace(Regex("^http:"), "https:")
}

