# RecoverAI Buildathon Demo Guide

This is the single reference document for understanding, presenting, and recording the RecoverAI project for the Razorpay AI Buildathon.

## 1. One-line pitch

**RecoverAI is an explainable, policy-controlled revenue recovery agent that detects at-risk payments, prevents unsafe retries, and runs verified Razorpay Test Mode recovery only when it is permitted.**

## 2. The merchant problem

Merchants lose revenue when customers abandon Checkout, banks time out, networks fail, mandates expire, or payment status is unclear. Treating every failed payment the same is unsafe:

- Retrying a customer-cancelled payment is wrong.
- Retrying an incorrect PIN can be unsafe.
- Creating a second charge while a payment is still pending risks duplicate debit.
- Treating a successful payment as a recovery case inflates revenue-recovery metrics.

RecoverAI makes each decision traceable and bounded by policy.

## 3. What we built

### A. Revenue-risk detection

The benchmark contains **80** persisted transactions:

- **65 at-risk** payment/mandate cases that enter the recovery workflow.
- **15 safely settled** payments that are visibly marked **At risk: No** and **No recovery required**.

Settlement signals such as `PAYMENT_SETTLED`, `SUCCESS_CONFIRMED`, and `DUPLICATE_WEBHOOK` keep successful payments out of the recovery workflow. This is important: a successful payment is not “recovered revenue”; it is normal settled revenue.

### B. Rule-based failure classification

`ClassificationService` matches observed gateway/UI strings case-insensitively. Examples:

| Observed signal | Classified cause |
| --- | --- |
| `BANK_TIMEOUT`, `CBS_OFFLINE` | Bank temporary error |
| `NETWORK_DROP`, `CLIENT_TIMEOUT` | Weak network |
| `PENDING_WITH_BANK` | Payment pending |
| `INSUFFICIENT_FUNDS` | Insufficient balance |
| `CANCELLED_BY_USER` | User cancelled |
| `INVALID_PIN` | Incorrect PIN |
| `APP_BACKGROUNDED`, `TAB_CLOSED` | Checkout abandoned |

Ambiguous codes deliberately use low confidence. Any classification below **0.75** is overridden to `UNKNOWN` and escalated for manual review; the agent never silently acts on a low-confidence cause.

### C. Deterministic policy engine

The policy engine is an `EnumMap<RootCause, PolicyDecision>`, not an LLM decision. It selects one allowed action and a hard attempt cap.

| Example cause | Allowed action | Guardrail |
| --- | --- | --- |
| Weak network / bank temporary error | Retry payment | Maximum 2 attempts |
| Payment pending | Verify status | Never creates a new charge |
| Insufficient balance | Alternative payment link | Customer must act; no auto-retry |
| User cancelled / incorrect PIN | Stop | No automated recovery |
| Checkout abandoned | Recovery link | One customer message/recovery path |
| Gateway issue / unknown | Escalate | Human review |
| Retryable mandate failure | Schedule mandate retry | Maximum 2 retries |

### D. AI explanation, not AI execution

The Gemini module can generate a semantic explanation, risk assessment, retry timing, and a five-stage trace:

```text
OBSERVE → CLASSIFY → DECIDE → GUARDRAIL_CHECK → ACT
```

Gemini is **not** allowed to create an order, charge a customer, modify an outcome, or override policy. If its key is missing, its call fails, or its response is invalid, the product uses a deterministic local fallback.

### E. Checkout-abandonment recovery copy

For checkout-abandoned cases only, the transaction drawer can generate empathetic recovery copy for **WhatsApp, SMS, or email**. It:

- uses the selected channel,
- includes a recovery CTA/link,
- reassures the customer they will not be charged twice,
- can be copied to clipboard or opened as a WhatsApp draft.

No message is offered for policy-stopped or already-settled transactions.

### F. Razorpay Test Mode order and verification flow

1. The backend creates a Razorpay **Test Mode** order; amount and currency are derived server-side.
2. The frontend opens Razorpay-hosted Standard Checkout.
3. Browser callback is initially only client-reported/unverified.
4. Backend verifies `HMAC-SHA256(order_id + "|" + payment_id)` using the server-only Razorpay Key Secret.
5. Only valid verification can mark the linked transaction as recovered.
6. A successful verification appends audit history and cannot be processed twice.

No live payment, payout, settlement, refund, or production webhook claim is made.

