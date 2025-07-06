**Founding Engineer Interview Prep: Early-Stage PSP Startup**

---

### 🚀 Role Context:

* **Position:** Founding Engineer (not just individual contributor)
* **Startup Type:** Early-stage PSP (Payments startup) founded by merchants
* **Team:** 5 engineers already hired, all with strong PSP experience
* **Expectations:** Strong technical skills + product thinking + strategic contributions

---

### 📈 Key Technical Areas to Prepare

#### 1. **Payment System Architecture**

* Event-driven architecture with Kafka as system of record
* Append-only logs and replayable ledgers
* Consumer-driven workflows: PSP calls, accounting, fraud checks
* DLQ (dead letter queues), poison pill handling
* Retry & backoff strategies with idempotency

#### 2. **PSP & Routing Logic**

* How PSP routing works in card networks (BIN-based routing, cost optimization)
* Could similar routing be built for A2A/APMs?
* How merchants benefit from PSP flexibility (higher auth rates, cost reduction)

#### 3. **ISO8583 / Direct Scheme Integrations**

* Interleaving socket connections (e.g., Cartes Bancaires)
* Threading, timeouts, connection pools
* Creating generic ISO8583 frameworks (customizable message logic, logging)

#### 4. **Accounting & Reconciliation**

* Internal double-entry ledger (debit/credit, sum = 0)
* Matching external bank settlement to internal transactions
* Templates per transaction type (e.g., PAYMENT\_CAPTURE, REFUND\_INITIATED)
* Importance for auditability, compliance, reporting

#### 5. **Observability & Monitoring**

* Structured logging (MDC: traceId, eventId, paymentId)
* Correlating logs across services
* Metrics and dashboards (e.g., retries, auth rates, PSP latency)

#### 6. **Merchant Problems**

* Lack of detailed reporting: binary "paid/not paid" vs. full status timeline
* Frustration with limited experimentation (e.g., not able to test message tweaks)
* High dropout rates due to poor A/B testing infrastructure

---

### 🧠 Product Thinking

* How to make PSP integrations reusable and fast to onboard
* Provide merchants with rich experimentation tools (e.g., A/B test different flows)
* Transparency in reporting and routing decisions
* Support for custom retry/timeout configs per merchant or region

---

### ❓ Questions to Ask Them

1. What’s your core differentiator vs. existing PSPs (Adyen, Stripe, etc.)?
2. Are you building a ledger? How are you modeling money movement?
3. How does experimentation work in your platform (e.g., auth flow, routing, UI)?
4. What do merchants complain about most with existing PSPs?
5. How much product ownership will I have as a founding engineer?

---

### 💬 Communication Tips

* Be concise and structured (Problem → Solution → Impact)
* Show energy and curiosity (ask follow-ups)
* Frame experience from Adyen/Rabobank/personal projects in startup terms
* Mention observability, reliability, and experimentation as product leverage

---

### 🌟 Personal Differentiators

* Deep ISO8583 + direct scheme integration experience
* Kafka-based event-driven design with reliability in mind
* Built accounting + reconciliation logic with financial correctness focus
* Understand both PSP-side infrastructure and merchant pain points

---

Let me know if you'd like mock interview questions or a tailored pitch script.

\\

## 🎯 Nopan Interview Prep: Strategy, Pitch, and Checklist

---

### 👋 Opening Elevator Pitch (Keep it < 90 seconds)

"Hi, I’m Doğan. I spent 6 years at Adyen working on local payment methods — including Sofort, iDEAL, Bancontact — and
one of my earliest projects was enabling recurring payments on rails that didn’t natively support them. We parsed bank
account data and internally triggered SEPA Direct Debits to make it work. That experience taught me a lot about
orchestration, reconciliation, and merchant expectations. I later worked at Rabobank on mission-critical backend
services with a focus on security, consistency, and large-scale infrastructure. More recently, I’ve been building an
event-driven, modular ecommerce platform that demonstrates fault-tolerant payment orchestration using Kotlin, Spring
Boot, Kafka, and Redis. What excites me about Nopan is how it’s tackling the exact friction points I’ve seen merchants
struggle with — recurring A2A payments, orchestration, smart routing — but from the merchant-first perspective."

Actually---

### ✅ Key Things to Highlight

