import type {
  ActionResult,
  BatchSummary,
  RazorpayCheckoutEvent,
  RazorpayCheckoutEventRequest,
  RazorpayTestConfig,
  RazorpayTestOrder,
  RazorpayPaymentVerification,
  RazorpayPaymentVerificationRequest,
  RecoveryDemoCase,
  RecoveryLinkedOrder,
  RecoveryPaymentStatus,
  NextRecoveryActionDecision,
  Transaction,
  TransactionRecoveryOrder,
  TransactionRecoveryStatus,
  AgentDecisionTrace,
  AiAnalysisResponse,
  AiMessageChannel,
  AiMessageResponse,
  ExplainabilityResponse,
  PolicyImpactResponse,
} from "@/lib/types"

const API_BASE_URL = (process.env.NEXT_PUBLIC_API_BASE_URL ?? "http://localhost:8080")
  .replace(/\/$/, "")

interface BackendErrorBody {
  error?: string
  message?: string
  reason?: string
}

export class ApiError extends Error {
  public readonly status?: number

  constructor(message: string, status?: number) {
    super(message)
    this.name = "ApiError"
    this.status = status
  }
}

async function requestJson(path: string, init?: RequestInit): Promise<unknown> {
  const controller = new AbortController()
  const timeout = setTimeout(() => controller.abort(), 15_000)
  const abortFromCaller = () => controller.abort()
  init?.signal?.addEventListener("abort", abortFromCaller, { once: true })
  if (init?.signal?.aborted) controller.abort()
  let response: Response
  try {
    response = await fetch(`${API_BASE_URL}${path}`, {
      cache: "no-store",
      ...init,
      signal: controller.signal,
    })
  } catch (error) {
    if (error instanceof DOMException && error.name === "AbortError") {
      throw new ApiError("Backend request timed out. Please try again.")
    }
    throw new ApiError("Unable to connect to the backend")
  } finally {
    clearTimeout(timeout)
    init?.signal?.removeEventListener("abort", abortFromCaller)
  }

  if (!response.ok) {
    let message = `Backend request failed with status ${response.status}`
    try {
      const body = (await response.json()) as BackendErrorBody
      if (body.reason) message = body.reason
      else if (body.message) message = body.message
      else if (body.error) message = body.error
    } catch {
      // Keep the status-based message for non-JSON error responses.
    }
    throw new ApiError(message, response.status)
  }

  try {
    return await response.json()
  } catch {
    throw new ApiError("Backend returned malformed JSON", response.status)
  }
}

function isTransaction(value: unknown): value is Transaction {
  if (typeof value !== "object" || value === null) return false
  const item = value as Partial<Transaction>
  return typeof item.event_id === "string"
    && typeof item.case_type === "string"
    && isFiniteNumber(item.amount)
    && typeof item.currency === "string"
    && typeof item.timestamp === "string"
    && typeof item.is_at_risk === "boolean"
    && isFiniteNumber(item.risk_amount)
    && typeof item.root_cause === "string"
    && isFiniteNumber(item.classification_confidence)
    && Array.isArray(item.signals_used)
    && item.signals_used.every(signal => typeof signal === "string")
    && typeof item.policy_rule_matched === "string"
    && typeof item.action_taken === "string"
    && isFiniteNumber(item.attempt_number)
    && isFiniteNumber(item.max_attempts_allowed)
    && typeof item.outcome === "string"
    && isFiniteNumber(item.recovered_amount)
    && (item.stop_or_escalate_reason == null || typeof item.stop_or_escalate_reason === "string")
    && typeof item.lifecycle_state === "string"
    && (item.next_eligible_action_at == null || typeof item.next_eligible_action_at === "string")
    && (item.recovery_window_expires_at == null || typeof item.recovery_window_expires_at === "string")
    && Array.isArray(item.history)
    && typeof item.customer_ref === "string"
    && ["live", "seeded_reference"].includes(item.source ?? "")
    && (item.verification_result == null || typeof item.verification_result === "string")
    && typeof item.detail === "string"
}

function isBatchSummary(value: unknown): value is BatchSummary {
  if (typeof value !== "object" || value === null) return false
  const summary = value as Partial<BatchSummary>
  return isFiniteNumber(summary.total_at_risk)
    && isFiniteNumber(summary.total_recovered)
    && isFiniteNumber(summary.recovery_rate)
    && isFiniteNumber(summary.total_cases)
    && Array.isArray(summary.by_cause)
    && summary.by_cause.every(item => typeof item === "object" && item !== null
      && typeof item.root_cause === "string"
      && isFiniteNumber(item.count)
      && isFiniteNumber(item.total_amount)
      && isFiniteNumber(item.recovered_amount))
    && Array.isArray(summary.escalated_summary)
    && summary.escalated_summary.every(item => typeof item === "object" && item !== null
      && typeof item.event_id === "string"
      && typeof item.root_cause === "string"
      && isFiniteNumber(item.amount)
      && (item.stop_or_escalate_reason == null || typeof item.stop_or_escalate_reason === "string"))
}

