package com.dogancaglar.paymentservice.psp

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Configuration

enum class PspScenario { NORMAL, PEAK, DEGRADED }

@Configuration
@ConfigurationProperties(prefix = "psp.simulation")
class PspSimulationProperties {
    var scenario: PspScenario = PspScenario.NORMAL

    // now wrap your existing blocks under a map of named configs
    var scenarios: Map<PspScenario, ScenarioConfig> = emptyMap()

    class ScenarioConfig {
        var timeouts: TimeoutConfig = TimeoutConfig()
        var latency: LatencyConfig = LatencyConfig()
        var response: ResponseDistribution = ResponseDistribution()
        // ... same as before
    }

    class TimeoutConfig {
        var enabled: Boolean = true
        var probability: Int = 5  // default: 5% chance of timeout
    }

    class LatencyConfig {
        var fast: Int = 50        // 50% chance: 500–1000ms
        var moderate: Int = 45    // 45% chance: 1000–2000ms
        var slow: Int = 5         // 5% chance: 2000–2800ms
    }

    class ResponseDistribution {
        var successful: Int = 80      // 60% of responses
        var retryable: Int = 17      // 25% of responses
        var statusCheck: Int = 0     // 10% of responses
        var nonRetryable: Int = 3     // 5% of responses
    }
    // existing inner classes TimeoutConfig, LatencyConfig, ResponseDistribution…
}