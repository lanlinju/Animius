package com.lanlinju.animius.data.remote.parse

import com.lanlinju.animius.data.remote.dto.AnimeBean
import com.lanlinju.animius.data.remote.dto.AnimeDetailBean
import com.lanlinju.animius.data.remote.dto.EpisodeBean
import com.lanlinju.animius.data.remote.dto.HomeBean
import com.lanlinju.animius.data.remote.dto.VideoBean
import androidx.core.content.edit
import com.lanlinju.animius.util.DownloadManager
import com.lanlinju.animius.util.preferences
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.Jsoup
import org.jsoup.nodes.Element

/**
 * 稀饭动漫(Next)数据源。
 *
 * 2026-10 站点整体重做：网页域名 `anime.xifanacg.com` 现在 301 跳到 `next.xifanacg.com`，
 * 前端换成 Next.js，数据搬到 Supabase(PostgREST + Edge Functions)上，旧的 maccms 模板
 * (`public-list-box` / `anthology-list` / `player_aaaa`)已经不存在——继续按老选择器抓 HTML
 * 只会得到空列表。实测老域名下 `div.box-width.wow`、`div#week-module-box`、
 * `div.public-list-box`、`player_aaaa` 命中数全为 0。
 *
 * 现在能用的接口(密钥是 Supabase 的 publishable anon key，前端 JS 里明文写着，不是私密凭据；
 * 与 `AniBakaRule/xifanacg.json`、`KazumiRules/xfdmnext.json` 用的是同一套)：
 *
 *  - 搜索 `POST {api}/rest/v1/rpc/search_animes` `{search_term, page_number, items_per_page}`
 *  - 详情 `POST {api}/rest/v1/rpc/get_anime_detail` `{p_id}` → `{anime, related, sources[]}`
 *  - 播放 `POST {api}/functions/v1/issue-web-playback` `{action, episode_id}` → `{url, candidates[]}`
 *
 * 首页与周表没有对应的 RPC(匿名用户只开放了上面两个 `rpc/`，其余一律 PGRST202)，只能抓站点的
 * 列表页 `/recent`、`/browse/format/{tv,movie,ova}`、`/schedule`。
 *
 * **不要抓首页**：Next.js 的流式渲染会把大部分卡片放进 `<div hidden id="S:n">` 占位容器，
 * 等客户端执行 `$RC` 脚本才搬进 `<section>`。实测首页 47 个番剧链接里只有 6 个在 `<section>` 内，
 * 按板块分组会丢掉四成条目；列表页则是一张平铺的 `<ul><li>`，可以直接取。
 */
class XifanSource : AnimeSource {

    override val DEFAULT_DOMAIN: String = XIFAN_DOMAIN
    override var baseUrl: String = xifanMigratedDomain(getStoredDomain()) ?: DEFAULT_DOMAIN

    /**
     * 把已失效的旧域名一次性迁到当前域名。
     *
     * 用户在站点改版前设置过 `https://anime.xifanacg.com/`，这个值会一直留在
     * SharedPreferences 里（`getDefaultDomain()` 直接读它）。而旧域名现在对
     * `/recent`、`/browse/format/{tv,movie,ova}`、`/schedule` 这些列表页
     * **一律 301 跳回首页**，于是首页板块与周表会静默地拿到首页 HTML ——
     * 搜索/详情/播放走 API 不受影响，所以现象是"首页和周表不对，其它正常"，
     * 很难联想到是域名残留。
     *
     * 迁而不删：改写用户设置属于副作用，写回 [KEY_SOURCE_DOMAIN] 才能让设置页的
     * 「修改域名」对话框也显示正确值，否则用户会看到一个已失效的旧域名。
     * 只在确实需要迁移时写一次，不每次构造都写。
     */
    init {
        xifanMigratedDomain(getStoredDomain())?.let { migrated ->
            preferences.edit { putString(KEY_SOURCE_DOMAIN, migrated) }
        }
    }

    private fun getStoredDomain(): String? =
        preferences.getString(KEY_SOURCE_DOMAIN, null)