function isFiniteNumber(value: unknown): value is number {
  return typeof value === "number" && Number.isFinite(value)
}

export async function getTransactions(): Promise<Transaction[]> {
  const body = await requestJson("/api/transactions")
  if (!Array.isArray(body) || !body.every(isTransaction)) {
    throw new ApiError("Backend returned an invalid transactions response")
  }
  return body
}

export async function getBatchSummary(): Promise<BatchSummary> {
  const body = await requestJson("/api/batch-summary")
  if (!isBatchSummary(body)) {
    throw new ApiError("Backend returned an invalid batch summary response")
  }
  return body
}

export async function getTransaction(eventId: string): Promise<Transaction> {
  const body = await requestJson(`/api/transactions/${encodeURIComponent(eventId)}`)
  if (!isTransaction(body)) {
    throw new ApiError("Backend returned an invalid transaction response")
  }
  return body
}

export async function getAgentTrace(eventId: string): Promise<AgentDecisionTrace[]> {
  const body = await requestJson(`/api/transactions/${encodeURIComponent(eventId)}/agent-trace`)
  if (!Array.isArray(body) || !body.every(item => typeof item === "object" && item !== null
    && typeof item.event_id === "string" && typeof item.stage === "string"
    && typeof item.summary === "string" && typeof item.detail === "string"
    && typeof item.actor === "string" && typeof item.timestamp === "string")) {
    throw new ApiError("Backend returned an invalid agent trace")
  }
  return body as AgentDecisionTrace[]
}

const AI_TRACE_STAGES = ["OBSERVE", "CLASSIFY", "DECIDE", "GUARDRAIL_CHECK", "ACT"] as const

export async function getAiAnalysis(eventId: string): Promise<AiAnalysisResponse> {
  const body = await requestJson(`/api/transactions/${encodeURIComponent(eventId)}/ai-analysis`)
  if (typeof body !== "object" || body === null) {
    throw new ApiError("Backend returned an invalid Gemini AI analysis")
  }
  const analysis = body as Partial<AiAnalysisResponse>
  const trace = analysis.reasoning_trace
  if (analysis.event_id !== eventId
    || typeof analysis.predicted_root_cause !== "string"
    || !isFiniteNumber(analysis.confidence) || analysis.confidence < 0 || analysis.confidence > 1
    || typeof analysis.recommended_action !== "string"
    || typeof analysis.ai_explanation !== "string"
    || typeof analysis.optimal_retry_timing !== "string"
    || typeof analysis.risk_assessment !== "string"
    || !["gemini", "rules_based"].includes(analysis.analysis_source ?? "")
    || !Array.isArray(trace) || trace.length !== AI_TRACE_STAGES.length
    || !trace.every((step, index) => typeof step === "object" && step !== null
      && step.stage === AI_TRACE_STAGES[index]
      && typeof step.thought === "string" && typeof step.conclusion === "string")) {
    throw new ApiError("Backend returned an invalid Gemini AI analysis")
  }
  return analysis as AiAnalysisResponse
}

export async function generateAiRecoveryMessage(
  eventId: string,
  channel: AiMessageChannel,
  customerName: string,
): Promise<AiMessageResponse> {
  const body = await requestJson(`/api/transactions/${encodeURIComponent(eventId)}/ai-message`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ channel, customerName }),
  })
  if (typeof body !== "object" || body === null) {
    throw new ApiError("Backend returned an invalid Gemini recovery message")
  }
  const message = body as Partial<AiMessageResponse>
  if (message.event_id !== eventId || typeof message.channel !== "string"
    || typeof message.subject !== "string" || typeof message.message !== "string"
    || typeof message.suggested_cta !== "string") {
    throw new ApiError("Backend returned an invalid Gemini recovery message")
  }
  return message as AiMessageResponse
}

export async function measureBackendLatency(): Promise<number> {
  const started = performance.now()
  await requestJson("/api/ping")
  return Math.round(performance.now() - started)
}

export async function createPaymentReservation(eventId: string): Promise<void> {
  await requestJson(`/api/transactions/${encodeURIComponent(eventId)}/reservation`, { method: "POST" })
}

