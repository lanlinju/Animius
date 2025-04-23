package com.android.cling.control

import android.os.Handler
import android.os.Looper
import com.android.cling.control.action.SetNextAVTransportURI
import com.android.cling.entity.ClingPlayType
import com.android.cling.util.ClingUtils.pushMediaToRender
import org.fourthline.cling.controlpoint.ActionCallback
import org.fourthline.cling.controlpoint.ControlPoint
import org.fourthline.cling.model.ModelUtil
import org.fourthline.cling.model.action.ActionInvocation
import org.fourthline.cling.model.message.UpnpResponse
import org.fourthline.cling.model.meta.Service
import org.fourthline.cling.support.avtransport.callback.GetMediaInfo
import org.fourthline.cling.support.avtransport.callback.GetPositionInfo
import org.fourthline.cling.support.avtransport.callback.GetTransportInfo
import org.fourthline.cling.support.avtransport.callback.Next
import org.fourthline.cling.support.avtransport.callback.Pause
import org.fourthline.cling.support.avtransport.callback.Play
import org.fourthline.cling.support.avtransport.callback.Previous
import org.fourthline.cling.support.avtransport.callback.Seek
import org.fourthline.cling.support.avtransport.callback.SetAVTransportURI
import org.fourthline.cling.support.avtransport.callback.Stop
import org.fourthline.cling.support.contentdirectory.callback.Browse
import org.fourthline.cling.support.contentdirectory.callback.Search
import org.fourthline.cling.support.lastchange.LastChangeParser
import org.fourthline.cling.support.model.BrowseFlag
import org.fourthline.cling.support.model.DIDLContent
import org.fourthline.cling.support.model.MediaInfo
import org.fourthline.cling.support.model.PositionInfo
import org.fourthline.cling.support.model.TransportInfo
import org.fourthline.cling.support.renderingcontrol.callback.GetMute
import org.fourthline.cling.support.renderingcontrol.callback.GetVolume
import org.fourthline.cling.support.renderingcontrol.callback.SetMute
import org.fourthline.cling.support.renderingcontrol.callback.SetVolume
import java.util.Formatter
import java.util.Locale
import java.util.logging.Logger

private object Actions {
    // AvTransport
    const val SetAVTransportURI = "SetAVTransportURI"
    const val SetNextAVTransportURI = "SetNextAVTransportURI"
    const val Play = "Play"
    const val Pause = "Pause"
    const val Stop = "Stop"
    const val Seek = "Seek"
    const val Next = "Next"
    const val Previous = "Previous"
    const val GetPositionInfo = "GetPositionInfo"
    const val GetMediaInfo = "GetMediaInfo"
    const val GetTransportInfo = "GetTransportInfo"

    // Renderer
    const val SetVolume = "SetVolume"
    const val GetVolume = "GetVolume"
    const val SetMute = "SetMute"
    const val GetMute = "GetMute"
}

private val logger = Logger.getLogger("ActionCallback")

internal class ActionCallbackWrapper(
    private val actionCallback: ActionCallback,
    private val logging: Boolean = true,
) : ActionCallback(actionCallback.actionInvocation) {
    override fun success(invocation: ActionInvocation<out Service<*, *>>?) {
        if (logging) logger.info("${invocation?.action?.name} [success] ${invocation?.outputMap?.toString()}")
        actionCallback.success(invocation)
    }

    override fun failure(
        invocation: ActionInvocation<out Service<*, *>>?,
        operation: UpnpResponse?,
        defaultMsg: String?
    ) {
        if (logging) logger.info("${invocation?.action?.name} [failure] $defaultMsg")
        actionCallback.failure(invocation, operation, defaultMsg)
    }
}

