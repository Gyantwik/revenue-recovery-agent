"use client"

import React, { useEffect, useRef, useState } from "react"
import Link from "next/link"
import { useRouter } from "next/navigation"
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog"
import { Button } from "@/components/ui/button"
import { StatusBadge, CauseBadge } from "@/components/status-badge"
import type { NextRecoveryActionDecision, Transaction } from "@/lib/types"
import { ACTION_LABELS, CASE_TYPE_LABELS } from "@/lib/labels"
import {
  createTransactionRecoveryCheckout,
  checkTransactionRecoveryStatus,
  getNextRecoveryAction,
  getRazorpayTestConfig,
  getTransaction,
  recordRazorpayCheckoutEvent,
  verifyRazorpayTestPayment,
} from "@/lib/api"
import { formatRecoveryStatus, getNextActionInteraction, getRecoveryDemoHref } from "@/lib/next-recovery-action"
import { createSingleFlightRunner, loadRazorpayCheckoutScript } from "@/lib/razorpay-checkout"
import { startTransactionRecoveryFlow, type TransactionRecoveryFlowState } from "@/lib/transaction-recovery"
import { formatCurrency, formatDateTime } from "@/lib/utils"
import {
  ExternalLink,
  Info,
  Radio,
  Loader2,
  ShieldAlert,
} from "lucide-react"

interface TransactionDetailDialogProps {
  transaction: Transaction | null
  open: boolean
  onOpenChange: (open: boolean) => void
  onTransactionUpdated: (transaction: Transaction) => void
}

