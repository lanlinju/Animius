package com.android.cling

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import androidx.lifecycle.MutableLiveData
import com.android.cling.control.CastControlImpl
import com.android.cling.control.DeviceControl
import com.android.cling.control.EmptyDeviceControl
import com.android.cling.control.OnDeviceControlListener
import com.android.cling.entity.ClingDevice
import com.android.cling.entity.ClingDeviceList
import com.android.cling.listener.BrowseRegistryListener
import com.android.cling.manager.IClingManager
import com.android.cling.service.ClingUpnpService
import com.android.cling.service.LocalFileService
import com.android.cling.util.Utils
import com.android.cling.util.Utils.isNull
import org.fourthline.cling.android.AndroidUpnpService
import org.fourthline.cling.controlpoint.ControlPoint
import org.fourthline.cling.model.meta.Device
import org.fourthline.cling.model.types.ServiceType
import org.fourthline.cling.model.types.UDAServiceType
import org.fourthline.cling.registry.Registry
import org.fourthline.cling.registry.RegistryListener

class ClingDLNAManager private constructor() : IClingManager {
    private var mUpnpService: AndroidUpnpService? = null
    private val mBrowseRegistryListener = BrowseRegistryListener()  //设备监听
    private val currentDevices = MutableLiveData<MutableList<ClingDevice>>()  //当前链接的设备
    private val currentSelectDevices = MutableLiveData<ClingDevice?>()  //当前链接的设备
    private var referer: String? = null

    val isInit: Boolean
        get() = !isNull(mUpnpService)

    override fun searchDevices() {
        if (isInit) {
            ClingDeviceList.clear()
            registry!!.removeAllRemoteDevices()
            registry!!.removeAllLocalDevices()
            controlPoint?.search()
        }
    }

    fun getSearchDevices(): MutableLiveData<MutableList<ClingDevice>> {
        return currentDevices
    }


    internal fun updateCurrentDevices(devices: MutableList<ClingDevice>) {
        currentDevices.postValue(devices)
    }

    override val controlPoint: ControlPoint?
        get() {
            if (isNull(mUpnpService)) {
                return null
            }
            return mUpnpService!!.controlPoint
        }


    override val registry: Registry?
        get() {
            if (isNull(mUpnpService)) {
                return null
            }
            return mUpnpService!!.registry
        }


    override fun setSelectDevice(selectedDevice: ClingDevice) {
        currentSelectDevices.postValue(selectedDevice)
    }

    override fun getSelectedDevice(): ClingDevice? {
        return currentSelectDevices.value
    }

    override fun cleanSelectedDevice() {
        currentSelectDevices.postValue(null)
    }

    override fun setUpnpService(upnpService: AndroidUpnpService?) {
        mUpnpService = upnpService
    }


    override fun addListener(registryListener: RegistryListener) {
        registry?.addListener(registryListener)
    }

    override fun removeListener(registryListener: RegistryListener) {
        registry?.removeListener(registryListener)
    }


    override fun destroy() {
        if (isInit) {
            mUpnpService = null
        }
    }


    fun startBindUpnpService(context: Context, success: (() -> Unit)? = null): ServiceConnection {
        val mServiceConnection = object : ServiceConnection {
            override fun onServiceConnected(className: ComponentName, service: IBinder) {
                val upnpService: AndroidUpnpService = service as AndroidUpnpService
                setUpnpService(upnpService)
                addListener(mBrowseRegistryListener)
                searchDevices()
                success?.invoke()
            }

            override fun onServiceDisconnected(className: ComponentName) {
                removeListener(mBrowseRegistryListener)
                setUpnpService(null)
            }

            override fun onBindingDied(componentName: ComponentName) {
                removeListener(mBrowseRegistryListener)
                setUpnpService(null)
            }
        }

        val serviceIntent = Intent(context, ClingUpnpService::class.java)
        context.applicationContext.bindService(
            serviceIntent,
            mServiceConnection,
            Context.BIND_AUTO_CREATE
        )
        return mServiceConnection
    }

    fun stopBindService(context: Context, mServiceConnection: ServiceConnection?) {
        if (!isInit) return
        mServiceConnection?.let { context.applicationContext.unbindService(it) }
    }

    internal fun getReferer(): String? {
        return referer
    }

    fun setReferer(referer: String?) {
        this.referer = referer
    }

    private val deviceControlMap = mutableMapOf<Device<*, *, *>, DeviceControl?>()

    fun connectDevice(device: ClingDevice, listener: OnDeviceControlListener): DeviceControl {
        return connectDevice(device.device, listener)
    }


    fun connectDevice(device: Device<*, *, *>, listener: OnDeviceControlListener): DeviceControl {
        val service = mUpnpService?.get() ?: return EmptyDeviceControl
        setSelectDevice(ClingDevice(device))
        var control = deviceControlMap[device]
        if (control == null) {
            val newController = CastControlImpl(service.controlPoint, device, listener)
            deviceControlMap[device] = newController
            control = newController
        }
        return control
    }

    fun disconnectDevice(device: Device<*, *, *>) {
        (deviceControlMap[device] as? CastControlImpl)?.release()
        deviceControlMap[device] = null
    }


    companion object {
        internal val AV_TRANSPORT_SERVICE: ServiceType = UDAServiceType("AVTransport")
        internal val RENDERING_CONTROL_SERVICE: ServiceType = UDAServiceType("RenderingControl")
        internal val SERVICE_CONNECTION_MANAGER: ServiceType = UDAServiceType("ConnectionManager")
        internal val SERVICE_TYPE_CONTENT_DIRECTORY: ServiceType =
            UDAServiceType("ContentDirectory")
        private var INSTANCE: ClingDLNAManager? = null

        @Synchronized
        fun getInstant(): ClingDLNAManager {
            if (INSTANCE == null) {
                INSTANCE = ClingDLNAManager()
            }
            return INSTANCE!!
        }

        /**
         * Set referer
         * 用于一些播放链接有防盗链的情况，需要设置referer
         * TODO 测试
         */
        fun setReferer(referer: String?) {
            getInstant().setReferer(referer)
        }

        fun getBaseUrl(context: Context): String {
            return Utils.getBaseUrl(context)
        }

        fun startLocalFileService(context: Context) {
            context.startService(Intent(context, LocalFileService::class.java))
        }

        fun stopLocalFileService(context: Context) {
            context.stopService(Intent(context, LocalFileService::class.java))
        }
    }

}