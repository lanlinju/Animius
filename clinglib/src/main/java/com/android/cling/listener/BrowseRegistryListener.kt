package com.android.cling.listener

import com.android.cling.ClingDLNAManager
import com.android.cling.entity.ClingDevice
import com.android.cling.entity.ClingDeviceList
import org.fourthline.cling.model.meta.Device
import org.fourthline.cling.model.meta.Service
import org.fourthline.cling.registry.DefaultRegistryListener
import org.fourthline.cling.registry.Registry

/**
 * Browse registry listener
 * Fix 设备没有 Action 的问题
 */
internal class BrowseRegistryListener : DefaultRegistryListener() {

    override fun deviceAdded(
        registry: Registry?,
        device: Device<*, out Device<*, *, *>, out Service<*, *>>?
    ) {
        deviceAdded(device as Device<*, *, *>) // 设备 已加入
    }

    override fun deviceRemoved(
        registry: Registry?,
        device: Device<*, out Device<*, *, *>, out Service<*, *>>?
    ) {
        deviceRemoved(device as Device<*, *, *>) // 设备 已移除
    }

    private fun deviceAdded(device: Device<*, *, *>) {
        val clingDevice = ClingDevice(device)
        ClingDeviceList.addDevice(clingDevice)
        ClingDLNAManager.getInstant().updateCurrentDevices(ClingDeviceList.getClingDeviceList())
    }

    private fun deviceRemoved(device: Device<*, *, *>) {
        val clingDevice = ClingDeviceList.getClingDevice(device)
        if (clingDevice != null) {
            ClingDeviceList.removeDevice(clingDevice)
            ClingDLNAManager.getInstant().updateCurrentDevices(ClingDeviceList.getClingDeviceList())
        }
    }
}