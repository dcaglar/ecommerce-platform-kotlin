package com.dogancaglar.paymentservice.config


import com.dogancaglar.paymentservice.application.service.PspResultProcessingService
import com.dogancaglar.paymentservice.application.service.AccountBalanceService
import com.dogancaglar.common.kafka.publisher.PaymentEventPublisher
import com.dogancaglar.paymentservice.application.service.AccountBalanceReadService
import com.dogancaglar.paymentservice.infra.adapter.outbound.persistence.AccountDirectoryImpl
import com.dogancaglar.paymentservice.infra.adapter.outbound.persistence.LedgerEntryTxAdapter
import com.dogancaglar.paymentservice.ports.outbound.AccountBalanceCachePort
import com.dogancaglar.paymentservice.ports.outbound.AccountBalanceSnapshotPort
import com.dogancaglar.paymentservice.ports.outbound.AccountDirectoryPort
import com.dogancaglar.paymentservice.ports.outbound.EventPublisherPort
import com.dogancaglar.paymentservice.ports.outbound.IdGeneratorPort
import com.dogancaglar.paymentservice.ports.outbound.LedgerEntryPort
import com.dogancaglar.paymentservice.ports.outbound.PaymentRepository
import com.dogancaglar.paymentservice.ports.outbound.PspAuthorizationGatewayPort
import com.dogancaglar.paymentservice.ports.outbound.SerializationPort
import com.dogancaglar.paymentservice.ports.outbound.PaymentTxPort
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import com.dogancaglar.paymentservice.ports.outbound.LocalOutboxWriterPort

@Configuration
open class PaymentConsumerConfig {





    @Bean
    fun getAccountBalanceReadService(cachePort: AccountBalanceCachePort,snapshotPort: AccountBalanceSnapshotPort): AccountBalanceReadService{
        return AccountBalanceReadService(cachePort,snapshotPort)
    }




    @Bean
    fun pspResultProcessingService(
        ledgerEntryPort: LedgerEntryPort,
        @Qualifier("accountDirectoryImpl") accountDirectoryImpl: AccountDirectoryPort,
        paymentTxPort: PaymentTxPort,
        idGeneratorPort: IdGeneratorPort,
        paymentRepository: PaymentRepository,
        localOutboxWriterPort: LocalOutboxWriterPort,
        serializationPort: SerializationPort
    ): PspResultProcessingService {
        return PspResultProcessingService(
            ledgerWritePort = ledgerEntryPort,
            accountDirectory = accountDirectoryImpl,
            paymentTxPort = paymentTxPort,
            idGeneratorPort = idGeneratorPort,
            paymentRepository = paymentRepository,
            localOutboxWriterPort = localOutboxWriterPort,
            serializationPort = serializationPort
        )
    }



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