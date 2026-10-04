package com.lanlinju.animius.parse.xifan

import com.lanlinju.animius.data.remote.parse.encodeUrlNonAscii
import com.lanlinju.animius.data.remote.parse.parseXifanAnimeList
import com.lanlinju.animius.data.remote.parse.parseXifanDetail
import com.lanlinju.animius.data.remote.parse.parseXifanSearch
import com.lanlinju.animius.data.remote.parse.parseXifanWeek
import com.lanlinju.animius.data.remote.parse.xifanAnimeId
import com.lanlinju.animius.data.remote.parse.xifanApiHost
import com.lanlinju.animius.data.remote.parse.XIFAN_DOMAIN
import com.lanlinju.animius.data.remote.parse.xifanEpisodeId
import com.lanlinju.animius.data.remote.parse.xifanMigratedDomain
import com.lanlinju.animius.data.remote.parse.xifanPlaybackRequestBody
import com.lanlinju.animius.data.remote.parse.xifanPlaybackUrl
import com.lanlinju.animius.data.remote.parse.xifanSourceCode
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 稀饭动漫 Next 的纯函数单测(JVM,不需要设备/网络)。
 *
 * 样本取自线上真实响应(2026-10-03)，站点此时已重做成 Next.js + Supabase：
 *  - `html/schedule.html` `/schedule` 周表页，已剥掉 `class`/`style`/`srcset`。
 *    **故意不带类名**：解析只该认 `aria-label` / `href` / `img[src]` 这些语义属性，
 *    Tailwind 类名随改版全换过一轮。
 *  - `html/recent.html` `/recent` 最近更新页，同样剥过属性。
 *  - `json/search.json` `POST /rest/v1/rpc/search_animes` 的响应(顶层是数组)。
 *  - `json/detail.json` `POST /rest/v1/rpc/get_anime_detail` 的响应，cast/staff 已截断。
 *  - `json/play.json` `POST /functions/v1/issue-web-playback` 的响应。
 */
class XifanSourceTest {

    private fun html(name: String): String =
        File("./src/test/java/com/lanlinju/animius/parse/xifan/html/$name").readText(Charsets.UTF_8)

    private fun json(name: String): String =
        File("./src/test/java/com/lanlinju/animius/parse/xifan/json/$name").readText(Charsets.UTF_8)

    // ---------- 域名 ----------

    /**
     * 数据不在网页域名上：网页是 `next.xifanacg.com`，接口在 `api.xifanacg.com`。
     * 推错主机名 = 所有请求 404/DNS 失败，所以这里逐个情形钉死。
     */
    @Test
    fun apiHostStripsWebSubdomain() {
        assertEquals("xifanacg.com", xifanApiHost("https://next.xifanacg.com/"))
        assertEquals("xifanacg.com", xifanApiHost("https://next.xifanacg.com"))
    }

    @Test
    fun apiHostAcceptsLegacyDomain() {
        // anime.xifanacg.com 现在只是 301 到 next，用户设置里很可能还留着旧域名。
        // 不去掉 anime. 前缀就会去连不存在的 api.anime.xifanacg.com。
        assertEquals("xifanacg.com", xifanApiHost("https://anime.xifanacg.com/"))
    }

    @Test
    fun apiHostKeepsCustomMirrorHost() {
        // 用户换域名/端口时不能把端口也当成主机名的一部分
        assertEquals("192.168.1.10", xifanApiHost("http://192.168.1.10:8080/"))
        assertEquals("xifanacg.com", xifanApiHost("  https://next.xifanacg.com/schedule  "))
        assertEquals("xifanacg.com", xifanApiHost(""))
    }

