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

## Policy-aware next recovery decisions

Transaction details load `GET /api/transactions/{eventId}/next-action` and display one backend-owned recommendation with its reason, safe next step, and policy guardrail. The UI intentionally has no generic **Recover Amount** or **Mark Recovered** shortcut. Stopped and recovered cases are disabled; pending payments show status-verification guidance; uncertain, gateway, expired-mandate, and retry-exhausted cases show non-financial review/escalation guidance; scheduled retries remain informational.

Normal synthetic benchmark records are read-only in this decision view. Only `TXN_DEMO_RECOVERY_001` can return `OPEN_TEST_MODE_RECOVERY_CHECKOUT`, which reuses the existing server-derived Razorpay Test Mode flow. Clicking any informational decision does not create an order, retry, audit entry, or recovered outcome. The demo becomes recovered only after hosted Checkout and successful server-side HMAC verification.
