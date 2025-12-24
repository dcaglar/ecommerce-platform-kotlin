Idempotency is the difference between “reliable payments” and “customer support tickets.”

In payment systems, retries are normal:
•	mobile networks drop
•	gateways time out
•	Network glitches

So the real question isn’t “will we retry?”
It’s: can we retry without double-charging?

My rule of thumb:
•	Every external side-effect needs an idempotency boundary (PSP call, ledger write, event publish)
•	You need a stable key that survives retries (client idempotency key / order id / payment intent id)
•	Your system must tolerate: duplicate requests, duplicate events, and out-of-order events

I implemented a small MoR-style payment flow showing:
•	PaymentIntent → Payment → PaymentOrder separation
•	idempotent state transitions
•	outbox + async processing
•	retry-safe processing

Repo: [REPO LINK]

Curious: in your experience, what’s the hardest part — API idempotency, Kafka duplicates, or PSP retries?