package com.dogancaglar.paymentservice.psp

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Configuration

enum class PspScenario { NORMAL, PEAK, DEGRADED }

@Configuration
@ConfigurationProperties(prefix = "psp.simulation")
class PspSimulationProperties {
    lateinit var currentScenario: String
    val scenario: PspScenario
        get() = PspScenario.valueOf(currentScenario)

    // now wrap your existing blocks under a map of named configs
    var scenarios: Map<PspScenario, ScenarioConfig> = emptyMap()

    class ScenarioConfig {
        var timeouts: TimeoutConfig = TimeoutConfig()
        var response: ResponseDistribution = ResponseDistribution()
        var latency: LatencyConfig = LatencyConfig()
        // ... same as before
    }

    class TimeoutConfig {
        var enabled: Boolean = true
        var probability: Int = 5  // default: 5% chance of timeout
    }


    class LatencyConfig {
        var fast = LatencyBucket()
        var moderate = LatencyBucket()
        var slow = LatencyBucket()
    }

    class LatencyBucket {
        var probability: Int = 0
        var minMs: Long = 0
        var maxMs: Long = 0
    }

    class ResponseDistribution {
        var successful: Int = 80      // 60% of responses
        var retryable: Int = 17      // 25% of responses
        var statusCheck: Int = 0     // 10% of responses
        var nonRetryable: Int = 3     // 5% of responses
    }
    // existing inner classes TimeoutConfig, LatencyConfig, ResponseDistribution…
}