    /**
     * 播放器请求头：只带 UA，**不要**带站点 Referer。
     *
     * 线路1(apn.moedot.net)会 302 跳到联通沃盘 `hydownload.pan.wo.cn`，该 CDN 拒绝非同站
     * Referer 并返回 400，而 ExoPlayer 会把 Referer 带到重定向后的请求上。线路2
     * (play.xfvod.pro:8088)实测对 Referer 完全不敏感，去掉不回归。
     */
    private val playHeaders = mapOf(
        "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36"
    )

    /**
     * 需要抓 HTML 的首页板块。取不到内容的板块直接不显示，不留空板块。
     *
     * 格式筛选没有对应的 RPC（`search_animes` 只认关键词），只能一页一页抓列表页。
     */
    private val htmlHomeSections = listOf(
        HomeSection("TV 番剧", "/browse/format/tv"),
        HomeSection("剧场版", "/browse/format/movie"),
        HomeSection("OVA", "/browse/format/ova"),
    )

    private data class HomeSection(val title: String, val path: String)

    // ---------- 首页 ----------

    /**
     * 首页板块**并发**拉取，且让「最近更新」走 JSON API 打头。
     *
     * 两个都是被逼出来的：
     *  - 三个列表页各 200~300KB，串行就是 0.7MB 起步。实测 Switch 上只有 ~27 kB/s，
     *    串行要 30s+，`HomeScreen` 拿不到数据就一直空白（既不是 Loading 也不是 Error，
     *    看上去像"源坏了"）。并发后耗时取最长那个，不是总和。
     *  - 「最近更新」改用 `search_animes` 的空关键词列表：接口本来就按上架时间倒序返回，
     *    几 KB 就够，不必为了同样一份数据下载 300KB 的 `/recent` 页。
     *
     * 列表页给 [HOME_PAGE_TIMEOUT] 的上限：慢网络下抓不完就跳过该板块，
     * 至少保证已经拿到的板块能显示出来。
     */
    override suspend fun getHomeData(): List<HomeBean> = coroutineScope {
        val recent = async { orNullOnFailure { fetchRecent() } }
        val paged = htmlHomeSections.map { section ->
            async {
                val animes = orNullOnFailure {
                    withTimeout(HOME_PAGE_TIMEOUT) { fetchPage(section.path) }
                        ?.let { parseXifanAnimeList(it) }
                        .orEmpty()
                }.orEmpty()
                section to animes
            }
        }

        buildList {
            recent.await()?.takeIf { it.isNotEmpty() }
                ?.let { add(HomeBean(title = "最近更新", moreUrl = "/recent", animes = it)) }
            paged.awaitAll().forEach { (section, animes) ->
                if (animes.isNotEmpty()) {
                    add(HomeBean(title = section.title, moreUrl = section.path, animes = animes))
                }
            }
        }
    }

    /**
     * 「最近更新」：API 的空关键词列表就是按上架时间倒序的番剧，与 `/recent` 页同源同序。
     * 响应结构和搜索一致，直接复用 [parseXifanSearch]。
     */
    private suspend fun fetchRecent(): List<AnimeBean> {
        val body = JSONObject()
            .put("search_term", "")
            .put("page_number", 1)
            .put("items_per_page", SEARCH_PAGE_SIZE)
            .toString()
        val text = postJson("/rest/v1/rpc/search_animes", body) ?: return emptyList()
        return parseXifanSearch(text)
    }

    // ---------- 周表 ----------

    override suspend fun getWeekData(): Map<Int, List<AnimeBean>> {
        val html = fetchPage("/schedule") ?: return emptyMap()
        return parseXifanWeek(html)
    }

    // ---------- 详情 ----------

    override suspend fun getAnimeDetail(detailUrl: String): AnimeDetailBean {
        // detailUrl 形如 /anime/3294
        val id = xifanAnimeId(detailUrl) ?: throw IllegalStateException("无效的详情地址: $detailUrl")
        val json = postJson("/rest/v1/rpc/get_anime_detail", JSONObject().put("p_id", id).toString())
            ?: throw IllegalStateException("详情请求失败: $detailUrl")
        return parseXifanDetail(JSONObject(json))
    }

