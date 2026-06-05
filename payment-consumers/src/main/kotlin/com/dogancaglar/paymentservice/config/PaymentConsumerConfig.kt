package com.dogancaglar.paymentservice.config


import com.dogancaglar.paymentservice.application.service.ProcessPspResultProcessingService
import com.dogancaglar.paymentservice.application.service.ProcessCaptureService
import com.dogancaglar.paymentservice.application.events.CaptureRequested
import com.dogancaglar.paymentservice.ports.outbound.RetryQueuePort
import com.dogancaglar.paymentservice.ports.outbound.PspCaptureGatewayPort
import com.dogancaglar.paymentservice.application.service.AccountBalanceService
import com.dogancaglar.paymentservice.application.service.AccountBalanceReadService
import com.dogancaglar.paymentservice.ports.outbound.AccountBalanceCachePort
import com.dogancaglar.paymentservice.ports.outbound.AccountBalanceSnapshotPort
import com.dogancaglar.paymentservice.ports.outbound.AccountDirectoryPort
import com.dogancaglar.paymentservice.ports.outbound.IdGeneratorPort
import com.dogancaglar.paymentservice.ports.outbound.CentralDbTransactionalFacadePort
import com.dogancaglar.paymentservice.ports.outbound.PaymentRepository
import com.dogancaglar.paymentservice.ports.outbound.SerializationPort
import com.dogancaglar.paymentservice.ports.outbound.PaymentTxPort
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import com.dogancaglar.paymentservice.ports.outbound.CentralOutboxWriterPort

@Configuration
open class PaymentConsumerConfig {



    @Bean
    fun getAccountBalanceReadService(cachePort: AccountBalanceCachePort,snapshotPort: AccountBalanceSnapshotPort): AccountBalanceReadService{
        return AccountBalanceReadService(cachePort,snapshotPort)
    }




    @Bean
    fun pspResultProcessingService(
        centralDbTransactionalFacadePort: CentralDbTransactionalFacadePort,
        @Qualifier("accountDirectoryImpl") accountDirectoryImpl: AccountDirectoryPort,
        paymentTxPort: PaymentTxPort,
        idGeneratorPort: IdGeneratorPort,
        paymentRepository: PaymentRepository,
        serializationPort: SerializationPort
    ): ProcessPspResultProcessingService {
        return ProcessPspResultProcessingService(
            centralDbTransactionalFacadePort = centralDbTransactionalFacadePort,
            accountDirectory = accountDirectoryImpl,
            paymentTxPort = paymentTxPort,
            idGeneratorPort = idGeneratorPort,
            paymentRepository = paymentRepository,
            serializationPort = serializationPort
        )
    }

    @Bean
    fun recordCaptureSubmissionService(
        centralDbTransactionalFacadePort: CentralDbTransactionalFacadePort,
        paymentRepository: PaymentRepository,
        paymentTxPort: PaymentTxPort,
        idGeneratorPort: IdGeneratorPort
    ): com.dogancaglar.paymentservice.application.service.RecordCaptureSubmissionService {
        return com.dogancaglar.paymentservice.application.service.RecordCaptureSubmissionService(
            centralDbTransactionalFacadePort = centralDbTransactionalFacadePort,
            paymentRepository = paymentRepository,
            paymentTxPort = paymentTxPort,
            idGeneratorPort = idGeneratorPort
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

    @Bean
    fun processCaptureService(
        pspCaptureGatewayPort: PspCaptureGatewayPort,
        paymentRepository: PaymentRepository,
        retryQueuePort: RetryQueuePort<CaptureRequested>,
        @Qualifier("centralOutboxWriterAdapter") centralOutboxWriterPort: CentralOutboxWriterPort,
        serializationPort: SerializationPort
    ): ProcessCaptureService {
        return ProcessCaptureService(
            pspCaptureGatewayPort,
            paymentRepository,
            retryQueuePort,
            centralOutboxWriterPort,
            serializationPort
        )
    }
}