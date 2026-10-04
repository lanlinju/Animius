package com.lanlinju.animius.database_test

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lanlinju.animius.data.local.database.AnimeDatabase
import com.lanlinju.animius.data.local.entity.DownloadEntity
import com.lanlinju.animius.data.local.entity.FavouriteEntity
import com.lanlinju.animius.data.local.entity.HistoryEntity
import com.lanlinju.animius.data.repository.RoomRepositoryImpl
import com.lanlinju.animius.util.SourceMode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException

/**
 * 数据源下线后,库里会留下 source 列是失效枚举名的老记录(收藏/历史/下载都是按名字存的)。
 *
 * 这些记录必须被跳过,不能直接 `SourceMode.valueOf(source)`——那会抛
 * IllegalArgumentException,并且因为异常是从 Flow 里冒出来的,会把整个列表一起打挂,
 * 而不仅仅跳过这一行。
 *
 * 回归背景:SourceMode 从 11 项删到 7 项时移除了 Mxdm / Ntdm / Nyafun / Yhdm,
 * 老用户的收藏页、历史页、下载页会直接崩。
 */
@RunWith(AndroidJUnit4::class)
class RemovedSourceRowTest {
    private lateinit var db: AnimeDatabase
    private lateinit var repository: RoomRepositoryImpl

    /** 上游存在过、现已从 [SourceMode] 移除的名字 */
    private val removedSource = "Mxdm"

    @Before
    fun createDb() {
        val context: Context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, AnimeDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = RoomRepositoryImpl(db)
    }

    @After
    @Throws(IOException::class)
    fun closeDb() {
        db.close()
    }

    @Test
    @Throws(Exception::class)
    fun getFavourites_skipsRowsWithRemovedSource() = runBlocking {
        db.favouriteDao().insertFavourite(
            FavouriteEntity(
                title = "失效源收藏",
                detailUrl = "/dead",
                imgUrl = "img",
                source = removedSource
            )
        )
        db.favouriteDao().insertFavourite(
            FavouriteEntity(
                title = "正常收藏",
                detailUrl = "/alive",
                imgUrl = "img",
                source = SourceMode.Silisili.name
            )
        )

        // 先确认两行真的入库了,否则下面的"只剩一行"可能只是插入失败,测不出过滤逻辑
        assertEquals(2, db.favouriteDao().getAllFavourites().first().size)

        val favourites = repository.getFavourites().first()

        assertEquals(listOf("/alive"), favourites.map { it.detailUrl })
    }

    @Test
    @Throws(Exception::class)
    fun getHistories_skipsRowsWithRemovedSource() = runBlocking {
        db.historyDao().insertHistory(
            HistoryEntity(
                title = "失效源历史",
                imgUrl = "img",
                detailUrl = "/dead",
                source = removedSource
            )
        )
        db.historyDao().insertHistory(
            HistoryEntity(
                title = "正常历史",
                imgUrl = "img",
                detailUrl = "/alive",
                source = SourceMode.Silisili.name
            )
        )

        // 先确认两行真的入库了,否则下面的"只剩一行"可能只是插入失败,测不出过滤逻辑
        assertEquals(2, db.historyDao().getHistoryWithEpisodes().first().size)

        val histories = repository.getHistories().first()

        assertEquals(listOf("/alive"), histories.map { it.detailUrl })
    }

    @Test
    @Throws(Exception::class)
    fun getDownloads_skipsRowsWithRemovedSource() = runBlocking {
        db.downLoadDao().insertDownload(
            DownloadEntity(
                title = "失效源下载",
                detailUrl = "/dead",
                imgUrl = "img",
                source = removedSource
            )
        )
        db.downLoadDao().insertDownload(
            DownloadEntity(
                title = "正常下载",
                detailUrl = "/alive",
                imgUrl = "img",
                source = SourceMode.Silisili.name
            )
        )

        // 先确认两行真的入库了,否则下面的"只剩一行"可能只是插入失败,测不出过滤逻辑
        assertEquals(2, db.downLoadDao().getDownloads().first().size)

        val downloads = repository.getDownloads().first()

        assertEquals(listOf("/alive"), downloads.map { it.detailUrl })
    }
}
