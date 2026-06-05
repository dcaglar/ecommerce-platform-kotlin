package com.dogancaglar.paymentservice.ports.outbound

import com.dogancaglar.paymentservice.domain.model.ledger.JournalEntry
import com.dogancaglar.paymentservice.domain.model.ledger.Tx
import com.dogancaglar.paymentservice.domain.model.payment.Payment
import com.dogancaglar.paymentservice.domain.model.payment.OutboxEvent

interface CentralDbTransactionalFacadePort {

    fun saveAtomically(
        payment: Payment?,
        tx: Tx?,
        journalEntries: List<JournalEntry>,
        outboxEvents: List<OutboxEvent> = emptyList()
    )
}