package com.dogancaglar.paymentservice.config.scheduling

/*
@Component
class ScheduledMetricsService(
    private val meterRegistry: MeterRegistry
) {
    private val lastRunTimes = ConcurrentHashMap<String, Long>()

    fun <T> recordJob(jobName: String, block: () -> Runnable): Unit {
        val now = System.currentTimeMillis()
        val last = lastRunTimes.getOrDefault(jobName, now)
        meterRegistry.timer("scheduled_job_delay_seconds", "jobName", jobName)
            .record(now - last, TimeUnit.MILLISECONDS)
        lastRunTimes[jobName] = now

         meterRegistry.timer("scheduled_job_execution_duration", "jobName", jobName)
            .record {
                block
            }
    }
}

*/