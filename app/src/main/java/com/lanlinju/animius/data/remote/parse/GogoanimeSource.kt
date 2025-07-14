package com.lanlinju.animius.data.remote.parse

import com.lanlinju.animius.data.remote.dto.AnimeBean
import com.lanlinju.animius.data.remote.dto.AnimeDetailBean
import com.lanlinju.animius.data.remote.dto.EpisodeBean
import com.lanlinju.animius.data.remote.dto.HomeBean
import com.lanlinju.animius.data.remote.dto.VideoBean
import com.lanlinju.animius.di.DefaultHttpClient
import com.lanlinju.animius.util.DownloadManager
import com.lanlinju.animius.util.getDefaultDomain
import com.lanlinju.animius.util.getDocument
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.select.Elements
import java.time.LocalDate

/**
 * reference     https://github.com/consumet/api.consumet.org
 * @webSite      https://consumet-leox-api.vercel.app/anime/gogoanime
 */
object GogoanimeSource : AnimeSource {
    override val DEFAULT_DOMAIN: String = "https://gogoanime.by/"
    override var baseUrl: String = getDefaultDomain()

    private val client: HttpClient = DefaultHttpClient

    override suspend fun getHomeData(): List<HomeBean> {
        val document = getDocument(baseUrl)
        val homeBeanList = mutableListOf<HomeBean>()
        document.select("div.bixbox").let { element ->
            val title = element.select("h3").text()
            val homeItemList = getAnimeList(element.select("article.bs"))
            homeBeanList.add(HomeBean(title = title, animes = homeItemList))
        }
        return homeBeanList
    }

    override suspend fun getAnimeDetail(detailUrl: String): AnimeDetailBean {
        val source = DownloadManager.getHtml(detailUrl)
        val document = Jsoup.parse(source)
        val detailInfo = document.select("div.bigcontent")
        val title = detailInfo.select("h1").text()
        val desc = detailInfo.select("p").text()
        val imgUrl = detailInfo.select("img").attr("src")
        val tags = detailInfo.select("div.genxed > a").map { it.text() }
        val episodes = getAnimeEpisodes(document)
        val relatedAnimes = getAnimeList(document.select("div.listupd > article"))
        return AnimeDetailBean(title, imgUrl, desc, tags, relatedAnimes, episodes)
    }

    private fun getAnimeEpisodes(document: Document): List<EpisodeBean> {
        return document.select("div.episodes-container > div.episode-item > a")
            .map {
                EpisodeBean(it.text(), it.select("a").attr("href"))
            }
    }

    /**
     * @param episodeUrl e.g. https://gogoanime.by/dragon-ball-daima-episode-3-english-subbed/
     */
    override suspend fun getVideoData(episodeUrl: String): VideoBean {
        /* val document = getDocument(episodeUrl)
         val result = extractTitleAndEpisode(document.select("h1").text())
         val title = result.first
         var episodeName = result.second
         val allEpisodesUrl = document.select("div.nvs.nvsc > a").attr("href")
         val episodes = getAnimeEpisodes(Jsoup.parse(DownloadManager.getHtml(allEpisodesUrl)))*/
        val regex = Regex(""".*/(.*)-english-subbed/""")
        val episodeId = regex.find(episodeUrl)?.groupValues[1]
            ?: throw IllegalArgumentException("episodeUrl does not match the expected pattern")
        val videoUrl = getVideoUrl(episodeId)
        return VideoBean(videoUrl)
    }


    /**
     * https://consumet-leox-api.vercel.app/anime/gogoanime/watch/dandadan-episode-6?server=vidstreaming
     * Docs: https://docs.consumet.org/rest-api/Anime/gogoanime/get-anime-episode-streaming-links
     */
    private suspend fun getVideoUrl(id: String): String {
        val url = "https://consumet-leox-api.vercel.app/anime/gogoanime/watch/$id?server=gogocdn"
        val result = client.get(url).body<ResponseData>()
        // 定义按优先级排序的质量列表
        val preferredQualities = listOf("1080p", "720p", "480p", "default")

        // 根据优先级依次查找第一个匹配的 URL
        val videoUrl = preferredQualities
            .asSequence()
            .mapNotNull { quality -> result.sources.find { it.quality == quality }?.url }
            .firstOrNull()

        return videoUrl ?: throw RuntimeException("Video URL is empty")
    }

    /*private fun extractTitleAndEpisode(input: String): Pair<String, String> {
        val regex = Regex("""(.+?) Episode (\d+)""")
        val matchResult = regex.find(input)
        return matchResult!!.let {
            val title = it.groupValues[1].trim()
            val episode = it.groupValues[2].trim()
            Pair(title, episode)
        }
    }*/

    override suspend fun getSearchData(query: String, page: Int): List<AnimeBean> {
        val document = getDocument("$baseUrl/page/$page/?s=$query")
        val animeList = getAnimeList(document.select("div.listupd > article.bs"))
        return animeList
    }

    override suspend fun getWeekData(): Map<Int, List<AnimeBean>> {
        val document = getDocument("$baseUrl/schedule/")
        val weekMap = mutableMapOf<Int, List<AnimeBean>>()
        document.select("div.bixbox.schedulepage").forEachIndexed { index, element ->
            val offset = LocalDate.now().dayOfWeek.value - 1
            val dayList = getAnimeList(element.select("div.bs"))
            weekMap[(index + offset).mod(7)] = dayList
        }
        return weekMap
    }

    fun getAnimeList(elements: Elements): List<AnimeBean> {
        val animeList = mutableListOf<AnimeBean>()
        elements.forEach { el ->
            val title = el.selectFirst("div.tt")!!.ownText()
            val url = el.select("a").attr("href").extractDetailUrl()
            val imgUrl = el.select("img").attr("src")
            val episodeName = el.select("div.bt > span.epx").text()
            animeList.add(AnimeBean(title = title, img = imgUrl, url = url, episodeName))
        }
        return animeList
    }

    private fun String.extractDetailUrl(): String {
        if (!this.contains("-episode")) return this
        return this.substringBefore("-episode")
    }

    @Serializable
    data class ResponseData(
        val headers: Headers,
        val sources: List<Source>,
        val download: String
    )

    @Serializable
    data class Headers(
        @SerialName("Referer")
        val referer: String,
        val watchsb: String? = null,
        val userAgent: String? = null
    )

    @Serializable
    data class Source(
        val url: String,
        val quality: String,
        val isM3U8: Boolean
    )
}