    // ---------- 播放 ----------

    override suspend fun getVideoData(episodeUrl: String): VideoBean {
        // episodeUrl 形如 /anime/3294/play/47887?source=xfxf1
        val episodeId = xifanEpisodeId(episodeUrl)
            ?: throw IllegalStateException("无效的播放地址: $episodeUrl")

        val json = requestPlayback(episodeId)
        val url = json
            ?.let { xifanPlaybackUrl(it, xifanSourceCode(episodeUrl)) }
            ?.takeIf { it.isNotBlank() }
        if (url.isNullOrBlank()) {
            // 集数 id 存在但服务端没有可用资源时接口会回 {"ok":false,"error":"not_found"}(404)
            throw IllegalStateException("该集暂无播放地址,请稍后再试或看看其它线路")
        }
        return VideoBean(videoUrl = encodeUrlNonAscii(url), headers = playHeaders)
    }

    /**
     * 取某一集的播放信息。请求体的拼法见 [xifanPlaybackRequestBody]。
     */
    private suspend fun requestPlayback(episodeId: String): JSONObject? {
        val body = xifanPlaybackRequestBody(episodeId) ?: return null
        val text = postJson("/functions/v1/issue-web-playback", body) ?: return null
        val json = runCatching { JSONObject(text) }.getOrNull() ?: return null
        // 失败时接口给的是 {"ok":false,"error":"not_found"} 而不是 HTTP 错误码
        return json.takeIf { it.optBoolean("ok") }
    }

    // ---------- 搜索 ----------

    override suspend fun getSearchData(query: String, page: Int): List<AnimeBean> {
        val body = JSONObject()
            .put("search_term", query)
            .put("page_number", page)
            .put("items_per_page", SEARCH_PAGE_SIZE)
            .toString()
        val text = postJson("/rest/v1/rpc/search_animes", body) ?: return emptyList()
        return parseXifanSearch(text)
    }

    // ---------- 内部工具 ----------

    private suspend fun fetchPage(path: String): String? =
        orNullOnFailure { DownloadManager.getHtml(baseUrl.trimEnd('/') + path) }

    private suspend fun postJson(path: String, body: String): String? =
        orNullOnFailure { DownloadManager.postJson(apiBase() + path, body, apiHeaders()) }

    /**
     * 失败返回 null 而不是把异常往上抛：一个板块挂了就跳过它，不该拖垮整个首页。
     *
     * 但**协程取消必须继续抛**：[withTimeout] 超时抛的是 `CancellationException`
     * （它继承自 `CancellationException` 而非普通异常语义），吞掉它会让被取消的协程
     * 继续跑下去，`coroutineScope` 也无法按结构化并发的方式等待/取消兄弟协程。
     */
    private suspend fun <T> orNullOnFailure(block: suspend () -> T): T? =
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }

    /**
     * API 主机：数据不在网页域名上，固定是 `api.<站点主域名>`。
     * 用户在设置里改了域名时跟着变，不必改代码。
     */
    private fun apiBase(): String = "https://api.${xifanApiHost(baseUrl)}"

    private fun apiHeaders(): Map<String, String> = mapOf(
        "Accept" to "application/json",
        "apikey" to XIFAN_ANON_KEY,
        "Authorization" to "Bearer $XIFAN_ANON_KEY",
        // PostgREST 校验 Origin，缺这个头边缘函数直接 400
        "Origin" to "https://${xifanApiHost(baseUrl)}",
    )
}

private const val XIFAN_ANON_KEY = "sb_publishable_OBIVAWACIX6lPXrO98_z24_HcsmalkA"

/** 站点改版后的域名。 */
internal const val XIFAN_DOMAIN = "https://next.xifanacg.com/"

/**
 * 改版前的域名，现在 301 到 [XIFAN_DOMAIN]。
 *
 * 留着它是为了识别用户设置里的残留值 —— 对列表页它不是跳到新站的对应路径，
 * 而是跳回首页，所以不能靠"会自动跳转"来兜住。
 */
