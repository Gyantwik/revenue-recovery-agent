# revenue-recovery-agent

- Frontend: Next.js (see /frontend)
- Backend: Spring Boot (see /backend)

## Razorpay Test Mode checkout demo

Configure only the environment variables `RAZORPAY_KEY_ID` and `RAZORPAY_KEY_SECRET`. Copy `backend/.env.example` to `backend/.env` for local development and replace its placeholders with Test Mode values. The local `.env` file is ignored by Git; never commit it.

Start the backend from `backend/` so the optional local `.env` file is discovered:

```powershell
mvn spring-boot:run
```

Create a test order without putting credentials in the request:

```powershell
curl.exe -X POST http://localhost:8080/api/razorpay/test/orders -H "Content-Type: application/json" -d '{"amount":500.00,"currency":"INR"}'
```

Start the frontend from `frontend/` with `npm run dev`, then open `http://localhost:3000/razorpay-test`. The page creates a server-side Test Mode order and opens Razorpay-hosted Standard Checkout. No real money is charged.

The browser receives the Test Mode Key ID through `GET /api/razorpay/test/config`, which is expected for Standard Checkout. The Key Secret always remains server-side. A browser callback is first stored as `client_reported_unverified`, then `POST /api/razorpay/test/verify-payment` authenticates it with `HMAC-SHA256(stored_order_id + "|" + payment_id, server-only Key Secret)`. The server's stored order ID—not the browser value—is the canonical HMAC input, and signature bytes are compared in constant time.

`verified_test_payment` authenticates a Razorpay Test Mode Checkout callback only; it does not establish capture or settlement. Invalid verification is terminal as `verification_failed`, and repeated verification returns `409`.

The `/razorpay-test` page also contains a separate **Live Test Mode Recovery Demo**. Its deterministic `TXN_DEMO_RECOVERY_001` case is a ₹500 checkout-abandonment case governed by `Checkout Abandoned -> Send Recovery Link`. The backend—not the browser—derives the order amount and policy eligibility. Only a valid server-side signature can atomically transition this dedicated case to `recovered` and append one audit-history entry.

The dedicated demo case is not an `AuditRecord` and is excluded from the **Synthetic Benchmark — 65 seeded cases**. Baseline totals remain 65 cases, ₹191,209 at risk, ₹95,647 recovered, and recovery rate 0.5002. Test Mode only: no real money, capture, settlement, merchant payout, webhook, polling, or Reserve Pay behavior is implemented.

## Policy-aware transaction recovery

Transaction details load `GET /api/transactions/{eventId}/next-action`; eligibility is calculated from each persisted transaction's root cause, outcome, and attempt counts. It is never keyed to a particular event ID. Eligible unrecovered rows are checkout abandonment (**Resume Payment**), insufficient balance (**Choose Another Payment Method**), incorrect PIN (**Try Payment Again Securely**), exhausted bank-error retries (**Try Payment Again**), exhausted retryable-mandate retries (**Pay Manually**), and non-escalated expired mandates (**Pay Manually**). Pending, weak-network, cancelled, merchant/gateway, unknown, scheduled-retry, escalated, and recovered rows remain protected by informational policy states.

`POST /api/transactions/{eventId}/recovery-checkout` accepts no amount or currency. The backend locks the persisted event, rechecks eligibility, derives its INR amount in paise, and creates an explicitly linked Razorpay Test Mode order. The transaction modal opens that order inline in Razorpay-hosted Checkout; it never redirects a normal transaction to the standalone `/razorpay-test` demo.

An unverified order less than 30 minutes old is resumable and reuses the same order. The modal also offers **Check Payment Status** through `POST /api/transactions/{eventId}/recovery-checkout/status-check`. At 30 minutes the backend marks an unverified order `abandoned`, and **Retry Payment** creates a fresh order without creating a second event-link row. A dismissal or failed verification leaves the transaction unrecovered and actionable.

Only successful server-side HMAC verification atomically marks the linked transaction `recovered`, sets `recovered_amount` to its persisted amount, moves lifecycle state to `recovered_by_verified_test_payment`, appends exactly one audit entry, and recomputes dashboard and root-cause totals from current database state. The file-based H2 database is seeded with the canonical 65 events only when both event and audit tables are empty; later restarts preserve all live recovery state.

Eligibility matrix:

| Persisted state | Result |
| --- | --- |
| Checkout abandoned, not recovered | Resume Payment |
| Insufficient balance, not recovered | Choose Another Payment Method |
| Incorrect PIN, not recovered/stopped | Try Payment Again Securely |
| Bank temporary error, retry exhausted | Try Payment Again |
| Retryable mandate failure, retry exhausted | Pay Manually |
| Expired mandate, not escalated | Pay Manually |
| Active linked order under 30 minutes | Resume Recovery Payment + Check Payment Status |
| Stale/failed linked order | Retry Payment with a fresh order |
| Pending, weak network, cancelled, merchant/gateway, unknown, retry scheduled | Informational/blocked |
| Recovered or escalated | Permanently blocked from another payment |

Terminology: an **automatic retry** is a policy-scheduled system attempt; a **customer-initiated retry** is a voluntary new attempt after a safe failure such as incorrect PIN; a **recovery Checkout** is the linked Razorpay-hosted Test Mode flow; and **verified recovery** is the persisted state reached only after backend HMAC verification. This demonstration involves no real money, Live Mode, capture, settlement, payout, or refund.
