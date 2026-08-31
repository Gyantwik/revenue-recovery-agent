# RecoverAI API Contract

RecoverAI is a synthetic/test-mode demonstration. The Razorpay demo opens Razorpay's sandbox Checkout; no real money moves. Recovery endpoints remain simulations and do not initiate a bank request, mandate debit, or customer message.

The backend listens on `http://localhost:8080` by default. The Next.js server uses that URL unless `NEXT_PUBLIC_API_BASE_URL` overrides it. JSON property names and enum values use lowercase `snake_case`.

## POST `/api/razorpay/test/orders`

Creates one Razorpay Test Mode order from the backend. Credentials are read server-side from `RAZORPAY_KEY_ID` and `RAZORPAY_KEY_SECRET`; neither credential is accepted from or returned to the frontend. Missing, live-mode, or otherwise invalid Key IDs fail safely without preventing the synthetic recovery APIs from starting.

Request:

```json
{
  "amount": 500.00,
  "currency": "INR"
}
```

`amount` is INR and must be between ₹1.00 and ₹10,000.00 with at most two decimal places. Only `INR` is supported. The backend converts INR to paise using `BigDecimal`, generates a unique receipt, and sends only `source=recoverai_test_mode` and `environment=test` as notes.

Success (`200 OK`):

```json
{
  "internal_request_id": "req_...",
  "razorpay_order_id": "order_...",
  "amount": 50000,
  "currency": "INR",
  "receipt": "recoverai_...",
  "status": "created",
  "mode": "test"
}
```

The response `amount` is paise. `created` means an order exists; it does not mean a payment occurred or money was collected.

Invalid requests return `400`. Missing/invalid Test Mode configuration returns `503`. Razorpay non-success responses return a safe `502`, while network/timeouts return a safe `503`. Error bodies contain only `error`, `message`, and numeric `status`; upstream response bodies and credentials are never returned.

Successful mappings are stored separately in `razorpay_test_order` with internal request ID, Razorpay order ID, receipt, INR and paise amounts, currency, `created` status, `test` mode, and creation time. They never enter the recovery pipeline or affect batch totals, recovered revenue, recovery rate, or recovery audit history.

For local setup, copy `backend/.env.example` to the ignored `backend/.env`, set Test Mode values for the two named variables, and start Spring Boot from `backend/`. No Razorpay configuration is required for the existing synthetic APIs.

`created` means only that the sandbox order exists. The order is not a verified payment.

## GET `/api/razorpay/test/config`

Returns the browser-safe Test Mode Checkout configuration:

```json
{
  "key_id": "rzp_test_...",
  "mode": "test"
}
```

Razorpay Standard Checkout expects the Key ID in the browser. This endpoint returns it only when both server credentials are configured and the Key ID starts with `rzp_test_`. It never returns or logs the Key Secret and returns a safe `503` for missing, invalid, or Live Mode configuration.

## POST `/api/razorpay/test/checkout-events`

Temporarily records the browser's unverified Checkout report. A success-shaped request is:

```json
{
  "internal_request_id": "req_...",
  "razorpay_order_id": "order_...",
  "razorpay_payment_id": "pay_...",
  "razorpay_signature": "callback_signature",
  "event_type": "checkout_success"
}
```

A dismissal/failure request uses `event_type=checkout_failed_or_dismissed`, omits payment ID and signature, and may include `reason=user_cancelled_or_test_failure`.

The backend checks that the internal request exists, that its stored order ID matches, that the event type and conditional fields are valid, and that a success order/payment pair is not duplicated. Invalid input returns `400`, an unknown internal request returns `404`, and a duplicate success returns `409`.

The response deliberately excludes the signature:

```json
{
  "internal_request_id": "req_...",
  "razorpay_order_id": "order_...",
  "razorpay_payment_id": "pay_...",
  "event_type": "checkout_success",
  "status": "client_reported_unverified",
  "timestamp": "2026-08-31T00:00:00Z"
}
```

Status meanings:

