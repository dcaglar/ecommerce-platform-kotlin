package  com.dogancaglar.payment.application.port.outbound

import com.dogancaglar.payment.application.events.OutboxEvent


interface OutboxEventPort {
    fun findByStatus(status: String): List<OutboxEvent>
    fun saveAll(events: List<OutboxEvent>): List<OutboxEvent>
    fun save(event: OutboxEvent): OutboxEvent
    fun countByStatus(status: String): Long
    fun findByStatusWithLimit(status: String, limit: Int): List<OutboxEvent>
    fun findBatchForDispatch(batchSize: Int): List<OutboxEvent>
    fun deleteByStatus(status: String): Int
}
