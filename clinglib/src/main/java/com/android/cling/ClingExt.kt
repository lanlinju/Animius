package com.android.cling

import android.content.Context
import android.content.ServiceConnection

fun Context.startBindUpnpService(success: (() -> Unit)? = null): ServiceConnection {
    return ClingDLNAManager.getInstant().startBindUpnpService(this, success)
}

fun Context.stopUpnpService(mServiceConnection: ServiceConnection?) {
    ClingDLNAManager.getInstant().stopBindService(this, mServiceConnection)
}
