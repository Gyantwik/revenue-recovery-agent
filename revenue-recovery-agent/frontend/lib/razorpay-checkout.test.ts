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

function harness() {
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

test("checkout page clearly labels Test Mode and unverified callback status", async () => {
  const source = await readFile(new URL("../app/razorpay-test/page.tsx", import.meta.url), "utf8")
  assert.match(source, /Razorpay Test Mode — Checkout Demo/)
  assert.match(source, /Server verification is pending|not treated as verified payment/)
  assert.doesNotMatch(source.toLowerCase(), /key_secret|razorpay_key_secret/)
})

test("order is created before Checkout opens", async () => {
  const context = harness()
  await startCheckoutFlow(500, context.dependencies)
  assert.ok(context.calls.indexOf("create-order") < context.calls.indexOf("open"))
  assert.deepEqual(context.calls.slice(0, 4), ["create-order", "get-config", "load-script", "create-checkout"])
})

test("success callback records an unverified event and shows verification pending", async () => {
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
  assert.match(context.states.at(-1)?.message ?? "", /verification is pending/i)
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
    reason: "user_cancelled_or_test_failure",
  })
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
