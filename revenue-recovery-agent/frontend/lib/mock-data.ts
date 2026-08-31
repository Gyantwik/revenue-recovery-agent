import { RootCause, TransactionOutcome, CaseType, ActionTaken, ROOT_CAUSE_CONFIG } from "./labels"
import type { Transaction } from "./types"

type LegacyMockTransaction = Omit<Transaction,
  "lifecycle_state" | "next_eligible_action_at" | "recovery_window_expires_at" | "history">

const LEGACY_MOCK_TRANSACTIONS: LegacyMockTransaction[] = [
  {
    "event_id": "TXN10001",
    "case_type": "payment_degradation",
    "amount": 2500,
    "currency": "INR",
    "timestamp": "2026-08-01T10:15:00Z",
    "is_at_risk": true,
    "risk_amount": 2500,
    "root_cause": "user_cancelled",
    "classification_confidence": 0.99,
    "signals_used": [
      "gateway_response_code: CANCELLED_BY_USER",
      "ui_event: BACK_BUTTON_PRESSED"
    ],
    "policy_rule_matched": "User cancellation -> Stop",
    "action_taken": "no_action_stop",
    "attempt_number": 0,
    "max_attempts_allowed": 0,
    "outcome": "stopped_correctly",
    "recovered_amount": 0,
    "stop_or_escalate_reason": "User explicitly cancelled \u2014 policy forbids retry"
  },
  {
    "event_id": "TXN10002",
    "case_type": "payment_degradation",
    "amount": 150,
    "currency": "INR",
    "timestamp": "2026-08-01T11:20:00Z",
    "is_at_risk": true,
    "risk_amount": 150,
    "root_cause": "user_cancelled",
    "classification_confidence": 0.98,
    "signals_used": [
      "gateway_response_code: U19_USER_ABORTED",
      "session_time: 45s"
    ],
    "policy_rule_matched": "User cancellation -> Stop",
    "action_taken": "no_action_stop",
    "attempt_number": 0,
    "max_attempts_allowed": 0,
    "outcome": "stopped_correctly",
    "recovered_amount": 0,
    "stop_or_escalate_reason": "User explicitly cancelled \u2014 policy forbids retry"
  },
  {
    "event_id": "TXN10003",
    "case_type": "payment_degradation",
    "amount": 750,
    "currency": "INR",
    "timestamp": "2026-08-02T09:10:00Z",
    "is_at_risk": true,
    "risk_amount": 750,
    "root_cause": "user_cancelled",
    "classification_confidence": 0.99,
    "signals_used": [
      "gateway_response_code: TXN_CANCELLED",
      "merchant_status: ABORTED"
    ],
    "policy_rule_matched": "User cancellation -> Stop",
    "action_taken": "no_action_stop",
    "attempt_number": 0,
    "max_attempts_allowed": 0,
    "outcome": "stopped_correctly",
    "recovered_amount": 0,
    "stop_or_escalate_reason": "User explicitly cancelled \u2014 policy forbids retry"
  },
  {
    "event_id": "TXN10004",
    "case_type": "payment_degradation",
    "amount": 4200,
    "currency": "INR",
    "timestamp": "2026-08-02T14:45:00Z",
    "is_at_risk": true,
    "risk_amount": 4200,
    "root_cause": "user_cancelled",
    "classification_confidence": 0.98,
    "signals_used": [
      "gateway_response_code: CANCELLED_BY_USER",
      "platform: iOS_APP"
    ],
    "policy_rule_matched": "User cancellation -> Stop",
    "action_taken": "no_action_stop",
    "attempt_number": 0,
    "max_attempts_allowed": 0,
    "outcome": "stopped_correctly",
    "recovered_amount": 0,
    "stop_or_escalate_reason": "User explicitly cancelled \u2014 policy forbids retry"
  },
  {
    "event_id": "TXN10005",
    "case_type": "payment_degradation",
    "amount": 8000,
    "currency": "INR",
    "timestamp": "2026-08-03T16:30:00Z",
    "is_at_risk": true,
    "risk_amount": 8000,
    "root_cause": "user_cancelled",
    "classification_confidence": 0.99,
    "signals_used": [
      "gateway_response_code: U19_USER_ABORTED",
      "ui_event: APP_KILLED"
    ],
    "policy_rule_matched": "User cancellation -> Stop",
    "action_taken": "no_action_stop",
    "attempt_number": 0,
    "max_attempts_allowed": 0,
    "outcome": "stopped_correctly",
    "recovered_amount": 0,
    "stop_or_escalate_reason": "User explicitly cancelled \u2014 policy forbids retry"
  },
  {
    "event_id": "TXN10006",
    "case_type": "payment_degradation",
    "amount": 550,
    "currency": "INR",
    "timestamp": "2026-08-03T18:15:00Z",
    "is_at_risk": true,
    "risk_amount": 550,
    "root_cause": "incorrect_pin",
    "classification_confidence": 0.97,
    "signals_used": [
      "gateway_response_code: INVALID_PIN",
      "attempt_history: 1 prior attempt"
    ],
    "policy_rule_matched": "Auth failure (PIN) -> Stop",
    "action_taken": "no_action_stop",
    "attempt_number": 1,
    "max_attempts_allowed": 0,
    "outcome": "stopped_correctly",
    "recovered_amount": 0,
    "stop_or_escalate_reason": "Incorrect PIN \u2014 authentication failure, requires manual customer retry"
  },
  {
    "event_id": "TXN10007",
    "case_type": "payment_degradation",
    "amount": 1200,
    "currency": "INR",
    "timestamp": "2026-08-04T08:22:00Z",
    "is_at_risk": true,
    "risk_amount": 1200,
    "root_cause": "incorrect_pin",
    "classification_confidence": 0.99,
    "signals_used": [
      "gateway_response_code: U16_PIN_INCORRECT",
      "issuer_bank: HDFC"
    ],
    "policy_rule_matched": "Auth failure (PIN) -> Stop",
    "action_taken": "no_action_stop",
    "attempt_number": 1,
    "max_attempts_allowed": 0,
    "outcome": "stopped_correctly",
    "recovered_amount": 0,
    "stop_or_escalate_reason": "Incorrect PIN \u2014 authentication failure, requires manual customer retry"
  },
  {
    "event_id": "TXN10008",
    "case_type": "payment_degradation",
    "amount": 3400,
    "currency": "INR",
    "timestamp": "2026-08-04T12:05:00Z",
    "is_at_risk": true,
    "risk_amount": 3400,
    "root_cause": "incorrect_pin",
    "classification_confidence": 0.98,
    "signals_used": [
      "gateway_response_code: INVALID_PIN",
      "auth_type: UPI"
    ],
    "policy_rule_matched": "Auth failure (PIN) -> Stop",
    "action_taken": "no_action_stop",
    "attempt_number": 1,
    "max_attempts_allowed": 0,
    "outcome": "stopped_correctly",
    "recovered_amount": 0,
    "stop_or_escalate_reason": "Incorrect PIN \u2014 authentication failure, requires manual customer retry"
  },
  {
    "event_id": "TXN10009",
    "case_type": "payment_degradation",
    "amount": 620,
    "currency": "INR",
    "timestamp": "2026-08-04T19:40:00Z",
    "is_at_risk": true,
    "risk_amount": 620,
    "root_cause": "incorrect_pin",
    "classification_confidence": 0.96,
    "signals_used": [
      "gateway_response_code: U16_PIN_INCORRECT",
      "attempt_history: 1 prior attempt"
    ],
    "policy_rule_matched": "Auth failure (PIN) -> Stop",
    "action_taken": "no_action_stop",
    "attempt_number": 1,
    "max_attempts_allowed": 0,
    "outcome": "stopped_correctly",
    "recovered_amount": 0,
    "stop_or_escalate_reason": "Incorrect PIN \u2014 authentication failure, requires manual customer retry"
  },
  {
    "event_id": "TXN10010",
    "case_type": "payment_degradation",
    "amount": 5100,
    "currency": "INR",
    "timestamp": "2026-08-05T09:12:00Z",
    "is_at_risk": true,
    "risk_amount": 5100,
    "root_cause": "merchant_gateway_issue",
    "classification_confidence": 0.65,
    "signals_used": [
      "gateway_response_code: ERR_UNKNOWN_X9",
      "latency_ms: 15200"
    ],
    "policy_rule_matched": "Low confidence -> Manual Review",
    "action_taken": "escalate_merchant",
    "attempt_number": 0,
    "max_attempts_allowed": 0,
    "outcome": "escalated",
    "recovered_amount": 0,
    "stop_or_escalate_reason": "Low classification confidence \u2014 routed to manual review"
  },
  {
    "event_id": "TXN10011",
    "case_type": "payment_degradation",
    "amount": 900,
    "currency": "INR",
    "timestamp": "2026-08-05T10:45:00Z",
    "is_at_risk": true,
    "risk_amount": 900,
    "root_cause": "bank_temp_error",
    "classification_confidence": 0.58,
    "signals_used": [
      "gateway_response_code: 99_SYSTEM_MALFUNCTION",
      "network_layer: TIMEOUT"
    ],
    "policy_rule_matched": "Low confidence -> Manual Review",
    "action_taken": "escalate_merchant",
    "attempt_number": 0,
    "max_attempts_allowed": 0,
    "outcome": "escalated",
    "recovered_amount": 0,
    "stop_or_escalate_reason": "Low classification confidence \u2014 routed to manual review"
  },
  {
    "event_id": "TXN10012",
    "case_type": "payment_degradation",
    "amount": 2750,
    "currency": "INR",
    "timestamp": "2026-08-06T14:20:00Z",
    "is_at_risk": true,
    "risk_amount": 2750,
    "root_cause": "payment_pending",
    "classification_confidence": 0.72,
    "signals_used": [
      "gateway_response_code: STATUS_UNAVAILABLE_WAIT",
      "issuer_bank: SBI"
    ],
    "policy_rule_matched": "Low confidence -> Manual Review",
    "action_taken": "escalate_merchant",
    "attempt_number": 0,
    "max_attempts_allowed": 0,
    "outcome": "escalated",
    "recovered_amount": 0,
    "stop_or_escalate_reason": "Low classification confidence \u2014 routed to manual review"
  },
  {
    "event_id": "TXN10013",
    "case_type": "payment_degradation",
    "amount": 3100,
    "currency": "INR",
    "timestamp": "2026-08-06T15:05:00Z",
    "is_at_risk": true,
    "risk_amount": 3100,
    "root_cause": "bank_temp_error",
    "classification_confidence": 0.94,
    "signals_used": [
      "gateway_response_code: BANK_TIMEOUT",
      "issuer: ICICI"
    ],
    "policy_rule_matched": "Bank temp error \u2192 Retry, max 2, 30 min window",
    "action_taken": "retry_payment",
    "attempt_number": 1,
    "max_attempts_allowed": 2,
    "outcome": "recovered",
    "recovered_amount": 3100,
    "stop_or_escalate_reason": null
  },
  {
    "event_id": "TXN10014",
    "case_type": "payment_degradation",
    "amount": 1400,
    "currency": "INR",
    "timestamp": "2026-08-06T16:50:00Z",
    "is_at_risk": true,
    "risk_amount": 1400,
    "root_cause": "bank_temp_error",
    "classification_confidence": 0.91,
    "signals_used": [
      "gateway_response_code: CBS_OFFLINE",
      "bank_downtime_api: TRUE"
    ],
    "policy_rule_matched": "Bank temp error \u2192 Retry, max 2, 30 min window",
    "action_taken": "retry_payment",
    "attempt_number": 2,
    "max_attempts_allowed": 2,
    "outcome": "not_recovered",
    "recovered_amount": 0,
    "stop_or_escalate_reason": null
  },
  {
    "event_id": "TXN10015",
    "case_type": "payment_degradation",
    "amount": 6500,
    "currency": "INR",
    "timestamp": "2026-08-07T09:30:00Z",
    "is_at_risk": true,
    "risk_amount": 6500,
    "root_cause": "bank_temp_error",
    "classification_confidence": 0.88,
    "signals_used": [
      "gateway_response_code: ISSUER_NODE_DOWN",
      "attempt_history: 1 prior attempt"
    ],
    "policy_rule_matched": "Bank temp error \u2192 Retry, max 2, 30 min window",
    "action_taken": "retry_payment",
    "attempt_number": 1,
    "max_attempts_allowed": 2,
    "outcome": "recovered",
    "recovered_amount": 6500,
    "stop_or_escalate_reason": null
  },
  {
    "event_id": "TXN10016",
    "case_type": "payment_degradation",
    "amount": 450,
    "currency": "INR",
    "timestamp": "2026-08-07T11:45:00Z",
    "is_at_risk": true,
    "risk_amount": 450,
    "root_cause": "bank_temp_error",
    "classification_confidence": 0.95,
    "signals_used": [
      "gateway_response_code: BANK_TIMEOUT",
      "npci_status: DEGRADED"
    ],
    "policy_rule_matched": "Bank temp error \u2192 Retry, max 2, 30 min window",
    "action_taken": "retry_payment",
    "attempt_number": 2,
    "max_attempts_allowed": 2,
    "outcome": "recovered",
    "recovered_amount": 450,
    "stop_or_escalate_reason": null
  },
  {
    "event_id": "TXN10017",
    "case_type": "payment_degradation",
    "amount": 2200,
    "currency": "INR",
    "timestamp": "2026-08-07T14:15:00Z",
    "is_at_risk": true,
    "risk_amount": 2200,
    "root_cause": "bank_temp_error",
    "classification_confidence": 0.92,
    "signals_used": [
      "gateway_response_code: 91_ISSUER_UNAVAILABLE",
      "issuer_bank: AXIS"
    ],
    "policy_rule_matched": "Bank temp error \u2192 Retry, max 2, 30 min window",
    "action_taken": "retry_payment",
    "attempt_number": 1,
    "max_attempts_allowed": 2,
    "outcome": "not_recovered",
    "recovered_amount": 0,
    "stop_or_escalate_reason": null
  },
  {
    "event_id": "TXN10018",
    "case_type": "payment_degradation",
    "amount": 7100,
    "currency": "INR",
    "timestamp": "2026-08-08T10:10:00Z",
    "is_at_risk": true,
    "risk_amount": 7100,
    "root_cause": "bank_temp_error",
    "classification_confidence": 0.89,
    "signals_used": [
      "gateway_response_code: BANK_TIMEOUT",
      "provider: JUSPAY"
    ],
    "policy_rule_matched": "Bank temp error \u2192 Retry, max 2, 30 min window",
    "action_taken": "retry_payment",
    "attempt_number": 2,
    "max_attempts_allowed": 2,
    "outcome": "recovered",
    "recovered_amount": 7100,
    "stop_or_escalate_reason": null
  },
  {
    "event_id": "TXN10019",
    "case_type": "payment_degradation",
    "amount": 1900,
    "currency": "INR",
    "timestamp": "2026-08-08T13:25:00Z",
    "is_at_risk": true,
    "risk_amount": 1900,
    "root_cause": "bank_temp_error",
    "classification_confidence": 0.93,
    "signals_used": [
      "gateway_response_code: CBS_OFFLINE",
      "attempt_history: 1 prior attempt"
    ],
    "policy_rule_matched": "Bank temp error \u2192 Retry, max 2, 30 min window",
    "action_taken": "retry_payment",
    "attempt_number": 1,
    "max_attempts_allowed": 2,
    "outcome": "not_recovered",
    "recovered_amount": 0,
    "stop_or_escalate_reason": null
  },
  {
    "event_id": "TXN10020",
    "case_type": "payment_degradation",
    "amount": 3800,
    "currency": "INR",
    "timestamp": "2026-08-08T18:40:00Z",
    "is_at_risk": true,
    "risk_amount": 3800,
    "root_cause": "bank_temp_error",
    "classification_confidence": 0.97,
    "signals_used": [
      "gateway_response_code: BANK_TIMEOUT",
      "issuer_bank: KOTAK"
    ],
    "policy_rule_matched": "Bank temp error \u2192 Retry, max 2, 30 min window",
    "action_taken": "retry_payment",
    "attempt_number": 2,
    "max_attempts_allowed": 2,
    "outcome": "recovered",
    "recovered_amount": 3800,
    "stop_or_escalate_reason": null
  },
  {
    "event_id": "TXN10021",
    "case_type": "payment_degradation",
    "amount": 500,
    "currency": "INR",
    "timestamp": "2026-08-09T08:05:00Z",
    "is_at_risk": true,
    "risk_amount": 500,
    "root_cause": "weak_network",
    "classification_confidence": 0.86,
    "signals_used": [
      "client_signal: HIGH_LATENCY",
      "gateway_response_code: CLIENT_TIMEOUT"
    ],
    "policy_rule_matched": "Weak network \u2192 Retry, max 2",
    "action_taken": "retry_payment",
    "attempt_number": 1,
    "max_attempts_allowed": 2,
    "outcome": "recovered",
    "recovered_amount": 500,
    "stop_or_escalate_reason": null
  },
  {
    "event_id": "TXN10022",
    "case_type": "payment_degradation",
    "amount": 1150,
    "currency": "INR",
    "timestamp": "2026-08-09T09:50:00Z",
    "is_at_risk": true,
    "risk_amount": 1150,
    "root_cause": "weak_network",
    "classification_confidence": 0.88,
    "signals_used": [
      "client_signal: NETWORK_DROP",
      "provider: RAZORPAY"
    ],
    "policy_rule_matched": "Weak network \u2192 Retry, max 2",
    "action_taken": "retry_payment",
    "attempt_number": 2,
    "max_attempts_allowed": 2,
    "outcome": "not_recovered",
    "recovered_amount": 0,
    "stop_or_escalate_reason": null
  },
  {
    "event_id": "TXN10023",
    "case_type": "payment_degradation",
    "amount": 4250,
    "currency": "INR",
    "timestamp": "2026-08-09T11:20:00Z",
    "is_at_risk": true,
    "risk_amount": 4250,
    "root_cause": "weak_network",
    "classification_confidence": 0.9,
    "signals_used": [
      "client_signal: JITTER_DETECTED",
      "gateway_response_code: NO_RESPONSE"
    ],
    "policy_rule_matched": "Weak network \u2192 Retry, max 2",
    "action_taken": "retry_payment",
    "attempt_number": 1,
    "max_attempts_allowed": 2,
    "outcome": "recovered",
    "recovered_amount": 4250,
    "stop_or_escalate_reason": null
  },
  {
    "event_id": "TXN10024",
    "case_type": "payment_degradation",
    "amount": 2900,
    "currency": "INR",
    "timestamp": "2026-08-09T14:10:00Z",
    "is_at_risk": true,
    "risk_amount": 2900,
    "root_cause": "weak_network",
    "classification_confidence": 0.84,
    "signals_used": [
      "client_signal: 3G_FALLBACK",
      "latency_ms: 18000"
    ],
    "policy_rule_matched": "Weak network \u2192 Retry, max 2",
    "action_taken": "retry_payment",
    "attempt_number": 2,
    "max_attempts_allowed": 2,
    "outcome": "recovered",
    "recovered_amount": 2900,
    "stop_or_escalate_reason": null
  },
  {
    "event_id": "TXN10025",
    "case_type": "payment_degradation",
    "amount": 6300,
    "currency": "INR",
    "timestamp": "2026-08-09T16:30:00Z",
    "is_at_risk": true,
    "risk_amount": 6300,
    "root_cause": "weak_network",
    "classification_confidence": 0.91,
    "signals_used": [
      "client_signal: CONNECTION_RESET",
      "gateway_response_code: CLIENT_TIMEOUT"
    ],
    "policy_rule_matched": "Weak network \u2192 Retry, max 2",
    "action_taken": "retry_payment",
    "attempt_number": 1,
    "max_attempts_allowed": 2,
    "outcome": "not_recovered",
    "recovered_amount": 0,
    "stop_or_escalate_reason": null
  },
  {
    "event_id": "TXN10026",
    "case_type": "payment_degradation",
    "amount": 750,
    "currency": "INR",
    "timestamp": "2026-08-10T08:15:00Z",
    "is_at_risk": true,
    "risk_amount": 750,
    "root_cause": "weak_network",
    "classification_confidence": 0.87,
    "signals_used": [
      "client_signal: PACKET_LOSS_HIGH",
      "auth_type: OTP"
    ],
    "policy_rule_matched": "Weak network \u2192 Retry, max 2",
    "action_taken": "retry_payment",
    "attempt_number": 2,
    "max_attempts_allowed": 2,
    "outcome": "recovered",
    "recovered_amount": 750,
    "stop_or_escalate_reason": null
  },
  {
    "event_id": "TXN10027",
    "case_type": "payment_degradation",
    "amount": 1600,
    "currency": "INR",
    "timestamp": "2026-08-10T12:00:00Z",
    "is_at_risk": true,
    "risk_amount": 1600,
    "root_cause": "weak_network",
    "classification_confidence": 0.89,
    "signals_used": [
      "client_signal: NETWORK_DROP",
      "attempt_history: 1 prior attempt"
    ],
    "policy_rule_matched": "Weak network \u2192 Retry, max 2",
    "action_taken": "retry_payment",
    "attempt_number": 1,
    "max_attempts_allowed": 2,
    "outcome": "not_recovered",
    "recovered_amount": 0,
    "stop_or_escalate_reason": null
  },
  {
    "event_id": "TXN10028",
    "case_type": "payment_degradation",
    "amount": 3450,
    "currency": "INR",
    "timestamp": "2026-08-10T15:45:00Z",
    "is_at_risk": true,
    "risk_amount": 3450,
    "root_cause": "weak_network",
    "classification_confidence": 0.92,
    "signals_used": [
      "client_signal: HIGH_LATENCY",
      "gateway_response_code: TIMEOUT"
    ],
    "policy_rule_matched": "Weak network \u2192 Retry, max 2",
    "action_taken": "retry_payment",
    "attempt_number": 2,
    "max_attempts_allowed": 2,
    "outcome": "recovered",
    "recovered_amount": 3450,
    "stop_or_escalate_reason": null
  },
  {
    "event_id": "TXN10029",
    "case_type": "payment_degradation",
    "amount": 5400,
    "currency": "INR",
    "timestamp": "2026-08-11T09:10:00Z",
    "is_at_risk": true,
    "risk_amount": 5400,
    "root_cause": "payment_pending",
    "classification_confidence": 0.98,
    "signals_used": [
      "gateway_response_code: PENDING_WITH_BANK",
      "npci_status: IN_PROCESS"
    ],
    "policy_rule_matched": "Payment pending \u2192 Verify status only",
    "action_taken": "verify_status",
    "attempt_number": 1,
    "max_attempts_allowed": 1,
    "outcome": "recovered",
    "recovered_amount": 5400,
    "stop_or_escalate_reason": null
  },
  {
    "event_id": "TXN10030",
    "case_type": "payment_degradation",
    "amount": 1250,
    "currency": "INR",
    "timestamp": "2026-08-11T11:25:00Z",
    "is_at_risk": true,
    "risk_amount": 1250,
    "root_cause": "payment_pending",
    "classification_confidence": 0.96,
    "signals_used": [
      "gateway_response_code: STATUS_UNKNOWN",
      "provider: CASHFREE"
    ],
    "policy_rule_matched": "Payment pending \u2192 Verify status only",
    "action_taken": "verify_status",
    "attempt_number": 1,
    "max_attempts_allowed": 1,
    "outcome": "escalated",
    "recovered_amount": 0,
    "stop_or_escalate_reason": "Status still pending after verification"
  },
  {
    "event_id": "TXN10031",
    "case_type": "payment_degradation",
    "amount": 7900,
    "currency": "INR",
    "timestamp": "2026-08-11T13:40:00Z",
    "is_at_risk": true,
    "risk_amount": 7900,
    "root_cause": "payment_pending",
    "classification_confidence": 0.99,
    "signals_used": [
      "gateway_response_code: PENDING_WITH_BANK",
      "polling_attempts: 3"
    ],
    "policy_rule_matched": "Payment pending \u2192 Verify status only",
    "action_taken": "verify_status",
    "attempt_number": 1,
    "max_attempts_allowed": 1,
    "outcome": "recovered",
    "recovered_amount": 7900,
    "stop_or_escalate_reason": null
  },
  {
    "event_id": "TXN10032",
    "case_type": "payment_degradation",
    "amount": 2300,
    "currency": "INR",
    "timestamp": "2026-08-11T16:05:00Z",
    "is_at_risk": true,
    "risk_amount": 2300,
    "root_cause": "payment_pending",
    "classification_confidence": 0.97,
    "signals_used": [
      "gateway_response_code: 68_AWAITING_RESPONSE",
      "issuer_bank: SBI"
    ],
    "policy_rule_matched": "Payment pending \u2192 Verify status only",
    "action_taken": "verify_status",
    "attempt_number": 1,
    "max_attempts_allowed": 1,
    "outcome": "escalated",
    "recovered_amount": 0,
    "stop_or_escalate_reason": "Status still pending after verification"
  },
  {
    "event_id": "TXN10033",
    "case_type": "payment_degradation",
    "amount": 4700,
    "currency": "INR",
    "timestamp": "2026-08-12T09:30:00Z",
    "is_at_risk": true,
    "risk_amount": 4700,
    "root_cause": "payment_pending",
    "classification_confidence": 0.95,
    "signals_used": [
      "gateway_response_code: PENDING_WITH_BANK",
      "latency_ms: 22000"
    ],
    "policy_rule_matched": "Payment pending \u2192 Verify status only",
    "action_taken": "verify_status",
    "attempt_number": 1,
    "max_attempts_allowed": 1,
    "outcome": "recovered",
    "recovered_amount": 4700,
    "stop_or_escalate_reason": null
  },
  {
    "event_id": "TXN10034",
    "case_type": "payment_degradation",
    "amount": 3200,
    "currency": "INR",
    "timestamp": "2026-08-12T11:50:00Z",
    "is_at_risk": true,
    "risk_amount": 3200,
    "root_cause": "payment_pending",
    "classification_confidence": 0.98,
    "signals_used": [
      "gateway_response_code: STATUS_UNKNOWN",
      "provider: STRIPE"
    ],
    "policy_rule_matched": "Payment pending \u2192 Verify status only",
    "action_taken": "verify_status",
    "attempt_number": 1,
    "max_attempts_allowed": 1,
    "outcome": "escalated",
    "recovered_amount": 0,
    "stop_or_escalate_reason": "Status still pending after verification"
  },
  {
    "event_id": "TXN10035",
    "case_type": "payment_degradation",
    "amount": 850,
    "currency": "INR",
    "timestamp": "2026-08-12T14:15:00Z",
    "is_at_risk": true,
    "risk_amount": 850,
    "root_cause": "payment_pending",
    "classification_confidence": 0.99,
    "signals_used": [
      "gateway_response_code: PENDING_WITH_BANK",
      "auth_type: NET_BANKING"
    ],
    "policy_rule_matched": "Payment pending \u2192 Verify status only",
    "action_taken": "verify_status",
    "attempt_number": 1,
    "max_attempts_allowed": 1,
    "outcome": "recovered",
    "recovered_amount": 850,
    "stop_or_escalate_reason": null
  },
  {
    "event_id": "TXN10036",
    "case_type": "payment_degradation",
    "amount": 1100,
    "currency": "INR",
    "timestamp": "2026-08-12T16:20:00Z",
    "is_at_risk": true,
    "risk_amount": 1100,
    "root_cause": "insufficient_balance",
    "classification_confidence": 0.99,
    "signals_used": [
      "gateway_response_code: 51_INSUFFICIENT_FUNDS",
      "issuer_bank: HDFC"
    ],
    "policy_rule_matched": "Insufficient balance \u2192 Send alternative payment link",
    "action_taken": "send_alt_payment_link",
    "attempt_number": 1,
    "max_attempts_allowed": 1,
    "outcome": "not_recovered",
    "recovered_amount": 0,
    "stop_or_escalate_reason": null
  },
  {
    "event_id": "TXN10037",
    "case_type": "payment_degradation",
    "amount": 3600,
    "currency": "INR",
    "timestamp": "2026-08-13T09:10:00Z",
    "is_at_risk": true,
    "risk_amount": 3600,
    "root_cause": "insufficient_balance",
    "classification_confidence": 0.98,
    "signals_used": [
      "gateway_response_code: INSUFFICIENT_FUNDS",
      "auth_type: DEBIT_CARD"
    ],
    "policy_rule_matched": "Insufficient balance \u2192 Send alternative payment link",
    "action_taken": "send_alt_payment_link",
    "attempt_number": 1,
    "max_attempts_allowed": 1,
    "outcome": "recovered",
    "recovered_amount": 3600,
    "stop_or_escalate_reason": null
  },
  {
    "event_id": "TXN10038",
    "case_type": "payment_degradation",
    "amount": 5800,
    "currency": "INR",
    "timestamp": "2026-08-13T10:45:00Z",
    "is_at_risk": true,
    "risk_amount": 5800,
    "root_cause": "insufficient_balance",
    "classification_confidence": 0.99,
    "signals_used": [
      "gateway_response_code: 51_INSUFFICIENT_FUNDS",
      "provider: PAYU"
    ],
    "policy_rule_matched": "Insufficient balance \u2192 Send alternative payment link",
    "action_taken": "send_alt_payment_link",
    "attempt_number": 1,
    "max_attempts_allowed": 1,
    "outcome": "not_recovered",
    "recovered_amount": 0,
    "stop_or_escalate_reason": null
  },
  {
    "event_id": "TXN10039",
    "case_type": "payment_degradation",
    "amount": 2100,
    "currency": "INR",
    "timestamp": "2026-08-13T13:20:00Z",
    "is_at_risk": true,
    "risk_amount": 2100,
    "root_cause": "insufficient_balance",
    "classification_confidence": 0.99,
    "signals_used": [
      "gateway_response_code: INSUFFICIENT_FUNDS",
      "issuer_bank: ICICI"
    ],
    "policy_rule_matched": "Insufficient balance \u2192 Send alternative payment link",
    "action_taken": "send_alt_payment_link",
    "attempt_number": 1,
    "max_attempts_allowed": 1,
    "outcome": "recovered",
    "recovered_amount": 2100,
    "stop_or_escalate_reason": null
  },
  {
    "event_id": "TXN10040",
    "case_type": "payment_degradation",
    "amount": 4500,
    "currency": "INR",
    "timestamp": "2026-08-13T15:05:00Z",
    "is_at_risk": true,
    "risk_amount": 4500,
    "root_cause": "insufficient_balance",
    "classification_confidence": 0.99,
    "signals_used": [
      "gateway_response_code: 51_INSUFFICIENT_FUNDS",
      "auth_type: UPI"
    ],
    "policy_rule_matched": "Insufficient balance \u2192 Send alternative payment link",
    "action_taken": "send_alt_payment_link",
    "attempt_number": 1,
    "max_attempts_allowed": 1,
    "outcome": "not_recovered",
    "recovered_amount": 0,
    "stop_or_escalate_reason": null
  },
  {
    "event_id": "TXN10041",
    "case_type": "payment_degradation",
    "amount": 1850,
    "currency": "INR",
    "timestamp": "2026-08-14T08:30:00Z",
    "is_at_risk": true,
    "risk_amount": 1850,
    "root_cause": "insufficient_balance",
    "classification_confidence": 0.98,
    "signals_used": [
      "gateway_response_code: INSUFFICIENT_BALANCE",
      "provider: RAZORPAY"
    ],
    "policy_rule_matched": "Insufficient balance \u2192 Send alternative payment link",
    "action_taken": "send_alt_payment_link",
    "attempt_number": 1,
    "max_attempts_allowed": 1,
    "outcome": "recovered",
    "recovered_amount": 1850,
    "stop_or_escalate_reason": null
  },
  {
    "event_id": "TXN10042",
    "case_type": "payment_degradation",
    "amount": 7200,
    "currency": "INR",
    "timestamp": "2026-08-14T10:15:00Z",
    "is_at_risk": true,
    "risk_amount": 7200,
    "root_cause": "insufficient_balance",
    "classification_confidence": 0.99,
    "signals_used": [
      "gateway_response_code: 51_INSUFFICIENT_FUNDS",
      "issuer_bank: AXIS"
    ],
    "policy_rule_matched": "Insufficient balance \u2192 Send alternative payment link",
    "action_taken": "send_alt_payment_link",
    "attempt_number": 1,
    "max_attempts_allowed": 1,
    "outcome": "not_recovered",
    "recovered_amount": 0,
    "stop_or_escalate_reason": null
  },
  {
    "event_id": "TXN10043",
    "case_type": "payment_degradation",
    "amount": 3150,
    "currency": "INR",
    "timestamp": "2026-08-14T11:45:00Z",
    "is_at_risk": true,
    "risk_amount": 3150,
    "root_cause": "checkout_abandoned",
    "classification_confidence": 0.95,
    "signals_used": [
      "ui_event: APP_BACKGROUNDED",
      "time_on_page: 300s"
    ],
    "policy_rule_matched": "Checkout abandoned \u2192 Send recovery link",
    "action_taken": "send_recovery_link",
    "attempt_number": 1,
    "max_attempts_allowed": 1,
    "outcome": "recovered",
    "recovered_amount": 3150,
    "stop_or_escalate_reason": null
  },
  {
    "event_id": "TXN10044",
    "case_type": "payment_degradation",
    "amount": 950,
    "currency": "INR",
    "timestamp": "2026-08-14T13:20:00Z",
    "is_at_risk": true,
    "risk_amount": 950,
    "root_cause": "checkout_abandoned",
    "classification_confidence": 0.93,
    "signals_used": [
      "ui_event: SESSION_EXPIRED",
      "interaction_count: 0"
    ],
    "policy_rule_matched": "Checkout abandoned \u2192 Send recovery link",
    "action_taken": "send_recovery_link",
    "attempt_number": 1,
    "max_attempts_allowed": 1,
    "outcome": "not_recovered",
    "recovered_amount": 0,
    "stop_or_escalate_reason": null
  },
  {
    "event_id": "TXN10045",
    "case_type": "payment_degradation",
    "amount": 5600,
    "currency": "INR",
    "timestamp": "2026-08-14T15:10:00Z",
    "is_at_risk": true,
    "risk_amount": 5600,
    "root_cause": "checkout_abandoned",
    "classification_confidence": 0.96,
    "signals_used": [
      "ui_event: TAB_CLOSED",
      "time_on_page: 120s"
    ],
    "policy_rule_matched": "Checkout abandoned \u2192 Send recovery link",
    "action_taken": "send_recovery_link",
    "attempt_number": 1,
    "max_attempts_allowed": 1,
    "outcome": "recovered",
    "recovered_amount": 5600,
    "stop_or_escalate_reason": null
  },
  {
    "event_id": "TXN10046",
    "case_type": "payment_degradation",
    "amount": 2800,
    "currency": "INR",
    "timestamp": "2026-08-14T17:05:00Z",
    "is_at_risk": true,
    "risk_amount": 2800,
    "root_cause": "checkout_abandoned",
    "classification_confidence": 0.94,
    "signals_used": [
      "ui_event: APP_BACKGROUNDED",
      "cart_value: 2800"
    ],
    "policy_rule_matched": "Checkout abandoned \u2192 Send recovery link",
    "action_taken": "send_recovery_link",
    "attempt_number": 1,
    "max_attempts_allowed": 1,
    "outcome": "not_recovered",
    "recovered_amount": 0,
    "stop_or_escalate_reason": null
  },
  {
    "event_id": "TXN10047",
    "case_type": "payment_degradation",
    "amount": 4100,
    "currency": "INR",
    "timestamp": "2026-08-14T19:30:00Z",
    "is_at_risk": true,
    "risk_amount": 4100,
    "root_cause": "checkout_abandoned",
    "classification_confidence": 0.97,
    "signals_used": [
      "ui_event: INACTIVITY_TIMEOUT",
      "time_on_page: 600s"
    ],
    "policy_rule_matched": "Checkout abandoned \u2192 Send recovery link",
    "action_taken": "send_recovery_link",
    "attempt_number": 1,
    "max_attempts_allowed": 1,
    "outcome": "recovered",
    "recovered_amount": 4100,
    "stop_or_escalate_reason": null
  },
  {
    "event_id": "TXN10048",
    "case_type": "payment_degradation",
    "amount": 6900,
    "currency": "INR",
    "timestamp": "2026-08-14T20:15:00Z",
    "is_at_risk": true,
    "risk_amount": 6900,
    "root_cause": "merchant_gateway_issue",
    "classification_confidence": 0.98,
    "signals_used": [
      "gateway_response_code: 02_MERCHANT_BLOCKED",
      "provider: JUSPAY"
    ],
    "policy_rule_matched": "Gateway issue -> Escalate",
    "action_taken": "escalate_merchant",
    "attempt_number": 0,
    "max_attempts_allowed": 0,
    "outcome": "escalated",
    "recovered_amount": 0,
    "stop_or_escalate_reason": "Merchant/gateway-side issue \u2014 requires manual review"
  },
  {
    "event_id": "TXN10049",
    "case_type": "payment_degradation",
    "amount": 3300,
    "currency": "INR",
    "timestamp": "2026-08-14T21:40:00Z",
    "is_at_risk": true,
    "risk_amount": 3300,
    "root_cause": "merchant_gateway_issue",
    "classification_confidence": 0.99,
    "signals_used": [
      "gateway_response_code: MID_INVALID",
      "latency_ms: 50"
    ],
    "policy_rule_matched": "Gateway issue -> Escalate",
    "action_taken": "escalate_merchant",
    "attempt_number": 0,
    "max_attempts_allowed": 0,
    "outcome": "escalated",
    "recovered_amount": 0,
    "stop_or_escalate_reason": "Merchant/gateway-side issue \u2014 requires manual review"
  },
  {
    "event_id": "TXN10050",
    "case_type": "payment_degradation",
    "amount": 5200,
    "currency": "INR",
    "timestamp": "2026-08-14T22:25:00Z",
    "is_at_risk": true,
    "risk_amount": 5200,
    "root_cause": "merchant_gateway_issue",
    "classification_confidence": 0.97,
    "signals_used": [
      "gateway_response_code: GATEWAY_ROUTING_ERROR",
      "provider: CASHFREE"
    ],
    "policy_rule_matched": "Gateway issue -> Escalate",
    "action_taken": "escalate_merchant",
    "attempt_number": 0,
    "max_attempts_allowed": 0,
    "outcome": "escalated",
    "recovered_amount": 0,
    "stop_or_escalate_reason": "Merchant/gateway-side issue \u2014 requires manual review"
  },
  {
    "event_id": "TXN10051",
    "case_type": "mandate_renewal",
    "amount": 999,
    "currency": "INR",
    "timestamp": "2026-08-01T08:00:00Z",
    "is_at_risk": true,
    "risk_amount": 999,
    "root_cause": "mandate_expired",
    "classification_confidence": 0.99,
    "signals_used": [
      "gateway_response_code: MANDATE_NOT_FOUND",
      "mandate_status: EXPIRED"
    ],
    "policy_rule_matched": "Mandate expired -> Escalate",
    "action_taken": "escalate_merchant",
    "attempt_number": 0,
    "max_attempts_allowed": 0,
    "outcome": "escalated",
    "recovered_amount": 0,
    "stop_or_escalate_reason": "Mandate expired or revoked \u2014 cannot retry, new mandate required"
  },
  {
    "event_id": "TXN10052",
    "case_type": "mandate_renewal",
    "amount": 1499,
    "currency": "INR",
    "timestamp": "2026-08-02T08:05:00Z",
    "is_at_risk": true,
    "risk_amount": 1499,
    "root_cause": "mandate_expired",
    "classification_confidence": 0.98,
    "signals_used": [
      "gateway_response_code: U30_MANDATE_REVOKED",
      "mandate_status: REVOKED"
    ],
    "policy_rule_matched": "Mandate expired -> Escalate",
    "action_taken": "escalate_merchant",
    "attempt_number": 0,
    "max_attempts_allowed": 0,
    "outcome": "escalated",
    "recovered_amount": 0,
    "stop_or_escalate_reason": "Mandate expired or revoked \u2014 cannot retry, new mandate required"
  },
  {
    "event_id": "TXN10053",
    "case_type": "mandate_renewal",
    "amount": 2999,
    "currency": "INR",
    "timestamp": "2026-08-03T08:10:00Z",
    "is_at_risk": true,
    "risk_amount": 2999,
    "root_cause": "mandate_expired",
    "classification_confidence": 0.99,
    "signals_used": [
      "gateway_response_code: MAX_AMOUNT_EXCEEDED",
      "mandate_status: EXPIRED"
    ],
    "policy_rule_matched": "Mandate expired -> Escalate",
    "action_taken": "escalate_merchant",
    "attempt_number": 0,
    "max_attempts_allowed": 0,
    "outcome": "escalated",
    "recovered_amount": 0,
    "stop_or_escalate_reason": "Mandate expired or revoked \u2014 cannot retry, new mandate required"
  },
  {
    "event_id": "TXN10054",
    "case_type": "mandate_renewal",
    "amount": 499,
    "currency": "INR",
    "timestamp": "2026-08-04T08:15:00Z",
    "is_at_risk": true,
    "risk_amount": 499,
    "root_cause": "mandate_failed_retryable",
    "classification_confidence": 0.62,
    "signals_used": [
      "gateway_response_code: UNKNOWN_MANDATE_ERR_88",
      "issuer_bank: KOTAK"
    ],
    "policy_rule_matched": "Low confidence -> Manual Review",
    "action_taken": "escalate_merchant",
    "attempt_number": 0,
    "max_attempts_allowed": 0,
    "outcome": "escalated",
    "recovered_amount": 0,
    "stop_or_escalate_reason": "Low classification confidence \u2014 routed to manual review"
  },
  {
    "event_id": "TXN10055",
    "case_type": "mandate_renewal",
    "amount": 1999,
    "currency": "INR",
    "timestamp": "2026-08-05T08:20:00Z",
    "is_at_risk": true,
    "risk_amount": 1999,
    "root_cause": "mandate_failed_retryable",
    "classification_confidence": 0.92,
    "signals_used": [
      "gateway_response_code: DECLINED_BY_BANK",
      "mandate_status: ACTIVE"
    ],
    "policy_rule_matched": "Mandate failed retryable \u2192 Retry, max 2",
    "action_taken": "schedule_mandate_retry",
    "attempt_number": 1,
    "max_attempts_allowed": 2,
    "outcome": "recovered",
    "recovered_amount": 1999,
    "stop_or_escalate_reason": null
  },
  {
    "event_id": "TXN10056",
    "case_type": "mandate_renewal",
    "amount": 599,
    "currency": "INR",
    "timestamp": "2026-08-06T08:25:00Z",
    "is_at_risk": true,
    "risk_amount": 599,
    "root_cause": "mandate_failed_retryable",
    "classification_confidence": 0.94,
    "signals_used": [
      "gateway_response_code: TEMPORARY_BLOCK",
      "attempt_history: 1 prior attempt"
    ],
    "policy_rule_matched": "Mandate failed retryable \u2192 Retry, max 2",
    "action_taken": "schedule_mandate_retry",
    "attempt_number": 2,
    "max_attempts_allowed": 2,
    "outcome": "escalated",
    "recovered_amount": 0,
    "stop_or_escalate_reason": "Max mandate retries reached"
  },
  {
    "event_id": "TXN10057",
    "case_type": "mandate_renewal",
    "amount": 3499,
    "currency": "INR",
    "timestamp": "2026-08-07T08:30:00Z",
    "is_at_risk": true,
    "risk_amount": 3499,
    "root_cause": "mandate_failed_retryable",
    "classification_confidence": 0.95,
    "signals_used": [
      "gateway_response_code: ISSUER_NODE_OFFLINE",
      "mandate_status: ACTIVE"
    ],
    "policy_rule_matched": "Mandate failed retryable \u2192 Retry, max 2",
    "action_taken": "schedule_mandate_retry",
    "attempt_number": 1,
    "max_attempts_allowed": 2,
    "outcome": "recovered",
    "recovered_amount": 3499,
    "stop_or_escalate_reason": null
  },
  {
    "event_id": "TXN10058",
    "case_type": "mandate_renewal",
    "amount": 899,
    "currency": "INR",
    "timestamp": "2026-08-08T08:35:00Z",
    "is_at_risk": true,
    "risk_amount": 899,
    "root_cause": "mandate_failed_retryable",
    "classification_confidence": 0.91,
    "signals_used": [
      "gateway_response_code: SERVER_BUSY",
      "attempt_history: 1 prior attempt"
    ],
    "policy_rule_matched": "Mandate failed retryable \u2192 Retry, max 2",
    "action_taken": "schedule_mandate_retry",
    "attempt_number": 2,
    "max_attempts_allowed": 2,
    "outcome": "recovered",
    "recovered_amount": 899,
    "stop_or_escalate_reason": null
  },
  {
    "event_id": "TXN10059",
    "case_type": "mandate_renewal",
    "amount": 2500,
    "currency": "INR",
    "timestamp": "2026-08-09T08:40:00Z",
    "is_at_risk": true,
    "risk_amount": 2500,
    "root_cause": "mandate_failed_retryable",
    "classification_confidence": 0.93,
    "signals_used": [
      "gateway_response_code: PROCESSING_ERROR",
      "mandate_status: ACTIVE"
    ],
    "policy_rule_matched": "Mandate failed retryable \u2192 Retry, max 2",
    "action_taken": "schedule_mandate_retry",
    "attempt_number": 1,
    "max_attempts_allowed": 2,
    "outcome": "not_recovered",
    "recovered_amount": 0,
    "stop_or_escalate_reason": null
  },
  {
    "event_id": "TXN10060",
    "case_type": "mandate_renewal",
    "amount": 1299,
    "currency": "INR",
    "timestamp": "2026-08-10T08:45:00Z",
    "is_at_risk": true,
    "risk_amount": 1299,
    "root_cause": "mandate_failed_retryable",
    "classification_confidence": 0.96,
    "signals_used": [
      "gateway_response_code: TIMEOUT",
      "attempt_history: 1 prior attempt"
    ],
    "policy_rule_matched": "Mandate failed retryable \u2192 Retry, max 2",
    "action_taken": "schedule_mandate_retry",
    "attempt_number": 2,
    "max_attempts_allowed": 2,
    "outcome": "recovered",
    "recovered_amount": 1299,
    "stop_or_escalate_reason": null
  },
  {
    "event_id": "TXN10061",
    "case_type": "mandate_renewal",
    "amount": 4500,
    "currency": "INR",
    "timestamp": "2026-08-11T08:50:00Z",
    "is_at_risk": true,
    "risk_amount": 4500,
    "root_cause": "mandate_failed_retryable",
    "classification_confidence": 0.9,
    "signals_used": [
      "gateway_response_code: SUSPECTED_FRAUD_TEMP",
      "mandate_status: ACTIVE"
    ],
    "policy_rule_matched": "Mandate failed retryable \u2192 Retry, max 2",
    "action_taken": "schedule_mandate_retry",
    "attempt_number": 1,
    "max_attempts_allowed": 2,
    "outcome": "recovered",
    "recovered_amount": 4500,
    "stop_or_escalate_reason": null
  },
  {
    "event_id": "TXN10062",
    "case_type": "mandate_renewal",
    "amount": 699,
    "currency": "INR",
    "timestamp": "2026-08-12T08:55:00Z",
    "is_at_risk": true,
    "risk_amount": 699,
    "root_cause": "mandate_failed_retryable",
    "classification_confidence": 0.97,
    "signals_used": [
      "gateway_response_code: SYSTEM_ERROR",
      "attempt_history: 1 prior attempt"
    ],
    "policy_rule_matched": "Mandate failed retryable \u2192 Retry, max 2",
    "action_taken": "schedule_mandate_retry",
    "attempt_number": 2,
    "max_attempts_allowed": 2,
    "outcome": "escalated",
    "recovered_amount": 0,
    "stop_or_escalate_reason": "Max mandate retries reached"
  },
  {
    "event_id": "TXN10063",
    "case_type": "mandate_renewal",
    "amount": 2100,
    "currency": "INR",
    "timestamp": "2026-08-13T09:00:00Z",
    "is_at_risk": true,
    "risk_amount": 2100,
    "root_cause": "mandate_failed_retryable",
    "classification_confidence": 0.95,
    "signals_used": [
      "gateway_response_code: BANK_INTERNAL_ERROR",
      "mandate_status: ACTIVE"
    ],
    "policy_rule_matched": "Mandate failed retryable \u2192 Retry, max 2",
    "action_taken": "schedule_mandate_retry",
    "attempt_number": 1,
    "max_attempts_allowed": 2,
    "outcome": "recovered",
    "recovered_amount": 2100,
    "stop_or_escalate_reason": null
  },
  {
    "event_id": "TXN10064",
    "case_type": "mandate_renewal",
    "amount": 399,
    "currency": "INR",
    "timestamp": "2026-08-14T09:05:00Z",
    "is_at_risk": true,
    "risk_amount": 399,
    "root_cause": "mandate_failed_retryable",
    "classification_confidence": 0.92,
    "signals_used": [
      "gateway_response_code: NO_RESPONSE",
      "attempt_history: 1 prior attempt"
    ],
    "policy_rule_matched": "Mandate failed retryable \u2192 Retry, max 2",
    "action_taken": "schedule_mandate_retry",
    "attempt_number": 2,
    "max_attempts_allowed": 2,
    "outcome": "recovered",
    "recovered_amount": 399,
    "stop_or_escalate_reason": null
  },
  {
    "event_id": "TXN10065",
    "case_type": "mandate_renewal",
    "amount": 5500,
    "currency": "INR",
    "timestamp": "2026-08-14T09:10:00Z",
    "is_at_risk": true,
    "risk_amount": 5500,
    "root_cause": "mandate_failed_retryable",
    "classification_confidence": 0.98,
    "signals_used": [
      "gateway_response_code: CONNECTION_TIMEOUT",
      "mandate_status: ACTIVE"
    ],
    "policy_rule_matched": "Mandate failed retryable \u2192 Retry, max 2",
    "action_taken": "schedule_mandate_retry",
    "attempt_number": 1,
    "max_attempts_allowed": 2,
    "outcome": "not_recovered",
    "recovered_amount": 0,
    "stop_or_escalate_reason": null
  }
]