export async function simulateReservationReconnect(eventId: string): Promise<void> {
  await requestJson(`/api/transactions/${encodeURIComponent(eventId)}/reservation/reconnect`, { method: "POST" })
}

export async function getExplainability(eventId: string): Promise<ExplainabilityResponse> {
  const body = await requestJson(`/api/transactions/${encodeURIComponent(eventId)}/explainability`)
  if (typeof body !== "object" || body === null) throw new ApiError("Backend returned an invalid explainability response")
  const result = body as Partial<ExplainabilityResponse>
  if (result.event_id !== eventId || !Array.isArray(result.signals_used) || typeof result.root_cause !== "string"
    || !isFiniteNumber(result.classification_confidence) || typeof result.policy_rule_matched !== "string"
    || typeof result.allowed_action !== "string" || !isFiniteNumber(result.attempt_number)
    || !isFiniteNumber(result.max_attempts_allowed) || typeof result.was_blocked !== "boolean") {
    throw new ApiError("Backend returned an invalid explainability response")
  }
  return result as ExplainabilityResponse
}

export async function getPolicyImpact(): Promise<PolicyImpactResponse> {
  const body = await requestJson("/api/simulator/policy-impact")
  if (typeof body !== "object" || body === null) throw new ApiError("Backend returned an invalid policy impact response")
  const result = body as Partial<PolicyImpactResponse>
  if (!isFiniteNumber(result.eligible_cases) || !isFiniteNumber(result.recoverable_amount)
    || !isFiniteNumber(result.expected_recovery_rate) || !isFiniteNumber(result.cases_blocked_for_safety)
    || !isFiniteNumber(result.blocked_amount) || !isFiniteNumber(result.at_risk_cases)
    || !isFiniteNumber(result.not_at_risk_cases) || !isFiniteNumber(result.total_cases_considered)) {
    throw new ApiError("Backend returned an invalid policy impact response")
  }
  return result as PolicyImpactResponse
}

export async function getNextRecoveryAction(eventId: string): Promise<NextRecoveryActionDecision> {
  const body = await requestJson(`/api/transactions/${encodeURIComponent(eventId)}/next-action`)
  if (typeof body !== "object" || body === null) {
    throw new ApiError("Backend returned an invalid next-action response")
  }
  const decision = body as Partial<NextRecoveryActionDecision>
  const recommendations = [
    "ALREADY_RECOVERED", "STOPPED_BY_POLICY", "ESCALATE_TO_MERCHANT",
    "ESCALATE_MANDATE_RENEWAL", "VERIFY_PAYMENT_STATUS", "AWAIT_SCHEDULED_RETRY",
    "AWAIT_SCHEDULED_MANDATE_RETRY", "ESCALATE_AFTER_RETRY_EXHAUSTED",
    "CUSTOMER_RECOVERY_CHECKOUT", "RESUME_PAYMENT", "CHOOSE_ANOTHER_PAYMENT_METHOD",
    "TRY_PAYMENT_AGAIN_SECURELY", "TRY_PAYMENT_AGAIN", "PAY_MANUALLY",
    "SEND_RECOVERY_LINK", "SEND_ALT_PAYMENT_LINK", "RESERVE_PAYMENT", "COMPLETE_RESERVATION",
  ]
  const actionTypes = ["NONE", "DISPLAY_INFORMATION", "OPEN_RECOVERY_CHECKOUT",
    "RESUME_RECOVERY_CHECKOUT", "CHECK_PAYMENT_STATUS", "OPEN_TEST_MODE_RECOVERY_CHECKOUT",
    "CREATE_RESERVATION", "SIMULATE_RECONNECT"]
  if (decision.event_id !== eventId
    || typeof decision.current_outcome !== "string"
    || typeof decision.outcome !== "string"
    || typeof decision.lifecycle_state !== "string"
    || !isFiniteNumber(decision.attempts_made)
    || !isFiniteNumber(decision.max_attempts)
    || typeof decision.is_action_allowed !== "boolean"
    || !recommendations.includes(decision.recommended_action ?? "")
    || typeof decision.button_label !== "string"
    || typeof decision.title !== "string"
    || typeof decision.reason !== "string"
    || typeof decision.next_step !== "string"
    || typeof decision.risk_note !== "string"
    || !actionTypes.includes(decision.action_type ?? "")
    || (decision.secondary_action_type != null && !actionTypes.includes(decision.secondary_action_type))
    || (decision.secondary_button_label != null && typeof decision.secondary_button_label !== "string")
    || typeof decision.existing_link_status !== "string"
    || !isFiniteNumber(decision.link_age_minutes)
    || !["synthetic_benchmark", "razorpay_test_recovery", "razorpay_test_demo"].includes(decision.mode ?? "")) {
    throw new ApiError("Backend returned an invalid next-action response")
  }
  return decision as NextRecoveryActionDecision
}

