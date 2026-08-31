import assert from "node:assert/strict"
import test from "node:test"
import {
  formatAttemptCount,
  formatCurrency,
  formatRecoveryRate,
  isAttemptCountValid,
} from "./formatters.ts"
import { filterTransactions, type FilterableTransaction } from "./transaction-filter.ts"

const transactions: FilterableTransaction[] = [
  {
    event_id: "TXN10013",
    case_type: "payment_degradation",
    root_cause: "bank_temp_error",
    outcome: "recovered",
    policy_rule_matched: "Bank temp error → Retry, max 2, 30 min window",
    signals_used: ["gateway_response_code: BANK_TIMEOUT"],
  },
  {
    event_id: "TXN10054",
    case_type: "mandate_renewal",
    root_cause: "unknown",
    outcome: "escalated",
    policy_rule_matched: "Low confidence -> Manual Review",
    signals_used: ["UNKNOWN_MANDATE_ERR_88"],
  },
]

const allFilters = {
  searchTerm: "",
  caseType: "all",
  rootCause: "all",
  outcome: "all",
}

test("formats a backend recovery rate as a percentage", () => {
  assert.equal(formatRecoveryRate(0.454), "45.4%")
})

test("formats INR using the en-IN currency contract", () => {
  assert.equal(formatCurrency(191209), "₹1,91,209")
})

test("formats stopped and escalated attempts as 0/0", () => {
  assert.equal(formatAttemptCount(0, 0), "0/0")
})

test("rejects retry attempt counts above the maximum", () => {
  assert.equal(isAttemptCountValid(1, 2), true)
  assert.equal(isAttemptCountValid(2, 2), true)
  assert.equal(isAttemptCountValid(3, 2), false)
})

test("searches event ID, policy rule, and signals", () => {
  assert.deepEqual(filterTransactions(transactions, { ...allFilters, searchTerm: "txn10013" }), [transactions[0]])
  assert.deepEqual(filterTransactions(transactions, { ...allFilters, searchTerm: "manual review" }), [transactions[1]])
  assert.deepEqual(filterTransactions(transactions, { ...allFilters, searchTerm: "bank_timeout" }), [transactions[0]])
})

test("combines case type, root cause, and outcome filters", () => {
  assert.deepEqual(filterTransactions(transactions, {
    searchTerm: "unknown_mandate",
    caseType: "mandate_renewal",
    rootCause: "unknown",
    outcome: "escalated",
  }), [transactions[1]])
})

test("returns an empty result for unmatched filters", () => {
  assert.deepEqual(filterTransactions(transactions, { ...allFilters, searchTerm: "does-not-exist" }), [])
})
