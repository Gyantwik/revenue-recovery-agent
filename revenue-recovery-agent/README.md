# revenue-recovery-agent

- Frontend: Next.js (see /frontend)
- Backend: Spring Boot (see /backend)

## Razorpay Test Mode orders

Phase 4A adds server-side Razorpay Test Mode order creation. Configure only the environment variables `RAZORPAY_KEY_ID` and `RAZORPAY_KEY_SECRET`. Copy `backend/.env.example` to `backend/.env` for local development and replace its placeholders with Test Mode values. The local `.env` file is ignored by Git; never commit it.

Start the backend from `backend/` so the optional local `.env` file is discovered:

```powershell
mvn spring-boot:run
```

Create a test order without putting credentials in the request:

```powershell
curl.exe -X POST http://localhost:8080/api/razorpay/test/orders -H "Content-Type: application/json" -d '{"amount":500.00,"currency":"INR"}'
```

The request amount is INR; the response amount is paise. This phase creates Razorpay Test Mode orders only. It does not open checkout, verify payments, process a payment, capture money, execute mandate retries, or change recovery metrics.
