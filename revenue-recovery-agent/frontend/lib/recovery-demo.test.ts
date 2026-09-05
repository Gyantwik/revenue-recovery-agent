import assert from "node:assert/strict"
import { readFile } from "node:fs/promises"
import test from "node:test"
import { createSingleFlightRunner, type RazorpayCheckoutOptions } from "./razorpay-checkout.ts"
import { startRecoveryDemoFlow, type RecoveryDemoFlowState } from "./recovery-demo.ts"
import type { RazorpayCheckoutEventRequest } from "./types.ts"

const eventId = "TXN_DEMO_RECOVERY_001"
const order = {
  event_id: eventId,
  internal_request_id: "req_recovery",
  razorpay_order_id: "order_recovery",
  amount: 50000,
  currency: "INR" as const,
  receipt: "recoverai_recovery",
  link_status: "order_created" as const,
  recovery_status: "awaiting_customer_payment" as const,
  mode: "test" as const,
}

function harness(result: "valid" | "invalid" | "error" = "valid") {
  const calls: string[] = []
  const createArguments: unknown[][] = []
  const events: RazorpayCheckoutEventRequest[] = []
  const states: RecoveryDemoFlowState[] = []
  let options: RazorpayCheckoutOptions | undefined
  const dependencies = {
    createLinkedOrder: async (...args: [string]) => {
      calls.push("create-linked-order")
      createArguments.push(args)
      return order
    },
    getConfig: async () => {
      calls.push("get-config")
      return { key_id: "rzp_test_recovery_safe", mode: "test" as const }
    },
    recordEvent: async (event: RazorpayCheckoutEventRequest) => {
      calls.push("record-event")
      events.push(event)
      return {
        internal_request_id: event.internal_request_id,
        razorpay_order_id: event.razorpay_order_id,
        razorpay_payment_id: event.razorpay_payment_id,
        event_type: event.event_type,
        status: "client_reported_unverified" as const,
        timestamp: "2026-08-31T00:00:00Z",
      }
    },
    verifyPayment: async () => {
      calls.push("verify-payment")
      if (result !== "valid") {
        const error = new Error("safe verification error") as Error & { status?: number }
        if (result === "invalid") error.status = 422
        throw error
      }
      return {
        internal_request_id: order.internal_request_id,
        razorpay_order_id: order.razorpay_order_id,
        razorpay_payment_id: "pay_recovery",
        verification_status: "verified_test_payment" as const,
        mode: "test" as const,
        verified_at: "2026-08-31T00:00:01Z",
        recovery_event_id: eventId,
        recovery_status: "recovered" as const,
        link_status: "recovered_by_verified_test_payment" as const,
      }
    },
    getStatus: async () => {
      calls.push("get-status")
      return {
        event_id: eventId,
        eligible: false,
        recovery_status: "recovered" as const,
        link_status: "recovered_by_verified_test_payment" as const,
        mode: "test" as const,
        recovered_at: "2026-08-31T00:00:01Z",
        audit_history: [],
      }
    },
    loadScript: async () => { calls.push("load-script") },
    createCheckout: (received: RazorpayCheckoutOptions) => {
      calls.push("create-checkout")
      options = received
      return {
        on: () => { calls.push("bind-failure") },
        open: () => { calls.push("open") },
      }
    },
    onState: (state: RecoveryDemoFlowState) => states.push(state),
  }
  return { calls, createArguments, events, states, dependencies, getOptions: () => options }
}

test("checkout page keeps recovery inside the transaction drawer", async () => {
  const recoveryPage = await readFile(new URL("../app/razorpay-test/page.tsx", import.meta.url), "utf8")
  const dashboard = await readFile(new URL("../app/page.tsx", import.meta.url), "utf8")
  const drawer = await readFile(new URL("../components/transactions/transaction-detail-dialog.tsx", import.meta.url), "utf8")
  assert.doesNotMatch(recoveryPage, /Live Test Mode Recovery Demo/)
  assert.doesNotMatch(recoveryPage, /TXN_DEMO_RECOVERY_001/)
  assert.match(recoveryPage, /Payment Failure Simulator \(Razorpay Sandbox\)/)
  assert.match(recoveryPage, /No real money is charged/)
  assert.match(drawer, /Next Recovery Decision/)
  assert.match(dashboard, /Synthetic Benchmark — 80 seeded cases/)
  assert.doesNotMatch(recoveryPage.toLowerCase(), /key_secret|razorpay_key_secret/)
  assert.doesNotMatch(recoveryPage, /razorpay_signature/)
})

test("linked order is created without a client amount before Checkout opens", async () => {
  const context = harness()
  await startRecoveryDemoFlow(eventId, context.dependencies)
  assert.deepEqual(context.createArguments, [[eventId]])
  assert.ok(context.calls.indexOf("create-linked-order") < context.calls.indexOf("create-checkout"))
  assert.equal(context.getOptions()?.amount, 50000)
})

test("intake precedes verification and recovered wording appears only after mapped status", async () => {
  const context = harness()
  await startRecoveryDemoFlow(eventId, context.dependencies)
  context.getOptions()?.handler({
    razorpay_order_id: order.razorpay_order_id,
    razorpay_payment_id: "pay_recovery",
    razorpay_signature: "callback_signature",
  })
  await new Promise(resolve => setTimeout(resolve, 0))
  assert.ok(context.calls.indexOf("record-event") < context.calls.indexOf("verify-payment"))
  assert.ok(context.calls.indexOf("verify-payment") < context.calls.indexOf("get-status"))
  assert.match(context.states.at(-1)?.message ?? "", /case recovered/i)
  assert.equal(context.states.at(-1)?.status, "recovered_by_verified_test_payment")
  assert.equal(context.events[0]?.event_type, "checkout_success")
})

test("invalid verification and server error never claim recovery", async () => {
  for (const result of ["invalid", "error"] as const) {
    const context = harness(result)
    await startRecoveryDemoFlow(eventId, context.dependencies)
    context.getOptions()?.handler({
      razorpay_order_id: order.razorpay_order_id,
      razorpay_payment_id: "pay_recovery",
      razorpay_signature: "callback_signature",
    })
    await new Promise(resolve => setTimeout(resolve, 0))
    assert.match(context.states.at(-1)?.message ?? "", /No recovery case changed/i)
    assert.doesNotMatch(context.states.at(-1)?.message ?? "", /case recovered/i)
  }
})

test("dismissal leaves recovery unchanged", async () => {
  const context = harness()
  await startRecoveryDemoFlow(eventId, context.dependencies)
  context.getOptions()?.modal.ondismiss()
  await new Promise(resolve => setTimeout(resolve, 0))
  assert.equal(context.events[0]?.event_type, "checkout_failed_or_dismissed")
  assert.match(context.states.at(-1)?.message ?? "", /No recovery case changed/i)
})

test("single-flight protection blocks duplicate recovery checkout creation", async () => {
  const runner = createSingleFlightRunner()
  let release: (() => void) | undefined
  const pending = new Promise<void>(resolve => { release = resolve })
  const first = runner(() => pending)
  const second = await runner(async () => {})
  assert.equal(second, false)
  release?.()
  assert.equal(await first, true)
})
