import React from "react"
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card"
import { formatCurrency } from "@/lib/utils"
import { RecoverySummaryStats } from "@/lib/mock-data"
import { TrendingUp, AlertCircle, CheckCircle2, ShieldCheck } from "lucide-react"

interface StatCardsProps {
  stats: RecoverySummaryStats
}

export function StatCards({ stats }: StatCardsProps) {
  return (
    <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
      {/* 1. At-Risk Revenue */}
      <Card className="border-border shadow-xs hover:shadow-sm transition-shadow">
        <CardHeader className="flex flex-row items-center justify-between pb-2 space-y-0">
          <CardTitle className="text-xs font-semibold uppercase tracking-wider text-muted-foreground">
            Total At-Risk Revenue
          </CardTitle>
          <div className="p-2 rounded-md bg-slate-100 dark:bg-slate-800 text-slate-700 dark:text-slate-300">
            <AlertCircle className="h-4 w-4" />
          </div>
        </CardHeader>
        <CardContent>
          <div className="text-2xl font-bold tracking-tight text-foreground">
            {formatCurrency(stats.total_at_risk_amount)}
          </div>
          <p className="text-xs text-muted-foreground mt-1 flex items-center gap-1.5">
            <span className="font-medium text-foreground">{stats.total_events}</span> total failure events evaluated
          </p>
        </CardContent>
      </Card>

      {/* 2. Successfully Recovered */}
      <Card className="border-emerald-200 dark:border-emerald-800 bg-emerald-50/20 dark:bg-emerald-950/10 shadow-xs hover:shadow-sm transition-shadow">
        <CardHeader className="flex flex-row items-center justify-between pb-2 space-y-0">
          <CardTitle className="text-xs font-semibold uppercase tracking-wider text-emerald-800 dark:text-emerald-300">
            Successfully Recovered
          </CardTitle>
          <div className="p-2 rounded-md bg-emerald-100 dark:bg-emerald-900/60 text-emerald-700 dark:text-emerald-300">
            <CheckCircle2 className="h-4 w-4" />
          </div>
        </CardHeader>
        <CardContent>
          <div className="text-2xl font-bold tracking-tight text-emerald-700 dark:text-emerald-400">
            {formatCurrency(stats.total_recovered_amount)}
          </div>
          <div className="flex items-center gap-2 mt-1">
            <div className="flex items-center text-xs font-semibold text-emerald-600 dark:text-emerald-400">
              <TrendingUp className="h-3.5 w-3.5 mr-0.5" />
              {stats.overall_recovery_rate.toFixed(1)}% Recovery Rate
            </div>
            <span className="text-xs text-muted-foreground">({stats.recovered_count} txns)</span>
          </div>
        </CardContent>
      </Card>

      {/* 3. Stopped by Policy (Safe Guard) */}
      <Card className="border-blue-200 dark:border-blue-800 bg-blue-50/20 dark:bg-blue-950/10 shadow-xs hover:shadow-sm transition-shadow">
        <CardHeader className="flex flex-row items-center justify-between pb-2 space-y-0">
          <CardTitle className="text-xs font-semibold uppercase tracking-wider text-blue-800 dark:text-blue-300">
            Stopped Under Policy
          </CardTitle>
          <div className="p-2 rounded-md bg-blue-100 dark:bg-blue-900/60 text-blue-700 dark:text-blue-300">
            <ShieldCheck className="h-4 w-4" />
          </div>
        </CardHeader>
        <CardContent>
          <div className="text-2xl font-bold tracking-tight text-blue-700 dark:text-blue-400">
            {stats.stopped_correctly_count} Events
          </div>
          <p className="text-xs text-muted-foreground mt-1">
            Zero unlawful retries (PIN & User Cancelled)
          </p>
        </CardContent>
      </Card>

      {/* 4. Escalated / Merchant Review */}
      <Card className="border-amber-200 dark:border-amber-800 bg-amber-50/20 dark:bg-amber-950/10 shadow-xs hover:shadow-sm transition-shadow">
        <CardHeader className="flex flex-row items-center justify-between pb-2 space-y-0">
          <CardTitle className="text-xs font-semibold uppercase tracking-wider text-amber-800 dark:text-amber-300">
            Escalated to Desk
          </CardTitle>
          <div className="p-2 rounded-md bg-amber-100 dark:bg-amber-900/60 text-amber-700 dark:text-amber-300">
            <AlertCircle className="h-4 w-4" />
          </div>
        </CardHeader>
        <CardContent>
          <div className="text-2xl font-bold tracking-tight text-amber-700 dark:text-amber-400">
            {formatCurrency(stats.escalated_amount)}
          </div>
          <p className="text-xs text-muted-foreground mt-1">
            <span className="font-medium text-foreground">{stats.escalated_count}</span> cases routed to manual review
          </p>
        </CardContent>
      </Card>
    </div>
  )
}