| Status | Meaning |
| --- | --- |
| `order_created` | The frontend has received a sandbox order; no Checkout result exists. |
| `checkout_opened` | Razorpay Test Mode Checkout was opened locally in the browser. |
| `client_reported_unverified` | The browser reported success, failure, or dismissal; the report is not authenticated or payment-confirming. |
| `verified_test_payment` | Server-side HMAC authenticated the Test Mode callback. This is not capture, settlement, or recovery. |
| `verification_failed` | Signature verification failed. This implementation treats the result as terminal. |
| `recovered_by_verified_test_payment` | The dedicated demo case was atomically recovered after valid Test Mode signature verification. This is not a capture or settlement claim. |

The `/razorpay-test` page uses synthetic prefill data, dynamically loads Razorpay-hosted Checkout once, and never collects payment instrument data itself. Checkout orders and attempts are separate from the recovery pipeline, batch totals, and recovery audit history.

## POST `/api/razorpay/test/verify-payment`

Authenticates one previously recorded `checkout_success` callback locally. It makes no Razorpay API request.

Request:

```json
{
  "internal_request_id": "req_...",
  "razorpay_order_id": "order_...",
  "razorpay_payment_id": "pay_...",
  "razorpay_signature": "signature_from_checkout"
}
```

The backend loads and locks the local Test Mode order, checks that all three public IDs map to the same recorded success attempt, checks the supplied signature against the stored callback signature, and calculates:

```text
HMAC-SHA256(stored_razorpay_order_id + "|" + razorpay_payment_id, server-only Key Secret)
```

The stored order ID is always the canonical HMAC input; the browser-provided order ID is validated but never trusted as the input source. The lowercase hexadecimal HMAC and received signature are decoded and compared with `MessageDigest.isEqual`. The Key Secret never leaves the backend and is never logged, persisted, serialized, or returned.

Verified response (`200`):

```json
{
  "internal_request_id": "req_...",
  "razorpay_order_id": "order_...",
  "razorpay_payment_id": "pay_...",
  "verification_status": "verified_test_payment",
  "mode": "test",
  "verified_at": "2026-08-31T00:00:00Z"
}
```

Invalid signature response (`422`) has the same public IDs, `verification_status=verification_failed`, `mode=test`, and no `verified_at`. Missing fields return `400`, unknown internal requests return `404`, missing matching success attempts return `409`, and missing/invalid Test Mode configuration returns `503`. Responses never contain the signature or Key Secret.

Verification is terminal and concurrency-safe. A pessimistic lock serializes verification for each local order. A second verification of the same verified payment returns `409`; a different payment ID for an already verified order also returns `409`. An invalid signature stores the safe failure code `SIGNATURE_MISMATCH`, clears the stored callback signature, and transitions the order/attempt to terminal `verification_failed`, so later retry is rejected.

For an unlinked order, verification authenticates the Test Mode callback only. For the explicitly linked Phase 4D demo order, the same transaction also finalizes the dedicated demo recovery case and returns optional `recovery_event_id`, `recovery_status`, and `link_status` fields. It never polls payment status, captures or settles money, or changes benchmark revenue.

## Test Mode recovery demo

The recovery demo is deliberately separate from the deterministic benchmark. `TXN_DEMO_RECOVERY_001` is persisted in `recovery_demo_case` as a ₹500 payment-degradation / checkout-abandonment example with policy action `SEND_RECOVERY_LINK`, initial outcome `NOT_RECOVERED`, and status `awaiting_customer_payment`. It is not stored in `audit_record`, so the baseline remains exactly 65 cases, ₹191,209 at risk, ₹95,647 recovered, and recovery rate 0.5002.

Eligibility is derived exclusively from persisted root cause, policy action, outcome, amount, and existing-link state. Only policies resolving to `SEND_RECOVERY_LINK` or `SEND_ALT_PAYMENT_LINK` are compatible. Cancelled, PIN/auth-failure, payment-pending, weak-network/client-timeout, gateway/manual-review, mandate-expired/revoked, unknown, stopped, escalated, or already-recovered cases are rejected. The frontend cannot override policy or submit an order amount.

### GET `/api/recovery/test-mode/demo-case`

Returns the safe, clearly labelled demo case:

```json
{
  "event_id": "TXN_DEMO_RECOVERY_001",
  "type": "Payment Degradation",
  "failure_root_cause": "Checkout Abandoned",
  "amount": 500.00,
  "currency": "INR",
  "policy_action": "Send Recovery Link",
  "recovery_status": "awaiting_customer_payment",
  "razorpay_mode": "test",
  "demo_only": true
}
```

