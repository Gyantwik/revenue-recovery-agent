import assert from "node:assert/strict"
import test from "node:test"
import { ApiError, generateAiRecoveryMessage, getAiAnalysis, getBatchSummary, getNextRecoveryAction, getTransaction, getTransactions, isTerminalLifecycle, triggerTransactionAction } from "./api.ts"

const originalFetch = globalThis.fetch

test.afterEach(() => {
  globalThis.fetch = originalFetch
})

test("network errors reject instead of returning mock transactions", async () => {
  globalThis.fetch = async () => { throw new Error("offline") }
  await assert.rejects(getTransactions(), /Unable to connect to the backend/)
})

test("aborted requests show a retryable timeout error", async () => {
  globalThis.fetch = async () => { throw new DOMException("Aborted", "AbortError") }

  await assert.rejects(
    getTransactions(),
    /Backend request timed out\. Please try again\./,
  )
})

test("malformed summary responses are rejected", async () => {
  globalThis.fetch = async () => new Response(JSON.stringify({ total_cases: 65 }), { status: 200 })
  await assert.rejects(getBatchSummary(), /invalid batch summary response/)
})

test("malformed JSON is rejected", async () => {
  globalThis.fetch = async () => new Response("{", { status: 200 })
  await assert.rejects(getTransactions(), /malformed JSON/)
})

test("transactions missing required audit fields are rejected", async () => {
  globalThis.fetch = async () => new Response(
    JSON.stringify([{ event_id: "TXN10013", signals_used: [] }]),
    { status: 200 },
  )
  await assert.rejects(getTransactions(), /invalid transactions response/)
})

test("transaction detail preserves a backend 404", async () => {
  globalThis.fetch = async () => new Response(
    JSON.stringify({ error: "Transaction not found", eventId: "missing" }),
    { status: 404, headers: { "Content-Type": "application/json" } },
  )

  await assert.rejects(
    getTransaction("missing"),
    (error: unknown) => error instanceof ApiError
      && error.status === 404
      && error.message === "Transaction not found",
  )
})

test("terminal lifecycle helper disables later actions", () => {
  assert.equal(isTerminalLifecycle("recovered"), true)
  assert.equal(isTerminalLifecycle("retry_exhausted"), true)
  assert.equal(isTerminalLifecycle("retry_scheduled"), false)
})

test("blocked action exposes backend policy reason", async () => {
  globalThis.fetch = async () => new Response(JSON.stringify({
    event_id: "TXN-PENDING",
    current_state: "verifying_payment",
    requested_action: "retry_payment",
    reason: "Action blocked by deterministic policy; allowed action is verify_status",
  }), { status: 409, headers: { "Content-Type": "application/json" } })

  await assert.rejects(
    triggerTransactionAction("TXN-PENDING", "retry_payment", "duplicate"),
    /allowed action is verify_status/,
  )
})

test("next-action client loads the backend-owned decision without posting an action", async () => {
  let requestUrl = ""
  let requestMethod = ""
  globalThis.fetch = async (input, init) => {
    requestUrl = String(input)
    requestMethod = init?.method ?? "GET"
    return new Response(JSON.stringify({
      event_id: "TXN10059",
      current_outcome: "not_recovered",
      outcome: "not_recovered",
      lifecycle_state: "retry_exhausted",
      attempts_made: 2,
      max_attempts: 2,
      is_action_allowed: true,
      recommended_action: "ESCALATE_AFTER_RETRY_EXHAUSTED",
      button_label: "Review Mandate / Escalate",
      title: "Automatic retries are exhausted",
      reason: "Further automatic retries are blocked.",
      next_step: "Review the mandate.",
      risk_note: "Do not initiate another automatic debit attempt.",
      action_type: "DISPLAY_INFORMATION",
      secondary_action_type: null,
      secondary_button_label: null,
      existing_link_status: "none",
      link_age_minutes: 0,
      mode: "synthetic_benchmark",
    }), { status: 200 })
  }

  const result = await getNextRecoveryAction("TXN10059")
  assert.equal(result.recommended_action, "ESCALATE_AFTER_RETRY_EXHAUSTED")
  assert.match(requestUrl, /\/api\/transactions\/TXN10059\/next-action$/)
  assert.equal(requestMethod, "GET")
})

test("malformed next-action responses are rejected", async () => {
  globalThis.fetch = async () => new Response(JSON.stringify({
    event_id: "TXN10059",
    action_type: "OPEN_TEST_MODE_RECOVERY_CHECKOUT",
  }), { status: 200 })
  await assert.rejects(getNextRecoveryAction("TXN10059"), /invalid next-action response/)
})

test("Gemini analysis validates and preserves the ordered five-stage trace", async () => {
  globalThis.fetch = async () => new Response(JSON.stringify({
    event_id: "TXN10013",
    predicted_root_cause: "bank_temp_error",
    confidence: 0.94,
    recommended_action: "retry_payment",
    ai_explanation: "The issuer timed out.",
    optimal_retry_timing: "Wait 15 minutes.",
    risk_assessment: "Low duplicate debit risk.",
    analysis_source: "gemini",
    reasoning_trace: [
      { stage: "OBSERVE", thought: "Timeout observed.", conclusion: "Issuer did not respond." },
      { stage: "CLASSIFY", thought: "Matched bank timeout.", conclusion: "bank_temp_error" },
      { stage: "DECIDE", thought: "Applied retry policy.", conclusion: "retry_payment" },
      { stage: "GUARDRAIL_CHECK", thought: "Checked stop rules.", conclusion: "Retry is allowed." },
      { stage: "ACT", thought: "Prepared recommendation.", conclusion: "Wait before retry." },
    ],
  }), { status: 200 })

  const result = await getAiAnalysis("TXN10013")
  assert.equal(result.reasoning_trace.length, 5)
  assert.equal(result.reasoning_trace[3].stage, "GUARDRAIL_CHECK")
})

test("AI message client posts the selected channel and customer name", async () => {
  let sentBody = ""
  globalThis.fetch = async (_input, init) => {
    sentBody = String(init?.body)
    return new Response(JSON.stringify({
      event_id: "TXN10043",
      channel: "whatsapp",
      subject: "Complete your payment",
      message: "Hi Ravi, continue securely.",
      suggested_cta: "Resume Payment Securely",
    }), { status: 200 })
  }

  await generateAiRecoveryMessage("TXN10043", "WHATSAPP", "Ravi K.")
  assert.deepEqual(JSON.parse(sentBody), { channel: "WHATSAPP", customerName: "Ravi K." })
})