    /**
     * 回归：用户设置里残留的旧域名会让首页板块与周表静默拿到首页 HTML。
     *
     * 旧域名对 `/recent`、`/browse/format/{tv,movie,ova}`、`/schedule` 一律 301 跳**首页**
     * （不是跳到新站的对应路径），所以靠"会自动跳转"兜不住 —— 表现为首页/周表不对，
     * 而搜索/详情/播放走 API 完全正常，很难联想到是域名残留。
     */
    @Test
    fun legacyDomainIsMigrated() {
        assertEquals(XIFAN_DOMAIN, xifanMigratedDomain("https://anime.xifanacg.com/"))
        assertEquals(XIFAN_DOMAIN, xifanMigratedDomain("https://anime.xifanacg.com"))
        assertEquals(XIFAN_DOMAIN, xifanMigratedDomain("  https://anime.xifanacg.com/recent  "))
        assertEquals(XIFAN_DOMAIN, xifanMigratedDomain("http://anime.xifanacg.com:80/"))
    }

    @Test
    fun currentDomainIsNotMigrated() {
        // 已经是新域名就别写回 preferences，避免每次构造都做一次无意义的写入
        assertNull(xifanMigratedDomain(XIFAN_DOMAIN))
        assertNull(xifanMigratedDomain("https://next.xifanacg.com/"))
    }

    @Test
    fun customDomainIsNeverMigrated() {
        // 用户自己填的镜像域名认不准就不动，迁移只针对确切的旧域名
        assertNull(xifanMigratedDomain("https://my.mirror.example/"))
        assertNull(xifanMigratedDomain("https://anime.my.mirror.example/"))
        assertNull(xifanMigratedDomain(""))
        assertNull(xifanMigratedDomain(null))
    }

    // ---------- 地址解析 ----------

    @Test
    fun animeIdIsTakenFromPath() {
        assertEquals("3294", xifanAnimeId("/anime/3294"))
        assertEquals("3294", xifanAnimeId("/anime/3294/play/47887?source=xfxf1"))
        assertNull(xifanAnimeId("/bangumi/no-id-here"))
    }

    @Test
    fun episodeIdAndSourceCodeAreTakenFromPlayPath() {
        val url = "/anime/3294/play/47887?source=xfxf1"

        assertEquals("47887", xifanEpisodeId(url))
        assertEquals("xfxf1", xifanSourceCode(url))
    }

    @Test
    fun sourceCodeIsOptional() {
        // 老数据/手工构造的播放地址可能没有 source 参数
        assertEquals("47887", xifanEpisodeId("/anime/3294/play/47887"))
        assertNull(xifanSourceCode("/anime/3294/play/47887"))
    }

    // ---------- 非 ASCII 编码 ----------

    @Test
    fun chinesePathIsEncoded() {
        // 播放接口给的地址带未编码中文(如 /新番/...)，原样发给 CDN 会 400
        assertEquals(
            "https://dl.playxf.top/%E6%96%B0%E7%95%AA/2604/Q-%E6%AC%BA%E8%AF%88%E6%B8%B8%E6%88%8F/01/%E6%AC%BA%E8%AF%8801.m3u8",
            encodeUrlNonAscii("https://dl.playxf.top/新番/2604/Q-欺诈游戏/01/欺诈01.m3u8")
        )
    }

    @Test
    fun existingPercentEncodingIsNotDoubleEncoded() {
        // 线路1 的地址本来就带 %XX，再编码一次就取不到文件了
        assertEquals(
            "https://apn.moedot.net/d/wo/2604/%E6%AC%BA%E8%AF%8801.mp4",
            encodeUrlNonAscii("https://apn.moedot.net/d/wo/2604/%E6%AC%BA%E8%AF%8801.mp4")
        )
    }

    @Test
    fun queryStringIsPreserved() {
        // 线路2 带签名参数(t 是过期时间戳)，?&= 都不能被编码掉
        val signed = "https://play.xfvod.pro:8088/temp/2604/%E6%AC%BA%E8%AF%8801.mp4?sign=37cf1d67&t=1791018000"

        assertEquals(signed, encodeUrlNonAscii(signed))
    }

