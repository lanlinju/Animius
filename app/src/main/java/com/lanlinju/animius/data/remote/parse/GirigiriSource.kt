package com.lanlinju.animius.data.remote.parse

import android.net.Uri
import com.lanlinju.animius.data.remote.dto.AnimeBean
import com.lanlinju.animius.data.remote.dto.AnimeDetailBean
import com.lanlinju.animius.data.remote.dto.EpisodeBean
import com.lanlinju.animius.data.remote.dto.HomeBean
import com.lanlinju.animius.data.remote.dto.VideoBean
import com.lanlinju.animius.util.DownloadManager
import com.lanlinju.animius.util.encodeForUrl
import com.lanlinju.animius.util.getDefaultDomain
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.select.Elements
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

object GirigiriSource : AnimeSource {

    override val DEFAULT_DOMAIN: String = "https://ani.girigirilove.com"
    override var baseUrl: String = getDefaultDomain()

    override suspend fun getSearchData(query: String, page: Int): List<AnimeBean> {
        // HTML 搜索页有 Cloudflare 验证码,改用 maccmsSuggest API(JSON 接口,免验证码)
        // 该接口没有分页参数,只有第一页:必须显式结束翻页,
        // 否则 PagingSource 永远拿不到空列表,会不断追加重复条目
        if (page > 1) return emptyList()
        val suggestUrl = "${baseUrl}/index.php/ajax/suggest?mid=1&wd=${query.encodeForUrl()}"
        val source = DownloadManager.getHtml(suggestUrl)
        val animeList = mutableListOf<AnimeBean>()
        try {
            val obj = org.json.JSONObject(source)
            val list = obj.optJSONArray("list") ?: org.json.JSONArray()
            for (i in 0 until list.length()) {
                val item = list.getJSONObject(i)
                val id = item.optString("id")
                val title = item.optString("name")
                val img = item.optString("pic")
                if (id.isNotBlank() && title.isNotBlank()) {
                    animeList.add(
                        AnimeBean(
                            title = title,
                            img = img.padDomain(),
                            url = "/GV$id/"
                        )
                    )
                }
            }
        } catch (e: Exception) {
            // 解析失败返回空列表
        }
        return animeList
    }

    override suspend fun getWeekData(): MutableMap<Int, List<AnimeBean>> {
        val source = DownloadManager.getHtml(baseUrl)
        val document = Jsoup.parse(source)
        val elements = document.select("div.wow")[0].select("div#week-module-box")
        val weekMap = mutableMapOf<Int, List<AnimeBean>>()
        elements.select("div.public-r").forEachIndexed { index, element ->
            val dayList = getAnimeList(element.select("div.public-list-box"))
            weekMap[index] = dayList
        }
        return weekMap
    }

    override suspend fun getHomeData(): List<HomeBean> {
        val source = DownloadManager.getHtml(baseUrl)
        val document = Jsoup.parse(source)
        val elements = document.select("div.wow").apply { removeAt(0) }
        val homeBeanList = mutableListOf<HomeBean>()
        for ((i, el) in elements.withIndex()) {
            if (i == 1 || i == elements.lastIndex) continue
            val title = el.select("div.title-left > h4").text()
            val moreUrl = el.select("div.title-right > a").attr("href")
            val homeItemBeanList = getAnimeList(el.select("div.public-list-box"))
            homeBeanList.add(HomeBean(title = title, moreUrl = moreUrl, animes = homeItemBeanList))
        }

        return homeBeanList
    }

    override suspend fun getAnimeDetail(detailUrl: String): AnimeDetailBean {
        val source = DownloadManager.getHtml("${baseUrl}/$detailUrl")
        val document = Jsoup.parse(source)
        val main = document.select("div.vod-detail")
        val title = main.select("h3").text()
        val desc = main.select("div#height_limit").text()
        val imgUrl = main.select("img").attr("data-src").padDomain()
        val tags =
            main.select("div.slide-info").last()?.select("a")?.map { it.text() }?.toMutableList()
                ?.also { it.removeAt(it.lastIndex) } ?: emptyList()
        val channels = getAnimeEpisodes(document.select("div.anthology-list").select("ul"))
        val relatedAnimes =
            getAnimeList(document.select("div.public-pic-b"))
        return AnimeDetailBean(title, imgUrl, desc, tags, relatedAnimes, channels = channels)
    }

    private fun getAnimeList(elements: Elements): List<AnimeBean> {
        val animeList = mutableListOf<AnimeBean>()
        elements.forEach { el ->
            el.select("div.public-list-div > a").apply {
                val title = attr("title")
                val url = attr("href")
                val imgUrl = select("img").attr("data-src").padDomain()
                val episodeName = select("span.public-list-prb").text()
                animeList.add(
                    AnimeBean(
                        title = title,
                        img = imgUrl,
                        url = url,
                        episodeName = episodeName
                    )
                )
            }
        }
        return animeList
    }

    override suspend fun getVideoData(episodeUrl: String): VideoBean {
        val url = "${baseUrl}/$episodeUrl"
        val source = DownloadManager.getHtml(url)
        val document = Jsoup.parse(source)
        val videoUrl = getVideoUrl(document)
        return VideoBean(videoUrl)
    }

    @OptIn(ExperimentalEncodingApi::class)
    private suspend fun getVideoUrl(document: Document): String {
        val videoUrlTarget = document.select("div.player-box > div.player-left > script")[0].data()
        val videoUrlRegex = """"url":"(.*?)","url_next"""".toRegex()
        val rawVideoUrl = videoUrlRegex.find(videoUrlTarget)?.groupValues?.get(1)
            ?: throw IllegalStateException("video url is empty")

        val encodedVideoUrl = String(Base64.decode(rawVideoUrl), Charsets.UTF_8)
        return Uri.decode(encodedVideoUrl)
    }

    private fun getAnimeEpisodes(elements: Elements): Map<Int, List<EpisodeBean>> {
        val channels = mutableMapOf<Int, List<EpisodeBean>>()
        elements.forEachIndexed { i, e ->
            val dramaElements = e.select("li").select("a")
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

    private fun String.padDomain(): String {
        return "$baseUrl$this"
    }
}