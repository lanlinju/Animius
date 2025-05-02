package com.android.cling.control.action

import org.fourthline.cling.controlpoint.ActionCallback
import org.fourthline.cling.model.action.ActionInvocation
import org.fourthline.cling.model.meta.Service
import org.fourthline.cling.model.types.UnsignedIntegerFourBytes
import java.util.logging.Logger

@Suppress("LeakingThis")
abstract class SetNextAVTransportURI @JvmOverloads constructor(
    service: Service<*, *>?,
    uri: String,
    metadata: String? = null,
) : ActionCallback(ActionInvocation(service?.getAction("SetNextAVTransportURI"))) {
    companion object {
        private val log = Logger.getLogger(SetNextAVTransportURI::class.java.name)
    }

    init {
        log.fine("Creating SetNextAVTransportURI action for URI: $uri")
        getActionInvocation().setInput("InstanceID", UnsignedIntegerFourBytes(0))
        getActionInvocation().setInput("NextURI", uri)
        getActionInvocation().setInput("NextURIMetaData", metadata)
    }

    override fun success(invocation: ActionInvocation<*>?) {
        log.fine("Execution successful")
    }
}