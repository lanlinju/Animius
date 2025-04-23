package com.android.cling.entity

import com.android.cling.ClingDLNAManager
import org.fourthline.cling.model.meta.Device
import java.util.concurrent.CopyOnWriteArrayList

internal object ClingDeviceList {

    private var mClingDeviceList: CopyOnWriteArrayList<ClingDevice> = CopyOnWriteArrayList()


    fun removeDevice(device: ClingDevice?) {
        mClingDeviceList.remove(device)
    }

    fun addDevice(device: ClingDevice?) {
        device?.let {
            if (contain(device.device)) {
                return
            }
            mClingDeviceList.add(device)
        }
    }

    fun getClingDevice(device: Device<*, *, *>?): ClingDevice? {
        return mClingDeviceList.find {
            it.device == device
        }
    }

    fun contain(device: Device<*, *, *>): Boolean {
        return getClingDevice(device) != null
    }

    fun getClingDeviceList(): MutableList<ClingDevice> {
        return mClingDeviceList
    }


    fun clear() {
        mClingDeviceList.clear()
        ClingDLNAManager.getInstant().updateCurrentDevices(mClingDeviceList)
    }

}