package com.android.cling.util

import com.android.cling.entity.ClingPlayType
import com.android.cling.entity.ClingPlayType.TYPE_AUDIO
import com.android.cling.entity.ClingPlayType.TYPE_IMAGE
import com.android.cling.entity.ClingPlayType.TYPE_VIDEO
import org.fourthline.cling.support.model.DIDLObject
import org.fourthline.cling.support.model.ProtocolInfo
import org.fourthline.cling.support.model.Res
import org.fourthline.cling.support.model.item.AudioItem
import org.fourthline.cling.support.model.item.ImageItem
import org.fourthline.cling.support.model.item.VideoItem
import org.seamless.util.MimeType
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Date

internal object ClingUtils {

    fun pushMediaToRender(url: String?, id: String, name: String, itemType: ClingPlayType): String {
        val size: Long = 0
        val res = Res(MimeType(ProtocolInfo.WILDCARD, ProtocolInfo.WILDCARD), size, url)
        val creator = "cling"
        val parentId = "0"
        val metadata: String = when (itemType) {
            TYPE_IMAGE -> {
                val imageItem = ImageItem(id, parentId, name, creator, res)
                createItemMetadata(imageItem)
            }

            TYPE_VIDEO -> {
                val videoItem = VideoItem(id, parentId, name, creator, res)
                createItemMetadata(videoItem)
            }

            TYPE_AUDIO -> {
                val audioItem = AudioItem(id, parentId, name, creator, res)
                createItemMetadata(audioItem)
            }
        }
        return metadata
    }

    private fun createItemMetadata(item: DIDLObject): String {
        val metadata = StringBuilder()
        metadata.append(DIDL_LITE_HEADER)
        metadata.append(
            String.format(
                "<item id=\"%s\" parentID=\"%s\" restricted=\"%s\">",
                item.id,
                item.parentID,
                if (item.isRestricted) "1" else "0"
            )
        )
        metadata.append(String.format("<dc:title>%s</dc:title>", item.title))
        var creator = item.creator
        if (creator != null) {
            creator = creator.replace("<".toRegex(), "_")
            creator = creator.replace(">".toRegex(), "_")
        }
        metadata.append(String.format("<upnp:artist>%s</upnp:artist>", creator))
        metadata.append(String.format("<upnp:class>%s</upnp:class>", item.clazz.value))
        val sdf: DateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss")
        val now = Date()
        val time = sdf.format(now)
        metadata.append(String.format("<dc:date>%s</dc:date>", time))
        val res = item.firstResource
        if (res != null) {
            // protocol info
            var protocolinfo = ""
            val pi = res.protocolInfo
            if (pi != null) {
                protocolinfo = String.format(
                    "protocolInfo=\"%s:%s:%s:%s\"",
                    pi.protocol,
                    pi.network,
                    pi.contentFormatMimeType,
                    pi
                        .additionalInfo
                )
            }

            // resolution, extra info, not adding yet
            var resolution = ""
            if (res.resolution != null && res.resolution.length > 0) {
                resolution = String.format("resolution=\"%s\"", res.resolution)
            }

            // duration
            var duration = ""
            if (res.duration != null && res.duration.length > 0) {
                duration = String.format("duration=\"%s\"", res.duration)
            }

            // res begin
            //            metadata.append(String.format("<res %s>", protocolinfo)); // no resolution & duration yet
            metadata.append(String.format("<res %s %s %s>", protocolinfo, resolution, duration))

            // url
            val url = res.value
            metadata.append(url)

            // res end
            metadata.append("</res>")
        }
        metadata.append("</item>")
        metadata.append(DIDL_LITE_FOOTER)
        return metadata.toString()
    }


    /** 每次接收 500ms 延迟  */
    private const val RECEIVE_DELAY = 500
    private const val DIDL_LITE_FOOTER = "</DIDL-Lite>"
    private const val DIDL_LITE_HEADER =
        "<?xml version=\"1.0\"?>" + "<DIDL-Lite " + "xmlns=\"urn:schemas-upnp-org:metadata-1-0/DIDL-Lite/\" " +
                "xmlns:dc=\"http://purl.org/dc/elements/1.1/\" " + "xmlns:upnp=\"urn:schemas-upnp-org:metadata-1-0/upnp/\" " +
                "xmlns:dlna=\"urn:schemas-dlna-org:metadata-1-0/\">"

}