private const val XIFAN_LEGACY_HOST = "anime.xifanacg.com"

private const val SEARCH_PAGE_SIZE = 24

/**
 * 单个列表页的上限。慢网络下抓不完就跳过该板块，不让一个页面把整个首页拖住。
 * 20s 是按实测最差情况定的：Switch 上约 27 kB/s，一个 230KB 的列表页要 ~8s。
 */
private const val HOME_PAGE_TIMEOUT = 20_000L

/** 列表里的封面取多宽。低于这个值在 720p/1080p 屏上会明显发虚。 */
private const val COVER_MIN_WIDTH = 400

/** 周表页的表头文字，顺序即 map 的 key(0=周一 … 6=周日)。 */
private val WEEK_LABELS = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")

// =====================================================================================
// 纯函数：解析与取值。放在顶层是为了能被 JVM 单测直接调用——`XifanSource` 的
// `baseUrl` 初始化需要 Application Context，整个类在单测里建不起来。
// =====================================================================================

/**
 * org.json 的 `optString` 在字段为 JSON null 时返回字面量 `"null"`，
 * 直接当数据用会把 "null" 拼进播放地址/标题，这里统一挡掉。
 */
private fun JSONObject.stringOrEmpty(key: String): String =
    optString(key).takeIf { it != "null" }.orEmpty()

/**
 * 把用户设置里的旧域名迁到当前域名；不是旧域名（或为空）则返回 null，表示沿用原值。
 *
 * 见 [XifanSource] 的 init：旧域名对列表页一律 301 跳首页，会让首页板块与周表
 * 静默地拿到首页 HTML。单独抽成纯函数是为了能在 JVM 单测里覆盖，
 * 而 `XifanSource` 的初始化需要 Application Context。
 */
internal fun xifanMigratedDomain(stored: String?): String? {
    val host = xifanHost(stored.orEmpty())
    // 只有确切的旧域名才迁。用户自己填的镜像域名不能动——认不准就别改。
    return if (host == XIFAN_LEGACY_HOST) XIFAN_DOMAIN else null
}

/**
 * 从用户配置的域名推出 API 主机名。
 *
 * API 与网页不在同一台主机：网页是 `next.xifanacg.com`，数据在 `api.xifanacg.com`。
 * 旧域名 `anime.xifanacg.com` 现在只是 301 到 next，用户设置里很可能还留着它，
 * 去掉 `anime.` 前缀才能落到还活着的 API 主机上。
 */
internal fun xifanApiHost(baseUrl: String): String {
    val host = xifanHost(baseUrl)
    return when {
        host.isEmpty() -> "xifanacg.com"
        host.startsWith("anime.") -> host.removePrefix("anime.")
        host.startsWith("next.") -> host.removePrefix("next.")
        else -> host
    }
}

/** 取出域名里的主机名：去掉协议、路径与端口。 */
private fun xifanHost(baseUrl: String): String {
    val trimmed = baseUrl.trim()
    return trimmed
        .substringAfter("://", trimmed)
        .substringBefore('/')
        .substringBefore(':')
        .trim()
}

/** 从 `/anime/3294`(或带查询串的同形地址)里取出番剧 id。 */
internal fun xifanAnimeId(detailUrl: String): String? =
    Regex("""/anime/(\d+)""").find(detailUrl)?.groupValues?.get(1)
        ?: Regex("""\d+""").find(detailUrl)?.value

/** 从 `/anime/3294/play/47887?source=xfxf1` 里取出剧集 id。 */
internal fun xifanEpisodeId(episodeUrl: String): String? =
    Regex("""/play/(\d+)""").find(episodeUrl)?.groupValues?.get(1)

/** 取出用户选的线路 code(播放接口一次返回全部线路，靠它区分)。 */
internal fun xifanSourceCode(episodeUrl: String): String? =
    Regex("""[?&]source=([^&#]+)""").find(episodeUrl)
        ?.groupValues?.get(1)
        ?.takeIf { it.isNotBlank() }