### G. User closes Checkout / disappears

If the customer dismisses Checkout or leaves the payment unfinished:

- the transaction remains unrecovered;
- the recovery link/order is retained while it is fresh;
- the customer can safely resume the same linked order;
- after the configured stale window, the old order is marked abandoned;
- the system may offer a fresh customer-initiated recovery Checkout only when policy still permits it.

This prevents accidental duplicate payment links and preserves an auditable state transition.

### H. Payment reservation demo

For weak-network cases, the product includes a **demo payment reservation** workflow:

1. A policy-eligible weak-network transaction can create one pending reservation.
2. The reservation has an expiry time and one persisted status.
3. “Simulate Reconnect” completes the demo only after the controlled state check.
4. The reservation prevents duplicate concurrent completion.

Important wording: call this a **local reservation safety demo**, not Razorpay Reserve Pay. It demonstrates how a merchant could safely hold recovery intent during connectivity loss; it is not a claim that the app calls a real Razorpay Reserve Pay API.

### I. Auditability and observability

Every important decision is persisted:

- original event and signals,
- risk amount and at-risk state,
- root cause and classification confidence,
- matched policy and permitted action,
- attempts/max attempts,
- outcome and recovered amount,
- stop/escalation reason,
- lifecycle state,
- append-only audit history and agent decision trace.

The dashboard provides an **All Transactions** view, while the **Live Recovery Stream** deliberately excludes safely settled transactions. The Policy Impact Simulator separates total, at-risk, safely settled, eligible, and safety-blocked cases.

## 4. Architecture slide

Use this diagram in your PPT:

```text
                         ┌──────────────────────────────┐
                         │ Next.js merchant dashboard    │
                         │ transactions · AI · recovery  │
                         └──────────────┬───────────────┘
                                        │ REST
                         ┌──────────────▼───────────────┐
                         │ Spring Boot recovery service  │
                         ├──────────────────────────────┤
                         │ Detection                     │
                         │ Classification                │
                         │ Confidence gate               │
                         │ Deterministic Policy Engine   │
                         │ Audit / idempotency           │
                         └───────┬──────────────┬────────┘
                                 │              │
                    ┌────────────▼───┐  ┌──────▼────────────┐
                    │ Gemini optional │  │ Razorpay Test Mode │
                    │ explanation     │  │ order + HMAC verify│
                    └────────────────┘  └───────────────────┘
                                 │
                         ┌───────▼────────┐
                         │ H2 audit store │
                         └────────────────┘
```

## 5. Five-minute talk track

Read this naturally; do not rush. The text is approximately five minutes with screen interaction.

### 0:00–0:30 — Opening

“Hi, I’m presenting RecoverAI, an AI-powered but policy-controlled revenue recovery agent for Razorpay merchants. Failed payments are not all the same. A bank timeout may need a retry, a pending payment needs verification, and a user cancellation must never be retried. RecoverAI detects the difference, chooses only a safe action, and makes every decision explainable.”

### 0:30–1:05 — Dashboard and honest baseline

“This is the dashboard. The benchmark contains 80 transactions: 65 are genuinely at risk, and 15 are safely settled. This separation matters because a successful payment should not be counted as recovered revenue. The Policy Impact Simulator shows total cases, at-risk cases, safely settled cases, eligible cases, and safety-blocked cases. The live recovery stream only shows cases that actually need recovery; the All Transactions page retains the full record.”

### 1:05–1:45 — Open an at-risk transaction

“I’ll open an at-risk transaction. Here we can see the raw signals, the classification confidence, and the matched policy. For example, a `BANK_TIMEOUT` is classified as a bank temporary error. The policy allows at most two retries. The key point is that the UI is not inventing a decision: the backend policy engine selects the permitted action and the cap.”

### 1:45–2:20 — Explainability and Gemini

“This card shows the decision chain: Observe, Classify, Decide, Guardrail Check, and Act. Gemini can provide plain-English diagnosis and customer-friendly wording for complex text, but Gemini never executes a payment action. If Gemini is unavailable, the deterministic rules-based fallback still works. Also, any confidence below 0.75 is forced to Unknown and escalated to a human instead of being guessed.”

### 2:20–2:50 — Customer left Checkout / recovery message

“For checkout-abandoned cases, RecoverAI can generate an empathetic WhatsApp, SMS, or email recovery message. It gives the customer a safe recovery call-to-action and reassures them that they will not be charged twice. This feature is restricted to checkout abandonment, so we do not message customers for a cancellation, incorrect PIN, or a payment that already settled.”

