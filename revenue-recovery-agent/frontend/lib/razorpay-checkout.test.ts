import assert from "node:assert/strict"
import { readFile } from "node:fs/promises"
import test from "node:test"
import {
  createSingleFlightRunner,
  loadRazorpayCheckoutScript,
  startCheckoutFlow,
  type CheckoutDisplayState,
  type RazorpayCheckoutOptions,
} from "./razorpay-checkout.ts"
import type { RazorpayCheckoutEventRequest } from "./types.ts"

const order = {
  internal_request_id: "req_test",
  razorpay_order_id: "order_test",
  amount: 50000,
  currency: "INR" as const,
  receipt: "recoverai_test",
  status: "created" as const,
  mode: "test" as const,
}

function harness(verificationResult: "valid" | "invalid" | "error" = "valid") {
  const calls: string[] = []
  const events: RazorpayCheckoutEventRequest[] = []
  const states: CheckoutDisplayState[] = []
  let options: RazorpayCheckoutOptions | undefined
  const dependencies = {
    createOrder: async () => { calls.push("create-order"); return order },
    getConfig: async () => { calls.push("get-config"); return { key_id: "rzp_test_browser_safe", mode: "test" as const } },
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
      if (verificationResult !== "valid") {
        const error = new Error("safe verification error") as Error & { status?: number }
        if (verificationResult === "invalid") error.status = 422
        throw error
      }
      return {
        internal_request_id: order.internal_request_id,
        razorpay_order_id: order.razorpay_order_id,
        razorpay_payment_id: "pay_test",
        verification_status: "verified_test_payment" as const,
        mode: "test" as const,
        verified_at: "2026-08-31T00:00:01Z",
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
    onState: (state: CheckoutDisplayState) => states.push(state),
  }
  return { calls, events, states, dependencies, getOptions: () => options }
}

test("checkout page clearly labels Test Mode and server-side verification boundary", async () => {
  const source = await readFile(new URL("../app/razorpay-test/page.tsx", import.meta.url), "utf8")
  assert.match(source, /Razorpay Test Mode — Checkout Demo/)
  assert.match(source, /Failure recovery is available from each eligible transaction’s detail drawer/i)
  assert.doesNotMatch(source.toLowerCase(), /key_secret|razorpay_key_secret/)
  assert.doesNotMatch(source, /razorpay_signature/)
})

test("order is created before Checkout opens", async () => {
  const context = harness()
  await startCheckoutFlow(500, context.dependencies)
  assert.ok(context.calls.indexOf("create-order") < context.calls.indexOf("open"))
  assert.deepEqual(context.calls.slice(0, 4), ["create-order", "get-config", "load-script", "create-checkout"])
})

test("success callback records intake before verification and shows signature verified", async () => {
  const context = harness()
  await startCheckoutFlow(500, context.dependencies)
  context.getOptions()?.handler({
    razorpay_order_id: "order_test",
    razorpay_payment_id: "pay_test",
    razorpay_signature: "callback_signature",
  })
  await new Promise(resolve => setTimeout(resolve, 0))
  assert.equal(context.events[0]?.event_type, "checkout_success")
  assert.equal(context.events[0]?.razorpay_payment_id, "pay_test")
  assert.ok(context.calls.indexOf("record-event") < context.calls.indexOf("verify-payment"))
  assert.match(context.states.at(-1)?.message ?? "", /signature verified/i)
  assert.match(context.states.at(-1)?.message ?? "", /available on the dashboard/i)
  assert.equal(context.states.at(-1)?.status, "verified_test_payment")
})

test("invalid verification displays safe no-recovery-changed wording", async () => {
  const context = harness("invalid")
  await startCheckoutFlow(500, context.dependencies)
  context.getOptions()?.handler({
    razorpay_order_id: "order_test",
    razorpay_payment_id: "pay_test",
    razorpay_signature: "callback_signature",
  })
  await new Promise(resolve => setTimeout(resolve, 0))
  assert.match(context.states.at(-1)?.message ?? "", /could not be verified/i)
  assert.match(context.states.at(-1)?.message ?? "", /No recovery case was changed/i)
  assert.equal(context.states.at(-1)?.status, "verification_failed")
})

test("verification error remains safely client-reported and unverified", async () => {
  const context = harness("error")
  await startCheckoutFlow(500, context.dependencies)
  context.getOptions()?.handler({
    razorpay_order_id: "order_test",
    razorpay_payment_id: "pay_test",
    razorpay_signature: "callback_signature",
  })
  await new Promise(resolve => setTimeout(resolve, 0))
  assert.match(context.states.at(-1)?.message ?? "", /verification could not be completed/i)
  assert.match(context.states.at(-1)?.message ?? "", /No recovery case was changed/i)
  assert.equal(context.states.at(-1)?.status, "client_reported_unverified")
})

test("dismissal records a non-success unverified event", async () => {
  const context = harness()
  await startCheckoutFlow(500, context.dependencies)
  context.getOptions()?.modal.ondismiss()
  await new Promise(resolve => setTimeout(resolve, 0))
  assert.deepEqual(context.events[0], {
    internal_request_id: "req_test",
    razorpay_order_id: "order_test",
    event_type: "checkout_failed_or_dismissed",
    reason: "payment_cancelled",
    customer_ref: "test.customer@example.invalid",
    error_code: undefined,
    error_description: undefined,
    error_source: undefined,
    error_step: undefined,
    latency_ms: 0,
    simulated_connection: false,
  })
})

test("payment.failed forwards the complete Razorpay error diagnostics", async () => {
  const context = harness()
  let failureHandler: ((response: { error?: Record<string, string> }) => void) | undefined
  context.dependencies.createCheckout = received => {
    return { open() {}, on: (_event, handler) => { failureHandler = handler } }
  }
  await startCheckoutFlow(500, context.dependencies)
  failureHandler?.({ error: { reason: "insufficient_fund", code: "BAD_REQUEST_ERROR",
    description: "Insufficient funds", source: "bank", step: "payment_authentication" } })
  await new Promise(resolve => setTimeout(resolve, 0))
  assert.equal(context.events[0]?.reason, "insufficient_fund")
  assert.equal(context.events[0]?.error_code, "BAD_REQUEST_ERROR")
  assert.equal(context.events[0]?.error_step, "payment_authentication")
})

test("selected demo preset reason overrides the generic Razorpay failure reason", async () => {
  const context = harness()
  let failureHandler: ((response: { error?: Record<string, string> }) => void) | undefined
  context.dependencies.createCheckout = received => {
    return { open() {}, on: (_event, handler) => { failureHandler = handler } }
  }
  await startCheckoutFlow(500, {
    ...context.dependencies,
    failureReason: "authentication_failed",
  })
  failureHandler?.({ error: { reason: "payment_failed" } })
  await new Promise(resolve => setTimeout(resolve, 0))
  assert.equal(context.events[0]?.reason, "authentication_failed")
})

test("script-load failure is propagated with a visible-safe message", async () => {
  const script = { src: "", async: false, onload: null, onerror: null, remove() {} } as unknown as HTMLScriptElement
  const fakeDocument = {
    querySelector: () => null,
    createElement: () => script,
    body: { appendChild: () => { queueMicrotask(() => script.onerror?.(new Event("error"))) } },
  } as unknown as Document
  await assert.rejects(loadRazorpayCheckoutScript(fakeDocument), /could not be loaded/i)
})

test("single-flight runner blocks a duplicate checkout click", async () => {
  const run = createSingleFlightRunner()
  let releases!: () => void
  const waiting = new Promise<void>(resolve => { releases = resolve })
  let starts = 0
  const first = run(async () => { starts += 1; await waiting })
  const second = await run(async () => { starts += 1 })
  releases()
  assert.equal(await first, true)
  assert.equal(second, false)
  assert.equal(starts, 1)
})

test("checkout feature does not alter existing dashboard source", async () => {
  const source = await readFile(new URL("../app/page.tsx", import.meta.url), "utf8")
  assert.match(source, /getBatchSummary/)
  assert.doesNotMatch(source, /razorpay/i)
})

test("demo aid documents the four failure scenarios without pretending to fill hosted card fields", async () => {
  const presets = await readFile(new URL("./demo-card-presets.ts", import.meta.url), "utf8")
  const page = await readFile(new URL("../app/razorpay-test/page.tsx", import.meta.url), "utf8")
  for (const card of ["4100280000020007", "4100280000080001", "4100280000000009", "4100280000090000"]) {
    assert.match(presets, new RegExp(card))
  }
  assert.match(page, /cannot be safely auto-filled/i)
  assert.match(page, /Payment Failure Simulator \(Razorpay Sandbox\)/)
  assert.doesNotMatch(page, /Simulated connection \(fallback demo control\)/)
  assert.doesNotMatch(presets, /label: "Success"/)
})

test("agent trace viewer includes the five explicit reasoning stages", async () => {
  const source = await readFile(new URL("../components/transactions/agent-trace-viewer.tsx", import.meta.url), "utf8")
  for (const stage of ["Observe", "Classify", "Decide", "Guardrail Check", "Act"]) {
    assert.match(source, new RegExp(stage))
  }
})
