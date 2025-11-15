package com.dogancaglar.paymentservice.port.inbound.consumers

import com.dogancaglar.paymentservice.config.kafka.KafkaTxExecutor
import com.dogancaglar.paymentservice.ports.inbound.AccountBalanceUseCase
import io.mockk.mockk
import org.junit.jupiter.api.BeforeEach

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

