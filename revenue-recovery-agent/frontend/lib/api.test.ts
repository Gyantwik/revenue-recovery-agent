import assert from "node:assert/strict"
import test from "node:test"
import { ApiError, getBatchSummary, getNextRecoveryAction, getTransaction, getTransactions, isTerminalLifecycle, triggerTransactionAction } from "./api.ts"

const originalFetch = globalThis.fetch

test.afterEach(() => {
  globalThis.fetch = originalFetch
})

test("network errors reject instead of returning mock transactions", async () => {
  globalThis.fetch = async () => { throw new Error("offline") }
  await assert.rejects(getTransactions(), /Unable to connect to the backend/)
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
