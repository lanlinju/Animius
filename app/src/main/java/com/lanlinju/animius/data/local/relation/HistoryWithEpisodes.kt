package com.lanlinju.animius.data.local.relation

import androidx.room.Embedded
import androidx.room.Relation
import com.lanlinju.animius.data.local.entity.EpisodeEntity
import com.lanlinju.animius.data.local.entity.HistoryEntity
import com.lanlinju.animius.domain.model.History
import com.lanlinju.animius.util.SourceMode
import java.text.SimpleDateFormat
import java.util.Locale

data class HistoryWithEpisodes(
    @Embedded val history: HistoryEntity,
    @Relation(
        parentColumn = "history_id",
        entityColumn = "history_id"
    )
    val episodes: List<EpisodeEntity>
) {
    /** 数据源已被移除时返回 null，由调用方过滤掉。 */
    fun toHistory(): History? {
        val sourceMode = SourceMode.fromName(history.source) ?: return null
        val sortedEpisodes = episodes.sortedByDescending { it.createdAt }
        val simpleDateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())

        return History(
            title = history.title,
            imgUrl = history.imgUrl,
            detailUrl = history.detailUrl,
            lastEpisodeName = if (sortedEpisodes.isEmpty()) "" else sortedEpisodes.first().name,
            lastEpisodeUrl = if (sortedEpisodes.isEmpty()) "" else sortedEpisodes.first().episodeUrl,
            sourceMode = sourceMode,
            time = simpleDateFormat.format(history.updatedAt),
            episodes = emptyList()
        )
    }
}
