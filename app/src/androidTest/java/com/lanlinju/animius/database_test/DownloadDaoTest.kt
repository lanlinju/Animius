package com.lanlinju.animius.database_test

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lanlinju.animius.data.local.dao.DownloadDao
import com.lanlinju.animius.data.local.database.AnimeDatabase
import com.lanlinju.animius.data.local.entity.DownloadEntity
import com.lanlinju.animius.util.SourceMode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException

@RunWith(AndroidJUnit4::class)
class DownloadDaoTest {
    private lateinit var downloadDao: DownloadDao
    private lateinit var animeDatabase: AnimeDatabase

    private var download1 =
        DownloadEntity(1, "海贼王1", "/detailUrl1", "/imgUrl1", SourceMode.Silisili.name, System.currentTimeMillis())
    private var download2 =
        DownloadEntity(2, "海贼王2", "/detailUrl2", "/imgUrl2", SourceMode.Silisili.name, System.currentTimeMillis())

    @Before
    fun createDb() {
        val context: Context = ApplicationProvider.getApplicationContext()
        animeDatabase = Room.inMemoryDatabaseBuilder(context, AnimeDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        downloadDao = animeDatabase.downLoadDao()
    }

    @After
    @Throws(IOException::class)
    fun closeDb() {
        animeDatabase.close()
    }

    @Test
    @Throws(Exception::class)
    fun daoInsert_insertsHistoryIntoDB() = runBlocking {
        addOneDownloadToDb()
        val download = downloadDao.getDownload(download1.detailUrl).first()
        assertEquals(download.detailUrl, download1.detailUrl)
    }

    @Test
    fun daoCheckDownload_returnsNotNullFromDB() = runBlocking {
        val download = downloadDao.checkDownload(download1.detailUrl).first()
        assertNull(download)
    }

    @Test
    fun daoCheckDownload_returnsNullFromDB() = runBlocking {
        addOneDownloadToDb()
        val download = downloadDao.checkDownload(download1.detailUrl).first()
        assertNotNull(download)
    }

    @Test
    fun daoDeleteOneDownload_deleteOneDownloadFromDB() = runBlocking {
        addOneDownloadToDb()
        downloadDao.deleteDownload(download1.detailUrl)
        val actual = downloadDao.checkDownload(download1.detailUrl).first()
        assertNull(actual)
    }

    private suspend fun addOneDownloadToDb() {
        downloadDao.insertDownload(download1)
    }

    private suspend fun addTwoDownloadsToDb() {
        downloadDao.insertDownload(download1)
        downloadDao.insertDownload(download2)
    }
}