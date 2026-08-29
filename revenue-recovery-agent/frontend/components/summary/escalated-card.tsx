import React from "react"
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
import { Transaction } from "@/lib/mock-data"
import { CauseBadge, StatusBadge } from "@/components/status-badge"
import { formatCurrency, formatDateTime } from "@/lib/utils"
import { AlertTriangle, ArrowRight } from "lucide-react"
import Link from "next/link"

interface EscalatedCardProps {
  transactions: Transaction[]
}

export function EscalatedCard({ transactions }: EscalatedCardProps) {
  const escalated = transactions.filter(t => t.outcome === "escalated")

  return (
    <Card className="col-span-full xl:col-span-4 shadow-xs border-amber-200/60 dark:border-amber-900/40 bg-gradient-to-b from-amber-50/10 to-transparent">
      <CardHeader className="pb-3">
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-2">
            <div className="p-1.5 rounded-md bg-amber-100 dark:bg-amber-900/60 text-amber-700 dark:text-amber-300">
              <AlertTriangle className="h-4 w-4" />
            </div>
            <div>
              <CardTitle className="text-base font-bold">Escalated to Desk</CardTitle>
              <CardDescription className="text-xs text-muted-foreground">
                {escalated.length} cases requiring manual intervention
              </CardDescription>
            </div>
          </div>
        </div>
      </CardHeader>
      <CardContent>
        <div className="space-y-3 max-h-[460px] overflow-y-auto pr-1">
          {escalated.slice(0, 5).map((txn) => (
            <div
              key={txn.event_id}
              className="p-3 rounded-lg border bg-card/60 hover:bg-card transition-colors flex flex-col gap-1.5"
            >
              <div className="flex items-start justify-between gap-2">
                <div>
                  <span className="font-mono text-xs font-bold text-foreground">{txn.event_id}</span>
                  <span className="text-xs text-muted-foreground block capitalize">{txn.case_type.replace('_', ' ')}</span>
                </div>
                <div className="text-right">
                  <p className="text-sm font-bold text-foreground">{formatCurrency(txn.amount)}</p>
                  <span className="text-[11px] text-muted-foreground">Conf: {(txn.classification_confidence * 100).toFixed(0)}%</span>
                </div>
              </div>

              <div className="pt-1 border-t border-dashed space-y-1">
                <div className="flex items-center justify-between">
                  <CauseBadge cause={txn.root_cause} className="text-[10px] py-0" />
                  <span className="text-[11px] text-amber-700 dark:text-amber-400 font-medium truncate max-w-[180px]">
                    {txn.stop_or_escalate_reason || txn.policy_rule_matched}
                  </span>
                </div>
              </div>
            </div>
          ))}

          <div className="pt-2">
            <Link
              href="/transactions?outcome=escalated"
              className="flex items-center justify-center gap-1.5 text-xs font-semibold text-primary hover:underline w-full py-2 rounded-md bg-primary/5 hover:bg-primary/10 transition-colors"
            >
              View all {escalated.length} escalated cases <ArrowRight className="h-3.5 w-3.5" />
            </Link>
          </div>
        </div>
      </CardContent>
    </Card>
  )
}
