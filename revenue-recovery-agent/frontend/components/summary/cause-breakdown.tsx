import React from "react"
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table"
import { CauseBadge } from "@/components/status-badge"
import { ROOT_CAUSE_CONFIG } from "@/lib/labels"
import type { CauseSummary } from "@/lib/types"
import { formatCurrency } from "@/lib/utils"
import { ShieldAlert } from "lucide-react"

interface CauseBreakdownProps {
  causes: CauseSummary[]
}

export function CauseBreakdown({ causes }: CauseBreakdownProps) {
  return (
    <Card className="col-span-full xl:col-span-8 shadow-xs">
      <CardHeader className="pb-3">
        <div className="flex items-center justify-between">
          <div>
            <CardTitle className="text-lg font-bold flex items-center gap-2">
              <ShieldAlert className="h-5 w-5 text-primary" />
              Failure Root Cause Breakdown & Policy Performance
            </CardTitle>
            <CardDescription className="text-xs text-muted-foreground mt-1">
              Distribution of payment degradation and mandate renewal failures, recovery efficacy, and enforced policy rules.
            </CardDescription>
          </div>
        </div>
      </CardHeader>
      <CardContent>
        <div className="rounded-md border overflow-x-auto">
          <Table>
            <TableHeader className="bg-muted/40">
              <TableRow>
                <TableHead className="w-[200px]">Failure Root Cause</TableHead>
                <TableHead className="text-right">Events</TableHead>
                <TableHead className="text-right">At-Risk</TableHead>
                <TableHead className="text-right">Recovered</TableHead>
                <TableHead className="w-[130px]">Recovery %</TableHead>
                <TableHead className="hidden md:table-cell">Enforced Policy Rule</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {causes.map((item) => {
                const recoveryRate = item.total_amount > 0
                  ? (item.recovered_amount / item.total_amount) * 100
                  : 0
                return (
                <TableRow key={item.root_cause} className="hover:bg-muted/30">
                  <TableCell className="font-medium">
                    <CauseBadge cause={item.root_cause} />
                  </TableCell>
                  <TableCell className="text-right font-semibold text-sm">
                    {item.count}
                  </TableCell>
                  <TableCell className="text-right text-sm font-medium text-muted-foreground">
                    {formatCurrency(item.total_amount)}
                  </TableCell>
                  <TableCell className="text-right text-sm font-semibold text-emerald-600 dark:text-emerald-400">
                    {formatCurrency(item.recovered_amount)}
                  </TableCell>
                  <TableCell>
                    <div className="flex items-center gap-2">
                      <div className="h-2 w-full bg-slate-100 dark:bg-slate-800 rounded-full overflow-hidden">
                        <div
                          className="h-full bg-emerald-500 rounded-full"
                          style={{ width: `${Math.min(recoveryRate, 100)}%` }}
                        />
                      </div>
                      <span className="text-xs font-semibold text-muted-foreground min-w-[34px]">
                        {recoveryRate.toFixed(0)}%
                      </span>
                    </div>
                  </TableCell>
                  <TableCell className="hidden md:table-cell text-xs text-muted-foreground max-w-[280px] truncate" title={ROOT_CAUSE_CONFIG[item.root_cause].standardPolicy}>
                    {ROOT_CAUSE_CONFIG[item.root_cause].standardPolicy}
                  </TableCell>
                </TableRow>
                )
              })}
            </TableBody>
          </Table>
        </div>
      </CardContent>
    </Card>
  )
}