internal abstract class BaseServiceExecutor(
    private val controlPoint: ControlPoint,
    protected val service: Service<*, *>?,
) {
    private val handler = Handler(Looper.getMainLooper())
    protected abstract val logger: Logger

    protected fun invalidServiceAction(actionName: String): Boolean {
        val result = service?.getAction(actionName) == null
        if (result) logger.warning("[Unsupported]$actionName")
        return result
    }

    protected fun executeAction(actionCallback: ActionCallback, logging: Boolean = true) {
        controlPoint.execute(ActionCallbackWrapper(actionCallback, logging))
    }

    fun subscribe(subscriptionCallback: SubscriptionListener, lastChangeParser: LastChangeParser) {
        controlPoint.execute(
            CastSubscriptionCallback(
                service,
                callback = subscriptionCallback,
                lastChangeParser = lastChangeParser
            )
        )
    }

    protected fun <T> notifySuccess(listener: ServiceActionCallback<T>?, result: T) {
        listener?.run { notify { onSuccess(result) } }
    }

    protected fun <T> notifyFailure(
        listener: ServiceActionCallback<T>?,
        exception: String = "Service not support this action."
    ) {
        listener?.run { notify { onFailure(exception) } }
    }

    private fun notify(runnable: Runnable) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            handler.post(runnable)
        } else {
            runnable.run()
        }
    }

    // ---------------------------------------------------------------------------------------------------------
    // AvService
    // ---------------------------------------------------------------------------------------------------------
    internal class AVServiceExecutorImpl(
        controlPoint: ControlPoint,
        service: Service<*, *>?,
    ) : BaseServiceExecutor(controlPoint, service), AvTransportServiceAction {
        override val logger = Logger.getLogger("AvTransportService")

        override fun setAVTransportURI(
            uri: String,
            title: String,
            type: ClingPlayType,
            callback: ServiceActionCallback<Unit>?
        ) {
            logger.info("${Actions.SetAVTransportURI}: $title, $uri")
            if (invalidServiceAction(Actions.SetAVTransportURI)) {
                notifyFailure(callback)
                return
            }
            val metadata = pushMediaToRender(uri, "-1", title, type)
            logger.info("${Actions.SetAVTransportURI}: $metadata")
            executeAction(object : SetAVTransportURI(service, uri, metadata) {
                override fun success(invocation: ActionInvocation<*>?) {
                    notifySuccess(callback, result = Unit)
                }

                override fun failure(
                    invocation: ActionInvocation<*>?,
                    operation: UpnpResponse?,
                    defaultMsg: String?
                ) {
                    notifyFailure(callback, defaultMsg ?: "Error")
                }
            })
        }

        override fun setNextAVTransportURI(
            uri: String,
            title: String,
            type: ClingPlayType,
            callback: ServiceActionCallback<Unit>?
        ) {
            logger.info("${Actions.SetNextAVTransportURI}: $title, $uri")
            if (invalidServiceAction(Actions.SetNextAVTransportURI)) {
                notifyFailure(callback)
                return
            }
            val metadata = pushMediaToRender(uri, "-1", title, type)
            logger.info("${Actions.SetNextAVTransportURI}: $metadata")
            executeAction(object :
                SetNextAVTransportURI(service = service, uri = uri, metadata = metadata) {
                override fun success(invocation: ActionInvocation<*>?) {
                    notifySuccess(callback, result = Unit)
                }

                override fun failure(
                    invocation: ActionInvocation<*>?,
                    operation: UpnpResponse?,
                    defaultMsg: String?
                ) {
                    notifyFailure(callback, defaultMsg ?: "Error")
                }
            })
        }

        override fun play(speed: String, callback: ServiceActionCallback<Unit>?) {
            logger.info(Actions.Play)
            if (invalidServiceAction(Actions.Play)) {
                notifyFailure(callback)
                return
            }
            executeAction(object : Play(service, speed) {
                override fun success(invocation: ActionInvocation<*>?) {
                    notifySuccess(callback, result = Unit)
                }

                override fun failure(
                    invocation: ActionInvocation<*>?,
                    operation: UpnpResponse?,
                    defaultMsg: String?
                ) {
                    notifyFailure(callback, defaultMsg ?: "Error")
                }
            })
        }

        override fun pause(callback: ServiceActionCallback<Unit>?) {
            logger.info(Actions.Pause)
            if (invalidServiceAction(Actions.Pause)) {
                notifyFailure(callback)
                return
            }
            executeAction(object : Pause(service) {
                override fun success(invocation: ActionInvocation<*>?) {
                    notifySuccess(callback, result = Unit)
                }

                override fun failure(
                    invocation: ActionInvocation<*>?,
                    operation: UpnpResponse?,
                    defaultMsg: String?
                ) {
                    notifyFailure(callback, defaultMsg ?: "Error")
                }
            })
        }

        override fun stop(callback: ServiceActionCallback<Unit>?) {
            logger.info(Actions.Stop)
            if (invalidServiceAction(Actions.Stop)) {
                notifyFailure(callback)
                return
            }
            executeAction(object : Stop(service) {
                override fun success(invocation: ActionInvocation<*>?) {
                    notifySuccess(callback, result = Unit)
                }

                override fun failure(
                    invocation: ActionInvocation<*>?,
                    operation: UpnpResponse?,
                    defaultMsg: String?
                ) {
                    notifyFailure(callback, defaultMsg ?: "Error")
                }
            })
        }

        override fun seek(millSeconds: Long, callback: ServiceActionCallback<Unit>?) {
            logger.info("${Actions.Seek}: ${ModelUtil.toTimeString(millSeconds / 1000)}")
            if (invalidServiceAction(Actions.Seek)) {
                notifyFailure(callback)
                return
            }
            executeAction(object : Seek(service, getStringTime(millSeconds)) {
                override fun success(invocation: ActionInvocation<*>?) {
                    notifySuccess(callback, result = Unit)
                }

                override fun failure(
                    invocation: ActionInvocation<*>?,
                    operation: UpnpResponse?,
                    defaultMsg: String?
                ) {
                    notifyFailure(callback, defaultMsg ?: "Error")
                }
            })
        }

        override fun next(callback: ServiceActionCallback<Unit>?) {
            logger.info(Actions.Next)
            if (invalidServiceAction(Actions.Next)) {
                notifyFailure(callback)
                return
            }
            executeAction(object : Next(service) {
                override fun success(invocation: ActionInvocation<*>?) {
                    notifySuccess(callback, result = Unit)
                }

                override fun failure(
                    invocation: ActionInvocation<*>?,
                    operation: UpnpResponse?,
                    defaultMsg: String?
                ) {
                    notifyFailure(callback, defaultMsg ?: "Error")
                }
            })
        }

        override fun previous(callback: ServiceActionCallback<Unit>?) {
            logger.info(Actions.Previous)
            if (invalidServiceAction(Actions.Previous)) {
                notifyFailure(callback)
                return
            }
            executeAction(object : Previous(service) {
                override fun success(invocation: ActionInvocation<*>?) {
                    notifySuccess(callback, result = Unit)
                }

                override fun failure(
                    invocation: ActionInvocation<*>?,
                    operation: UpnpResponse?,
                    defaultMsg: String?
                ) {
                    notifyFailure(callback, defaultMsg ?: "Error")
                }
            })
        }

        override fun getPositionInfo(callback: ServiceActionCallback<PositionInfo>?) {
            //logger.i(Actions.GetPositionInfo)
            if (invalidServiceAction(Actions.GetPositionInfo)) {
                notifyFailure(callback)
                return
            }
            executeAction(object : GetPositionInfo(service) {
                override fun received(
                    invocation: ActionInvocation<*>?,
                    positionInfo: PositionInfo
                ) {
                    notifySuccess(callback, result = positionInfo)
                }

                override fun failure(
                    invocation: ActionInvocation<*>?,
                    operation: UpnpResponse?,
                    defaultMsg: String?
                ) {
                    notifyFailure(callback, defaultMsg ?: "Error")
                }
            }, logging = false)
        }

        override fun getMediaInfo(callback: ServiceActionCallback<MediaInfo>?) {
            logger.info(Actions.GetMediaInfo)
            if (invalidServiceAction(Actions.GetMediaInfo)) {
                notifyFailure(callback)
                return
            }
            executeAction(object : GetMediaInfo(service) {
                override fun received(invocation: ActionInvocation<*>?, mediaInfo: MediaInfo) {
                    notifySuccess(callback, result = mediaInfo)
                }

                override fun failure(
                    invocation: ActionInvocation<*>?,
                    operation: UpnpResponse?,
                    defaultMsg: String?
                ) {
                    notifyFailure(callback, defaultMsg ?: "Error")
                }
            })
        }

        override fun getTransportInfo(callback: ServiceActionCallback<TransportInfo>?) {
            logger.info(Actions.GetTransportInfo)
            if (invalidServiceAction(Actions.GetTransportInfo)) {
                notifyFailure(callback)
                return
            }
            executeAction(object : GetTransportInfo(service) {
                override fun received(
                    invocation: ActionInvocation<*>?,
                    transportInfo: TransportInfo
                ) {
                    notifySuccess(callback, result = transportInfo)
                }

                override fun failure(
                    invocation: ActionInvocation<*>?,
                    operation: UpnpResponse?,
                    defaultMsg: String?
                ) {
                    notifyFailure(callback, defaultMsg ?: "Error")
                }
            })
        }
    }

    // ---------------------------------------------------------------------------------------------------------
    // RendererService
    // ---------------------------------------------------------------------------------------------------------
    internal class RendererServiceExecutorImpl(
        controlPoint: ControlPoint,
        service: Service<*, *>?,
    ) : BaseServiceExecutor(controlPoint, service), RendererServiceAction {
        override val logger: Logger = Logger.getLogger("RendererService")
        override fun setVolume(volume: Int, callback: ServiceActionCallback<Unit>?) {
            logger.info("${Actions.SetVolume}: $volume")
            if (invalidServiceAction(Actions.SetVolume)) {
                notifyFailure(callback)
                return
            }
            executeAction(object : SetVolume(service, volume.toLong()) {
                override fun success(invocation: ActionInvocation<*>?) {
                    notifySuccess(callback, result = Unit)
                }

                override fun failure(
                    invocation: ActionInvocation<*>?,
                    operation: UpnpResponse?,
                    defaultMsg: String?
                ) {
                    notifyFailure(callback, defaultMsg ?: "Error")
                }
            })
        }

        override fun getVolume(callback: ServiceActionCallback<Int>?) {
            logger.info(Actions.GetVolume)
            if (invalidServiceAction(Actions.GetVolume)) {
                notifyFailure(callback)
                return
            }
            executeAction(object : GetVolume(service) {
                override fun received(invocation: ActionInvocation<*>?, currentVolume: Int) {
                    notifySuccess(callback, result = currentVolume)
                }

                override fun failure(
                    invocation: ActionInvocation<*>?,
                    operation: UpnpResponse?,
                    defaultMsg: String?
                ) {
                    notifyFailure(callback, defaultMsg ?: "Error")
                }
            })
        }

        override fun setMute(mute: Boolean, callback: ServiceActionCallback<Unit>?) {
            logger.info("${Actions.SetMute}: $mute")
            if (invalidServiceAction(Actions.SetMute)) {
                notifyFailure(callback)
                return
            }
            executeAction(object : SetMute(service, mute) {
                override fun success(invocation: ActionInvocation<*>?) {
                    notifySuccess(callback, result = Unit)
                }

                override fun failure(
                    invocation: ActionInvocation<*>?,
                    operation: UpnpResponse?,
                    defaultMsg: String?
                ) {
                    notifyFailure(callback, defaultMsg ?: "Error")
                }
            })
        }

        override fun getMute(callback: ServiceActionCallback<Boolean>?) {
            logger.info(Actions.GetMute)
            if (invalidServiceAction(Actions.GetMute)) {
                notifyFailure(callback)
                return
            }
            executeAction(object : GetMute(service) {
                override fun received(invocation: ActionInvocation<*>?, currentMute: Boolean) {
                    notifySuccess(callback, result = currentMute)
                }

                override fun failure(
                    invocation: ActionInvocation<*>?,
                    operation: UpnpResponse?,
                    defaultMsg: String?
                ) {
                    notifyFailure(callback, defaultMsg ?: "Error")
                }
            })
        }
    }

    // ---------------------------------------------------------------------------------------------------------
    // ContentService
    // ---------------------------------------------------------------------------------------------------------
    internal class ContentServiceExecutorImpl(
        controlPoint: ControlPoint,
        service: Service<*, *>?,
    ) : BaseServiceExecutor(controlPoint, service), ContentServiceAction {
        override val logger: Logger = Logger.getLogger("ContentService")
        override fun browse(
            objectId: String,
            flag: BrowseFlag,
            filter: String,
            firstResult: Int,
            maxResults: Int,
            callback: ServiceActionCallback<DIDLContent>?
        ) {
            if (invalidServiceAction("Browse")) {
                notifyFailure(callback)
                return
            }
            executeAction(object :
                Browse(service, objectId, flag, filter, firstResult.toLong(), maxResults.toLong()) {
                override fun received(
                    actionInvocation: ActionInvocation<out Service<*, *>>?,
                    didl: DIDLContent
                ) {
                    notifySuccess(callback, result = didl)
                }

                override fun failure(
                    invocation: ActionInvocation<*>?,
                    operation: UpnpResponse?,
                    defaultMsg: String?
                ) {
                    notifyFailure(callback, defaultMsg ?: "Error")
                }

                override fun updateStatus(status: Status?) {}
            })
        }

        override fun search(
            containerId: String,
            searchCriteria: String,
            filter: String,
            firstResult: Int,
            maxResults: Int,
            callback: ServiceActionCallback<DIDLContent>?
        ) {
            if (invalidServiceAction("Search")) {
                notifyFailure(callback)
                return
            }
            executeAction(object : Search(
                service,
                containerId,
                searchCriteria,
                filter,
                firstResult.toLong(),
                maxResults.toLong()
            ) {
                override fun received(
                    actionInvocation: ActionInvocation<out Service<*, *>>?,
                    didl: DIDLContent
                ) {
                    notifySuccess(callback, result = didl)
                }

                override fun failure(
                    invocation: ActionInvocation<*>?,
                    operation: UpnpResponse?,
                    defaultMsg: String?
                ) {
                    notifyFailure(callback, defaultMsg ?: "Error")
                }

                override fun updateStatus(status: Status?) {}
            })
        }
    }
}

private fun getStringTime(timeMs: Long): String {
    val formatter = Formatter(StringBuilder(), Locale.US)
    val totalSeconds = timeMs / 1000
    val seconds = totalSeconds % 60
    val minutes = totalSeconds / 60 % 60
    val hours = totalSeconds / 3600
    return formatter.format("%02d:%02d:%02d", hours, minutes, seconds).toString()
}