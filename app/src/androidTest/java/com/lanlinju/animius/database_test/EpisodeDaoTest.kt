package com.lanlinju.animius.database_test

import android.content.Context
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lanlinju.animius.data.local.dao.EpisodeDao
import com.lanlinju.animius.data.local.dao.HistoryDao
import com.lanlinju.animius.data.local.database.AnimeDatabase
import com.lanlinju.animius.data.local.entity.EpisodeEntity
import com.lanlinju.animius.data.local.entity.HistoryEntity
import com.lanlinju.animius.util.SourceMode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException

@RunWith(AndroidJUnit4::class)
class EpisodeDaoTest {
    private lateinit var episodeDao: EpisodeDao
    private lateinit var historyDao: HistoryDao
    private lateinit var animeDatabase: AnimeDatabase

    private var history1 = HistoryEntity(1, "海贼王1", "img1", "/video1", SourceMode.Silisili.name)

    @Before
    fun createDb() {
        val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
        animeDatabase = Room.inMemoryDatabaseBuilder(context, AnimeDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        episodeDao = animeDatabase.episodeDao()
        historyDao = animeDatabase.historyDao()

    }

    @After
    @Throws(IOException::class)
    fun closeDb() {
        animeDatabase.close()
    }

    @Test
    @Throws(Exception::class)
    fun daoInsert_insertsEpisodeIntoDB() = runBlocking {
        addOneHistoryToDb()
        val history = historyDao.getHistory("/video1").first()
        val ep1 = EpisodeEntity(1, history.historyId, "第一集", "/video2")
        val ep2 = EpisodeEntity(2, history.historyId, "第二集", "/video3")

        episodeDao.insertEpisodes(listOf(ep1, ep2))
        val episodes = episodeDao.getEpisodes(history.historyId).first()
        Assert.assertEquals(episodes.size, 2)
    }

    @Test
    @Throws(Exception::class)
    fun daoGetAllEpisodes_returnsAllEpisodesFromDB() = runBlocking {
        addTwoEpisodesToDb()
        val history = historyDao.getHistory("/video1").first()

        val episodes = episodeDao.getEpisodes(history.historyId).first()
        Assert.assertEquals(episodes.size, 2)
    }

    @Test
    @Throws(Exception::class)
    fun daoGetNoExistEpisode_returnsNullEpisodeFromDB() = runBlocking {
        val episodes = episodeDao.getEpisodes(-1).first()
        Assert.assertTrue(episodes.isEmpty())
    }

    @Test
    @Throws(Exception::class)
    fun daoDeleteEpisodes_DatabaseWillBeEmpty() = runBlocking {
        addOneEpisodesToDb()
        episodeDao.deleteAll()

        val history = historyDao.getHistory("/video1").first()
        val episodes = episodeDao.getEpisodes(history.historyId).first()
        Assert.assertEquals(episodes.size, 0)
    }

    private suspend fun addOneHistoryToDb() {
        historyDao.insertHistory(history1)
    }

    private suspend fun addOneEpisodesToDb() {
        addOneHistoryToDb()
        val history = historyDao.getHistory("/video1").first()
        val ep1 = EpisodeEntity(1, history.historyId, "第一集", "/video2")
        episodeDao.insertEpisode(ep1)
    }

    private suspend fun addTwoEpisodesToDb() {
        addOneHistoryToDb()
        val history = historyDao.getHistory("/video1").first()
        val ep1 = EpisodeEntity(1, history.historyId, "第一集", "/video2")
        val ep2 = EpisodeEntity(2, history.historyId, "第二集", "/video3")
        episodeDao.insertEpisodes(listOf(ep1, ep2))
    }

}