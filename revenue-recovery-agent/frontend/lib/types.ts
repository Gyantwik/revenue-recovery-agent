import type { ActionTaken, CaseType, RootCause, TransactionOutcome } from "@/lib/labels"

export type LifecycleState =
  | "received" | "at_risk" | "classified" | "action_approved"
  | "retry_scheduled" | "verifying_payment" | "recovery_link_sent"
  | "retry_exhausted" | "recovered" | "not_recovered" | "stopped" | "escalated"
  | "recovered_by_verified_test_payment"

export type AuditActor = "system_simulation" | "merchant_manual" | "razorpay_test_verification" | "reservation_system"
export type TransactionSource = "live" | "seeded_reference"
export type VerificationResult = "confirmed_success" | "still_pending" | "confirmed_failed_retry_blocked"
export type AgentTraceStage = "observe" | "classify" | "decide" | "guardrail_check" | "act"
export interface AgentDecisionTrace {
  event_id: string
  stage: AgentTraceStage
  summary: string
  detail: string
  actor: AuditActor
  timestamp: string
}

export type AiTraceStage = "OBSERVE" | "CLASSIFY" | "DECIDE" | "GUARDRAIL_CHECK" | "ACT"

export interface AiTraceStep {
  stage: AiTraceStage
  thought: string
  conclusion: string
}

export interface AiAnalysisResponse {
  event_id: string
  predicted_root_cause: RootCause
  confidence: number
  recommended_action: ActionTaken
  ai_explanation: string
  optimal_retry_timing: string
  risk_assessment: string
  reasoning_trace: AiTraceStep[]
  analysis_source: "gemini" | "rules_based"
}

export type AiMessageChannel = "WHATSAPP" | "SMS" | "EMAIL"

export interface AiMessageResponse {
  event_id: string
  channel: string
  subject: string
  message: string
  suggested_cta: string
}

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
  customer_ref: string
  source: TransactionSource
  verification_result: VerificationResult | null
  detail: string
  gateway_error_reason: string | null
  gateway_order_id: string | null
  gateway_payment_id: string | null
  escalation_reason: string | null
}

export interface ActionResult {
  event_id: string
  current_state: LifecycleState
  requested_action: ActionTaken
  reason: string
  next_eligible_action_at: string | null
}

export type NextRecoveryAction =
  | "ALREADY_RECOVERED"
  | "STOPPED_BY_POLICY"
  | "ESCALATE_TO_MERCHANT"
  | "ESCALATE_MANDATE_RENEWAL"
  | "VERIFY_PAYMENT_STATUS"
  | "AWAIT_SCHEDULED_RETRY"
  | "AWAIT_SCHEDULED_MANDATE_RETRY"
  | "ESCALATE_AFTER_RETRY_EXHAUSTED"
  | "CUSTOMER_RECOVERY_CHECKOUT"
  | "RESUME_PAYMENT"
  | "CHOOSE_ANOTHER_PAYMENT_METHOD"
  | "TRY_PAYMENT_AGAIN_SECURELY"
  | "TRY_PAYMENT_AGAIN"
  | "PAY_MANUALLY"
  | "SEND_RECOVERY_LINK"
  | "SEND_ALT_PAYMENT_LINK"
  | "RESERVE_PAYMENT"
  | "COMPLETE_RESERVATION"

export type NextActionType = "NONE" | "DISPLAY_INFORMATION" | "OPEN_RECOVERY_CHECKOUT"
  | "RESUME_RECOVERY_CHECKOUT" | "CHECK_PAYMENT_STATUS" | "OPEN_TEST_MODE_RECOVERY_CHECKOUT"
  | "CREATE_RESERVATION" | "SIMULATE_RECONNECT"
export type NextActionMode = "synthetic_benchmark" | "razorpay_test_recovery" | "razorpay_test_demo"

