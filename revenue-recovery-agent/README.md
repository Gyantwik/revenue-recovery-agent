# RecoverAI — Revenue Recovery Agent

> Razorpay AI Buildathon 2026 · AI Revenue Recovery track

RecoverAI helps merchants separate revenue that needs recovery from payments that already settled safely. It classifies payment signals, applies deterministic policy guardrails, offers bounded Razorpay Test Mode recovery actions, and records each decision in an auditable trail.

## Architecture

```text
Gateway signal / Razorpay Test Mode Checkout
                 ↓
      Detection: at risk or safely settled
                 ↓
  Rule classification + optional Gemini explanation
                 ↓
       Deterministic Policy Engine / confidence gate
                 ↓
    Bounded recovery action or manual escalation
                 ↓
Server-side Razorpay signature verification + audit history
```

## What the demo proves

| Capability | Evidence |
| --- | --- |
| At-risk detection | 80 seeded transactions: 65 at-risk and 15 safely settled. |
| Safe action selection | Policy caps retries and blocks cancellation/PIN auto-retries. |
| Explainability | Signal → cause → policy → action, agent traces, and Gemini/rules-based explanation. |
| Razorpay integration | Server-created Test Mode orders and server-side HMAC signature verification. |
| Idempotency | Existing audit/order links are reused; a payment cannot be finalized twice. |
| Honest reporting | Safely settled payments are excluded from recovery eligibility and recovery-rate calculations. |

The Policy Impact Simulator separates total, at-risk, and safely settled cases so already-settled money is never misreported as recovered revenue.

## Stack

- Next.js, TypeScript, Tailwind CSS
- Java 17, Spring Boot 3, Maven, Spring Data JPA
- File-based H2 database for reproducible local demo state
- Gemini 1.5 Flash when configured, with deterministic rules-based fallback
- Razorpay Standard Checkout in Test Mode only

## Safety model

- Gemini has no payment-execution authority; the deterministic policy engine is authoritative.
- Confidence below `0.75` becomes `UNKNOWN` and routes to manual review.
- `USER_CANCELLED` and `INCORRECT_PIN` are hard-stop policy outcomes.
- Retry and mandate actions are policy capped.
- The backend verifies Checkout callbacks with the server-held Razorpay Key Secret.
- Verified payments are idempotent and append a final audit outcome once.
- This is Razorpay **Test Mode** only: it makes no claim of real capture, settlement, payout, or refund.

## Run locally

### Prerequisites

- Java 17+, Node.js 18+, Maven
- Optional: Razorpay Test Mode credentials and Gemini API key

### Configure local credentials

```powershell
cd backend
Copy-Item .env.example .env
```

Edit `backend/.env` with your own values:

```properties
RAZORPAY_KEY_ID=rzp_test_your_key_id
RAZORPAY_KEY_SECRET=your_test_key_secret
RAZORPAY_WEBHOOK_SECRET=optional_test_webhook_secret
GEMINI_API_KEY=optional_gemini_api_key
GEMINI_MODEL=gemini-1.5-flash
```

`backend/.env` is ignored by Git. Never commit it. Without Gemini, RecoverAI uses its rules-based fallback. Without Razorpay keys, the dashboard and benchmark run while Test Mode Checkout stays unavailable.

### Start the application

Terminal 1:

```powershell
cd backend
mvn spring-boot:run
```

Terminal 2:

```powershell
cd frontend
npm install
npm run dev
```

Open `http://localhost:3000`. H2 Console: `http://localhost:8080/h2-console`.

### Validate

```powershell
cd backend
mvn test

cd ..\frontend
npm run build
```

## Five-minute demo flow

1. Show the dashboard: 80 total, 65 at-risk, 15 safely settled.
2. Open an at-risk transaction: signals, confidence, policy explanation, and audit trace.
3. Show the Policy Impact Simulator: only at-risk revenue is eligible for recovery.
4. Open an eligible failed transaction and start Razorpay Test Mode recovery Checkout.
5. Complete the Test Mode payment and show server-side signature verification.
6. Refresh: the transaction is recovered with lifecycle/audit history.
7. Show a successful payment in All Transactions: **At risk: No**, **Safely settled**, **No recovery required**.
8. State the guardrails and limitations.

## Metrics and limitations

The benchmark is synthetic and deterministic. It demonstrates policy behavior, not production payment-loss performance; the dashboard calculates current amounts and recovery rates from persisted data.

- Gateway mappings require production calibration on a held-out, consented dataset.
- Gemini is explanation support, not an autonomous actor or payment source of truth.
- H2 is for this reproducible demo; production requires managed durable storage, monitoring, access controls, and key management.
- Razorpay Test Mode verification proves the integration flow, not real payment capture or settlement.

## API highlights

| Endpoint | Purpose |
| --- | --- |
| `POST /api/batch/run` | Run the synthetic benchmark and return a summary. |
| `GET /api/transactions` | View all persisted transactions. |
| `GET /api/transactions/{eventId}/ai-analysis` | Get Gemini or rules-based diagnostic explanation. |
| `POST /api/transactions/{eventId}/recovery-checkout` | Create a policy-gated Test Mode recovery order. |
| `POST /api/razorpay/test/verify-payment` | Verify a Checkout callback server-side. |

## Submission checklist

- [ ] Public GitHub repository with no `.env`, database, or build artifacts
- [ ] Five-minute pitch/demo video
- [ ] Architecture and safety explanation
- [ ] Test Mode keys supplied locally only through `backend/.env`
- [ ] Honest metrics and limitation list
