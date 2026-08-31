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

The browser receives the Test Mode Key ID through `GET /api/razorpay/test/config`, which is expected for Standard Checkout. The Key Secret always remains server-side. A browser callback is stored only as `client_reported_unverified`; it is not proof that a payment is paid, captured, settled, or recovered. Phase 4C will add server-side HMAC signature verification.

The checkout demo is isolated from the synthetic 65-case recovery dataset, audit history, and dashboard metrics.
