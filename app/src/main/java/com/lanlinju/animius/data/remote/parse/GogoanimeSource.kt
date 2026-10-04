package com.lanlinju.animius.data.remote.parse

import com.lanlinju.animius.data.remote.dto.AnimeBean
import com.lanlinju.animius.data.remote.dto.AnimeDetailBean
import com.lanlinju.animius.data.remote.dto.EpisodeBean
import com.lanlinju.animius.data.remote.dto.HomeBean
import com.lanlinju.animius.data.remote.dto.VideoBean
import com.lanlinju.animius.util.DownloadManager
import com.lanlinju.animius.util.getDefaultDomain
import com.lanlinju.animius.util.getDocument
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

    /** 可见性为 internal 以便单元测试覆盖(见 GogoanimeSourceTest) */
    internal fun getAnimeEpisodes(document: Document): List<EpisodeBean> =
        parseEpisodes(document)

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
        val pageUrl =
            if (episodeUrl.startsWith("http")) episodeUrl
            else "$baseUrl${episodeUrl.removePrefix("/")}"
        return VideoBean(getVideoUrl(pageUrl))
    }

    /**
     * 站点自身的解析链路(不再依赖第三方 consumet API)。
     *
     * 原实现调用 `consumet-leox-api.vercel.app`,该域名已被 DNS 污染(解析到 Meta 的 IP 段,
     * 连接被重置),且官方 api.consumet.org 已改为 451 仅供自建,因此改用站点自己暴露的链路
     * ——全程无需代理:
     *   1. 剧集页里含 source=embed 的播放器链接
     *   2. 该播放器页内嵌 megavid 的 iframe
     *   3. 请求 <iframe>/source 得到 JSON,其中 source 字段即 m3u8
     * embed 失败时回退 source=blogger,其页面内含 googlevideo 直链(mp4)。
     *
     * 注意:/player/ 必须带 Referer,否则 302 回首页(页面里就没有 iframe 了)。
     */
    private suspend fun getVideoUrl(pageUrl: String): String {
        val html = DownloadManager.getHtml(pageUrl)
        // 播放器页要求同站 Referer
        val playerHeaders = mapOf("Referer" to baseUrl)

        // 主线路:embed → megavid → m3u8
        extractPlayerUrl(html, "embed")?.let { playerUrl ->
            runCatching { resolveMegavid(playerUrl, playerHeaders) }.getOrNull()?.let { return it }
        }

        // 备用线路:blogger → googlevideo 直链
        extractPlayerUrl(html, "blogger")?.let { playerUrl ->
            runCatching {
                val playerHtml = DownloadManager.getHtml(playerUrl, playerHeaders)
                Regex("""https?://[^"'\\\s]*googlevideo\.com/videoplayback[^"'\\\s]*""")
                    .find(playerHtml)?.value
                    ?.replace("&amp;", "&")?.replace("\\u0026", "&")
            }.getOrNull()?.let { return it }
        }

        throw IllegalStateException("无法解析播放地址(embed/blogger 均失败)")
    }

    /** 从剧集页提取形如 /player/?source=xxx&url=... 的播放器地址 */
    private fun extractPlayerUrl(html: String, source: String): String? =
        Regex("""(?:src|href)="([^"]*?/player/\?source=$source&(?:amp;)?url=[^"]+)"""")
            .find(html)?.groupValues?.get(1)?.replace("&amp;", "&")

    /** 播放器页 → megavid iframe → /source 接口取 m3u8 */
    private suspend fun resolveMegavid(
        playerUrl: String,
        headers: Map<String, String>
    ): String? {
        val playerHtml = DownloadManager.getHtml(playerUrl, headers)
        val iframe = Regex("""<iframe[^>]*class="player-iframe"[^>]*src="([^"]+)"""")
            .find(playerHtml)?.groupValues?.get(1)
            ?: Regex("""<iframe[^>]*src="([^"]+)"""")
                .find(playerHtml)?.groupValues?.get(1)
            ?: return null
        val sourceJson = DownloadManager.getHtml(iframe.trimEnd('/') + "/source", headers)
        return Regex(""""source"\s*:\s*"([^"]+)"""").find(sourceJson)?.groupValues?.get(1)
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
}

/**
 * 解析剧集列表。
 *
 * 独立成顶层函数(而非 [GogoanimeSource] 的成员)是为了可离线单测:
 * object 初始化时 `baseUrl` 依赖 [com.lanlinju.animius.application.AnimeApplication] 的 Context,
 * JVM 单测里无法初始化,因此纯解析逻辑不能挂在 object 上。
 *
 * 站点按"最新一集在前"排列(Episode 20 → Episode 1),与其他数据源(第01集 → 第N集)相反,
 * 这里反转成升序,保证各源在详情页的默认显示顺序一致;
 * 想"最新在前"的用户可以用详情页已有的倒序按钮。
 * 注意:续播定位靠 URL 匹配(见 GetAnimeDetailUseCase)而非下标,反转不影响续播。
 */
internal fun parseEpisodes(document: Document): List<EpisodeBean> =
    document.select("div.episodes-container > div.episode-item > a")
        .map { EpisodeBean(it.text(), it.attr("href")) }
        .reversed()