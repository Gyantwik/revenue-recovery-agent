import assert from "node:assert/strict"
import test from "node:test"
import { ApiError, getBatchSummary, getTransaction, getTransactions } from "./api.ts"

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
