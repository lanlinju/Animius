package com.android.cling.manager

import org.fourthline.cling.android.AndroidUpnpService
import org.fourthline.cling.registry.Registry

interface IClingManager : IDLNAManager {
    fun setUpnpService(upnpService: AndroidUpnpService?)
    val registry: Registry?
}