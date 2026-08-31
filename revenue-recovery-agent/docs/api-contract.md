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
| Phase 4C verification status | Not implemented. Phase 4C will perform server-side HMAC signature verification. |

The `/razorpay-test` page uses synthetic prefill data, dynamically loads Razorpay-hosted Checkout once, and never collects payment instrument data itself. Neither the callback nor the intake row marks anything paid, captured, settled, verified, or recovered. Checkout orders and attempts are separate from the recovery pipeline, batch totals, and recovery audit history.

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

The transaction-row dialog intentionally reuses the complete audit object already returned by `GET /api/transactions`; it does not invent or supplement fields. Direct detail navigation and escalation links use the single-transaction endpoint.

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