export function TransactionDetailDialog({
  transaction,
  open,
  onOpenChange,
  onTransactionUpdated,
}: TransactionDetailDialogProps) {
  const router = useRouter()
  const runRecovery = useRef(createSingleFlightRunner())
  const [decision, setDecision] = useState<NextRecoveryActionDecision | null>(null)
  const [decisionError, setDecisionError] = useState<string | null>(null)
  const [decisionLoading, setDecisionLoading] = useState(false)
  const [informationAcknowledged, setInformationAcknowledged] = useState(false)
  const [recoveryState, setRecoveryState] = useState<TransactionRecoveryFlowState | null>(null)
  const [recoveryRunning, setRecoveryRunning] = useState(false)

  const refreshTransactionState = async (eventId: string) => {
    const [updated, updatedDecision] = await Promise.all([
      getTransaction(eventId),
      getNextRecoveryAction(eventId),
    ])
    onTransactionUpdated(updated)
    setDecision(updatedDecision)
    router.refresh()
  }

  useEffect(() => {
    if (!open || !transaction) return
    let active = true
    setDecision(null)
    setDecisionError(null)
    setInformationAcknowledged(false)
    setRecoveryState(null)
    setDecisionLoading(true)
    void getNextRecoveryAction(transaction.event_id)
      .then(result => { if (active) setDecision(result) })
      .catch(error => {
        if (active) setDecisionError(error instanceof Error ? error.message : "Unable to load the next recovery decision")
      })
      .finally(() => { if (active) setDecisionLoading(false) })
    return () => { active = false }
  }, [open, transaction])

  const handleRecoveryCheckout = async () => {
    if (!transaction || !decision) return
    setRecoveryRunning(true)
    setRecoveryState(null)
    try {
      const started = await runRecovery.current(async () => startTransactionRecoveryFlow(
        transaction.event_id,
        {
          createOrder: createTransactionRecoveryCheckout,
          getConfig: getRazorpayTestConfig,
          recordEvent: recordRazorpayCheckoutEvent,
          verifyPayment: verifyRazorpayTestPayment,
          getTransaction,
          loadScript: loadRazorpayCheckoutScript,
          createCheckout: options => {
            if (!window.Razorpay) throw new Error("Razorpay Checkout is unavailable")
            return new window.Razorpay(options)
          },
          onState: state => {
            setRecoveryState(state)
            if (["client_reported_unverified", "verification_failed"].includes(state.status)) {
              void getNextRecoveryAction(transaction.event_id).then(setDecision)
            }
          },
          onRecovered: recovered => {
            onTransactionUpdated(recovered)
            void refreshTransactionState(recovered.event_id)
          },
        },
      ))
      if (!started) {
        setRecoveryState({ message: "A recovery Checkout is already being opened.", status: "error" })
      }
    } catch (error) {
      setRecoveryState({
        message: error instanceof Error ? error.message : "Recovery Checkout could not be opened.",
        status: "error",
      })
    } finally {
      setRecoveryRunning(false)
    }
  }

  const handleStatusCheck = async () => {
    if (!transaction || recoveryRunning) return
    setRecoveryRunning(true)
    try {
      const status = await checkTransactionRecoveryStatus(transaction.event_id)
      setRecoveryState({
        message: status.message,
        status: status.is_recovered ? "recovered_by_verified_test_payment"
          : status.status === "verification_failed" ? "verification_failed" : "client_reported_unverified",
      })
      await refreshTransactionState(transaction.event_id)
    } catch (error) {
      setRecoveryState({
        message: error instanceof Error ? error.message : "Payment status could not be checked.",
        status: "error",
      })
    } finally {
      setRecoveryRunning(false)
    }
  }

  if (!transaction) return null

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

          {/* Backend-owned next action: this card never derives policy from UI strings. */}
          <div className="p-3.5 rounded-lg border border-teal-200 bg-teal-50/40 dark:border-teal-900 dark:bg-teal-950/20 space-y-3">
            <h4 className="text-xs font-bold uppercase tracking-wider text-teal-800 dark:text-teal-300 flex items-center gap-1.5">
              <ShieldAlert className="h-3.5 w-3.5" /> Next Recovery Decision
            </h4>

            {decisionLoading && (
              <p className="flex items-center gap-2 text-xs text-muted-foreground">
                <Loader2 className="h-3.5 w-3.5 animate-spin" /> Loading policy decision…
              </p>
            )}
            {decisionError && (
              <p role="alert" className="text-xs text-red-700 dark:text-red-300">
                Decision unavailable: {decisionError}. No recovery action was performed.
              </p>
            )}
            {decision && (
              <>
                <dl className="grid gap-3 text-xs sm:grid-cols-2">
                  <DecisionDetail label="Current Recovery Status" value={formatRecoveryStatus(decision)} />
                  <DecisionDetail label="Recommended Action" value={decision.title} />
                  <DecisionDetail label="Why" value={decision.reason} wide />
                  <DecisionDetail label="Safe Next Step" value={decision.next_step} wide />
                  <DecisionDetail label="Policy Guardrail" value={decision.risk_note} wide />
                </dl>
                <DecisionButton
                  decision={decision}
                  acknowledged={informationAcknowledged}
                  onAcknowledge={() => setInformationAcknowledged(true)}
                  recoveryRunning={recoveryRunning}
                  onRecoveryCheckout={() => { void handleRecoveryCheckout() }}
                  onStatusCheck={() => { void handleStatusCheck() }}
                />
                {["OPEN_RECOVERY_CHECKOUT", "RESUME_RECOVERY_CHECKOUT"].includes(decision.action_type) && (
                  <p className="text-xs text-muted-foreground">
                    Razorpay Test Mode — no real money is charged. The customer must voluntarily complete Checkout.
                  </p>
                )}
                {recoveryState && (
                  <p role="status" className={recoveryState.status === "error" || recoveryState.status === "verification_failed"
                    ? "text-xs text-red-700 dark:text-red-300"
                    : "text-xs text-teal-800 dark:text-teal-200"}>
                    {recoveryState.message}
                  </p>
                )}
              </>
            )}
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

          {/* Closing the modal is the only generic action; policy actions live in the decision card. */}
          <div className="flex flex-wrap items-center justify-end gap-2 pt-2 border-t">
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

function DecisionDetail({ label, value, wide = false }: { label: string; value: string; wide?: boolean }) {
  return (
    <div className={wide ? "sm:col-span-2" : undefined}>
      <dt className="text-[11px] uppercase tracking-wide text-muted-foreground">{label}:</dt>
      <dd className="mt-0.5 font-medium leading-5">{value}</dd>
    </div>
  )
}

function DecisionButton({
  decision,
  acknowledged,
  onAcknowledge,
  recoveryRunning,
  onRecoveryCheckout,
  onStatusCheck,
}: {
  decision: NextRecoveryActionDecision
  acknowledged: boolean
  onAcknowledge: () => void
  recoveryRunning: boolean
  onRecoveryCheckout: () => void
  onStatusCheck: () => void
}) {
  const interaction = getNextActionInteraction(decision)
  const demoHref = getRecoveryDemoHref(decision)

  if (interaction === "recovery_checkout") {
    return (
      <div className="flex flex-wrap gap-2">
        <Button type="button" size="sm" className="gap-1.5 text-xs"
          disabled={recoveryRunning} onClick={onRecoveryCheckout}>
          {recoveryRunning && <Loader2 className="h-3.5 w-3.5 animate-spin" />}
          {decision.button_label}
        </Button>
        {decision.secondary_action_type === "CHECK_PAYMENT_STATUS" && (
          <Button type="button" variant="outline" size="sm" className="text-xs"
            disabled={recoveryRunning} onClick={onStatusCheck}>
            {decision.secondary_button_label ?? "Check Payment Status"}
          </Button>
        )}
      </div>
    )
  }

  if (interaction === "test_mode_checkout" && demoHref) {
    return (
      <Button asChild size="sm" className="gap-1.5 text-xs">
        <Link href={demoHref}>
          <ExternalLink className="h-3.5 w-3.5" /> {decision.button_label}
        </Link>
      </Button>
    )
  }

  if (interaction === "information") {
    return (
      <div className="space-y-2">
        <Button type="button" variant="outline" size="sm" className="gap-1.5 text-xs" onClick={onAcknowledge}>
          <Info className="h-3.5 w-3.5" /> {decision.button_label}
        </Button>
        {acknowledged && (
          <p role="status" className="text-xs text-muted-foreground">
            Guidance acknowledged. No payment, retry, order, outcome, or audit record was created.
          </p>
        )}
      </div>
    )
  }

  return (
    <Button type="button" variant="outline" size="sm" className="text-xs" disabled>
      {decision.button_label}
    </Button>
  )
}
