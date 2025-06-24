package com.dogancaglar.paymentservice.adapter.outbox.producer

import com.dogancaglar.paymentservice.domain.model.OutboxEvent
import com.dogancaglar.paymentservice.domain.port.OutboxEventPort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class OutboxReplicaPoller(
    private val outboxEventPort: OutboxEventPort
) {
    @Transactional("replicaTransactionManager", readOnly = true)
    fun pollBatch(status: String, batchSize: Int): List<OutboxEvent> {
        return outboxEventPort.findBatchForDispatch(status, batchSize)
    }
}