import React from "react"
import { StatCards } from "@/components/summary/stat-cards"
import { CauseBreakdown } from "@/components/summary/cause-breakdown"
import { EscalatedCard } from "@/components/summary/escalated-card"
import { TransactionTable } from "@/components/transactions/transaction-table"
import { getSummaryStats, getCauseBreakdown, type Transaction } from "@/lib/mock-data"
import { getTransactions } from "@/lib/api"
import { ShieldCheck, Zap, ArrowRight } from "lucide-react"
import Link from "next/link"

export const dynamic = "force-dynamic"

export default async function DashboardPage() {
  let transactions: Transaction[] = []
  let apiError: string | null = null

  try {
    transactions = await getTransactions()
  } catch (error) {
    apiError = error instanceof Error ? error.message : "Unable to load backend data"
  }

  const stats = getSummaryStats(transactions)
  const breakdown = getCauseBreakdown(transactions)

  return (
    <div className="space-y-8">
      {apiError && (
        <div role="alert" className="rounded-lg border border-red-200 bg-red-50 p-4 text-sm text-red-800">
          Backend unavailable: {apiError}. Confirm Spring Boot is running on the configured API URL.
        </div>
      )}
      {/* Header Banner */}
      <div className="flex flex-col md:flex-row md:items-center md:justify-between gap-4 bg-card border rounded-xl p-6 shadow-xs">
        <div>
          <div className="flex items-center gap-2">
            <h1 className="text-2xl font-bold tracking-tight text-foreground">
              Autonomous Revenue Recovery
            </h1>
            <span className="px-2 py-0.5 rounded text-xs font-semibold bg-emerald-100 text-emerald-800 dark:bg-emerald-950/60 dark:text-emerald-400 border border-emerald-300 dark:border-emerald-800">
              Live Production
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
      <StatCards stats={stats} />

      {/* Cause Breakdown and Escalations */}
      <div className="grid grid-cols-1 xl:grid-cols-12 gap-6">
        <CauseBreakdown breakdown={breakdown} />
        <EscalatedCard transactions={transactions} />
      </div>

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
        <TransactionTable initialTransactions={transactions} />
      </div>
    </div>
  )
}
