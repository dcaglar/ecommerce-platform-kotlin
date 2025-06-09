POST /payment ➝ Save Payment + PaymentOrder + OutboxEvent(status=new,payload=payment_order_created) (sync tx)
↳ Response: 202 Accepted (Pending)

Scheduled Job:
OutboxDispatcher polls outboxevent with status=new ➝ Emits PaymentOrderCreated() -> and mark status as sent.so itt never
gonna sent again.

Kafka Consumer:
PaymentOrderExecutor(paymentordercreated)_ ➝ PSP.charge(paymetnOrder)(sync) call (3s timeout)
➝ PSP result ➝ processPspResult(...)

Inside processPspResult():
┌──────────────────────────────┬────────────────────────────────────────┐
│ PSP Status: SUCCESSFUL │ publish PaymentOrderSucceeded,persist payment │
│ PSP Status: RETRYABLE │ Redis ZSet(timestamp as score) ← PaymentOrderRetryRequested│
│ PSP Status: NON-RETRYABLE │ Persist failure, finalize │
└──────────────────────────────┴────────────────────────────────────────┘

Scheduled Retry Job:
Redis ZSet (due) ➝ emit PaymentOrderRetryRequested ➝ Kafka

Kafka Consumer:
PaymentOrderRetryExecutor ➝ PSP.recharge(psymentorder) sync call 3s timeout ➝ processPspResult(...) again

so conumers are just listenig to evennts and not doing any business lpogic,payment service does all the business logic
decides retry or not retry or publush success or failure events.

Below is an explicit, step-by-step Kotlin example showing how to instrument three kinds of scheduled‐job metrics with
Micrometer:

1. scheduled_job_delay_seconds – how late the job fired compared to when it was supposed to
2. scheduled_job_execution_duration – how long the job’s work actually took
3. outbox_dispatcher_latency_seconds – how long the Outbox dispatcher spends per cycle

All of this lives in your adapter layer, alongside your existing @Scheduled methods.

⸻

1. Instrumenting a Generic Scheduled Job

Suppose you have a job that runs every 5 seconds. We’ll manually track:
• lastExpectedRun – when it should have fired
• Compute delay = now − lastExpectedRun
• Reset nextExpectedRun = lastExpectedRun + interval

@Component
class SomeScheduledJob(
private val meterRegistry: MeterRegistry
) {
// 1️⃣ The fixed interval in milliseconds
private val intervalMs = 5_000L

// 2️⃣ Track when the job *should* next run
// initialized to "now" so first run has zero delay
private var lastExpectedRun = System.currentTimeMillis()

@Scheduled(fixedRate = 5000)
fun runEveryFiveSeconds() {
// 3️⃣ Measure delay: how late are we?
val actualRun = System.currentTimeMillis()
val delayMs = actualRun - lastExpectedRun

    // Record that delay in seconds
    meterRegistry.timer("scheduled_job_delay_seconds", "jobName", "runEveryFiveSeconds")
      .record(delayMs, TimeUnit.MILLISECONDS)

    // 4️⃣ Update expected next run
    lastExpectedRun += intervalMs

    // 5️⃣ Time the actual work
    meterRegistry.timer("scheduled_job_execution_duration", "jobName", "runEveryFiveSeconds")
      .record {
        // 👇 your real job logic here
        doWork()
      }

}

private fun doWork() {
// simulate real work
Thread.sleep(1000)
}
}

	We use two Micrometer Timers (as histograms):
	•	scheduled_job_delay_seconds tagged with jobName=runEveryFiveSeconds.
	•	scheduled_job_execution_duration tagged the same way.
	•	Both record with .record(...), passing a block for execution‐time measurement, or (delayMs, MILLISECONDS) for the delay.

@Component
class OutboxDispatcherScheduler(
/* your existing injections */,
private val meterRegistry: MeterRegistry
) {
@Scheduled(fixedDelay = 5000)
@Transactional
fun dispatchEvents() {
// 1️⃣ Start timing
val startTime = System.nanoTime()

    // 2️⃣ Grab & dispatch your events as before…
    val newEvents = outboxEventPort.findByStatus("NEW")
    // … loop, publish, markAsSent, save …

    // 3️⃣ Stop timing and record
    val durationNanos = System.nanoTime() - startTime
    meterRegistry.timer("outbox_dispatcher_latency_seconds")
      .record(durationNanos, TimeUnit.NANOSECONDS)

}
}

@Component
class OutboxDispatcherScheduler(
/* your existing injections */,
private val meterRegistry: MeterRegistry
) {
@Scheduled(fixedDelay = 5000)
@Transactional
fun dispatchEvents() {
// 1️⃣ Start timing
val startTime = System.nanoTime()

    // 2️⃣ Grab & dispatch your events as before…
    val newEvents = outboxEventPort.findByStatus("NEW")
    // … loop, publish, markAsSent, save …

    // 3️⃣ Stop timing and record
    val durationNanos = System.nanoTime() - startTime
    meterRegistry.timer("outbox_dispatcher_latency_seconds")
      .record(durationNanos, TimeUnit.NANOSECONDS)

}
}

@Component
class OutboxDispatcherScheduler(
/* your injections */
) {

@Timed(value = "outbox_dispatcher_latency_seconds", histogram = true)
@Scheduled(fixedDelay = 5000)
@Transactional
fun dispatchEvents() {
// existing logic…
}
}

Metric
Type
Tags
How to compute
payment_requests_total
Counter
result=[accepted,rejected]
increment once in your POST /payment handler
payment_order_created_total
Counter
seller_id, psp
increment when you persist each PaymentOrder
outbox_events_dispatched_total
Counter
event_type, status=[new,sent]
increment in your OutboxDispatcher before/after dispatch
payment_execution_latency_seconds
Histogram
psp, seller_id
start timer before PSP.charge(), stop in processPspResult
payment_success_total
Counter
seller_id, psp
increment on PSP Status=SUCCESSFUL
payment_retry_requested_total
Counter
seller_id, psp
increment on PSP Status=RETRYABLE
payment_non_retryable_failures_total
Counter
seller_id, psp, error_code
increment on PSP Status=NON-RETRYABLE
retry_execution_latency_seconds
Histogram
psp, seller_id
timer around your retry consumer’s PSP.recharge()
retry_backoff_delay_seconds
Histogram
—
record delay between original request and retry job firing
retry_count_per_payment_order
Gauge or Summary
payment_order_id
increment per retry cycle (or track via DB/Redis)
