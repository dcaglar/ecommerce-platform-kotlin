package com.dogancaglar.paymentservice.config.metrics

/**
 * Creates a key-value tag pair in a type-safe and readable way.
 * Example: tag(MetricTags.EVENT_TYPE, MetricTagValues.EventType.PAYMENT_ORDER_CREATED)
 */
fun tag(key: String, value: String): Pair<String, String> = key to value

/**
 * Converts a vararg of tag pairs into the format expected by MeterRegistry methods.
 * Example:
 *   tagsOf(
 *     tag(MetricTags.FLOW, MetricTagValues.Flow.OUTBOX),
 *     tag(MetricTags.EVENT_TYPE, MetricTagValues.EventType.PAYMENT_ORDER_CREATED)
 *   )
 */
fun tagsOf(vararg tags: Pair<String, String>): Array<String> =
    tags.flatMap { listOf(it.first, it.second) }.toTypedArray()
