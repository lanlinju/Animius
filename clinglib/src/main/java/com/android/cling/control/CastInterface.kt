package com.android.cling.control

import androidx.lifecycle.MutableLiveData
import com.android.cling.entity.ClingPlayType
import org.fourthline.cling.model.meta.Device
import org.fourthline.cling.support.lastchange.EventedValue
import org.fourthline.cling.support.model.BrowseFlag
import org.fourthline.cling.support.model.DIDLContent
import org.fourthline.cling.support.model.MediaInfo
import org.fourthline.cling.support.model.PositionInfo
import org.fourthline.cling.support.model.TransportInfo
import org.fourthline.cling.support.model.TransportState

interface DeviceControl : AvTransportServiceAction, RendererServiceAction, ContentServiceAction,
    LiveDateAction

object EmptyDeviceControl : DeviceControl {
    override fun setAVTransportURI(
        uri: String,
        title: String,
        type: ClingPlayType,
        callback: ServiceActionCallback<Unit>?
    ) {
    }

    override fun setNextAVTransportURI(
        uri: String,
        title: String,
        type: ClingPlayType,
        callback: ServiceActionCallback<Unit>?
    ) {
    }

    override fun play(speed: String, callback: ServiceActionCallback<Unit>?) {}
    override fun pause(callback: ServiceActionCallback<Unit>?) {}
    override fun stop(callback: ServiceActionCallback<Unit>?) {}
    override fun seek(millSeconds: Long, callback: ServiceActionCallback<Unit>?) {}
    override fun next(callback: ServiceActionCallback<Unit>?) {}
    override fun previous(callback: ServiceActionCallback<Unit>?) {}
    override fun getPositionInfo(callback: ServiceActionCallback<PositionInfo>?) {}
    override fun getMediaInfo(callback: ServiceActionCallback<MediaInfo>?) {}
    override fun getTransportInfo(callback: ServiceActionCallback<TransportInfo>?) {}
    override fun setVolume(volume: Int, callback: ServiceActionCallback<Unit>?) {}
    override fun getVolume(callback: ServiceActionCallback<Int>?) {}
    override fun setMute(mute: Boolean, callback: ServiceActionCallback<Unit>?) {}
    override fun getMute(callback: ServiceActionCallback<Boolean>?) {}
    override fun browse(
        objectId: String,
        flag: BrowseFlag,
        filter: String,
        firstResult: Int,
        maxResults: Int,
        callback: ServiceActionCallback<DIDLContent>?
    ) {
    }

    override fun search(
        containerId: String,
        searchCriteria: String,
        filter: String,
        firstResult: Int,
        maxResults: Int,
        callback: ServiceActionCallback<DIDLContent>?
    ) {
    }

    override fun getCurrentState(): MutableLiveData<TransportState> = MutableLiveData()
    override fun getCurrentVolume(): MutableLiveData<Int> = MutableLiveData()
    override fun getCurrentMute(): MutableLiveData<Boolean> = MutableLiveData()
    override fun getCurrentPositionInfo(): MutableLiveData<PositionInfo> = MutableLiveData()
}

interface OnDeviceControlListener {
    fun onConnected(device: Device<*, *, *>) {}
    fun onDisconnected(device: Device<*, *, *>) {}
}

internal interface SubscriptionListener {
    fun failed(subscriptionId: String?) {}
    fun established(subscriptionId: String?) {}
    fun ended(subscriptionId: String?) {}
    fun onReceived(subscriptionId: String?, event: EventedValue<*>) {}
}
