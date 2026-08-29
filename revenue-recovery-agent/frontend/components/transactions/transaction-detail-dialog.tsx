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
import { Transaction } from "@/lib/mock-data"
import { ACTION_LABELS, CASE_TYPE_LABELS } from "@/lib/labels"
import { formatCurrency, formatDateTime } from "@/lib/utils"
import {
  CheckCircle2,
  RotateCw,
  UserCheck,
  Radio,
  Send
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

  if (!transaction) return null

  const handleAction = (actionName: string) => {
    setActionDone(actionName)
    setTimeout(() => {
      setActionDone(null)
    }, 4000)
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
            </div>
          </div>
        </DialogHeader>

        {actionDone && (
          <div className="p-3 rounded-md bg-emerald-50 dark:bg-emerald-950/60 border border-emerald-200 dark:border-emerald-800 text-xs text-emerald-800 dark:text-emerald-200 flex items-center gap-2 animate-in fade-in">
            <CheckCircle2 className="h-4 w-4 text-emerald-600" />
            <span><strong>Action Executed:</strong> {actionDone}</span>
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
                <span className="text-muted-foreground block text-[11px]">Attempts Made / Max:</span>
                <span className="font-bold text-foreground text-sm">
                  {transaction.attempt_number} of max {transaction.max_attempts_allowed}
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

          {/* Interactive Actions */}
          <div className="flex flex-wrap items-center justify-end gap-2 pt-2 border-t">
            {transaction.action_taken === "send_alt_payment_link" && (
              <Button
                variant="outline"
                size="sm"
                onClick={() => handleAction("Resent Instant Payment Link via WhatsApp & SMS")}
                className="gap-1.5 text-xs"
              >
                <Send className="h-3.5 w-3.5" /> Resend Payment Link
              </Button>
            )}

            {transaction.attempt_number < transaction.max_attempts_allowed && transaction.outcome !== "recovered" && (
              <Button
                variant="secondary"
                size="sm"
                onClick={() => handleAction(`Triggered immediate retry #${transaction.attempt_number + 1}`)}
                className="gap-1.5 text-xs"
              >
                <RotateCw className="h-3.5 w-3.5" /> Force Retry
              </Button>
            )}

            {transaction.outcome !== "escalated" && (
              <Button
                variant="outline"
                size="sm"
                onClick={() => handleAction("Case manually escalated to Merchant Support Desk")}
                className="gap-1.5 text-xs text-amber-700 dark:text-amber-400 border-amber-300"
              >
                <UserCheck className="h-3.5 w-3.5" /> Route to Support
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
