package  com.dogancaglar.payment.application.port.outbound

import com.dogancaglar.common.event.EventEnvelope
import com.dogancaglar.common.event.EventMetadata
import java.util.*

interface EventPublisherPort {
    fun <T> publish(
        preSetEventIdFromCaller: UUID? = null,
        aggregateId: String,
        eventMetaData: EventMetadata<T>,
        data: T,
        traceId: String? = null,
        parentEventId: UUID? = null
    ): EventEnvelope<T>

    /**
     * Publishes an event synchronously (blocking until confirmation).
     */
    fun <T> publishSync(
        preSetEventIdFromCaller: UUID? = null,
        aggregateId: String,
        eventMetaData: EventMetadata<T>,
        data: T,
        traceId: String? = null,
        parentEventId: UUID? = null,
        timeoutSeconds: Long = 5
    ): EventEnvelope<T>
}

