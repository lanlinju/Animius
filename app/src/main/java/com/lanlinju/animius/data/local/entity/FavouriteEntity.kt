package com.lanlinju.animius.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.lanlinju.animius.domain.model.Favourite
import com.lanlinju.animius.util.FAVOURITE_TABLE
import com.lanlinju.animius.util.SourceMode

@Entity(
    tableName = FAVOURITE_TABLE,
    indices = [Index("detail_url", unique = true)]
)
data class FavouriteEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "favourite_id") val favouriteId: Long = 0,
    @ColumnInfo(name = "title") val title: String,
    @ColumnInfo(name = "detail_url") val detailUrl: String,
    @ColumnInfo(name = "img_url") val imgUrl: String,
    @ColumnInfo(name = "source") val source: String, /* SourceMode  用于确定域名 */
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis()
) {
    /**
     * 数据源已被移除时返回 null（站点都没了，记录留着也放不了），由调用方过滤掉。
     * 集合字段存的是枚举名，不能用 [SourceMode.valueOf] 直接读——
     * 老记录里的失效名字会抛异常，把整个收藏列表一起带崩。
     */
    fun toFavourite(): Favourite? {
        val sourceMode = SourceMode.fromName(source) ?: return null
        return Favourite(
            title = title,
            detailUrl = detailUrl,
            imgUrl = imgUrl,
            sourceMode = sourceMode
        )
    }
}