export interface NextRecoveryActionDecision {
  event_id: string
  current_outcome: TransactionOutcome
  outcome: TransactionOutcome
  lifecycle_state: string
  attempts_made: number
  max_attempts: number
  is_action_allowed: boolean
  recommended_action: NextRecoveryAction
  button_label: string
  title: string
  reason: string
  next_step: string
  risk_note: string
  action_type: NextActionType
  secondary_action_type: NextActionType | null
  secondary_button_label: string | null
  existing_link_status: "none" | "order_created" | "checkout_opened"
    | "client_reported_unverified" | "abandoned" | "verified_test_payment"
    | "recovered_by_verified_test_payment" | "verification_failed" | "recovered"
  link_age_minutes: number
  mode: NextActionMode
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

export interface ExplainabilityResponse {
  event_id: string
  signals_used: string[]
  root_cause: RootCause
  classification_confidence: number
  policy_rule_matched: string
  allowed_action: ActionTaken
  attempt_number: number
  max_attempts_allowed: number
  was_blocked: boolean
  block_reason: string | null
  outcome: TransactionOutcome
  recovered_amount: number
}

export interface PolicyImpactResponse {
  root_cause: RootCause | null
  policy_rule: string | null
  allowed_action: ActionTaken | null
  eligible_cases: number
  recoverable_amount: number
  expected_recovery_rate: number
  cases_blocked_for_safety: number
  blocked_amount: number
  at_risk_cases: number
  not_at_risk_cases: number
  total_cases_considered: number
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
  customer_ref?: string
  error_code?: string
  error_description?: string
  error_source?: string
  error_step?: string
  latency_ms?: number
  simulated_connection?: boolean
}

export interface RazorpayCheckoutEvent {
  internal_request_id: string
  razorpay_order_id: string
  razorpay_payment_id?: string
  event_type: RazorpayCheckoutEventType
  status: "client_reported_unverified"
  timestamp: string
}

export interface RazorpayPaymentVerificationRequest {
  internal_request_id: string
  razorpay_order_id: string
  razorpay_payment_id: string
  razorpay_signature: string
}

export interface RazorpayPaymentVerification {
  internal_request_id: string
  razorpay_order_id: string
  razorpay_payment_id: string
  verification_status: "verified_test_payment" | "verification_failed"
  mode: "test"
  verified_at?: string
  recovery_event_id?: string
  recovery_status?: "awaiting_customer_payment" | "recovered"
  link_status?: "verification_failed" | "recovered_by_verified_test_payment"
}

export interface RecoveryDemoCase {
  event_id: string
  type: "Payment Degradation"
  failure_root_cause: "Checkout Abandoned"
  amount: number
  currency: "INR"
  policy_action: "Send Recovery Link"
  recovery_status: "awaiting_customer_payment" | "recovered"
  razorpay_mode: "test"
  demo_only: true
}

export interface RecoveryLinkedOrder {
  event_id: string
  internal_request_id: string
  razorpay_order_id: string
  amount: number
  currency: "INR"
  receipt: string
  link_status: "order_created"
  recovery_status: "awaiting_customer_payment"
  mode: "test"
}

export type RecoveryCheckoutAction =
  | "RESUME_PAYMENT"
  | "CHOOSE_ANOTHER_PAYMENT_METHOD"
  | "TRY_PAYMENT_AGAIN_SECURELY"
  | "TRY_PAYMENT_AGAIN"
  | "PAY_MANUALLY"

export interface TransactionRecoveryOrder {
  event_id: string
  internal_request_id: string
  razorpay_order_id: string
  amount: number
  currency: "INR"
  receipt: string
  recovery_action: RecoveryCheckoutAction
  link_status: "order_created" | "checkout_opened" | "client_reported_unverified"
  recovery_status: "awaiting_customer_payment"
  mode: "test"
}

export interface TransactionRecoveryStatus {
  event_id: string
  status: "no_payment_recorded" | "verification_failed" | "recovered"
  message: string
  is_recovered: boolean
  link_status: string
  mode: "test"
}

export interface RecoveryAuditEntry {
  timestamp: string
  action: string
  outcome: string
  reason: string
  actor: string
}

export interface RecoveryPaymentStatus {
  event_id: string
  eligible: boolean
  recovery_status: "awaiting_customer_payment" | "recovered"
  link_status?: "order_created" | "client_reported_unverified" | "verified_test_payment"
    | "recovered_by_verified_test_payment" | "verification_failed"
  internal_request_id?: string
  razorpay_order_id?: string
  razorpay_payment_id?: string
  verified_at?: string
  recovered_at?: string
  mode: "test"
  audit_history: RecoveryAuditEntry[]
}
