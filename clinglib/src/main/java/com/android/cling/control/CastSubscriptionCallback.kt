package com.android.cling.control

import org.fourthline.cling.controlpoint.SubscriptionCallback
import org.fourthline.cling.model.gena.CancelReason
import org.fourthline.cling.model.gena.GENASubscription
import org.fourthline.cling.model.message.UpnpResponse
import org.fourthline.cling.model.meta.Service
import org.fourthline.cling.support.lastchange.LastChangeParser
import java.util.logging.Logger

/**
 *
 */
internal class CastSubscriptionCallback(
    service: Service<*, *>?,
    requestedDurationSeconds: Int = 1800, // Cling default 1800
    private val lastChangeParser: LastChangeParser,
    private val callback: SubscriptionListener,
) : SubscriptionCallback(service, requestedDurationSeconds) {

    private val logger = Logger.getLogger("SubscriptionCallback")

    override fun failed(
        subscription: GENASubscription<*>,
        responseStatus: UpnpResponse?,
        exception: Exception?,
        defaultMsg: String?
    ) {
        logger.warning("${getTag(subscription)} failed:${responseStatus}, $exception, $defaultMsg")
        executeInMainThread { callback.failed(subscription.subscriptionId) }
    }

    override fun established(subscription: GENASubscription<*>) {
        logger.info("${getTag(subscription)} established")
        executeInMainThread { callback.established(subscription.subscriptionId) }
    }

    override fun ended(
        subscription: GENASubscription<*>,
        reason: CancelReason?,
        responseStatus: UpnpResponse?
    ) {
        logger.warning("${getTag(subscription)} ended: $reason, $responseStatus")
        executeInMainThread { callback.ended(subscription.subscriptionId) }
    }

    override fun eventsMissed(subscription: GENASubscription<*>, numberOfMissedEvents: Int) {
        logger.warning("${getTag(subscription)} eventsMissed: $numberOfMissedEvents")
    }

    override fun eventReceived(subscription: GENASubscription<*>) {
        val lastChangeEventValue = subscription.currentValues["LastChange"]?.value?.toString()
        if (lastChangeEventValue.isNullOrBlank()) return
        logger.info("${getTag(subscription)} eventReceived: ${subscription.currentValues.keys}")
        try {
            val events =
                lastChangeParser.parse(lastChangeEventValue)?.instanceIDs?.firstOrNull()?.values
            events?.forEach { value ->
                logger.info("    value: [${value.javaClass.simpleName}] $value")
                executeInMainThread { callback.onReceived(subscription.subscriptionId, value) }
            }
        } catch (e: Exception) {
            logger.warning("${getTag(subscription)} currentValues: ${subscription.currentValues}")
            e.printStackTrace()
        }
    }

    private fun getTag(subscription: GENASubscription<*>) =
        "[${subscription.service.serviceType.type}](${
            subscription.subscriptionId?.split("-")?.last()
        })"
}