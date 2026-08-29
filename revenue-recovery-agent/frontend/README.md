# AI Revenue Recovery Dashboard (RecovrAI)

An enterprise-grade Next.js dashboard built for monitoring, diagnosing, and autonomously recovering failed recurring subscription payments and mandate renewals (NPCI UPI AutoPay, e-Mandate, NetBanking, Debit/Credit Card recurring charges).

## Dataset Integration & Analysis Summary
The dashboard is powered by the full 65-record production dataset (`synthetic-dataset.json`), capturing both **Payment Degradation** and **Mandate Renewal** failure scenarios:

- **Total Evaluated Events**: 65 failure events
- **Total At-Risk Revenue**: ₹1,91,209
- **Successfully Recovered**: ₹86,745 (45.4% Net Recovery Rate across 27 transactions)
- **Safely Stopped by Policy**: 9 transactions (₹18,570 protected from unlawful retries under PIN auth failure and explicit user cancellation rules)
- **Escalated Cases**: 15 transactions (₹35,745 routed for manual review due to low confidence, expired mandates, or gateway errors)
- **Unrecovered Cases**: 14 transactions (₹50,149 exhausted retry limits without recovery)

## Key Root Causes & Enforced Banking Policies

1. **User Cancelled (`user_cancelled`)**:
   - *Policy*: `User cancellation -> Stop`
   - *Action*: `no_action_stop` (0 retry attempts).
2. **Incorrect PIN (`incorrect_pin`)**:
   - *Policy*: `Auth failure (PIN) -> Stop`
   - *Action*: `no_action_stop` (Zero automated retries; customer manual re-authentication required).
3. **Mandate Failed Retryable (`mandate_failed_retryable`)**:
   - *Policy*: `Mandate failed retryable → Retry, max 2`
   - *Action*: `schedule_mandate_retry` (Strictly capped at maximum 2 attempts).
4. **Bank Temporary Error (`bank_temp_error`)**:
   - *Policy*: `Bank temp error → Retry, max 2, 30 min window`
   - *Action*: `retry_payment`
5. **Weak Network (`weak_network`)**:
   - *Policy*: `Weak network → Retry, max 2`
   - *Action*: `retry_payment`
6. **Payment Pending (`payment_pending`)**:
   - *Policy*: `Payment pending → Verify status only`
   - *Action*: `verify_status`
7. **Insufficient Balance (`insufficient_balance`)**:
   - *Policy*: `Insufficient balance → Send alternative payment link`
   - *Action*: `send_alt_payment_link`
8. **Checkout Abandoned (`checkout_abandoned`)**:
   - *Policy*: `Checkout abandoned → Send recovery link`
   - *Action*: `send_recovery_link`
9. **Merchant / Gateway Issue (`merchant_gateway_issue`)**:
   - *Policy*: `Gateway issue -> Escalate`
   - *Action*: `escalate_merchant`
10. **Mandate Expired (`mandate_expired`)**:
    - *Policy*: `Mandate expired -> Escalate`
    - *Action*: `escalate_merchant`

## Tech Stack
- **Framework**: Next.js 14 (App Router)
- **Language**: TypeScript 5
- **Styling**: Tailwind CSS & Lucide Icons
- **Components**: Radix UI / shadcn/ui primitives

## Getting Started

### Installation
```bash
npm install
# or
pnpm install
```

### Development Server
```bash
npm run dev
# or
pnpm dev
```
Open [http://localhost:3000](http://localhost:3000) to view the dashboard in your browser.
