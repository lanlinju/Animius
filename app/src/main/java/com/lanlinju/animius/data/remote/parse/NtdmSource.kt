package com.lanlinju.animius.data.remote.parse

import com.lanlinju.animius.data.remote.dto.AnimeBean
import com.lanlinju.animius.data.remote.dto.AnimeDetailBean
import com.lanlinju.animius.data.remote.dto.EpisodeBean
import com.lanlinju.animius.data.remote.dto.HomeBean
import com.lanlinju.animius.data.remote.dto.VideoBean
import com.lanlinju.animius.util.DownloadManager
import com.lanlinju.animius.util.decryptData
import com.lanlinju.animius.util.getDefaultDomain
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.select.Elements

class NtdmSource : AnimeSource {
    override val DEFAULT_DOMAIN: String = "https://www.ntdm8.com/"
    override var baseUrl: String = getDefaultDomain()

    override suspend fun getHomeData(): List<HomeBean> {
        val source = DownloadManager.getHtml(baseUrl)
        val document = Jsoup.parse(source)

        val homeBeanList = mutableListOf<HomeBean>()
        val content = document.select("div.div_left.baseblock")
        content.select("div.blockcontent")
            .zip(content.select("div.blocktitle"))
            .forEach { (elements, titleElement) ->
                val title = titleElement.select("a").text()
                val homeItemBeanList = getAnimeList(elements.select("li"))
                homeBeanList.add(HomeBean(title = title, animes = homeItemBeanList))
            }

        return homeBeanList
    }

    private fun getAnimeList(elements: Elements): List<AnimeBean> {
        val animeList = mutableListOf<AnimeBean>()
        elements.forEach { el ->
            val (title, url) = el.select("a").last()!!.run { Pair(text(), attr("href")) }
            val imgUrl = el.select("img").attr("src")
            animeList.add(AnimeBean(title = title, img = imgUrl, url = url))
        }
        return animeList
    }

    override suspend fun getAnimeDetail(detailUrl: String): AnimeDetailBean {
        val source = DownloadManager.getHtml("${baseUrl}/$detailUrl")
        val document = Jsoup.parse(source)

        val info = document.select("div.div_right")
        val title = info.select("h4").text()
        val desc = info.select("div.baseblock").select("p").text()
        val contentLeft = document.select("div.div_left")
        val imgUrl = contentLeft.select("img").attr("src")
        val tags = contentLeft.select("li.detail_imform_kv").last()!!.select("a").map { it.text() }
        val channels = getAnimeEpisodes(document)
        val relatedAnimes =
            getAnimeList(info.select("div#recommend_block").select("li"))
        return AnimeDetailBean(title, imgUrl, desc, tags, relatedAnimes, channels = channels)
    }

    private fun getAnimeEpisodes(document: Document): Map<Int, List<EpisodeBean>> {
        val channels = mutableMapOf<Int, List<EpisodeBean>>()
        document.select("div#content").select("div.movurl").forEachIndexed { i, e ->
            val episodes = mutableListOf<EpisodeBean>()
            e.select("a").forEach { el ->
                val name = el.text()
                val url = el.attr("href")
                episodes.add(EpisodeBean(name, url))
            }
            channels[i] = episodes
        }

        return channels
    }

    override suspend fun getVideoData(episodeUrl: String): VideoBean {
        val source = DownloadManager.getHtml("${baseUrl}/${episodeUrl}")
        val document = Jsoup.parse(source)

        val videoUrl = getVideoUrl(document)

        return VideoBean(videoUrl)
    }

    override suspend fun getSearchData(
        query: String,
        page: Int
    ): List<AnimeBean> {
        val source = DownloadManager.getHtml("${baseUrl}/search/-------------.html?wd=$query&page=$page")
        val document = Jsoup.parse(source)

        val animeList = mutableListOf<AnimeBean>()
        document.select("div.cell.blockdif2").forEach { el ->
            val title = el.select("div.cell_imform").select("a")[0].text()
            val url = el.select("a")[0].attr("href")
            val imgUrl = el.select("img").attr("src")
            animeList.add(AnimeBean(title = title, img = imgUrl, url = url))
        }
        return animeList
    }

    override suspend fun getWeekData(): Map<Int, List<AnimeBean>> {
        val source = DownloadManager.getHtml(baseUrl)
        val document = Jsoup.parse(source)
        val weekMap = mutableMapOf<Int, List<AnimeBean>>()
        document.select("div#content")
            .select("div.mod").forEachIndexed { index, element ->
                val dayList = mutableListOf<AnimeBean>()
                element.select("li").forEach { el ->
                    val (title, url) = el.select("a").first()!!.run { Pair(text(), attr("href")) }
                    val episodeName = el.select("a").last()!!.text()
                    dayList.add(AnimeBean(title = title, img = "", url = url, episodeName  = episodeName))
                }
                weekMap[index] = dayList
            }
        return weekMap
    }

    private val BASE_M3U8 = "https://danmu.yhdmjx.com/m3u8.php?url="
    private val AES_KEY = "57A891D97E332A9D"
    private suspend fun getVideoUrl(document: Document): String {
        val urlTarget = document.select("div#ageframediv > script")[0].data()
        val urlRegex = """"url":"(.*?)","url_next"""".toRegex()
        val url = urlRegex.find(urlTarget)!!.groupValues[1]

        val doc = Jsoup.parse(DownloadManager.getHtml(BASE_M3U8 + url))
        val ivTarget = doc.select("head > script")[1].data()
        val ivRegex = """var bt_token = "(.*?)"""".toRegex()
        val iv = ivRegex.find(ivTarget)!!.groupValues[1]

        val videoUrlTarget = doc.select("body > script")[0].data()
        val videoUrlRegex = """getVideoInfo\("(.*?)"""".toRegex()
        val encryptedVideoUrl = videoUrlRegex.find(videoUrlTarget)!!.groupValues[1]
        return decryptData(encryptedVideoUrl, key = AES_KEY, iv = iv)
    }
}