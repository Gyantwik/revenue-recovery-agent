import type { ActionResult, BatchSummary, Transaction } from "@/lib/types"

const API_BASE_URL = (process.env.NEXT_PUBLIC_API_BASE_URL ?? "http://localhost:8080")
  .replace(/\/$/, "")

interface BackendErrorBody {
  error?: string
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
  let response: Response
  try {
    response = await fetch(`${API_BASE_URL}${path}`, { cache: "no-store", ...init })
  } catch {
    throw new ApiError("Unable to connect to the backend")
  }

  if (!response.ok) {
    let message = `Backend request failed with status ${response.status}`
    try {
      const body = (await response.json()) as BackendErrorBody
      if (body.reason) message = body.reason
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
