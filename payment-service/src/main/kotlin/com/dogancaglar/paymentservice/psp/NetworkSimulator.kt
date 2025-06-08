package com.dogancaglar.paymentservice.psp

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import kotlin.random.Random

@Component
class NetworkSimulator(
    private val config: PspSimulationProperties
) {
    private val logger = LoggerFactory.getLogger(NetworkSimulator::class.java)

    private val active: PspSimulationProperties.ScenarioConfig
        get() = config.scenarios[config.scenario]
            ?: throw IllegalStateException("No scenario config for ${config.scenario}")

    fun simulate() {
        val sc = active

        // 1) timeouts
        if (sc.timeouts.enabled && Random.nextInt(100) < sc.timeouts.probability) {
            logger.warn(
                "💥 [${
                    config.scenario
                }] Simulated PSP timeout"
            )
            Thread.sleep(5_000)
        }

        // 2) latency buckets
        val roll = Random.nextInt(100)
        val latency = when {
            roll < sc.latency.fast -> Random.nextLong(50, 150)       // fast path
            roll < sc.latency.fast + sc.latency.moderate -> Random.nextLong(150, 300)
            roll < sc.latency.fast + sc.latency.moderate + sc.latency.slow -> Random.nextLong(300, 600)
            else -> Random.nextLong(300, 600)
        }
        logger.debug("🕒 [${config.scenario}] Latency ${latency}ms (roll=$roll)")
        Thread.sleep(latency)
    }
}