    // ---------- 列表页 ----------

    @Test
    fun animeListIsParsedFromRecentPage() {
        val animes = parseXifanAnimeList(html("recent.html"))

        assertEquals("最近更新页的条目数", 28, animes.size)
        val first = animes.first()
        assertEquals("我家的弟弟们真是让您费心了", first.title)
        assertEquals("/anime/3396", first.url)
        assertTrue("封面应来自 img", first.img.startsWith("http"))
        // 角标是左下角的进度条，右上角的评分(5.4)不该混进来
        assertEquals("更新至 13 / 24", first.episodeName)
    }

    /**
     * 站点的 `src` 永远是 1200w 原图(209KB/张)，列表一屏 7 张就是 1.4MB。
     * `srcset` 里站点自己声明了 100/200/400/800/1200，400w 只要 48KB 且列表里够清晰。
     */
    @Test
    fun coverPrefersSmallestSrcsetVariant() {
        val list = parseXifanAnimeList(
            """<a aria-label="番" href="/anime/1">
                 <img src="https://cdn/cover/r/1200/a.jpg"
                      srcset="https://cdn/cover/r/100/a.jpg 100w,
                              https://cdn/cover/r/200/a.jpg 200w,
                              https://cdn/cover/r/400/a.jpg 400w,
                              https://cdn/cover/r/800/a.jpg 800w,
                              https://cdn/cover/r/1200/a.jpg 1200w">
               </a>""".trimIndent()
        )

        assertEquals("https://cdn/cover/r/400/a.jpg", list.single().img)
    }

    @Test
    fun coverFallsBackToSrcWithoutSrcset() {
        val list = parseXifanAnimeList(
            """<a aria-label="番" href="/anime/1"><img src="https://cdn/cover/r/1200/a.jpg"></a>"""
        )

        assertEquals("https://cdn/cover/r/1200/a.jpg", list.single().img)
    }

    @Test
    fun animeListTitlesComeFromAriaLabel() {
        // 卡片的可见文本 = 角标(EP1) + 标题，直接 text() 会把角标混进标题
        val animes = parseXifanAnimeList(html("recent.html"))

        assertTrue(
            "标题里不该混进角标",
            animes.none { it.title.startsWith("EP") || it.title.contains(" 部") }
        )
    }

    @Test
    fun animeListDropsEntriesWithoutTitle() {
        val list = parseXifanAnimeList(
            """
            <ul>
              <li><a href="/anime/100"><img src="http://x/1.jpg"></a></li>
              <li><a aria-label="有标题" href="/anime/101"><img src="http://x/2.jpg"></a></li>
              <li><a aria-label="别的站的链接" href="/other/102"></a></li>
            </ul>
            """.trimIndent()
        )

        assertEquals(1, list.size)
        assertEquals("/anime/101", list.single().url)
    }

    // ---------- 周表 ----------

    @Test
    fun weekIsGroupedByDayHeader() {
        val week = parseXifanWeek(html("schedule.html"))

        assertEquals("应覆盖周一到周日", 7, week.size)
        // 样本抓取当天的真实条目数(2026-10-03)
        assertEquals(5, week[0]?.size)
        assertEquals(11, week[1]?.size)
        assertEquals(7, week[2]?.size)
        assertEquals(19, week[6]?.size)
    }

    /**
     * 回归：页面顶部那排「周几」切换按钮里也有 `周一`…`周日` 的 span，且排在正文前面。
     * 谁先被匹配到谁拿整页番剧——周一会被塞进全部 73 部，其余六天全空。
     */
    @Test
    fun weekTabsDoNotStealEveryday() {
        val week = parseXifanWeek(html("schedule.html"))

        val monday = week[0].orEmpty()
        assertEquals("周一只能是当天的 5 部", 5, monday.size)
        assertTrue(week.values.all { it.isNotEmpty() })
        assertTrue(
            "各天条目不应互相重叠",
            week.values.sumOf { it.size } == 73
        )
    }

