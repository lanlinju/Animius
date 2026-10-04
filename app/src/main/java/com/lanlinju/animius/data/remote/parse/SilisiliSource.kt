package com.lanlinju.animius.data.remote.parse

import com.lanlinju.animius.data.remote.dto.AnimeBean
import com.lanlinju.animius.data.remote.dto.AnimeDetailBean
import com.lanlinju.animius.data.remote.dto.EpisodeBean
import com.lanlinju.animius.data.remote.dto.HomeBean
import com.lanlinju.animius.data.remote.dto.VideoBean
import com.lanlinju.animius.util.DownloadManager
import com.lanlinju.animius.util.decryptData
import com.lanlinju.animius.util.getDefaultDomain
import com.lanlinju.animius.util.log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.security.MessageDigest
import java.util.concurrent.TimeUnit


/**
 * 嘶哩嘶哩域名发布地址：https://silisili-link.github.io/
 */
object SilisiliSource : AnimeSource {

    private const val LOG_TAG = "SilisiliSource"

    override val DEFAULT_DOMAIN: String = "https://www.silisili.link"
    override var baseUrl = getDefaultDomain()

    /**
     * 取播放地址用的 POST 客户端。
     * 复用一个实例(原先每次调用都新建,连接池/线程被反复创建);
     * 超时对齐 [DownloadManager](30s),原先用 OkHttp 默认的 10s,
     * 在网络较慢时(模拟器/TV)会误报 SocketTimeoutException。
     */
    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    override suspend fun getHomeData(): List<HomeBean> {
        val headers = mapOf(
            Pair("Sec-Ch-Ua-Mobile", "?0"),
            Pair("Sec-Ch-Ua-Platform", "\"Windows\""),
            Pair("Cookie", "silisili=on;path=/;max-age=86400"),
            Pair(
                "User-Agent",
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/117.0.0.0 Safari/537.36 Edg/117.0.2045.60"
            ),
        )
        val source = DownloadManager.getHtml(baseUrl, headers)
        val document = Jsoup.parse(source)
        val homeList = mutableListOf<HomeBean>()

        // 今日更新
        val animeList1 = mutableListOf<AnimeBean>()
        document.select("div.index_slide_r > div.sliderlist > div.sliderli").forEach { element ->
            val animeTitle = element.select("div.list-body").text()
            val url = element.select("a").attr("href")
            val img = getImgUrl(
                element.select("i.thumb").attr("style")
            )
            val episodeName = element.select("time.d-inline-block").text()
            val anime = AnimeBean(animeTitle, img, url, episodeName)
            animeList1.add(anime)
        }

        // 热门推荐
        val animeList2 = mutableListOf<AnimeBean>()
        document.select("div.list-box").forEach { element ->
            val animeTitle = element.select("span.card-right-avatar-name").text()
            val url = element.select("a").attr("href")
            val img = element.select("img").attr("data-url")
            val episodeName = animeTitle.split("|")[1]
            val anime = AnimeBean(animeTitle, img, url, episodeName)
            animeList2.add(anime)
        }

        // 更新动态
        val animeList3 = getStaggeredGridAnimes(document)

        // 新番日番
        val animeFromJapan = DownloadManager.getHtml("$baseUrl/vodtype/xinfanriman/")
        val animeList4 = getStaggeredGridAnimes(Jsoup.parse(animeFromJapan))

        // 剧场动漫
        val theater = DownloadManager.getHtml("$baseUrl/vodtype/juchang/")
        val animeList5 = getStaggeredGridAnimes(Jsoup.parse(theater))

        homeList.add(HomeBean("今日更新", "", animeList1))
        homeList.add(HomeBean("热门推荐", "", animeList2))
        homeList.add(HomeBean("更新动态", "", animeList3))
        homeList.add(HomeBean("新番日漫", "", animeList4))
        homeList.add(HomeBean("剧场动漫", "", animeList5))

        return homeList
    }

    private fun getStaggeredGridAnimes(document: Document): List<AnimeBean> {
        val animeList = mutableListOf<AnimeBean>()
        document.select("article.article").forEach { el ->
            val header = el.select("header")
            header.select("span").remove()
            val animeTitle = header.text()
            val url = el.select("div.entry-media > a").attr("href")
            val img = el.select("div.entry-media > a > img").attr("src")
            val desc = el.select("div.entry-summary > p").text()
            val anime = AnimeBean(animeTitle, img, url, desc)
            animeList.add(anime)
        }
        return animeList
    }

    override suspend fun getAnimeDetail(detailUrl: String): AnimeDetailBean {
        val source = getHtml("$baseUrl/$detailUrl")
        val document = Jsoup.parse(source)

        val title = document.select("h1.entry-title").text().split(" ").first()
        val img = document.select("div.v_sd_l > img").attr("src")
        val tags = document.select("p.data").select("a")

        val tagTitles = mutableListOf<String>()
        for (tag in tags) {
            if (tag.text().isEmpty()) continue
            tagTitles.add(tag.text().uppercase())
        }

        /*val span = document.select("span.text-muted")
        var updateTime = ""
        for (s in span) {
            if (s.text().contains("更新")) {
                updateTime = s.parent()!!.text()
                break
            }
        }*/
        val desc = document.select("div.v_cont")
        desc.select("div.v_sd").remove()
        desc.select("span").remove()
        val description = desc.text()

        val channels = getAnimeEpisodes(document)
        val relatedAnimes = getAnimeList(document)

        return AnimeDetailBean(
            title, img, description, tagTitles, relatedAnimes, channels = channels
        )
    }

