package com.dogancaglar.paymentservice.port.inbound.consumers.config


import com.dogancaglar.paymentservice.adapter.outbound.kafka.PaymentEventPublisher
import com.dogancaglar.paymentservice.adapter.outbound.redis.PaymentOrderRetryQueueAdapter
import com.dogancaglar.paymentservice.application.usecases.ProcessPaymentService
import com.dogancaglar.paymentservice.application.usecases.RecordLedgerEntriesService
import com.dogancaglar.paymentservice.application.usecases.RequestLedgerRecordingService
import com.dogancaglar.paymentservice.application.util.PaymentOrderDomainEventMapper
import com.dogancaglar.paymentservice.application.usecases.AccountBalanceService
import com.dogancaglar.paymentservice.consumers.EventDedupCache
import com.dogancaglar.paymentservice.ports.outbound.AccountBalanceCachePort
import com.dogancaglar.paymentservice.ports.outbound.AccountBalanceSnapshotPort
import com.dogancaglar.paymentservice.ports.outbound.AccountDirectoryPort
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
    fun processPaymentService(
        paymentOrderModificationPort: PaymentOrderModificationPort,
        @Qualifier("syncPaymentEventPublisher") syncPaymentEventPublisher: PaymentEventPublisher,
        paymentOrderRetryQueueAdapter: PaymentOrderRetryQueueAdapter,
        clock: Clock,
        paymentOrderDomainEventMapper: PaymentOrderDomainEventMapper
    ): ProcessPaymentService {
        return ProcessPaymentService(
            paymentOrderModificationPort = paymentOrderModificationPort,
            eventPublisher = syncPaymentEventPublisher,
            retryQueuePort = paymentOrderRetryQueueAdapter,
            clock = clock,
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
        ledgerEntrPort: LedgerEntryPort,
        syncPaymentEventPublisher: PaymentEventPublisher,
        @Qualifier(
            "accountDirectoryImpl") accountDirectoryImpl: AccountDirectoryPort,
                                                clock: Clock): RecordLedgerEntriesService{

        return RecordLedgerEntriesService(ledgerEntrPort,syncPaymentEventPublisher,accountDirectoryImpl,clock)
    }

    @Bean
    fun eventDedupCache(): EventDedupCache = EventDedupCache()



    @Bean
    fun accountBalanceService(
        @Qualifier("accountBalanceSnapshotAdapter") accountBalanceSnapshotAdapter: AccountBalanceSnapshotPort,
        @Qualifier("accountBalanceRedisCacheAdapter") accountBalanceRedisCacheAdapter : AccountBalanceCachePort
    ): AccountBalanceService {
        return AccountBalanceService(
            snapshotPort =accountBalanceSnapshotAdapter,
            cachePort = accountBalanceRedisCacheAdapter
        )
    }
}