import React from "react"
import { StatCards } from "@/components/summary/stat-cards"
import { CauseBreakdown } from "@/components/summary/cause-breakdown"
import { EscalatedCard } from "@/components/summary/escalated-card"
import { PolicyImpactSimulator } from "@/components/summary/policy-impact-simulator"
import { TransactionTable } from "@/components/transactions/transaction-table"
import { ApiErrorState } from "@/components/api-error-state"
import { getBatchSummary, getTransactions } from "@/lib/api"
import type { BatchSummary, Transaction } from "@/lib/types"
import { ArrowRight } from "lucide-react"
import Link from "next/link"

export const dynamic = "force-dynamic"

export default async function DashboardPage() {
  let transactions: Transaction[] = []
  let summary: BatchSummary | null = null
  let apiError: string | null = null

  try {
    ;[summary, transactions] = await Promise.all([getBatchSummary(), getTransactions()])
  } catch (error) {
    apiError = error instanceof Error ? error.message : "Unable to load backend data"
  }

  return (
    <div className="space-y-8">
      {apiError && <ApiErrorState message={apiError} />}
      {/* Header Banner */}
      <div className="flex flex-col md:flex-row md:items-center md:justify-between gap-4 bg-card border rounded-xl p-6 shadow-xs">
        <div>
          <div className="flex items-center gap-2">
            <h1 className="text-2xl font-bold tracking-tight text-foreground">
              Autonomous Revenue Recovery
            </h1>
            <span className="px-2 py-0.5 rounded text-xs font-semibold bg-emerald-100 text-emerald-800 dark:bg-emerald-950/60 dark:text-emerald-400 border border-emerald-300 dark:border-emerald-800">
              Synthetic Benchmark — 80 seeded cases
            </span>
          </div>
          <p className="text-sm text-muted-foreground mt-1">
            Real-time tracking of failed subscription charges, banking retry policy compliance, and automated customer re-engagement.
          </p>
        </div>
        <div className="flex items-center gap-2 self-start md:self-auto">
          <Link
            href="/transactions"
            className="inline-flex items-center justify-center gap-2 px-4 py-2 rounded-md bg-primary text-primary-foreground text-sm font-semibold hover:bg-primary/90 transition-colors shadow-xs"
          >
            Explore All Transactions <ArrowRight className="h-4 w-4" />
          </Link>
        </div>
      </div>

      {/* Top Level Metric Cards */}
      {summary && <StatCards summary={summary} transactions={transactions} />}
      <PolicyImpactSimulator />

      {/* Cause Breakdown and Escalations */}
      {summary && (
        <div className="grid grid-cols-1 xl:grid-cols-12 gap-6">
          <CauseBreakdown causes={summary.by_cause} />
          <EscalatedCard escalated={summary.escalated_summary} />
        </div>
      )}

      {/* Recent Activity Snapshot */}
      <div className="space-y-4">
        <div className="flex items-center justify-between">
          <div>
            <h2 className="text-lg font-bold tracking-tight text-foreground">
              Live Recovery Stream
            </h2>
            <p className="text-xs text-muted-foreground">
              Recent recurring charge failures and autonomous recovery steps executed.
            </p>
          </div>
          <Link
            href="/transactions"
            className="text-xs font-semibold text-primary hover:underline"
          >
            View full dataset →
          </Link>
        </div>
        {!apiError && <TransactionTable initialTransactions={transactions.filter(transaction => transaction.is_at_risk)} />}
      </div>
    </div>
  )
}
