package com.dogancaglar.paymentservice.adapter.outbound.psp

import com.dogancaglar.paymentservice.domain.exception.PspPermanentException
import com.dogancaglar.paymentservice.domain.exception.PspTransientException
import com.dogancaglar.paymentservice.domain.model.PaymentIntent
import com.dogancaglar.paymentservice.domain.model.PaymentMethod
import com.dogancaglar.paymentservice.ports.outbound.PspAuthorizationGatewayPort
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor
import org.springframework.stereotype.Component
import java.util.*
import java.util.concurrent.CompletableFuture
import javax.management.RuntimeMBeanException
import kotlin.random.Random

@Component
@ConditionalOnProperty(name = ["psp.gateway.type"], havingValue = "SIMULATED")
class SimulatedPspAuthorizationGatewayAdapter(
    private val simulator: AuthorizationNetworkSimulator,
    private val config: AuthorizationSimulationProperties,
    @param:Qualifier("createPaymentIntentExecutor") private val createPaymentIntentExecutor: ThreadPoolTaskExecutor,
    @param:Qualifier("authorizePaymentIntentExecutor") private val authorizePaymentIntentExecutor: ThreadPoolTaskExecutor

) : PspAuthorizationGatewayPort {

    private val logger = LoggerFactory.getLogger(javaClass)

    private val active: AuthorizationSimulationProperties.ScenarioConfig
        get() = config.scenarios[config.scenario]
            ?: throw IllegalStateException("No scenario config for ${config.scenario}")

    override fun createPaymentIntent(paymentIntent: PaymentIntent): CompletableFuture<PaymentIntent> {
        return CompletableFuture.supplyAsync({
            // 1. Apply configured simulation path (latency or timeout) from YAML
            simulator.simulate()

            // 2. Decide outcome based on the response distribution defined in configuration
            val sc = active.response
            val roll = Random.nextInt(100) // Percentile roll (0-99) to match YAML probabilities

            when {
                roll < sc.successful -> {
                    // Default high-performance path (successfully returns client data)
                    paymentIntent.markAsCreatedWithPspReferenceAndClientSecret(
                        pspReference = "sim_pi_${UUID.randomUUID()}",
                        clientSecret = "sim_cs_${UUID.randomUUID()}"
                    )
                }
                roll < sc.successful + sc.retryable -> {
                    // Enables testing of application-level retry/fallback logic
                    throw PspTransientException("Simulated transient PSP failure", RuntimeException("transient simulator"))
                }
                else -> {
                    throw PspPermanentException("Simulated permanent PSP failure",RuntimeException("permanent simulator"))
                }
            }
        }, createPaymentIntentExecutor)
    }

    override fun authorizePaymentIntent(paymentIntent: PaymentIntent, token: PaymentMethod?): CompletableFuture<PaymentIntent> {
        return CompletableFuture.supplyAsync({
            // 1. Apply configured simulation path (latency or timeout) from YAML
            simulator.simulate()

            // 2. Decide outcome based on the response distribution defined in configuration
            val sc = active.response
            val roll = Random.nextInt(100) // Percentile roll (0-99) to match YAML probabilities

            when {
                roll < sc.successful -> {
                    // Default high-performance path (successfully returns client data)
                    paymentIntent.markAuthorized()
                }
                roll < sc.successful + sc.retryable -> {
                    // Enables testing of application-level retry/fallback logic
                    throw PspTransientException("Simulated transient PSP failure", RuntimeException("transient simulator"))
                }
                else -> {
                    throw PspPermanentException("Simulated permanent PSP failure",RuntimeException("permanent simulator"))
                }
            }
        }, createPaymentIntentExecutor)
        // For now, simply mark as authorized or throw based on similar logic if needed
        // but user asked only for createPaymentIntent implementation for now.
    }

    override fun retrieveClientSecret(pspReference: String): CompletableFuture<String>? {

        return CompletableFuture.supplyAsync({
            // 1. Apply configured simulation path (latency or timeout) from YAML
            simulator.simulate()

            // 2. Decide outcome based on the response distribution defined in configuration
            val sc = active.response
            val roll = Random.nextInt(100) // Percentile roll (0-99) to match YAML probabilities

            when {
                roll < sc.successful -> {
                    // Default high-performance path (successfully returns client data)
                     "sim_cs_${UUID.randomUUID()}"

                }
                roll < sc.successful + sc.retryable -> {
                    // Enables testing of application-level retry/fallback logic
                    throw PspTransientException("Simulated transient PSP failure", RuntimeException("transient simulator"))
                }
                else -> {
                    throw PspPermanentException("Simulated permanent PSP failure",RuntimeException("permanent simulator"))
                }
            }
        }, createPaymentIntentExecutor)
    }
}
