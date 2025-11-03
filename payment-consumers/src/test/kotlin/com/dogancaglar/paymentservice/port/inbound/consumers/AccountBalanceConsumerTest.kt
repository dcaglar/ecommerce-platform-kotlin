package com.dogancaglar.paymentservice.port.inbound.consumers

import com.dogancaglar.common.event.DomainEventEnvelopeFactory
import com.dogancaglar.common.event.EventEnvelope
import com.dogancaglar.common.logging.LogContext
import com.dogancaglar.paymentservice.config.kafka.KafkaTxExecutor
import com.dogancaglar.paymentservice.domain.event.EventMetadatas
import com.dogancaglar.paymentservice.domain.event.LedgerEntriesRecorded
import com.dogancaglar.paymentservice.ports.inbound.AccountBalanceUseCase
import com.dogancaglar.paymentservice.util.KafkaRecordTestHelper
import com.dogancaglar.paymentservice.util.LedgerEntriesRecordedTestHelper
import io.mockk.*
import org.apache.kafka.clients.consumer.Consumer
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.consumer.OffsetAndMetadata
import org.apache.kafka.common.TopicPartition
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

class AccountBalanceConsumerTest {

    private lateinit var kafkaTxExecutor: KafkaTxExecutor
    private lateinit var accountBalanceService: AccountBalanceUseCase
    private lateinit var consumer: AccountBalanceConsumer

    @BeforeEach
    fun setUp() {
        kafkaTxExecutor = mockk(relaxed = true)
        accountBalanceService = mockk(relaxed = true)
        
        consumer = AccountBalanceConsumer(kafkaTxExecutor, accountBalanceService)
    }

}