// Retained for isolated UI development only; normal runtime always uses backend APIs.
export const MOCK_TRANSACTIONS: Transaction[] = LEGACY_MOCK_TRANSACTIONS.map(transaction => ({
  ...transaction,
  lifecycle_state: transaction.outcome === "recovered" ? "recovered"
    : transaction.outcome === "escalated" ? "escalated"
      : transaction.outcome === "stopped_correctly" ? "stopped" : "not_recovered",
  next_eligible_action_at: null,
  recovery_window_expires_at: null,
  history: [],
}))

export interface RecoverySummaryStats {
  total_events: number
  total_at_risk_amount: number
  total_recovered_amount: number
  overall_recovery_rate: number
  recovered_count: number
  stopped_correctly_count: number
  escalated_count: number
  escalated_amount: number
  not_recovered_count: number
  not_recovered_amount: number
}

export interface CauseBreakdownItem {
  root_cause: RootCause
  label: string
  total_count: number
  total_amount: number
  recovered_count: number
  recovered_amount: number
  escalated_count: number
  stopped_count: number
  recovery_rate: number
  policy: string
  color: string
}

export function getSummaryStats(transactions: Transaction[] = MOCK_TRANSACTIONS): RecoverySummaryStats {
  const total_events = transactions.length
  const total_at_risk_amount = transactions.reduce((sum, t) => sum + t.amount, 0)
  const total_recovered_amount = transactions.reduce((sum, t) => sum + t.recovered_amount, 0)

  const recovered = transactions.filter(t => t.outcome === "recovered")
  const stopped = transactions.filter(t => t.outcome === "stopped_correctly")
  const escalated = transactions.filter(t => t.outcome === "escalated")
  const not_recovered = transactions.filter(t => t.outcome === "not_recovered")

  const escalated_amount = escalated.reduce((sum, t) => sum + t.amount, 0)
  const not_recovered_amount = not_recovered.reduce((sum, t) => sum + t.amount, 0)

  const overall_recovery_rate = total_at_risk_amount > 0
    ? (total_recovered_amount / total_at_risk_amount) * 100
    : 0

  return {
    total_events,
    total_at_risk_amount,
    total_recovered_amount,
    overall_recovery_rate,
    recovered_count: recovered.length,
    stopped_correctly_count: stopped.length,
    escalated_count: escalated.length,
    escalated_amount,
    not_recovered_count: not_recovered.length,
    not_recovered_amount,
  }
}

