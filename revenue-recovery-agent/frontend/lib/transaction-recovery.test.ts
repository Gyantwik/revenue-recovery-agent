import assert from "node:assert/strict"
import { readFile } from "node:fs/promises"
import test from "node:test"
import { startTransactionRecoveryFlow, type TransactionRecoveryFlowState } from "./transaction-recovery.ts"
import type { RazorpayCheckoutEventRequest, Transaction } from "./types.ts"

const eventId = "TXN10044"
const order = {
  event_id: eventId,
  internal_request_id: "req_transaction_recovery",
  razorpay_order_id: "order_transaction_recovery",
  amount: 95000,
  currency: "INR" as const,
  receipt: "recoverai_transaction_recovery",
  recovery_action: "RESUME_PAYMENT" as const,
  recovery_status: "awaiting_customer_payment" as const,
  mode: "test" as const,
}

function recoveredTransaction(): Transaction {
  return {
    event_id: eventId, case_type: "payment_degradation", amount: 950, currency: "INR",
    timestamp: "2026-01-01T00:00:00Z", is_at_risk: true, risk_amount: 950,
    root_cause: "checkout_abandoned", classification_confidence: 0.95, signals_used: [],
    policy_rule_matched: "Checkout abandoned", action_taken: "send_recovery_link",
    attempt_number: 1, max_attempts_allowed: 1, outcome: "recovered", recovered_amount: 950,
    stop_or_escalate_reason: null, lifecycle_state: "recovered", next_eligible_action_at: null,
    recovery_window_expires_at: null, history: [],
  }
}

function harness(result: "valid" | "invalid" = "valid") {
  const calls: string[] = []
  const createArgs: unknown[][] = []
  const events: RazorpayCheckoutEventRequest[] = []
  const states: TransactionRecoveryFlowState[] = []
  const recovered: Transaction[] = []
  let options: ConstructorParameters<NonNullable<Window["Razorpay"]>>[0] | undefined
  const dependencies = {
    createOrder: async (...args: [string]) => { calls.push("create-order"); createArgs.push(args); return order },
    getConfig: async () => ({ key_id: "rzp_test_replace_with_test_key_id", mode: "test" as const }),
    recordEvent: async (event: RazorpayCheckoutEventRequest) => {
      calls.push("record-event"); events.push(event)
      return { ...event, status: "client_reported_unverified" as const, timestamp: "2026-01-01T00:00:00Z" }
    },
    verifyPayment: async () => {
      calls.push("verify")
      if (result === "invalid") throw Object.assign(new Error("invalid"), { status: 422 })
      return {
        internal_request_id: order.internal_request_id, razorpay_order_id: order.razorpay_order_id,
        razorpay_payment_id: "pay_transaction_recovery", verification_status: "verified_test_payment" as const,
        mode: "test" as const, verified_at: "2026-01-01T00:00:01Z", recovery_event_id: eventId,
        recovery_status: "recovered" as const, link_status: "recovered_by_verified_test_payment" as const,
      }
    },
    getTransaction: async () => { calls.push("get-transaction"); return recoveredTransaction() },
    loadScript: async () => { calls.push("load-script") },
    createCheckout: (received: ConstructorParameters<NonNullable<Window["Razorpay"]>>[0]) => {
      calls.push("create-checkout"); options = received
      return { open: () => calls.push("open"), on: () => calls.push("bind-failure") }
    },
    onState: (state: TransactionRecoveryFlowState) => states.push(state),
    onRecovered: (transaction: Transaction) => recovered.push(transaction),
  }
  return { calls, createArgs, events, states, recovered, dependencies, getOptions: () => options }
}

test("linked order is requested by event ID only before Checkout opens", async () => {
  const context = harness()
  await startTransactionRecoveryFlow(eventId, context.dependencies)
  assert.deepEqual(context.createArgs, [[eventId]])
  assert.ok(context.calls.indexOf("create-order") < context.calls.indexOf("open"))
  assert.equal(context.getOptions()?.amount, 95000)
})

test("checkout intake precedes verification and verified state refreshes the transaction", async () => {
  const context = harness()
  await startTransactionRecoveryFlow(eventId, context.dependencies)
  context.getOptions()?.handler({
    razorpay_order_id: order.razorpay_order_id,
    razorpay_payment_id: "pay_transaction_recovery",
    razorpay_signature: "callback_signature_fixture",
  })
  await new Promise(resolve => setTimeout(resolve, 0))
  assert.ok(context.calls.indexOf("record-event") < context.calls.indexOf("verify"))
  assert.ok(context.calls.indexOf("verify") < context.calls.indexOf("get-transaction"))
  assert.equal(context.recovered.length, 1)
  assert.match(context.states.at(-1)?.message ?? "", /now marked recovered/i)
})

test("dismissal and invalid verification never report recovery", async () => {
  const dismissed = harness()
  await startTransactionRecoveryFlow(eventId, dismissed.dependencies)
  dismissed.getOptions()?.modal.ondismiss()
  await new Promise(resolve => setTimeout(resolve, 0))
  assert.equal(dismissed.recovered.length, 0)
  assert.match(dismissed.states.at(-1)?.message ?? "", /remains unrecovered/i)

  const invalid = harness("invalid")
  await startTransactionRecoveryFlow(eventId, invalid.dependencies)
  invalid.getOptions()?.handler({
    razorpay_order_id: order.razorpay_order_id,
    razorpay_payment_id: "pay_transaction_recovery",
    razorpay_signature: "invalid_callback_fixture",
  })
  await new Promise(resolve => setTimeout(resolve, 0))
  assert.equal(invalid.recovered.length, 0)
  assert.match(invalid.states.at(-1)?.message ?? "", /was not changed/i)
})

test("transaction modal uses backend eligibility and contains no generic recovery shortcut", async () => {
  const source = await readFile(new URL("../components/transactions/transaction-detail-dialog.tsx", import.meta.url), "utf8")
  assert.match(source, /createTransactionRecoveryCheckout/)
  assert.match(source, /Razorpay Test Mode — no real money is charged/)
  assert.match(source, /router\.refresh\(\)/)
  assert.doesNotMatch(source, /Recover Amount|Mark Recovered|Force Payment|Auto Debit/)
})
