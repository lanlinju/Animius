/*
 * Copyright (C) 2014 Kevin Shen
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.cling.control

import android.os.Handler
import android.os.Looper
import org.fourthline.cling.support.model.item.VideoItem

internal object MetadataUtils {
    private const val DIDL_LITE_XML =
        """<?xml version="1.0"?><DIDL-Lite xmlns="urn:schemas-upnp-org:metadata-1-0/DIDL-Lite/" xmlns:dc="http://purl.org/dc/elements/1.1/" xmlns:upnp="urn:schemas-upnp-org:metadata-1-0/upnp/">%s</DIDL-Lite>"""

    fun create(url: String, title: String) = DIDL_LITE_XML.format(buildItemXml(url, title))

    private fun buildItemXml(url: String, title: String): String {
        val item = VideoItem(title, "-1", title, null)
        val builder = StringBuilder()
        builder.append("<item id=\"$title\" parentID=\"-1\" restricted=\"1\">")
        builder.append("<dc:title>$title</dc:title>")
        builder.append("<upnp:class>${item.clazz.value}</upnp:class>")
        builder.append("<res protocolInfo=\"http-get:*:video/mp4:*;DLNA.ORG_OP=01;\">$url</res>")
        builder.append("</item>")
        return builder.toString()
    }
}

private val mainHandler = Handler(Looper.getMainLooper())

fun executeInMainThread(runnable: Runnable) {
    if (Thread.currentThread() == Looper.getMainLooper().thread) {
        runnable.run()
    } else {
        mainHandler.post(runnable)
    }
}