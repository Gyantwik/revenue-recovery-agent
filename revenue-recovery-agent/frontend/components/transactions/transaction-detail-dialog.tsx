"use client"

import React, { useState } from "react"
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog"
import { Button } from "@/components/ui/button"
import { StatusBadge, CauseBadge } from "@/components/status-badge"
import type { Transaction } from "@/lib/types"
import type { ActionTaken } from "@/lib/labels"
import { ACTION_LABELS, CASE_TYPE_LABELS } from "@/lib/labels"
import { isTerminalLifecycle, triggerTransactionAction } from "@/lib/api"
import { formatCurrency, formatDateTime } from "@/lib/utils"
import {
  CheckCircle2,
  Radio,
  Send,
  Loader2,
} from "lucide-react"

interface TransactionDetailDialogProps {
  transaction: Transaction | null
  open: boolean
  onOpenChange: (open: boolean) => void
}

export function TransactionDetailDialog({
  transaction,
  open,
  onOpenChange,
}: TransactionDetailDialogProps) {
  const [actionDone, setActionDone] = useState<string | null>(null)
  const [actionError, setActionError] = useState<string | null>(null)
  const [actionPending, setActionPending] = useState(false)

  if (!transaction) return null

  const handleAction = async (action: ActionTaken) => {
    setActionPending(true)
    setActionError(null)
    try {
      const result = await triggerTransactionAction(
        transaction.event_id,
        action,
        `${transaction.event_id}:frontend:${transaction.attempt_number + 1}:${action}`,
      )
      setActionDone(`${ACTION_LABELS[action]}: ${result.reason}`)
    } catch (error) {
      setActionError(error instanceof Error ? error.message : "Policy blocked this action")
    } finally {
      setActionPending(false)
    }
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-w-2xl max-h-[90vh] overflow-y-auto">
        <DialogHeader className="border-b pb-4">
          <div className="flex flex-wrap items-center justify-between gap-2">
            <div>
              <div className="flex items-center gap-2">
                <DialogTitle className="text-xl font-bold tracking-tight">
                  {transaction.event_id}
                </DialogTitle>
                <StatusBadge outcome={transaction.outcome} />
              </div>
              <DialogDescription className="text-xs text-muted-foreground mt-1">
                Timestamp: {formatDateTime(transaction.timestamp)} • {CASE_TYPE_LABELS[transaction.case_type]}
              </DialogDescription>
            </div>
            <div className="text-right">
              <div className="text-2xl font-bold text-foreground">
                {formatCurrency(transaction.amount, transaction.currency)}
              </div>
              {transaction.recovered_amount > 0 && (
                <span className="text-xs font-semibold text-emerald-600 dark:text-emerald-400 block">
                  Recovered: {formatCurrency(transaction.recovered_amount, transaction.currency)}
                </span>
              )}
              {transaction.recovered_amount === 0 && (
                <span className="text-xs text-muted-foreground block">
                  Recovered: {formatCurrency(0, transaction.currency)}
                </span>
              )}
            </div>
          </div>
        </DialogHeader>

        {actionDone && (
          <div className="p-3 rounded-md bg-emerald-50 dark:bg-emerald-950/60 border border-emerald-200 dark:border-emerald-800 text-xs text-emerald-800 dark:text-emerald-200 flex items-center gap-2 animate-in fade-in">
            <CheckCircle2 className="h-4 w-4 text-emerald-600" />
            <span><strong>Action Executed:</strong> {actionDone}</span>
          </div>
        )}
        {actionError && (
          <div role="alert" className="p-3 rounded-md bg-red-50 dark:bg-red-950/50 border border-red-200 dark:border-red-800 text-xs text-red-800 dark:text-red-200">
            <strong>Action blocked by policy:</strong> {actionError}
          </div>
        )}

        <div className="space-y-4 py-2">
          {/* Classification & Confidence */}
          <div className="p-3.5 rounded-lg bg-muted/40 border space-y-2">
            <div className="flex items-center justify-between">
              <h4 className="text-xs font-bold uppercase tracking-wider text-muted-foreground">
                AI Root Cause & Classification
              </h4>
              <span className="text-xs font-semibold px-2 py-0.5 rounded bg-background border">
                Confidence: {(transaction.classification_confidence * 100).toFixed(0)}%
              </span>
            </div>
            <div className="flex items-center gap-3 pt-1">
              <CauseBadge cause={transaction.root_cause} />
              <span className="text-xs text-muted-foreground">
                Matched Policy: <strong>{transaction.policy_rule_matched}</strong>
              </span>
            </div>
            <p className="text-xs text-muted-foreground">
              At risk: <strong>{transaction.is_at_risk ? "Yes" : "No"}</strong>
            </p>
          </div>

          {/* Signals Used */}
          <div className="p-3.5 rounded-lg border bg-card space-y-2">
            <h4 className="text-xs font-bold uppercase tracking-wider text-muted-foreground flex items-center gap-1.5">
              <Radio className="h-3.5 w-3.5 text-primary" /> Detection Signals Used
            </h4>
            <div className="flex flex-wrap gap-1.5">
              {transaction.signals_used.map((signal, idx) => (
                <span
                  key={idx}
                  className="font-mono text-xs px-2.5 py-1 rounded bg-muted text-foreground border"
                >
                  {signal}
                </span>
              ))}
              {transaction.signals_used.length === 0 && (
                <span className="text-xs text-muted-foreground">No signals recorded</span>
              )}
            </div>
          </div>

          {/* Execution & Attempts */}
          <div className="p-3.5 rounded-lg border bg-card space-y-3">
            <h4 className="text-xs font-bold uppercase tracking-wider text-muted-foreground">
              Policy Action & Attempt Tracking
            </h4>

            <div className="grid grid-cols-1 sm:grid-cols-3 gap-3 text-xs">
              <div className="p-2.5 rounded bg-muted/30 border">
                <span className="text-muted-foreground block text-[11px]">Action Taken:</span>
                <span className="font-semibold text-foreground text-sm">
                  {ACTION_LABELS[transaction.action_taken] || transaction.action_taken}
                </span>
              </div>
              <div className="p-2.5 rounded bg-muted/30 border">
                <span className="text-muted-foreground block text-[11px]">Lifecycle State:</span>
                <span className="font-semibold text-foreground text-sm">
                  {transaction.lifecycle_state.replaceAll("_", " ")}
                </span>
              </div>
              <div className="p-2.5 rounded bg-muted/30 border">
                <span className="text-muted-foreground block text-[11px]">Attempts Made / Max:</span>
                <span className="font-bold text-foreground text-sm">
                  {transaction.attempt_number}/{transaction.max_attempts_allowed}
                </span>
              </div>
              <div className="p-2.5 rounded bg-muted/30 border">
                <span className="text-muted-foreground block text-[11px]">Risk Amount:</span>
                <span className="font-bold text-foreground text-sm">
                  {formatCurrency(transaction.risk_amount, transaction.currency)}
                </span>
              </div>
            </div>

            {transaction.stop_or_escalate_reason && (
              <div className="p-2.5 rounded bg-amber-50/60 dark:bg-amber-950/30 border border-amber-200 dark:border-amber-900 text-xs">
                <span className="font-semibold text-amber-800 dark:text-amber-300 block">
                  Stop / Escalation Reason:
                </span>
                <p className="text-amber-900 dark:text-amber-200 mt-0.5">
                  {transaction.stop_or_escalate_reason}
                </p>
              </div>
            )}
          </div>

          {transaction.history.length > 0 && (
            <div className="p-3.5 rounded-lg border bg-card space-y-3">
              <h4 className="text-xs font-bold uppercase tracking-wider text-muted-foreground">Audit Timeline</h4>
              <ol className="space-y-3 border-l pl-4">
                {transaction.history.map((entry, index) => (
                  <li key={`${entry.timestamp}-${index}`} className="text-xs">
                    <div className="font-semibold">{entry.previous_state ?? "start"} → {entry.new_state}</div>
                    <div className="text-muted-foreground">{formatDateTime(entry.timestamp)} · {entry.actor.replaceAll("_", " ")}</div>
                    <p className="mt-1">{entry.reason}</p>
                  </li>
                ))}
              </ol>
            </div>
          )}

          {/* Interactive Actions */}
          <div className="flex flex-wrap items-center justify-end gap-2 pt-2 border-t">
            {!isTerminalLifecycle(transaction.lifecycle_state) && !actionDone && (
              <Button
                variant="outline"
                size="sm"
                onClick={() => handleAction(transaction.action_taken)}
                disabled={actionPending}
                className="gap-1.5 text-xs"
              >
                {actionPending ? <Loader2 className="h-3.5 w-3.5 animate-spin" /> : <Send className="h-3.5 w-3.5" />}
                Run test-mode {ACTION_LABELS[transaction.action_taken]}
              </Button>
            )}

            <Button
              size="sm"
              onClick={() => onOpenChange(false)}
              className="text-xs"
            >
              Close
            </Button>
          </div>
        </div>
      </DialogContent>
    </Dialog>
  )
}