    @Test
    fun weekEntriesCarryBroadcastSlotAsEpisodeName() {
        val week = parseXifanWeek(html("schedule.html"))

        // 角标是播出时段(周三深夜)、新集标记(EP1)、开播预告(预计7.19开播)，不是标题的一部分
        val names = week.values.flatten().map { it.episodeName }.filter { it.isNotBlank() }
        assertTrue("应能取到角标", names.isNotEmpty())
        assertTrue("角标不该是整段卡片文本", names.none { it.length > 12 })
        // 周一那组里既有 EP 标记也有时段标记
        assertTrue(week.getValue(0).any { it.episodeName == "EP1" })
    }

    @Test
    fun cardWithoutBadgeHasEmptyEpisodeName() {
        // 周表里常规更新的条目没有角标，不能拿整段文本顶上
        val list = parseXifanAnimeList(
            """<ul><li>
                 <a aria-label="在异世界获得超强能力的我" href="/anime/3348">
                   <div class="relative"><img src="http://x/1.jpg"></div>
                   <p>在异世界获得超强能力的我</p>
                 </a>
               </li></ul>""".trimIndent()
        )

        assertEquals("", list.single().episodeName)
    }

    // ---------- 搜索 ----------

    @Test
    fun searchResultIsParsed() {
        val animes = parseXifanSearch(json("search.json"))

        assertEquals(4, animes.size)
        assertEquals("Fate/Zero", animes[1].title)
        assertEquals("/anime/859", animes[1].url)
        assertTrue(animes[1].img.startsWith("http"))
        assertEquals("更新至 25/25", animes[1].episodeName)
    }

    @Test
    fun searchWithoutProgressLeavesBadgeEmpty() {
        // current/total 为 0 时不能显示成「更新至 0/0」
        val animes = parseXifanSearch(
            """[{"id":1,"title":"剧场版","cover_url":"http://x/1.jpg",
                "current_episodes":0,"total_episodes":1}]"""
        )

        assertEquals("", animes.single().episodeName)
    }

    @Test
    fun searchToleratesBrokenPayload() {
        assertEquals(emptyList<Any>(), parseXifanSearch(""))
        assertEquals(emptyList<Any>(), parseXifanSearch("not json"))
        assertEquals(emptyList<Any>(), parseXifanSearch("""[{"title":"没有 id"}]"""))
        assertEquals(emptyList<Any>(), parseXifanSearch("""[null,{"id":2,"title":null}]"""))
    }

    // ---------- 详情 ----------

    @Test
    fun detailIsParsed() {
        val detail = parseXifanDetail(JSONObject(json("detail.json")))

        assertEquals("欺诈游戏", detail.title)
        assertTrue(detail.imgUrl.startsWith("https://img2."))
        assertTrue("简介应有内容", detail.desc.isNotBlank())
        assertTrue("标签应含 meta_tags", detail.tags.contains("TV"))
        assertTrue("标签应含年份", detail.tags.contains("2026"))
        assertEquals(3, detail.relatedAnimes.size)
        assertEquals("/anime/3463", detail.relatedAnimes.first().url)
    }

    @Test
    fun detailChannelsAreLinesWithPlayUrls() {
        val detail = parseXifanDetail(JSONObject(json("detail.json")))

        // 线路数 = 站点 sources[] 的条数(实测 3 条)
        assertEquals(3, detail.channels.size)
        val firstLine = detail.channels.getValue(0)
        assertEquals("传说的欺诈师", firstLine.first().name)
        // 播放地址必须带线路 code：播放接口一次返回全部线路，要靠它挑用户选的那条
        assertEquals("/anime/3294/play/47887?source=xfxf1", firstLine.first().url)
        assertTrue(
            "每条线路都要有自己的播放地址",
            detail.channels.values.all { line -> line.all { it.url.contains("source=") } }
        )
        // 同一个剧集在各线路下的 id 相同，只有 source 不同
        assertEquals(
            firstLine.map { it.url.substringBefore("?") },
            detail.channels.getValue(1).map { it.url.substringBefore("?") }
        )
    }