| Experience                              | Value to Nopan                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                   |
|-----------------------------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Adyen LPM team (Sofort, iDEAL, SEPA DD) | Deep insight into A2A constraints, recurring flows, and merchant pressure                                                                                                                                                                                                                                                                                                                                                                                                                                        |
| SEPA DD orchestration workaround        | Mirrors what Nopan is building: recurring via PSP-agnostic orchestration                                                                                                                                                                                                                                                                                                                                                                                                                                         |
| Rabobank                                | Large-scale backend engineering, security, and reliability mindset                                                                                                                                                                                                                                                                                                                                                                                                                                               |
| Personal Project                        | Hands-on experience with retries, idempotency, Kafka outbox, monitoring                                                                                                                                                                                                                                                                                                                                                                                                                                          |
| French Local Card Scheme (ISO 8583)     | Designed and built ISO8583 integration from scratch for direct local French card scheme, including rewriting and abstracting InterleavingSocketConnection and ISO8583 modules. Project was so robust that even Mastercard/Visa flows migrated to use the new consumer. Business impact: enabled collaboration with Arkea for capture flow, gained full control over authorization request structure, increased auth rate by optimizing field-level strategy.                                                     |
| Varying Integration Levels at Adyen     | Some payment methods were integrated in a lightweight, gateway-style fashion (internally called "C-level integrations"), where we merely passed on messages and had limited responsibilities. However, for most methods, our team owned the full lifecycle — including reconciliation, settlement, capture, and reporting — which meant understanding scheme behavior deeply and building end-to-end resilient flows. This gave me a strong appreciation for where orchestration ends and accountability begins. |
| **Dev ≅ SRE Culture (Adyen)**           | Adyen followed a strict *“you build it, you run it”* model — no separate SRE team. As an engineer I owned AWS infra, Terraform modules, alerting (Grafana/Prometheus), on‑call, and post‑mortems. That hands‑on production responsibility shapes how I think about reliability and observability today — a direct match for Nopan’s founding‑team expectations.                                                                                                                                                  |
| Personal Implementation Ownership       | Personally implemented around 10 payment method integrations at Adyen, including PayPal, Alipay, WeChat Pay, and direct ISO8583 connections to traditional card networks. This gave me both protocol-level experience and cultural exposure to payment preferences across regions.                                                                                                                                                                                                                               |
| Scale Awareness                         | When I left Adyen, we had over 250 local payment methods in production. That scale taught me just how fragmented the landscape is — and why orchestration, retries, and abstraction are necessary at the platform level.                                                                                                                                                                                                                                                                                         |
| Sofort/GiroPay Recurring Workaround     | Originally implemented a recurring workaround for Sofort and GiroPay due to lack of persistent shopper references. We extracted bank details post-payment to generate internal SEPA mandates. This enabled simulated recurring flows. As of recent updates, Sofort has added support for recurring identifiers when configured properly, and now natively issues SEPA mandates — showing how the ecosystem is slowly aligning with recurring use cases.                                                          |

---

### 📚 Follow-up Questions to Ask Them

1. **Roadmap**: "What does your 6–12 month roadmap look like in terms of product and infrastructure?"
2. **Challenges**: "What’s been the hardest part so far — integrations, regulation, merchant onboarding?"
3. **Measuring Success**: "How do you measure success today — merchant adoption, volume, retention, or something else?"
4. **Recurring Logic**: "How do you approach tokenization for recurring flows across fragmented A2A methods?"
5. **Routing**: "Are you thinking of offering smart routing as fully managed, configurable, or hybrid for merchants?"
6. **Observability Strategy**: "Since transparency is such a core value for Nopan — both for merchants and internally —
   I’m curious how you're approaching observability in these early stages. Are you already investing in structured logs,
   metrics, and traceability across orchestration steps? Or is it more about building lightweight diagnostics that
   support iteration speed?"
7. **Clarifying A2A vs Wallets**: "I saw you mention both A2A and digital wallets in your messaging — I assume A2A
   refers to schemes like iDEAL, Sofort, PIX, UPI, and maybe Open Banking APIs. Is that correct? And on the wallet side,
   are you mainly focusing on tokenized card flows like Apple Pay and Google Pay? Would love to understand how you see
   the boundary between orchestration of true A2A vs wallets that still ride card rails."
8. **Transparency vs Abstraction**: "At Adyen, we often delivered clean, aggregated settlement and performance reports —
   which worked well for many merchants because they didn’t want to deal with payment-level fragmentation. But for
   merchants who wanted transparency and experimentation, that abstraction became a limitation. I'm curious how you at
   Nopan balance simplicity with flexibility — especially as you scale to more complex merchant profiles."

> *If they turn the question back to you:*
> "In my side project, I started with structured logging using MDC for traceId/parentEventId so every Kafka event and DB
> update is traceable. I also added delay metrics for scheduled jobs and consumer lag monitoring. I didn’t implement full
> tracing yet, but laid a foundation for it. I’d be curious how you balance transparency for internal ops vs. merchant
> reporting."

---

### 🧠 Behavioral Reminders (From Recruiter)

* ✅ Show energy, enthusiasm, and passion — **no monologues**.
* ✅ **Be concise**, stick to the point.
* ✅ Never interrupt.
* ✅ If unsure if they’re following, kindly ask: *"Does that make sense so far?"*
* ✅ Ask about roadmap, challenges, and success metrics.
* ✅ Smile, stay positive.
* ✅ Mention **Adyen** and **Rabobank**, not just personal projects.
* ✅ Show **consistency, reliability**, and any **accounting or reconciliation experience**.
* ✅ Ask follow-ups and **acknowledge answers with enthusiasm**.

---

### 🧩 Bonus Lines to Reuse

> "Actually, one of my earliest projects at Adyen was adding recurring support for Sofort and iDEAL — we parsed bank
> account numbers and internally created SEPA direct debits. I completely understand the challenge you’re solving, and I’m
> excited about helping build a clean orchestration layer for that."

> "Your cofounder’s post after Money20/20 — the one about subscriptions being the true test for A2A — really resonated
> with me. That’s exactly the kind of gap I saw during my time at Adyen."

> "While I didn’t directly work on merchant-side reporting, I saw how tightly coupled LPM logic was embedded inside our
> payment-service. That made method-level insights hard to extract even internally. It’s part of why Nopan’s
> transparency-first approach really resonates with me."

> "Adyen did an excellent job at building converters for different payment methods. That infrastructure pattern —
> translating a unified model into diverse protocol requirements — was one of the reasons we could onboard new LPMs
> quickly. I’d love to bring that kind of abstraction discipline to Nopan as well."

> "Some integrations at Adyen were what we called 'C-level' — meaning we acted more like a gateway, simply passing
> through messages. But most of the time, we were responsible for reconciliation, settlement, and full control over the
> flow. That ownership mindset taught me how critical it is to build orchestration with accountability baked in."

---

Let me know if you’d like a one-slide version of this or mock Q\&A practice!