export async function createTransactionRecoveryCheckout(
  eventId: string,
): Promise<TransactionRecoveryOrder> {
  const body = await requestJson(
    `/api/transactions/${encodeURIComponent(eventId)}/recovery-checkout`,
    { method: "POST" },
  )
  if (typeof body !== "object" || body === null) {
    throw new ApiError("Backend returned an invalid transaction recovery order")
  }
  const order = body as Partial<TransactionRecoveryOrder>
  const actions = [
    "RESUME_PAYMENT", "CHOOSE_ANOTHER_PAYMENT_METHOD", "TRY_PAYMENT_AGAIN_SECURELY",
    "TRY_PAYMENT_AGAIN", "PAY_MANUALLY",
  ]
  if (order.event_id !== eventId || typeof order.internal_request_id !== "string"
    || typeof order.razorpay_order_id !== "string" || !isFiniteNumber(order.amount)
    || order.currency !== "INR" || typeof order.receipt !== "string"
    || !actions.includes(order.recovery_action ?? "")
    || !["order_created", "checkout_opened", "client_reported_unverified"].includes(order.link_status ?? "")
    || order.recovery_status !== "awaiting_customer_payment" || order.mode !== "test") {
    throw new ApiError("Backend returned an invalid transaction recovery order")
  }
  return order as TransactionRecoveryOrder
}

export async function checkTransactionRecoveryStatus(
  eventId: string,
): Promise<TransactionRecoveryStatus> {
  const body = await requestJson(
    `/api/transactions/${encodeURIComponent(eventId)}/recovery-checkout/status-check`,
    { method: "POST" },
  )
  if (typeof body !== "object" || body === null) {
    throw new ApiError("Backend returned an invalid recovery status response")
  }
  const result = body as Partial<TransactionRecoveryStatus>
  if (result.event_id !== eventId
    || !["no_payment_recorded", "verification_failed", "recovered"].includes(result.status ?? "")
    || typeof result.message !== "string" || typeof result.is_recovered !== "boolean"
    || typeof result.link_status !== "string" || result.mode !== "test") {
    throw new ApiError("Backend returned an invalid recovery status response")
  }
  return result as TransactionRecoveryStatus
}

const ACTION_PATHS = {
  retry_payment: "retry",
  schedule_mandate_retry: "retry",
  verify_status: "verify-status",
  send_recovery_link: "send-recovery-link",
  send_alt_payment_link: "send-alt-payment-link",
  escalate_merchant: "escalate",
  no_action_stop: "stop",
} as const

export async function triggerTransactionAction(
  eventId: string,
  action: keyof typeof ACTION_PATHS,
  idempotencyKey: string,
): Promise<ActionResult> {
  const body = await requestJson(
    `/api/transactions/${encodeURIComponent(eventId)}/actions/${ACTION_PATHS[action]}`,
    { method: "POST", headers: { "Idempotency-Key": idempotencyKey } },
  )
  if (typeof body !== "object" || body === null) throw new ApiError("Backend returned an invalid action response")
  return body as ActionResult
}

export function isTerminalLifecycle(state: Transaction["lifecycle_state"]): boolean {
  return ["recovered", "not_recovered", "stopped", "escalated", "retry_exhausted"].includes(state)
}

export async function createRazorpayTestOrder(amount: number): Promise<RazorpayTestOrder> {
  const body = await requestJson("/api/razorpay/test/orders", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ amount, currency: "INR" }),
  })
  if (!isRazorpayTestOrder(body)) throw new ApiError("Backend returned an invalid test order response")
  return body
}

export async function getRazorpayTestConfig(): Promise<RazorpayTestConfig> {
  const body = await requestJson("/api/razorpay/test/config")
  if (typeof body !== "object" || body === null) {
    throw new ApiError("Backend returned an invalid Razorpay Test Mode configuration")
  }
  const config = body as Partial<RazorpayTestConfig>
  if (typeof config.key_id !== "string" || !config.key_id.startsWith("rzp_test_") || config.mode !== "test") {
    throw new ApiError("Backend returned an invalid Razorpay Test Mode configuration")
  }
  return config as RazorpayTestConfig
}

