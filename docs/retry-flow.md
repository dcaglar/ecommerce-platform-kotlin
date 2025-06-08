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