### POST `/api/recovery/{eventId}/razorpay-test-order`

Creates at most one linked Test Mode order. The endpoint accepts no policy, amount, or currency input; any request body is ignored. The backend locks the persisted case, re-evaluates policy, derives ₹500/INR, creates the Razorpay order, and stores a `recovery_payment_link` with unique event, internal-request, order, and optional payment IDs.

```json
{
  "event_id": "TXN_DEMO_RECOVERY_001",
  "internal_request_id": "req_...",
  "razorpay_order_id": "order_...",
  "amount": 50000,
  "currency": "INR",
  "receipt": "recoverai_...",
  "link_status": "order_created",
  "recovery_status": "awaiting_customer_payment",
  "mode": "test"
}
```

Unknown events return `404`. Ineligible, already-recovered, or already-linked cases return `409` with a safe business reason.

### GET `/api/recovery/{eventId}/razorpay-test-status`

Returns the event ID, policy eligibility, recovery/link status, safe public Razorpay IDs, verification/recovery timestamps, `mode=test`, and the demo event's append-only audit history. It never returns a signature, Key Secret, payment-instrument detail, or personal data.

On `checkout_success`, the existing intake endpoint first changes the link to `client_reported_unverified` without changing recovery. A valid call to the existing verification endpoint then locks the order/link/case and atomically:

1. stores `verified_test_payment` on the checkout attempt/order;
2. stores the public payment ID and timestamps on the recovery link;
3. transitions the demo case to outcome `RECOVERED` and status `recovered`;
4. appends one `AuditHistory` entry with result `RECOVERY_PAYMENT_VERIFIED_TEST_MODE`, actor `razorpay_test_verification`, and mode `TEST`;
5. transitions the link to `recovered_by_verified_test_payment`.

A pessimistic order/link/case lock and database uniqueness constraints prevent duplicate links and concurrent double recovery. Repeated verification returns `409` and cannot append a second audit entry. Any persistence/audit failure rolls back the verification, link, case, and history changes together. Invalid verification moves only Razorpay/link status to `verification_failed`; the recovery case and audit history remain unchanged.

Status sequence: `awaiting_customer_payment` → `order_created` → browser-only `checkout_opened` → `client_reported_unverified` → `verified_test_payment` → `recovered_by_verified_test_payment`. `verification_failed` is a terminal alternative after intake.

This feature is Test Mode only. It makes no claim that funds are captured, settled, refunded, paid out, or merchant-settled, and it implements no webhooks, polling, subscriptions, mandates, Live Mode, Reserve Pay, escrow, or payouts.

## Shared transaction response

Transaction endpoints return these audit fields:

| Field | Type | Notes |
| --- | --- | --- |
| `event_id` | string | Synthetic event identifier. |
| `case_type` | string | `payment_degradation` or `mandate_renewal`. |
| `amount` | decimal | Original amount. |
| `currency` | string | Currency code, currently `INR`. |
| `timestamp` | ISO-8601 string | Event time. |
| `is_at_risk` | boolean | MVP detection result. |
| `risk_amount` | decimal | Amount considered at risk. |
| `root_cause` | string | One of the root-cause values below. |
| `classification_confidence` | decimal | Rule-based confidence from 0 to 1. |
| `signals_used` | string array | Gateway/UI signals used for classification. |
| `policy_rule_matched` | string | Human-readable policy rule. |
| `action_taken` | string | One of the action values below. |
| `attempt_number` | integer | Executed/simulated attempt count. |
| `max_attempts_allowed` | integer | Policy cap. |
| `outcome` | string | One of the outcome values below. |
| `recovered_amount` | decimal | Synthetic recovered amount. |
| `stop_or_escalate_reason` | string or null | Present for stopped or escalated cases. |
| `lifecycle_state` | string | Current recovery lifecycle state. Terminal values cannot return to active states. |
| `next_eligible_action_at` | ISO-8601 string or null | Earliest synthetic/manual retry time when applicable. |
| `recovery_window_expires_at` | ISO-8601 string or null | End of the 30-minute bank/network recovery window when applicable. |
| `history` | array | Append-only lifecycle/action timeline. Summary-list responses may return an empty array; detail responses include all entries. |

