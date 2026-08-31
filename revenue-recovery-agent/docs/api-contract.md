# RecoverAI API Contract

RecoverAI is a synthetic/test-mode demonstration. None of these endpoints initiates a real payment, bank request, mandate debit, or customer message.

The backend listens on `http://localhost:8080` by default. The Next.js server uses that URL unless `NEXT_PUBLIC_API_BASE_URL` overrides it. JSON property names and enum values use lowercase `snake_case`.

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