/**
 * 只编码 URL 中的非 ASCII 字符(中文等)，保留协议/域名/路径分隔符/查询参数。
 * 因为播放接口给的地址含未编码中文(如 `/新番/...`)，原样发给 CDN 会 400。
 * 已有的 `%XX` 必须原样保留，不能再编码一次。
 */
internal fun encodeUrlNonAscii(url: String): String {
    val sb = StringBuilder()
    for (ch in url) {
        val c = ch.code
        if (ch == '%' || ch == '/' || ch == ':' || ch == '?' || ch == '&' || ch == '=' ||
            ch == '-' || ch == '_' || ch == '.' || ch == '~' || ch == '+' ||
            (c in 0x30..0x39) || (c in 0x41..0x5A) || (c in 0x61..0x7A)
        ) {
            sb.append(ch)
        } else {
            // 非 ASCII 或特殊字符,UTF-8 编码
            for (b in ch.toString().toByteArray(Charsets.UTF_8)) {
                sb.append('%').append(String.format("%02X", b))
            }
        }
    }
    return sb.toString()
}

/**
 * 从番剧列表页(`/recent`、`/browse/format/{tv,movie,ova}`)里取出番剧卡片。
 *
 * 卡片结构跨页面一致：`<a aria-label="标题" href="/anime/{id}"><img src="封面">…<p>标题</p></a>`。
 * 只依赖语义属性(aria-label / href / img[src] / p[title])，不碰站点的 Tailwind 类名——
 * 类名随改版全换过一轮，语义属性是这批页面里最稳的一层。
 */
internal fun parseXifanAnimeList(html: String): List<AnimeBean> = parseXifanCards(Jsoup.parse(html))

/**
 * 从周表页(`/schedule`)取出 7 天的番剧。
 *
 * 页面把一周铺成 7 个分组(站点给每组加了 `id="day-0"`…`day-6`，0=周一)，每组一个
 * 「周一…周日」表头 + 一个 `<ul>`；同一天的番剧会跨时段(周三深夜/周四上午)混在同一个
 * `<ul>` 里，所以按表头分组而不是按条目自带的时间。
 *
 * 这里按**表头文字**而不是 `id="day-N"` 取下标：站点改了分组顺序(比如从今天开始排)时
 * 文字仍然对，id 会错位。
 */
internal fun parseXifanWeek(html: String): Map<Int, List<AnimeBean>> {
    val document = Jsoup.parse(html)
    val weekMap = mutableMapOf<Int, List<AnimeBean>>()
    document.select("span").forEach { label ->
        val dayIndex = WEEK_LABELS.indexOf(label.text().trim())
        if (dayIndex < 0 || weekMap.containsKey(dayIndex)) return@forEach
        // 「周三」在只含「标题 + 部数」的小表头里，再往上一层才是当天的分组容器
        val group = label.parents().firstOrNull { it.select("a[href^=/anime/]").isNotEmpty() }
            ?: return@forEach
        // 页面顶部那排「周几」切换按钮里也有同名的 span，先被找到就会一路爬到装下全页番剧
        // 的容器(那里有 14 个周名)。当天的分组里只应有一个周名，多于一个说明爬过头了。
        if (group.select("span").count { WEEK_LABELS.contains(it.text().trim()) } != 1) return@forEach
        val animes = parseXifanCards(group)
        if (animes.isNotEmpty()) weekMap[dayIndex] = animes
    }
    return weekMap
}

private fun parseXifanCards(root: Element): List<AnimeBean> {
    val result = mutableListOf<AnimeBean>()
    val seen = mutableSetOf<String>()
    root.select("a[href^=/anime/]").forEach { card ->
        val anime = card.toAnimeBean() ?: return@forEach
        // 同一部番在同一页里可能出现多次(封面大图 + 小图卡)，按详情地址去重
        if (seen.add(anime.url)) result.add(anime)
    }
    return result
}

/**
 * 把一张番剧卡片转成 [AnimeBean]。
 *
 * 标题取 `aria-label` 而不是可见文本：卡片可见文本由角标(EP1、周三深夜)和标题 `<p>` 拼成，
 * 直接 `text()` 会把角标混进标题。
 */