Root causes: `bank_temp_error`, `weak_network`, `payment_pending`, `insufficient_balance`, `user_cancelled`, `incorrect_pin`, `merchant_gateway_issue`, `checkout_abandoned`, `mandate_failed_retryable`, `mandate_expired`, `unknown`.

Actions: `retry_payment`, `verify_status`, `send_alt_payment_link`, `send_recovery_link`, `no_action_stop`, `escalate_merchant`, `schedule_mandate_retry`.

Outcomes: `recovered`, `not_recovered`, `escalated`, `stopped_correctly`.

## POST `/api/batch/run`

- Request body: none.
- Behavior: processes the canonical 65-event synthetic dataset. Repeated calls are idempotent by `event_id`.
- Success: `200 OK` with the batch-summary response described below.
- Errors: unexpected processing failures return `500` with `{"error":"Internal server error"}`.
- Frontend consumer: batch execution clients; the dashboard subsequently reads the same persisted summary and records.

## GET `/api/batch-summary`

- Request: no body or query parameters.
- Success: `200 OK` with:

| Field | Type | Notes |
| --- | --- | --- |
| `total_at_risk` | decimal | Sum of all `risk_amount` values. |
| `total_recovered` | decimal | Sum of all `recovered_amount` values. |
| `recovery_rate` | decimal | Ratio, not percentage; for example `0.454` renders as `45.4%`. |
| `total_cases` | integer | Number of persisted audit records. |
| `by_cause` | array | Objects containing `root_cause`, `count`, `total_amount`, and `recovered_amount`. |
| `escalated_summary` | array | Escalated objects containing `event_id`, `root_cause`, `amount`, and `stop_or_escalate_reason`. |

- Frontend consumer: Recovery Overview KPI cards, root-cause table, and Escalated to Desk panel.

## GET `/api/transactions`

- Optional query parameters:
  - `cause`: exact lowercase root-cause value.
  - `status`: exact lowercase outcome value.
- Empty parameters mean no server-side filter. Filters can be combined.
- Success: `200 OK` with an array of shared transaction responses. No match returns `[]`.
- Invalid enum filter: `400 Bad Request` with `{"error":"Invalid request"}`.
- Frontend consumer: All Transactions directory and the Recovery Overview recent-activity table. The UI performs its case-type, root-cause, outcome, and text search together over this returned backend list.

## GET `/api/transactions/{eventId}`

- Path parameter: URL-encoded synthetic `eventId`.
- Success: `200 OK` with one shared transaction response.
- Not found: `404 Not Found` with `{"error":"Transaction not found","eventId":"..."}`.
- Unexpected errors: `500` with `{"error":"Internal server error"}`.
- Frontend consumer: dedicated transaction detail route, including links from Escalated to Desk.

The transaction-row dialog uses the complete audit object returned by `GET /api/transactions`, then separately loads the backend-owned policy recommendation described below. Direct detail navigation and escalation links use the single-transaction endpoint.

## GET `/api/transactions/{eventId}/next-action`

Returns a read-only, policy-derived explanation of the current state and exactly one safe next recommendation. Looking up a decision never creates a retry, order, Checkout attempt, audit note, notification, payment, or outcome change.

```json
{
  "event_id": "TXN10059",
  "current_outcome": "not_recovered",
  "lifecycle_state": "retry_exhausted",
  "attempts_made": 2,
  "max_attempts": 2,
  "is_action_allowed": true,
  "recommended_action": "ESCALATE_AFTER_RETRY_EXHAUSTED",
  "button_label": "Review Mandate / Escalate",
  "title": "Automatic retries are exhausted",
  "reason": "This mandate retry has reached its maximum of 2 attempts. Further automatic retries are blocked.",
  "next_step": "Review the mandate or escalate the case for manual resolution.",
  "risk_note": "Do not initiate another automatic debit attempt.",
  "action_type": "DISPLAY_INFORMATION",
  "mode": "synthetic_benchmark"
}
```

Stable `action_type` values:

