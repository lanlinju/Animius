package com.sakura.anime.data.remote.parse

import android.annotation.SuppressLint
import com.sakura.anime.data.remote.dto.AnimeBean
import com.sakura.anime.data.remote.dto.AnimeDetailBean
import com.sakura.anime.data.remote.dto.EpisodeBean
import com.sakura.anime.data.remote.dto.HomeBean
import com.sakura.anime.data.remote.dto.VideoBean
import com.sakura.anime.data.remote.parse.util.WebViewUtil
import com.sakura.anime.util.DownloadManager
import com.sakura.anime.util.getDefaultDomain
import com.sakura.anime.util.log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.ResponseBody
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.select.Elements
import retrofit2.Response
import java.io.Closeable

@SuppressLint("StaticFieldLeak", "SetJavaScriptEnabled")
object AgedmSource : AnimeSource {

    private const val LOG_TAG = "AgedmSource"

    override val DEFAULT_DOMAIN: String = "http://www.agedm.org"

    override var baseUrl: String = getDefaultDomain()

    private val webViewUtil: WebViewUtil by lazy { WebViewUtil() }

    private val filterReqUrl: Array<String> = arrayOf(
        ".css", ".js", ".jpeg", ".svg", ".ico", ".ts",
        ".gif", ".jpg", ".png", ".webp", ".wasm", "age", ".php"
    )

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
        val source = DownloadManager.getHtml(detailUrl)
        val document = Jsoup.parse(source)
        val score = ""
        val videoDetailRight = document.select("div.video_detail_right")
        val title = videoDetailRight.select("h2").text()
        val desc = videoDetailRight.select("div.video_detail_desc").text()
        val imgUrl = document.select("div.video_detail_cover > img").attr("data-original")
        val detailBoxList = document.select("div.video_detail_box").select("li")
        val updateTime = detailBoxList[6].text()
        val tags = detailBoxList[9].text().split("：")[1].split(" ").toMutableList()
        tags.add(detailBoxList[0].text().split("：")[1])
        tags.add(detailBoxList[1].text().split("：")[1])
        val playlist = document.select("div.tab-content").select("div.tab-pane")
        val episodes = getAnimeEpisodes(playlist)
        val relatedAnimes =
            getAnimeList(document.select("div.video_list_box").select("div.video_item"))
        val animeDetailBean =
            AnimeDetailBean(title, imgUrl, desc, score, tags, updateTime, episodes, relatedAnimes)

        return animeDetailBean
    }

    override suspend fun getVideoData(episodeUrl: String): VideoBean {
        val source = DownloadManager.getHtml(episodeUrl)
        val document = Jsoup.parse(source)
        val elements = document.select("div.cata_video_item")
        val title = elements.select("h5").text()
        var episodeName = ""
        val playlist = document.select("div.playlist-source-tab").select("div.tab-pane")
        val episodes = getAnimeEpisodes(playlist, action = { episodeName = it })
        val videoUrl = getVideoUrl(document)

        return VideoBean(title, videoUrl, episodeName, episodes)
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

    private suspend fun getAnimeEpisodes(
        elements: Elements,
        action: (String) -> Unit = {}
    ): List<EpisodeBean> {

        if (elements.isEmpty()) return emptyList()

        val episodes = mutableListOf<EpisodeBean>()
        elements[0].select("li").forEach { el ->
            val name = el.text()
            val url = el.select("a").attr("href")
            if (el.select("div.video_detail_spisode_playing").isNotEmpty()) {
                action(name)
            }
            episodes.add(EpisodeBean(name, url))
        }

        return episodes
    }

    private suspend fun getVideoUrl(document: Document): String {

        val videoUrl = document.select("#iframeForVideo").attr("src")

        // 用于判断url的返回类型是否是 video/mp4
        val predicate: suspend (requestUrl: String) -> Boolean = { requestUrl ->
            withContext(Dispatchers.IO) {
                var response: Response<ResponseBody>? = null
                try {
                    "predicate $requestUrl".log(LOG_TAG)
                    response = DownloadManager.request(requestUrl)
                    response.isSuccessful && response.isVideoType()
                } catch (_: Exception) {
                    false
                } finally {
                    response?.closeQuietly()
                }
            }
        }

        return webViewUtil.interceptRequest(
            url = videoUrl,
            regex = ".mp4|.m3u8|video|playurl|hsl|obj|bili",
            predicate = predicate,
            filterRequestUrl = filterReqUrl
        )
    }

    /**
     * age动漫的跳转链接是完整形式，需要去掉域名
     *  eg. http://www.agedm.org/detail/20240060 裁剪成为 /detail/20240060
     */
//    private fun String.trimDomain() = replace("http://www.agedm.org", "")

    private fun Response<*>.header(key: String): String {
        val header = headers()[key]
        return header ?: ""
    }

    private fun Response<*>.isVideoType(): Boolean {
        return header("Content-Type") == "video/mp4"
    }

    private fun Closeable.closeQuietly() {
        try {
            close()
        } catch (rethrown: RuntimeException) {
            throw rethrown
        } catch (_: Exception) {
        }
    }

    private fun Response<ResponseBody>.closeQuietly() {
        body()?.closeQuietly()
        errorBody()?.closeQuietly()
    }
}