    override suspend fun getVideoData(episodeUrl: String): VideoBean {
//        val source = getHtml("$baseUrl/$episodeUrl")
//        val document = Jsoup.parse(source)
//
//        val title = document.select("h1 > a").text()
//        val episodeName = document.select("span.nidname").text()
//        val episodes = getAnimeEpisodes(document)
        val videoUrl = getVideoUrl("$baseUrl/$episodeUrl")

        return VideoBean(videoUrl)
    }

    override suspend fun getSearchData(query: String, page: Int): List<AnimeBean> {
        val source = getHtml("$baseUrl/vodsearch$query/page/$page/")
        val document = Jsoup.parse(source)
        val animeList = mutableListOf<AnimeBean>()
        document.select("article.post-list").forEach { el ->
            val title = el.select("div.search-image").select("a").attr("title")
            val url = el.select("div.search-image").select("a").attr("href")
            val imgUrl = el.select("div.search-image").select("img").attr("srcset")
            animeList.add(AnimeBean(title = title, img = imgUrl, url = url))
        }

        return animeList
    }

    override suspend fun getWeekData(): Map<Int, List<AnimeBean>> {
        val source = getHtml(baseUrl)
        val document = Jsoup.parse(source)

        val weekMap = mutableMapOf<Int, MutableList<AnimeBean>>()
        val dayElements = document.select("div.week_item").select("ul.tab-content")
        dayElements.size.log(LOG_TAG) // 14

        dayElements.movePosition(0, 6) // 日漫 sunday
        dayElements.movePosition(7, 13) // 国漫 sunday

        for (i in 0 until 14) {
            val dayList = mutableListOf<AnimeBean>()

            dayElements[i].select("li").forEach { li ->
                val title = li.select("a.item-cover").attr("title")
                val url = li.select("a.item-cover").attr("href")
                val spanStyle = li.select("span[style]").attr("style")
                val img = getImgUrl(spanStyle)
                val episodeName = li.select("p.num").text()
                dayList.add(AnimeBean(title, img, url, episodeName))
            }

            weekMap[i % 7]?.addAll(dayList) ?: weekMap.set(i, dayList)

        }
        return weekMap
    }

    private fun MutableList<Element>.movePosition(current: Int, destination: Int) {
        val tmp = this[current]
        this.removeAt(current)
        this.add(destination, tmp)
    }

    private fun getAnimeEpisodes(document: Document): Map<Int, List<EpisodeBean>> {
        val channels = mutableMapOf<Int, List<EpisodeBean>>()
        document.select("div.play-pannel-list").forEachIndexed { i, e ->
            val episodes = mutableListOf<EpisodeBean>()
            e.select("li > a").forEach { el ->
                val name = el.text()
                val url = el.attr("href")
                episodes.add(EpisodeBean(name, url))
            }
            channels[i] = episodes
        }

        return channels
    }

    private fun getAnimeList(document: Document): List<AnimeBean> {
        val animeList = mutableListOf<AnimeBean>()
        document.select("div.vod_hl_list").select("a").forEach { el ->
            val title = el.select("div.list-body").text()
            val img: String = getImgUrl(el.select("i.thumb").attr("style"))
            val url = el.attr("href")
            animeList.add(AnimeBean(title, img, url, ""))
        }

        return animeList
    }

    private suspend fun getVideoUrl(url: String): String {
        val encryptData = postRequest(url)
        // 站点偶尔返回空响应,substring 会直接抛 StringIndexOutOfBoundsException
        if (encryptData.length <= 9) {
            throw IllegalStateException("播放地址解析失败:站点返回了空响应")
        }
        val params1 = encryptData.substring(0, 9)
        val params2 = encryptData.substring(9)

        val ivAndKey = md5DigestAsHex(params1)
        val iv = ivAndKey.substring(0, 16)
        val key = ivAndKey.substring(16)

        val result = decryptData(params2, key = key, iv = iv)
        val urlRegex = """"url":"(.*?)",""".toRegex()
        // 站点偶尔会返回错误页/空壳响应,此时不应抛 NPE,给出可读原因
        return urlRegex.find(result)?.groupValues?.get(1)?.replace("\\", "")
            ?: throw IllegalStateException("播放地址解析失败:响应中没有 url 字段")
    }

    private fun getImgUrl(urlTarget: String): String {
        val urlRegex = """url\((.*?)\)""".toRegex()
        return urlRegex.find(urlTarget)?.groupValues?.get(1) ?: ""
    }

    private suspend fun getHtml(url: String): String {
        val headerMap = mapOf(Pair("Cookie", "silisili=on;path=/;max-age=86400"))
        return DownloadManager.getHtml(url, headerMap)
    }

    private suspend fun postRequest(url: String): String = withContext(Dispatchers.IO) {
        val body = FormBody.Builder().add("player", "sili").build()
        val request = Request.Builder()
            .url(url)
            .addHeader("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
            .post(body)
            .build()
        okHttpClient.newCall(request).execute().use { response ->
            response.body?.charStream()?.readText().orEmpty()
        }
    }

    /**
     * 返回长度为32的16进制字符串
     */
    private fun md5DigestAsHex(input: String): String {
        val md = MessageDigest.getInstance("MD5")
        return md.digest(input.toByteArray()).toHex()
    }

    @OptIn(ExperimentalUnsignedTypes::class)
    private fun ByteArray.toHex() = asUByteArray().joinToString(separator = "") { byte ->
        byte.toString(16).padStart(2, '0')
    }
}