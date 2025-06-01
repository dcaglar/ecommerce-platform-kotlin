package com.dogancaglar.paymentservice.psp

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import kotlin.random.Random

@Component
class NetworkSimulator(
    private val config: PspSimulationProperties
) {
    private val logger = LoggerFactory.getLogger(NetworkSimulator::class.java)

    fun simulate() {
        val timeoutChance = Random.nextInt(100)
        if (config.timeouts.enabled && timeoutChance < config.timeouts.probability) {
            logger.warn("💣 Simulated PSP timeout triggered (chance=$timeoutChance%)")
            Thread.sleep(5000) // simulate a real stall
        }

        val chance = Random.nextInt(100)
        val delayMillis = when {
            chance < config.latency.fast -> Random.nextLong(500, 1000)
            chance < config.latency.fast + config.latency.moderate -> Random.nextLong(1000, 2000)
            else -> Random.nextLong(2000, 2800)
        }

        logger.debug("🕒 Simulating network latency: ${delayMillis}ms (chance=$chance)")
        Thread.sleep(delayMillis)
    }
}