export async function recordRazorpayCheckoutEvent(
  event: RazorpayCheckoutEventRequest,
): Promise<RazorpayCheckoutEvent> {
  const body = await requestJson("/api/razorpay/test/checkout-events", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(event),
  })
  if (typeof body !== "object" || body === null) {
    throw new ApiError("Backend returned an invalid checkout event response")
  }
  const result = body as Partial<RazorpayCheckoutEvent>
  if (typeof result.internal_request_id !== "string"
    || typeof result.razorpay_order_id !== "string"
    || (result.razorpay_payment_id != null && typeof result.razorpay_payment_id !== "string")
    || !["checkout_success", "checkout_failed_or_dismissed"].includes(result.event_type ?? "")
    || result.status !== "client_reported_unverified"
    || typeof result.timestamp !== "string") {
    throw new ApiError("Backend returned an invalid checkout event response")
  }
  return result as RazorpayCheckoutEvent
}

function isRazorpayTestOrder(value: unknown): value is RazorpayTestOrder {
  if (typeof value !== "object" || value === null) return false
  const order = value as Partial<RazorpayTestOrder>
  return typeof order.internal_request_id === "string"
    && typeof order.razorpay_order_id === "string"
    && isFiniteNumber(order.amount)
    && order.currency === "INR"
    && typeof order.receipt === "string"
    && order.status === "created"
    && order.mode === "test"
}

export async function verifyRazorpayTestPayment(
  request: RazorpayPaymentVerificationRequest,
): Promise<RazorpayPaymentVerification> {
  const body = await requestJson("/api/razorpay/test/verify-payment", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(request),
  })
  if (typeof body !== "object" || body === null) {
    throw new ApiError("Backend returned an invalid verification response")
  }
  const result = body as Partial<RazorpayPaymentVerification>
  if (typeof result.internal_request_id !== "string"
    || typeof result.razorpay_order_id !== "string"
    || typeof result.razorpay_payment_id !== "string"
    || result.verification_status !== "verified_test_payment"
    || result.mode !== "test"
    || typeof result.verified_at !== "string") {
    throw new ApiError("Backend returned an invalid verification response")
  }
  return result as RazorpayPaymentVerification
}

export async function getRecoveryDemoCase(): Promise<RecoveryDemoCase> {
  const body = await requestJson("/api/recovery/test-mode/demo-case")
  if (typeof body !== "object" || body === null) {
    throw new ApiError("Backend returned an invalid recovery demo case")
  }
  const demo = body as Partial<RecoveryDemoCase>
  if (typeof demo.event_id !== "string" || demo.type !== "Payment Degradation"
    || demo.failure_root_cause !== "Checkout Abandoned" || !isFiniteNumber(demo.amount)
    || demo.currency !== "INR" || demo.policy_action !== "Send Recovery Link"
    || !["awaiting_customer_payment", "recovered"].includes(demo.recovery_status ?? "")
    || demo.razorpay_mode !== "test" || demo.demo_only !== true) {
    throw new ApiError("Backend returned an invalid recovery demo case")
  }
  return demo as RecoveryDemoCase
}

export async function createRecoveryTestOrder(eventId: string): Promise<RecoveryLinkedOrder> {
  const body = await requestJson(
    `/api/recovery/${encodeURIComponent(eventId)}/razorpay-test-order`,
    { method: "POST" },
  )
  if (typeof body !== "object" || body === null) {
    throw new ApiError("Backend returned an invalid linked recovery order")
  }
  const order = body as Partial<RecoveryLinkedOrder>
  if (order.event_id !== eventId || typeof order.internal_request_id !== "string"
    || typeof order.razorpay_order_id !== "string" || !isFiniteNumber(order.amount)
    || order.currency !== "INR" || typeof order.receipt !== "string"
    || order.link_status !== "order_created" || order.recovery_status !== "awaiting_customer_payment"
    || order.mode !== "test") {
    throw new ApiError("Backend returned an invalid linked recovery order")
  }
  return order as RecoveryLinkedOrder
}

export async function getRecoveryTestStatus(eventId: string): Promise<RecoveryPaymentStatus> {
  const body = await requestJson(
    `/api/recovery/${encodeURIComponent(eventId)}/razorpay-test-status`,
  )
  if (typeof body !== "object" || body === null) {
    throw new ApiError("Backend returned an invalid recovery payment status")
  }
  const status = body as Partial<RecoveryPaymentStatus>
  if (status.event_id !== eventId || typeof status.eligible !== "boolean"
    || !["awaiting_customer_payment", "recovered"].includes(status.recovery_status ?? "")
    || status.mode !== "test" || !Array.isArray(status.audit_history)) {
    throw new ApiError("Backend returned an invalid recovery payment status")
  }
  return status as RecoveryPaymentStatus
}
