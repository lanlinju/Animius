package com.lanlinju.animius.data.local.relation

import androidx.room.Embedded
import androidx.room.Relation
import com.lanlinju.animius.data.local.entity.DownloadDetailEntity
import com.lanlinju.animius.data.local.entity.DownloadEntity
import com.lanlinju.animius.domain.model.Download
import com.lanlinju.animius.util.SourceMode

data class DownloadWithDownloadDetails(
    @Embedded val download: DownloadEntity,
    @Relation(
        parentColumn = "download_id",
        entityColumn = "download_id",
    )
    val downloadDetails: List<DownloadDetailEntity>
) {
    /** 数据源已被移除时返回 null，由调用方过滤掉。 */
    fun toDownload(): Download? {
        val sourceMode = SourceMode.fromName(download.source) ?: return null
        val totalSize = downloadDetails.fold(0L) { acc, d -> acc + d.fileSize }
        return Download(
            title = download.title,
            detailUrl = download.detailUrl,
            imgUrl = download.imgUrl,
            sourceMode = sourceMode,
            totalSize = totalSize,
            downloadDetails = downloadDetails.map { it.toDownloadDetail() }
        )
    }
}
