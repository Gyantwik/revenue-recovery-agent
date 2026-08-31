import assert from "node:assert/strict"
import { readFileSync } from "node:fs"
import test from "node:test"
import { formatRecoveryStatus, getNextActionInteraction, getRecoveryDemoHref } from "./next-recovery-action.ts"
import type { NextRecoveryActionDecision } from "./types.ts"

function decision(overrides: Partial<NextRecoveryActionDecision>): NextRecoveryActionDecision {
  return {
    event_id: "TXN10059",
    current_outcome: "not_recovered",
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
    mode: "synthetic_benchmark",
    ...overrides,
  }
}

test("retry-exhausted and escalation decisions are informational, never checkout", () => {
  const exhausted = decision({})
  assert.equal(getNextActionInteraction(exhausted), "information")
  assert.equal(getRecoveryDemoHref(exhausted), null)
  assert.equal(formatRecoveryStatus(exhausted), "Not recovered — retry limit reached")
})

test("cancelled, PIN failure, and recovered decisions remain disabled", () => {
  for (const item of [
    decision({ recommended_action: "STOPPED_BY_POLICY", button_label: "No Further Action Allowed", is_action_allowed: false, action_type: "NONE" }),
    decision({ event_id: "PIN", recommended_action: "STOPPED_BY_POLICY", button_label: "No Further Action Allowed", is_action_allowed: false, action_type: "NONE" }),
    decision({ recommended_action: "ALREADY_RECOVERED", button_label: "Already Recovered", current_outcome: "recovered", lifecycle_state: "recovered", is_action_allowed: false, action_type: "NONE" }),
  ]) {
    assert.equal(getNextActionInteraction(item), "disabled")
    assert.equal(getRecoveryDemoHref(item), null)
    assert.doesNotMatch(item.button_label, /Recover Amount|Mark Recovered/i)
  }
})

test("payment status verification and scheduled retries cannot open Checkout", () => {
  const verification = decision({ recommended_action: "VERIFY_PAYMENT_STATUS", button_label: "Verify Payment Status" })
  const retry = decision({ recommended_action: "AWAIT_SCHEDULED_RETRY", button_label: "Retry Scheduled", is_action_allowed: false, action_type: "NONE" })
  assert.equal(getNextActionInteraction(verification), "information")
  assert.equal(getRecoveryDemoHref(verification), null)
  assert.equal(getNextActionInteraction(retry), "disabled")
})

test("only the dedicated Test Mode demo creates a safe navigation target without amount or currency", () => {
  const demo = decision({
    event_id: "TXN_DEMO_RECOVERY_001",
    lifecycle_state: "awaiting_customer_payment",
    attempts_made: 0,
    max_attempts: 1,
    recommended_action: "SEND_RECOVERY_LINK",
    button_label: "Open Test Mode Recovery Checkout",
    action_type: "OPEN_TEST_MODE_RECOVERY_CHECKOUT",
    mode: "razorpay_test_demo",
  })
  const href = getRecoveryDemoHref(demo)
  assert.equal(getNextActionInteraction(demo), "test_mode_checkout")
  assert.equal(href, "/razorpay-test?eventId=TXN_DEMO_RECOVERY_001")
  assert.doesNotMatch(href ?? "", /amount|currency|signature|secret/i)

  const forgedBenchmark = { ...demo, event_id: "TXN10043", mode: "synthetic_benchmark" as const }
  assert.equal(getNextActionInteraction(forgedBenchmark), "disabled")
})

test("transaction modal loads the backend decision card and removes the old generic execution shortcut", () => {
  const source = readFileSync(
    new URL("../components/transactions/transaction-detail-dialog.tsx", import.meta.url),
    "utf8",
  )
  assert.match(source, /getNextRecoveryAction\(transaction\.event_id\)/)
  assert.match(source, /Next Recovery Decision/)
  assert.match(source, /Current Recovery Status/)
  assert.match(source, /Policy Guardrail/)
  assert.doesNotMatch(source, /triggerTransactionAction|Run test-mode|Recover Amount|Mark Recovered/)
})

test("dedicated demo page reuses the existing verified recovery flow under the backend decision", () => {
  const source = readFileSync(new URL("../app/razorpay-test/page.tsx", import.meta.url), "utf8")
  assert.match(source, /getNextRecoveryAction\(demo\.event_id\)/)
  assert.match(source, /startRecoveryDemoFlow\(demoCase\.event_id/)
  assert.match(source, /demoInteraction !== "test_mode_checkout"/)
  assert.doesNotMatch(source, /RAZORPAY_KEY_SECRET|razorpay_signature\s*[}:]/)
})
