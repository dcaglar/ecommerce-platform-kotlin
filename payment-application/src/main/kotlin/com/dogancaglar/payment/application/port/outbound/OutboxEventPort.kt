package  com.dogancaglar.payment.application.port.outbound

import com.dogancaglar.payment.domain.events.OutboxEvent


interface OutboxEventPort {
    fun findByStatus(status: String): List<OutboxEvent>
    fun saveAll(events: List<OutboxEvent>): List<OutboxEvent>
    fun save(event: OutboxEvent): OutboxEvent
    fun countByStatus(status: String): Long
    fun findBatchForDispatch(batchSize: Int): List<OutboxEvent>
}