### 2:50–3:40 — Razorpay Test Mode recovery

“Now I’ll show an eligible recovery action. The backend checks the persisted transaction, its outcome, root cause, and attempt limits. It derives the amount server-side and creates a linked Razorpay Test Mode order. The customer completes Razorpay-hosted Checkout. The browser callback is not trusted by itself. The backend verifies Razorpay’s HMAC signature using the server-only Key Secret before it changes the transaction to recovered.”

### 3:40–4:15 — What if the user goes away?

“If the customer closes Checkout or leaves before payment, nothing is falsely recovered. The transaction remains unrecovered and the current order can be safely resumed while it is fresh. When it becomes stale, it is marked abandoned. A new Checkout can only be created if policy still permits it. This avoids duplicate links and duplicate collection.”

### 4:15–4:40 — Weak network reservation demo

“For weak-network cases, I also implemented a local payment reservation safety demo. It creates one persisted pending reservation, expires it safely, and allows a simulated reconnect. This is not a claim that I am calling Razorpay Reserve Pay. It demonstrates the safety pattern needed when connectivity drops: one intent, one controlled completion, and no duplicate processing.”

### 4:40–5:00 — Close with safety and limitations

“Finally, every decision is recorded in the audit history: signals, confidence, policy, attempts, outcome, and reason. This prototype is honest about its limits: the benchmark is synthetic, Gemini is advisory only, and Razorpay is Test Mode only. The production next step is calibration on consented historical data with managed storage and operational monitoring. RecoverAI’s core contribution is making revenue recovery explainable, bounded, and safe.”

## 6. PPT structure

| Slide | Title | Put on the slide |
| --- | --- | --- |
| 1 | RecoverAI | One-line pitch and Razorpay AI Buildathon. |
| 2 | The problem | Failed payment ≠ one recovery action; duplicate-charge/cancellation risks. |
| 3 | Solution | Detection → classification → policy → verified recovery → audit. |
| 4 | Architecture | Use the architecture diagram above. |
| 5 | Policy guardrails | Examples of caps, hard stops, confidence gate. |
| 6 | Product walkthrough | Screenshot: dashboard / transaction drawer / policy simulator. |
| 7 | Razorpay Test Mode | Server order creation, hosted Checkout, HMAC verification, idempotency. |
| 8 | Customer recovery | Checkout-abandonment message, WhatsApp draft, resume after dismissal. |
| 9 | Metrics and honesty | 80 total, 65 at-risk, 15 safely settled; synthetic/Test Mode limitations. |
| 10 | Why it matters | Safe, explainable, merchant-focused recovery automation. |

## 7. Screenshot checklist

Capture these before recording:

- Dashboard with the 80 / 65 / 15 simulator counts.
- An at-risk transaction drawer showing policy and trace.
- A checkout-abandoned transaction showing generated WhatsApp copy.
- Razorpay Test Mode Checkout open.
- A recovered transaction after verification and its audit history.
- A safely settled transaction showing `At risk: No` and `No recovery required`.

## 8. Reviewer setup

```powershell
cd backend
Copy-Item .env.example .env
# Add personal Razorpay Test Mode/Gemini keys to .env; never commit it
mvn spring-boot:run

# In a second terminal
cd frontend
npm install
npm run dev
```

Validation commands:

```powershell
cd backend
mvn test

cd ..\frontend
npm run build
```

## 9. Questions judges may ask

**Why use Gemini if policy is deterministic?**  
Gemini converts complex telemetry into readable diagnosis and customer copy. Policy remains deterministic because money actions require predictable, auditable guardrails.

**How do you prevent duplicate charge?**  
Pending status gets verification, not a new charge; orders are linked persistently; HMAC verification is server-side; finalization is idempotent.

**What happens when AI is wrong?**  
Low confidence is routed to manual review. Gemini failure or malformed output falls back to deterministic local reasoning.

**Is Reserve Pay integrated?**  
No. It is a local reservation safety demonstration, clearly presented as such. Razorpay integration in this project is Test Mode Standard Checkout and server-side verification.

**What would production require?**  
Managed database, encrypted key management, authenticated merchant access, real webhook verification, monitoring, rate limits, consented calibration data, and production reconciliation/capture/settlement handling.
