package com.dogancaglar.paymentservice.adapter.outbox.producer

import com.dogancaglar.paymentservice.domain.model.OutboxEvent
import com.dogancaglar.paymentservice.domain.port.OutboxEventPort
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Service
class ReplicaReadService(
    @Qualifier("outboxEventReader")             // ← replica
    private val reader: OutboxEventPort
) {
    fun pollBatch(cursor: LocalDateTime, batchSize: Int): List<OutboxEvent> {
        return reader.findBatchAfter(cursor, batchSize)
    }

    @Transactional("replicaTransactionManager", readOnly = true)
    fun countByStatus(status: String): Long {
        return reader.countByStatus(status)
    }


}