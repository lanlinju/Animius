package com.android.cling.control

import androidx.lifecycle.MutableLiveData
import com.android.cling.entity.ClingPlayType
import org.fourthline.cling.support.model.BrowseFlag
import org.fourthline.cling.support.model.DIDLContent
import org.fourthline.cling.support.model.MediaInfo
import org.fourthline.cling.support.model.PositionInfo
import org.fourthline.cling.support.model.TransportInfo
import org.fourthline.cling.support.model.TransportState

interface ServiceActionCallback<T> {
    fun onSuccess(result: T)
    fun onFailure(msg: String)
}

// --------------------------------------------------------------------------------
// ---- AvService
// --------------------------------------------------------------------------------
interface AvTransportServiceAction {
    fun setAVTransportURI(
        uri: String,
        title: String,
        type: ClingPlayType,
        callback: ServiceActionCallback<Unit>? = null
    )

    fun setNextAVTransportURI(
        uri: String,
        title: String,
        type: ClingPlayType,
        callback: ServiceActionCallback<Unit>? = null
    )

    fun play(speed: String = "1", callback: ServiceActionCallback<Unit>? = null)
    fun pause(callback: ServiceActionCallback<Unit>? = null)
    fun stop(callback: ServiceActionCallback<Unit>? = null)
    fun seek(millSeconds: Long, callback: ServiceActionCallback<Unit>? = null)
    fun next(callback: ServiceActionCallback<Unit>? = null)
    fun canNext(callback: ServiceActionCallback<Boolean>? = null) {
        callback?.onSuccess(false)
    }

    fun previous(callback: ServiceActionCallback<Unit>? = null)
    fun canPrevious(callback: ServiceActionCallback<Boolean>? = null) {
        callback?.onSuccess(false)
    }

    fun getPositionInfo(callback: ServiceActionCallback<PositionInfo>?)
    fun getMediaInfo(callback: ServiceActionCallback<MediaInfo>?)
    fun getTransportInfo(callback: ServiceActionCallback<TransportInfo>?)

}

interface LiveDateAction {
    fun getCurrentState(): MutableLiveData<TransportState>
    fun getCurrentVolume(): MutableLiveData<Int>
    fun getCurrentMute(): MutableLiveData<Boolean>
    fun getCurrentPositionInfo(): MutableLiveData<PositionInfo>
}

// --------------------------------------------------------------------------------
// ---- RendererService
// --------------------------------------------------------------------------------
interface RendererServiceAction {
    fun setVolume(volume: Int, callback: ServiceActionCallback<Unit>? = null)
    fun getVolume(callback: ServiceActionCallback<Int>?)
    fun setMute(mute: Boolean, callback: ServiceActionCallback<Unit>? = null)
    fun getMute(callback: ServiceActionCallback<Boolean>?)
}

// --------------------------------------------------------------------------------
// ---- ContentService
// --------------------------------------------------------------------------------
interface ContentServiceAction {
    fun browse(
        objectId: String = "0",
        flag: BrowseFlag = BrowseFlag.DIRECT_CHILDREN,
        filter: String = "*",
        firstResult: Int = 0,
        maxResults: Int = Int.MAX_VALUE,
        callback: ServiceActionCallback<DIDLContent>?
    )

    fun search(
        containerId: String = "0",
        searchCriteria: String = "",
        filter: String = "*",
        firstResult: Int = 0,
        maxResults: Int = Int.MAX_VALUE,
        callback: ServiceActionCallback<DIDLContent>?
    )
}