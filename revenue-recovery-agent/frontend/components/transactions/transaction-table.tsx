"use client"

import React, { useEffect, useState, useMemo } from "react"
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table"
import { Button } from "@/components/ui/button"
import { StatusBadge, CauseBadge } from "@/components/status-badge"
import type { Transaction } from "@/lib/types"
import { ACTION_LABELS, CASE_TYPE_LABELS } from "@/lib/labels"
import { formatAttemptCount, formatCurrency } from "@/lib/utils"
import { filterTransactions } from "@/lib/transaction-filter"
import { TransactionDetailDialog } from "./transaction-detail-dialog"
import { Search, ChevronRight } from "lucide-react"

interface TransactionTableProps {
  initialTransactions: Transaction[]
  defaultOutcomeFilter?: string
}

export function TransactionTable({
  initialTransactions,
  defaultOutcomeFilter = "all",
}: TransactionTableProps) {
  const [searchTerm, setSearchTerm] = useState("")
  const [selectedCause, setSelectedCause] = useState<string>("all")
  const [selectedOutcome, setSelectedOutcome] = useState<string>(defaultOutcomeFilter)
  const [selectedCaseType, setSelectedCaseType] = useState<string>("all")
  const [selectedSource, setSelectedSource] = useState<string>("all")
  const [selectedTxn, setSelectedTxn] = useState<Transaction | null>(null)
  const [dialogOpen, setDialogOpen] = useState(false)
  const [transactions, setTransactions] = useState(initialTransactions)

  useEffect(() => setTransactions(initialTransactions), [initialTransactions])

  const filteredTransactions = useMemo(() => {
    return filterTransactions(transactions, {
      searchTerm,
      caseType: selectedCaseType,
      rootCause: selectedCause,
      outcome: selectedOutcome,
      source: selectedSource,
    })
  }, [transactions, searchTerm, selectedCause, selectedOutcome, selectedCaseType, selectedSource])

  const resetFilters = () => {
    setSearchTerm("")
    setSelectedCause("all")
    setSelectedOutcome("all")
    setSelectedCaseType("all")
    setSelectedSource("all")
  }

  const handleRowClick = (txn: Transaction) => {
    setSelectedTxn(txn)
    setDialogOpen(true)
  }

  const handleTransactionUpdated = (updated: Transaction) => {
    setTransactions(current => current.map(transaction =>
      transaction.event_id === updated.event_id ? updated : transaction))
    setSelectedTxn(updated)
  }

  const causesList: { value: string; label: string }[] = [
    { value: "all", label: "All Failure Causes" },
    { value: "mandate_failed_retryable", label: "Mandate Failed (Retryable)" },
    { value: "bank_temp_error", label: "Bank Temporary Error" },
    { value: "weak_network", label: "Weak Network" },
    { value: "payment_pending", label: "Payment Pending" },
    { value: "insufficient_balance", label: "Insufficient Balance" },
    { value: "checkout_abandoned", label: "Checkout Abandoned" },
    { value: "user_cancelled", label: "User Cancelled" },
    { value: "incorrect_pin", label: "Incorrect PIN" },
    { value: "merchant_gateway_issue", label: "Merchant / Gateway Issue" },
    { value: "mandate_expired", label: "Mandate Expired / Revoked" },
    { value: "unknown", label: "Unknown / Manual Review" },
  ]

  const outcomesList: { value: string; label: string }[] = [
    { value: "all", label: "All Outcomes" },
    { value: "recovered", label: "Recovered" },
    { value: "stopped_correctly", label: "Stopped (Policy)" },
    { value: "escalated", label: "Escalated to Desk" },
    { value: "not_recovered", label: "Not Recovered" },
  ]

  const caseTypesList: { value: string; label: string }[] = [
    { value: "all", label: "All Case Types" },
    { value: "payment_degradation", label: "Payment Degradation" },
    { value: "mandate_renewal", label: "Mandate Renewal" },
  ]

  return (
    <div className="space-y-4">
      {/* Search & Filter Controls */}
      <div className="flex flex-col md:flex-row items-stretch md:items-center justify-between gap-3 bg-card p-3 rounded-lg border shadow-xs">
        <div className="relative flex-1 min-w-[240px]">
          <Search className="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-muted-foreground" />
          <input
            type="text"
            placeholder="Search by event ID (e.g. TXN10006), policy, or signal..."
            value={searchTerm}
            onChange={(e) => setSearchTerm(e.target.value)}
            className="w-full pl-9 pr-4 py-2 text-sm rounded-md border border-input bg-background focus:outline-none focus:ring-2 focus:ring-ring"
          />
        </div>

        <div className="flex flex-wrap items-center gap-2">
          <select value={selectedSource} onChange={(e) => setSelectedSource(e.target.value)}
            aria-label="Transaction source"
            className="text-xs sm:text-sm font-medium border border-input rounded-md px-3 py-2 bg-background">
            <option value="all">All Sources</option>
            <option value="live">Live</option>
            <option value="seeded_reference">Reference</option>
          </select>
          {/* Case Type Filter */}
          <select
            value={selectedCaseType}
            onChange={(e) => setSelectedCaseType(e.target.value)}
            className="text-xs sm:text-sm font-medium border border-input rounded-md px-3 py-2 bg-background focus:outline-none focus:ring-2 focus:ring-ring"
          >
            {caseTypesList.map((ct) => (
              <option key={ct.value} value={ct.value}>
                {ct.label}
              </option>
            ))}
          </select>

          {/* Cause Filter */}
          <select
            value={selectedCause}
            onChange={(e) => setSelectedCause(e.target.value)}
            className="text-xs sm:text-sm font-medium border border-input rounded-md px-3 py-2 bg-background focus:outline-none focus:ring-2 focus:ring-ring"
          >
            {causesList.map((c) => (
              <option key={c.value} value={c.value}>
                {c.label}
              </option>
            ))}
          </select>

          {/* Outcome Filter */}
          <select
            value={selectedOutcome}
            onChange={(e) => setSelectedOutcome(e.target.value)}
            className="text-xs sm:text-sm font-medium border border-input rounded-md px-3 py-2 bg-background focus:outline-none focus:ring-2 focus:ring-ring"
          >
            {outcomesList.map((o) => (
              <option key={o.value} value={o.value}>
                {o.label}
              </option>
            ))}
          </select>

          {(searchTerm !== "" || selectedCause !== "all" || selectedOutcome !== "all" || selectedCaseType !== "all" || selectedSource !== "all") && (
            <Button
              variant="ghost"
              size="sm"
              onClick={resetFilters}
              className="text-xs h-9 px-2 text-muted-foreground hover:text-foreground"
            >
              Reset
            </Button>
          )}
        </div>
      </div>

      {/* Transactions Table */}
      <div className="rounded-lg border bg-card shadow-xs overflow-hidden">
        <div className="overflow-x-auto">
          <Table>
            <TableHeader className="bg-muted/40">
              <TableRow>
                <TableHead className="w-[110px]">Event ID</TableHead>
                <TableHead title="Payment Degradation = one-time checkout failure; Mandate Renewal = recurring auto-debit failure">Type ⓘ</TableHead>
                <TableHead>Amount</TableHead>
                <TableHead>Failure Root Cause</TableHead>
                <TableHead>Action Taken</TableHead>
                <TableHead>Attempts</TableHead>
                <TableHead>Outcome</TableHead>
                <TableHead className="text-right">Detail</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {filteredTransactions.map((txn) => (
                <TableRow
                  key={txn.event_id}
                  onClick={() => handleRowClick(txn)}
                  className="cursor-pointer hover:bg-muted/30 transition-colors"
                >
                  <TableCell className="font-mono text-xs font-bold text-foreground">
                    {txn.event_id}
                    <span className="ml-1 rounded border px-1 py-0.5 text-[9px] font-sans font-medium text-muted-foreground">
                      {txn.source === "live" ? "LIVE" : "REFERENCE"}
                    </span>
                  </TableCell>
                  <TableCell className="text-xs text-muted-foreground whitespace-nowrap">
                    {CASE_TYPE_LABELS[txn.case_type] || txn.case_type}
                  </TableCell>
                  <TableCell className="font-semibold text-sm">
                    {formatCurrency(txn.amount, txn.currency)}
                  </TableCell>
                  <TableCell>
                    {txn.is_at_risk
                      ? <CauseBadge cause={txn.root_cause} />
                      : <span className="inline-flex rounded border border-emerald-200 bg-emerald-50 px-2 py-0.5 text-xs font-medium text-emerald-700 dark:border-emerald-900 dark:bg-emerald-950/30 dark:text-emerald-300">Safely settled</span>}
                  </TableCell>
                  <TableCell className="text-xs text-foreground font-medium">
                    {txn.is_at_risk ? (ACTION_LABELS[txn.action_taken] || txn.action_taken) : "No recovery required"}
                  </TableCell>
                  <TableCell>
                    <div className="flex items-center gap-1 text-xs font-semibold">
                      <span className={txn.attempt_number >= txn.max_attempts_allowed && txn.max_attempts_allowed > 0 ? "text-amber-600 font-bold" : "text-foreground"}>
                        {txn.is_at_risk ? formatAttemptCount(txn.attempt_number, txn.max_attempts_allowed) : "N/A (no risk)"}
                      </span>
                    </div>
                  </TableCell>
                  <TableCell>
                    <StatusBadge outcome={txn.outcome} />
                  </TableCell>
                  <TableCell className="text-right">
                    <Button
                      variant="ghost"
                      size="sm"
                      onClick={(e) => {
                        e.stopPropagation()
                        handleRowClick(txn)
                      }}
                      className="h-8 w-8 p-0"
                    >
                      <ChevronRight className="h-4 w-4 text-muted-foreground" />
                    </Button>
                  </TableCell>
                </TableRow>
              ))}

              {filteredTransactions.length === 0 && (
                <TableRow>
                  <TableCell colSpan={8} className="h-32 text-center text-muted-foreground text-sm">
                    <p>No transactions match your search/filter criteria.</p>
                    <Button variant="outline" size="sm" className="mt-3" onClick={resetFilters}>
                      Reset filters
                    </Button>
                  </TableCell>
                </TableRow>
              )}
            </TableBody>
          </Table>
        </div>

        <div className="p-3 border-t bg-muted/20 flex items-center justify-between text-xs text-muted-foreground">
          <span>
            Showing <strong>{filteredTransactions.length}</strong> of <strong>{transactions.length}</strong> transactions
          </span>
          <span className="hidden sm:inline">
            Click any row to inspect signals used, policy match rules, and live triggers.
          </span>
        </div>
      </div>

      <TransactionDetailDialog
        transaction={selectedTxn}
        open={dialogOpen}
        onOpenChange={setDialogOpen}
        onTransactionUpdated={handleTransactionUpdated}
      />
    </div>
  )
}