    @Test
    fun detailWithoutEpisodesHasNoChannels() {
        val detail = parseXifanDetail(JSONObject("""{"anime":{"id":1,"title":"x"}}"""))

        assertEquals("x", detail.title)
        assertTrue(detail.channels.isEmpty())
    }

    @Test
    fun detailToleratesJsonNullFields() {
        // org.json 的 optString 遇到 JSON null 会返回字面量 "null"，不能当数据用
        val detail = parseXifanDetail(
            JSONObject("""{"anime":{"id":7,"title":"番","cover_url":null,"description":null},
                          "related":[],"sources":null}""")
        )

        assertEquals("番", detail.title)
        assertEquals("", detail.imgUrl)
        assertEquals("", detail.desc)
    }

    // ---------- 播放 ----------

    @Test
    fun playbackUrlFollowsSelectedLine() {
        val json = JSONObject(json("play.json"))

        // 顶层 url 恒为线路1(apn.moedot.net)；用户在详情页切了线路就必须跟着换
        assertEquals(
            "https://apn.moedot.net/d/wo/2604/%E6%AC%BA%E8%AF%8826.mp4",
            xifanPlaybackUrl(json, "xfxf1")
        )
        assertTrue(xifanPlaybackUrl(json, "AL").startsWith("https://play.xfvod.pro:8088/"))
        assertTrue(xifanPlaybackUrl(json, "CS").startsWith("https://dl.playxf.top/"))
    }

    @Test
    fun playbackFallsBackToTopLevelUrl() {
        // 线路被撤下时不该直接报错，播站点选的那条更好
        val json = JSONObject(json("play.json"))

        assertEquals(
            "https://apn.moedot.net/d/wo/2604/%E6%AC%BA%E8%AF%8826.mp4",
            xifanPlaybackUrl(json, "GONE")
        )
        assertEquals(
            "https://apn.moedot.net/d/wo/2604/%E6%AC%BA%E8%AF%8826.mp4",
            xifanPlaybackUrl(json, null)
        )
    }

    @Test
    fun playbackUrlIsBlankWhenNothingResolved() {
        // {"ok":false,"error":"not_found"} 这类响应没有 url 也没有 candidates
        assertEquals("", xifanPlaybackUrl(JSONObject("""{"ok":false}"""), "xfxf1"))
    }

    /**
     * 回归：`issue-web-playback` 是 Edge Function，`episode_id` 传字符串一律 400
     * `bad_request`。传数字才返回线路——之前传的是字符串，表现为"该集暂无播放地址"，
     * 看起来像这集没资源，实际是请求体类型不对。
     *
     * 断言的是"值的 JSON 类型"而不是整个请求体文本：org.json 底层是 HashMap，
     * 键的输出顺序不保证，拿字符串比对会得到一个和本 bug 无关的脆弱断言。
     */
    @Test
    fun playbackRequestSendsEpisodeIdAsNumber() {
        val body = xifanPlaybackRequestBody("12303")
        val json = JSONObject(body!!)

        assertEquals("fallback", json.getString("action"))
        assertTrue(
            "episode_id 必须是 JSON 数字，传字符串会被服务端判为 bad_request",
            json.get("episode_id") is Number
        )
        assertEquals(12303L, json.getLong("episode_id"))
    }

    @Test
    fun playbackRequestRejectsNonNumericEpisodeId() {
        // 从播放地址里没解析出数字 id 时不该发出一个注定 400 的请求
        assertNull(xifanPlaybackRequestBody("abc"))
        assertNull(xifanPlaybackRequestBody(""))
    }
}