private fun Element.toAnimeBean(): AnimeBean? {
    val id = xifanAnimeId(attr("href")) ?: return null
    val title = attr("aria-label")
        // 首页大图卡的 aria-label 是「观看 <标题>」
        .removePrefix("观看 ")
        .trim()
        .ifBlank { selectFirst("p")?.attr("title").orEmpty().trim() }
    if (title.isBlank()) return null
    return AnimeBean(
        title = title,
        img = coverUrl(),
        url = "/anime/$id",
        episodeName = badgeText(),
    )
}

/**
 * 封面地址优先从 `srcset` 里挑宽度够列表用、但最小的那一档。
 *
 * 站点的 `src` 永远是 1200w 的原图（实测 209KB/张），列表一屏 7 张就是 1.4MB；
 * `srcset` 里站点自己声明了 100/200/400/800/1200 几档，400w 只有 48KB，在列表里
 * 清晰度完全够。从站点声明的档位里挑，不会像自己拼 `r/400/` 那样猜错 CDN 规则。
 * 没有 srcset 时才退回 `src`。
 */
private fun Element.coverUrl(): String {
    val img = selectFirst("img") ?: return ""
    val candidates = Regex("""(\S+)\s+(\d+)w""")
        .findAll(img.attr("srcset"))
        .map { it.groupValues[1] to it.groupValues[2].toInt() }
        .filter { (_, width) -> width >= COVER_MIN_WIDTH }
        .sortedBy { (_, width) -> width }
        .toList()
    return candidates.firstOrNull()?.first ?: img.attr("src")
}

/**
 * 卡片封面上的角标文字：左下角的「更新至 13 / 24」、周表里的播出时段/EP 标记。
 *
 * 角标是封面浮层里的 div，DOM 上排在标题 `<p>` 之前，右上角评分(`5.4`)在最前、左下角进度在最后。
 * 所以取最后一个带文字的 div——正好是那条进度；评分没有量纲，单拎出来对用户没意义。
 * 没有角标的卡片(周表里常规更新的条目)返回空串。
 */
private fun Element.badgeText(): String =
    select("div").mapNotNull { it.ownText().trim().ifBlank { null } }.lastOrNull().orEmpty()

/**
 * 解析 `search_animes` 的响应(顶层是 JSON 数组)。
 *
 * 接口有真分页(`page_number` + `total_count`)，越界页返回 `[]`，所以翻页能自然终止。
 */
internal fun parseXifanSearch(json: String): List<AnimeBean> {
    val array = runCatching { JSONArray(json) }.getOrNull() ?: return emptyList()
    val result = mutableListOf<AnimeBean>()
    for (i in 0 until array.length()) {
        val item = array.optJSONObject(i) ?: continue
        val id = item.optInt("id").takeIf { it > 0 } ?: continue
        val title = item.stringOrEmpty("title")
        if (title.isBlank()) continue
        result.add(
            AnimeBean(
                title = title,
                img = item.stringOrEmpty("cover_url"),
                url = "/anime/$id",
                episodeName = item.progressText(),
            )
        )
    }
    return result
}

/**
 * 搜索结果里的进度角标，如「更新至 13/13」。
 * 接口没给 current/total 时留空——不能显示成「更新至 0/0」。
 */
private fun JSONObject.progressText(): String {
    val current = optInt("current_episodes")
    val total = optInt("total_episodes")
    return if (current > 0 && total > 0) "更新至 $current/$total" else ""
}

/**
 * 解析 `get_anime_detail` 的响应 `{anime, related, sources[]}`。
 *
 * `sources[]` 就是线路(实测三条：稀饭新番主线-1/2 + 稀饭备用-1)，每条线路带自己的
 * `episodes[]`；同一个剧集在所有线路里的 id 是一样的，靠 `code` 区分线路。
 */
