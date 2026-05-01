package com.dogancaglar.paymentservice.port.inbound.consumers

import com.dogancaglar.paymentservice.infra.adapter.outbound.kafka.config.KafkaTxExecutor
import com.dogancaglar.paymentservice.ports.inbound.usecases.AccountBalanceUseCase
import com.dogancaglar.paymentservice.ports.outbound.EventDeduplicationPort
import io.mockk.mockk
import org.junit.jupiter.api.BeforeEach

class AccountBalanceConsumerTest {

    private lateinit var kafkaTxExecutor: KafkaTxExecutor
    private lateinit var accountBalanceService: AccountBalanceUseCase
    private lateinit var consumer: AccountBalanceConsumer
    private lateinit var eventDeduplicationPort: EventDeduplicationPort

    @BeforeEach
    fun setUp() {
        kafkaTxExecutor = mockk(relaxed = true)
        accountBalanceService = mockk(relaxed = true)
        eventDeduplicationPort = mockk(relaxed = true)
        
        consumer = AccountBalanceConsumer(kafkaTxExecutor, accountBalanceService,eventDeduplicationPort)
    }

}

