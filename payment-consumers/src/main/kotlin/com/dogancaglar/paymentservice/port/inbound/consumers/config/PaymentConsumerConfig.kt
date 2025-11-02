package com.dogancaglar.paymentservice.port.inbound.consumers.config


import com.dogancaglar.paymentservice.adapter.outbound.kafka.PaymentEventPublisher
import com.dogancaglar.paymentservice.adapter.outbound.redis.PaymentRetryQueueAdapter
import com.dogancaglar.paymentservice.adapter.outbound.redis.PspResultRedisCacheAdapter
import com.dogancaglar.paymentservice.application.usecases.ProcessPaymentService
import com.dogancaglar.paymentservice.application.usecases.RecordLedgerEntriesService
import com.dogancaglar.paymentservice.application.usecases.RequestLedgerRecordingService
import com.dogancaglar.paymentservice.domain.util.PaymentFactory
import com.dogancaglar.paymentservice.domain.util.PaymentOrderDomainEventMapper
import com.dogancaglar.paymentservice.domain.util.PaymentOrderFactory
import com.dogancaglar.paymentservice.application.usecases.AccountBalanceService
import com.dogancaglar.paymentservice.ports.inbound.AccountBalanceUseCase
import com.dogancaglar.paymentservice.ports.outbound.AccountBalanceCachePort
import com.dogancaglar.paymentservice.ports.outbound.BalanceIdempotencyPort
import com.dogancaglar.paymentservice.ports.outbound.LedgerEntryPort
import com.dogancaglar.paymentservice.ports.outbound.PaymentOrderModificationPort
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Clock

@Configuration
class PaymentConsumerConfig {


    @Bean
    fun paymentOrderDomainEventMapper(clock: Clock): PaymentOrderDomainEventMapper =
        PaymentOrderDomainEventMapper(clock)

    @Bean
    fun paymentOrderFactory(): PaymentOrderFactory =
        PaymentOrderFactory()


    @Bean
    fun processPaymentService(
        paymentOrderModificationPort: PaymentOrderModificationPort,
        @Qualifier("syncPaymentEventPublisher") syncPaymentEventPublisher: PaymentEventPublisher,
        paymentRetryQueueAdapter: PaymentRetryQueueAdapter,
        clock: Clock,
        paymentOrderFactory: PaymentOrderFactory,
        paymentOrderDomainEventMapper: PaymentOrderDomainEventMapper
    ): ProcessPaymentService {
        return ProcessPaymentService(
            paymentOrderModificationPort = paymentOrderModificationPort,
            eventPublisher = syncPaymentEventPublisher,
            retryQueuePort = paymentRetryQueueAdapter,
            clock = clock,
            paymentOrderFactory = paymentOrderFactory,
            paymentOrderDomainEventMapper = paymentOrderDomainEventMapper
        )
    }

    @Bean
    fun requestLedgerRecordingService(
        @Qualifier(
            "syncPaymentEventPublisher") syncPaymentEventPublisher: PaymentEventPublisher,
                                                clock: Clock): RequestLedgerRecordingService{

        return RequestLedgerRecordingService(syncPaymentEventPublisher,clock)
    }


    @Bean
    fun recordLedgerEntriesService(
        @Qualifier("ledgerEntryTxAdapter") ledgerEntryTxAdapter: LedgerEntryPort,
        @Qualifier(
            "syncPaymentEventPublisher") syncPaymentEventPublisher: PaymentEventPublisher,
                                                clock: Clock): RecordLedgerEntriesService{

        return RecordLedgerEntriesService(ledgerEntryTxAdapter,syncPaymentEventPublisher,clock)
    }



    @Bean
    fun createPaymentFactory(clock: Clock) =
        PaymentFactory(clock = clock)

    @Bean
    fun createPaymentOrderFactory(clock: Clock) =
        PaymentOrderFactory()

    @Bean
    fun accountBalanceService(
        balanceIdempotencyPort: BalanceIdempotencyPort,
        accountBalanceCachePort: AccountBalanceCachePort
    ): AccountBalanceService {
        return AccountBalanceService(
            balanceIdempotencyPort = balanceIdempotencyPort,
            accountBalanceCachePort = accountBalanceCachePort
        )
    }
}