internal fun parseXifanDetail(json: JSONObject): AnimeDetailBean {
    val anime = json.optJSONObject("anime") ?: JSONObject()
    val animeId = anime.optInt("id").takeIf { it > 0 } ?: 0

    val tags = mutableListOf<String>()
    anime.optJSONArray("meta_tags")?.let { array ->
        for (i in 0 until array.length()) {
            array.optString(i).takeIf { it.isNotBlank() && it != "null" }?.let(tags::add)
        }
    }
    anime.optInt("release_year").takeIf { it > 0 }?.let { tags.add(it.toString()) }

    val relatedAnimes = mutableListOf<AnimeBean>()
    val related = json.optJSONArray("related") ?: JSONArray()
    for (i in 0 until related.length()) {
        val item = related.optJSONObject(i) ?: continue
        val id = item.optInt("id").takeIf { it > 0 } ?: continue
        val title = item.stringOrEmpty("title")
        if (title.isNotBlank()) {
            relatedAnimes.add(
                AnimeBean(
                    title = title,
                    img = item.stringOrEmpty("cover_url"),
                    url = "/anime/$id",
                )
            )
        }
    }

    val channels = mutableMapOf<Int, List<EpisodeBean>>()
    val sources = json.optJSONArray("sources") ?: JSONArray()
    for (i in 0 until sources.length()) {
        val source = sources.optJSONObject(i) ?: continue
        // 线路 code 必须带进播放地址：播放接口一次返回全部线路，得知道用户选的是哪条
        val code = source.stringOrEmpty("code").ifBlank { source.stringOrEmpty("name") }
        val episodes = source.optJSONArray("episodes") ?: continue
        val list = mutableListOf<EpisodeBean>()
        for (j in 0 until episodes.length()) {
            val episode = episodes.optJSONObject(j) ?: continue
            val episodeId = episode.optString("id").takeIf { it.isNotBlank() && it != "null" }
                ?: continue
            val name = episode.stringOrEmpty("title").ifBlank { "第${j + 1}集" }
            list.add(EpisodeBean(name = name, url = "/anime/$animeId/play/$episodeId?source=$code"))
        }
        if (list.isNotEmpty()) channels[i] = list
    }

    return AnimeDetailBean(
        title = anime.stringOrEmpty("title"),
        imgUrl = anime.stringOrEmpty("cover_url"),
        desc = anime.stringOrEmpty("description"),
        tags = tags,
        relatedAnimes = relatedAnimes,
        channels = channels,
    )
}

/**
 * 拼 `issue-web-playback` 的请求体。[episodeId] 不是数字时返回 null。
 *
 * `episode_id` 必须是数字而不是字符串：这个接口是 Edge Function(Deno)，不做 PostgREST
 * 的类型强转，实测 `"episode_id":"12303"` 返回 400 `bad_request`，只有 `12303` 才返回线路。
 * 传错类型的症状极具误导性——会一路走到"该集暂无播放地址"，像是这集没资源。
 * (对比 `get_anime_detail` 的 `p_id`：PostgREST 会强转，字符串和数字都行。)
 */
internal fun xifanPlaybackRequestBody(episodeId: String): String? {
    val id = episodeId.toLongOrNull() ?: return null
    return JSONObject()
        .put("action", "fallback")
        .put("episode_id", id)
        .toString()
}

/**
 * 从播放响应里取出用户所选线路的地址。
 *
 * 接口一次返回全部线路：顶层 `url` 是站点替用户挑的那条(实测恒为 apn.moedot.net 的 mp4)，
 * `candidates[]` 里每条带 `source_code`。详情页能切线路，所以必须按 `source_code` 取，
 * 否则用户切了线路还是播同一条。线路被撤下(找不到对应 `source_code`)时退回顶层 `url`，
 * 好过直接报错。
 */
internal fun xifanPlaybackUrl(json: JSONObject, sourceCode: String?): String {
    val candidates = json.optJSONArray("candidates") ?: JSONArray()
    for (i in 0 until candidates.length()) {
        val candidate = candidates.optJSONObject(i) ?: continue
        if (sourceCode != null && candidate.stringOrEmpty("source_code") == sourceCode) {
            candidate.stringOrEmpty("url").takeIf { it.isNotBlank() }?.let { return it }
        }
    }
    return json.stringOrEmpty("url")
}