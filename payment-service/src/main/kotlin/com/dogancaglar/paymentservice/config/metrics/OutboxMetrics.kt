package com.dogancaglar.paymentservice.config.metrics

object OutboxMetrics {
    fun processed(eventType: String) = "outbox_${eventType}_dispatcher_processed_total"
    fun failed(eventType: String) = "outbox_${eventType}_dispatcher_failed_total"
    fun backlog(eventType: String) = "outbox_${eventType}_event_backlog"
    fun dispatchDelay(eventType: String) = "outbox_${eventType}_dispatch_delay_seconds"
    fun jobDuration(eventType: String) = "outbox_${eventType}_job_duration_seconds"
}