export function getCauseBreakdown(transactions: Transaction[] = MOCK_TRANSACTIONS): CauseBreakdownItem[] {
  const causes: RootCause[] = [
    "mandate_failed_retryable",
    "bank_temp_error",
    "weak_network",
    "payment_pending",
    "insufficient_balance",
    "checkout_abandoned",
    "user_cancelled",
    "incorrect_pin",
    "merchant_gateway_issue",
    "mandate_expired",
  ]

  return causes.map(cause => {
    const config = ROOT_CAUSE_CONFIG[cause]
    const matching = transactions.filter(t => t.root_cause === cause)
    const total_count = matching.length
    const total_amount = matching.reduce((sum, t) => sum + t.amount, 0)
    const recovered = matching.filter(t => t.outcome === "recovered")
    const recovered_count = recovered.length
    const recovered_amount = matching.reduce((sum, t) => sum + t.recovered_amount, 0)
    const escalated_count = matching.filter(t => t.outcome === "escalated").length
    const stopped_count = matching.filter(t => t.outcome === "stopped_correctly").length
    const recovery_rate = total_amount > 0 ? (recovered_amount / total_amount) * 100 : 0

    return {
      root_cause: cause,
      label: config.label,
      total_count,
      total_amount,
      recovered_count,
      recovered_amount,
      escalated_count,
      stopped_count,
      recovery_rate,
      policy: config.standardPolicy,
      color: config.color,
    }
  }).filter(item => item.total_count > 0)
}
