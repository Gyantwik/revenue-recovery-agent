import React from "react"
import { TransactionTable } from "@/components/transactions/transaction-table"
import { getTransactions } from "@/lib/api"
import type { Transaction } from "@/lib/mock-data"
import { ArrowLeft } from "lucide-react"
import Link from "next/link"

export const dynamic = "force-dynamic"

export default async function TransactionsPage() {
  let transactions: Transaction[] = []
  let apiError: string | null = null

  try {
    transactions = await getTransactions()
  } catch (error) {
    apiError = error instanceof Error ? error.message : "Unable to load backend data"
  }

  return (
    <div className="space-y-6">
      {apiError && (
        <div role="alert" className="rounded-lg border border-red-200 bg-red-50 p-4 text-sm text-red-800">
          Backend unavailable: {apiError}. Confirm Spring Boot is running on the configured API URL.
        </div>
      )}
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-2 border-b pb-4">
        <div>
          <div className="flex items-center gap-2">
            <Link
              href="/"
              className="text-xs font-semibold text-muted-foreground hover:text-foreground flex items-center gap-1"
            >
              <ArrowLeft className="h-3.5 w-3.5" /> Back to Overview
            </Link>
          </div>
          <h1 className="text-2xl font-bold tracking-tight text-foreground mt-1">
            Subscription Transactions Directory
          </h1>
          <p className="text-xs text-muted-foreground mt-0.5">
            Audit failure root causes, bank attempt counters, retry limits, and execute manual overrides.
          </p>
        </div>
      </div>

      <TransactionTable initialTransactions={transactions} />
    </div>
  )
}
