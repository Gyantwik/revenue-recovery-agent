import type { ActionTaken, CaseType, RootCause, TransactionOutcome } from "@/lib/labels"

export type LifecycleState =
  | "received" | "at_risk" | "classified" | "action_approved"
  | "retry_scheduled" | "verifying_payment" | "recovery_link_sent"
  | "retry_exhausted" | "recovered" | "not_recovered" | "stopped" | "escalated"

export type AuditActor = "system_simulation" | "merchant_manual"

export interface AuditHistoryEntry {
  timestamp: string
  previous_state: LifecycleState | null
  new_state: LifecycleState
  root_cause: RootCause
  classification_confidence: number
  policy_rule_matched: string
  action_taken: ActionTaken | null
  attempt_number: number
  max_attempts_allowed: number
  outcome_if_terminal: TransactionOutcome | null
  reason: string
  actor: AuditActor
  idempotency_key_or_action_sequence_key: string
}

export interface Transaction {
  event_id: string
  case_type: CaseType
  amount: number
  currency: string
  timestamp: string
  is_at_risk: boolean
  risk_amount: number
  root_cause: RootCause
  classification_confidence: number
  signals_used: string[]
  policy_rule_matched: string
  action_taken: ActionTaken
  attempt_number: number
  max_attempts_allowed: number
  outcome: TransactionOutcome
  recovered_amount: number
  stop_or_escalate_reason: string | null
  lifecycle_state: LifecycleState
  next_eligible_action_at: string | null
  recovery_window_expires_at: string | null
  history: AuditHistoryEntry[]
}

export interface ActionResult {
  event_id: string
  current_state: LifecycleState
  requested_action: ActionTaken
  reason: string
  next_eligible_action_at: string | null
}

export interface CauseSummary {
  root_cause: RootCause
  count: number
  total_amount: number
  recovered_amount: number
}

export interface EscalatedSummary {
  event_id: string
  root_cause: RootCause
  amount: number
  stop_or_escalate_reason: string | null
}

export interface BatchSummary {
  total_at_risk: number
  total_recovered: number
  recovery_rate: number
  total_cases: number
  by_cause: CauseSummary[]
  escalated_summary: EscalatedSummary[]
}

export interface RazorpayTestOrder {
  internal_request_id: string
  razorpay_order_id: string
  amount: number
  currency: "INR"
  receipt: string
  status: "created"
  mode: "test"
}

export interface RazorpayTestConfig {
  key_id: string
  mode: "test"
}

export type RazorpayCheckoutEventType = "checkout_success" | "checkout_failed_or_dismissed"

export interface RazorpayCheckoutEventRequest {
  internal_request_id: string
  razorpay_order_id: string
  razorpay_payment_id?: string
  razorpay_signature?: string
  event_type: RazorpayCheckoutEventType
  reason?: string
}

export interface RazorpayCheckoutEvent {
  internal_request_id: string
  razorpay_order_id: string
  razorpay_payment_id?: string
  event_type: RazorpayCheckoutEventType
  status: "client_reported_unverified"
  timestamp: string
}