| Value | Meaning |
| --- | --- |
| `NONE` | Blocked, completed, or informationally scheduled; the button is disabled. |
| `DISPLAY_INFORMATION` | Shows/acknowledges guidance in the browser only. No backend mutation occurs. |
| `OPEN_TEST_MODE_RECOVERY_CHECKOUT` | Navigates only the dedicated demo case to the existing Razorpay Test Mode recovery flow. |

Stable `recommended_action` values are `ALREADY_RECOVERED`, `STOPPED_BY_POLICY`, `ESCALATE_TO_MERCHANT`, `ESCALATE_MANDATE_RENEWAL`, `VERIFY_PAYMENT_STATUS`, `AWAIT_SCHEDULED_RETRY`, `AWAIT_SCHEDULED_MANDATE_RETRY`, `ESCALATE_AFTER_RETRY_EXHAUSTED`, `SEND_RECOVERY_LINK`, and `SEND_ALT_PAYMENT_LINK`. Modes are `synthetic_benchmark` and `razorpay_test_demo`.

Policy behavior:

- recovered cases return `ALREADY_RECOVERED`;
- cancelled and PIN/authentication-failure cases return `STOPPED_BY_POLICY`;
- unknown and gateway cases return merchant-review guidance;
- expired/revoked mandates return mandate-review guidance;
- pending payments return status-verification guidance to avoid duplicate debit;
- bank/network/mandate retries with attempts remaining stay scheduled and cannot be manually forced;
- exhausted retries return review/escalation guidance and prohibit another automatic debit;
- ordinary checkout-abandoned and insufficient-balance benchmark rows expose policy guidance only and cannot open Checkout;
- only the separate, eligible `TXN_DEMO_RECOVERY_001` returns `OPEN_TEST_MODE_RECOVERY_CHECKOUT`;
- after verified demo recovery, the same event returns `ALREADY_RECOVERED`.

Recommended actions, scheduled actions, escalation guidance, and blocked actions are not payment success. **Recover Amount** and **Mark Recovered** are intentionally absent. Only the dedicated demo's verified Razorpay Test Mode callback can atomically change its separate recovery status. The original 65-case benchmark remains static and excluded from the demo; no real money moves.

## Synthetic action endpoints

These endpoints perform deterministic test-mode actions only. They never call Razorpay, a bank, NPCI, or a customer-messaging service. The backend validates the current lifecycle, root-cause policy, attempt cap, recovery window, terminal-state rule, and the optional `Idempotency-Key` header before execution.

- `POST /api/transactions/{eventId}/actions/retry`
- `POST /api/transactions/{eventId}/actions/verify-status`
- `POST /api/transactions/{eventId}/actions/send-recovery-link`
- `POST /api/transactions/{eventId}/actions/send-alt-payment-link`
- `POST /api/transactions/{eventId}/actions/escalate`
- `POST /api/transactions/{eventId}/actions/stop`

A permitted request returns `200` with `event_id`, `current_state`, `requested_action`, `reason`, and optional `next_eligible_action_at`. A policy-blocked request returns the same shape with `409 Conflict`. A missing transaction returns `404`.

Lifecycle history records the previous and new states, classification and policy evidence, action and attempt counts, terminal outcome, reason, actor (`system_simulation` or `merchant_manual`), and an idempotency/action-sequence key. Existing entries are never updated.

## Batch rerun safety

The backend uses Option B: existing transaction and lifecycle history are reused by `event_id`. Repeated `POST /api/batch/run` calls do not duplicate transactions, history, at-risk revenue, or recovered revenue.

## Attempt-count rules

| Action / outcome | Attempts |
| --- | --- |
| `no_action_stop` / `stopped_correctly` | `0/0` |
| `escalate_merchant` / `escalated` | `0/0` |
| `retry_payment` | `1/2` or `2/2` |
| `schedule_mandate_retry` | `1/2` or `2/2` |
| `verify_status` | `1/1` |
| `send_recovery_link` | `1/1` |
| `send_alt_payment_link` | `1/1` |

## Frontend failure behavior

Network failures, non-success HTTP responses, malformed JSON, and structurally invalid payloads do not fall back to fixtures. Pages display an error with a Retry action. Missing detail records render the transaction-not-found screen. Empty filter results render an empty state with Reset filters.
