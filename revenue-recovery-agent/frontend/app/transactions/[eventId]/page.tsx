import Link from "next/link"
import { notFound } from "next/navigation"
import { ApiErrorState } from "@/components/api-error-state"
import { CauseBadge, StatusBadge } from "@/components/status-badge"
import { ApiError, getTransaction } from "@/lib/api"
import { ACTION_LABELS, CASE_TYPE_LABELS } from "@/lib/labels"
import type { Transaction } from "@/lib/types"
import { formatAttemptCount, formatCurrency, formatDateTime } from "@/lib/utils"

export const dynamic = "force-dynamic"

export default async function TransactionDetailPage({ params }: { params: { eventId: string } }) {
  let transaction: Transaction | null = null
  let apiError: string | null = null

  try {
    transaction = await getTransaction(params.eventId)
  } catch (error) {
    if (error instanceof ApiError && error.status === 404) notFound()
    apiError = error instanceof Error ? error.message : "Unable to load transaction"
  }

  if (apiError) return <ApiErrorState message={apiError} />
  if (!transaction) return null

  return (
    <div className="space-y-6">
      <Link href="/transactions" className="text-sm font-semibold text-primary hover:underline">
        ← Back to transactions
      </Link>
      <div className="rounded-xl border bg-card p-6 shadow-xs space-y-6">
        <div className="flex flex-wrap items-start justify-between gap-4 border-b pb-4">
          <div>
            <div className="flex items-center gap-2">
              <h1 className="font-mono text-xl font-bold">{transaction.event_id}</h1>
              <StatusBadge outcome={transaction.outcome} />
            </div>
            <p className="mt-1 text-sm text-muted-foreground">
              {CASE_TYPE_LABELS[transaction.case_type]} • {formatDateTime(transaction.timestamp)}
            </p>
          </div>
          <div className="text-right">
            <p className="text-2xl font-bold">{formatCurrency(transaction.amount, transaction.currency)}</p>
            <p className="text-sm text-emerald-700">Recovered: {formatCurrency(transaction.recovered_amount, transaction.currency)}</p>
          </div>
        </div>

        <dl className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3 text-sm">
          <Detail label="Currency" value={transaction.currency} />
          <Detail label="At risk" value={transaction.is_at_risk ? "Yes" : "No"} />
          <Detail label="Risk amount" value={formatCurrency(transaction.risk_amount, transaction.currency)} />
          <Detail label={transaction.is_at_risk ? "Root cause" : "Settlement status"} value={transaction.is_at_risk
            ? <CauseBadge cause={transaction.root_cause} />
            : "Safely settled — no recovery required"} />
          <Detail label="Classification confidence" value={`${(transaction.classification_confidence * 100).toFixed(1)}%`} />
          <Detail label="Policy rule matched" value={transaction.policy_rule_matched} />
          <Detail label="Action taken" value={transaction.is_at_risk ? ACTION_LABELS[transaction.action_taken] : "No recovery required"} />
          <Detail label="Attempts / maximum" value={transaction.is_at_risk ? formatAttemptCount(transaction.attempt_number, transaction.max_attempts_allowed) : "N/A (no risk)"} />
          <Detail label="Outcome" value={transaction.outcome} />
          <Detail label="Lifecycle state" value={transaction.lifecycle_state.replaceAll("_", " ")} />
          <Detail label="Next eligible action" value={transaction.next_eligible_action_at ? formatDateTime(transaction.next_eligible_action_at) : "Not scheduled"} />
          <Detail label="Recovery window expires" value={transaction.recovery_window_expires_at ? formatDateTime(transaction.recovery_window_expires_at) : "Not applicable"} />
          <Detail label="Stop or escalation reason" value={transaction.stop_or_escalate_reason ?? "Not applicable"} />
        </dl>

        <div>
          <h2 className="text-sm font-semibold">Signals used</h2>
          <div className="mt-2 flex flex-wrap gap-2">
            {transaction.signals_used.map(signal => (
              <span key={signal} className="rounded border bg-muted px-2 py-1 font-mono text-xs">{signal}</span>
            ))}
            {transaction.signals_used.length === 0 && <span className="text-sm text-muted-foreground">No signals recorded</span>}
          </div>
        </div>

        <div>
          <h2 className="text-sm font-semibold">Append-only audit timeline</h2>
          {transaction.history.length === 0 ? (
            <p className="mt-2 text-sm text-muted-foreground">No lifecycle history recorded.</p>
          ) : (
            <ol className="mt-3 space-y-3 border-l pl-5">
              {transaction.history.map((entry, index) => (
                <li key={`${entry.timestamp}-${index}`} className="text-sm">
                  <p className="font-semibold">{entry.previous_state ?? "start"} → {entry.new_state}</p>
                  <p className="text-xs text-muted-foreground">{formatDateTime(entry.timestamp)} · {entry.actor.replaceAll("_", " ")}</p>
                  <p className="mt-1 text-sm">{entry.reason}</p>
                </li>
              ))}
            </ol>
          )}
        </div>
      </div>
    </div>
  )
}

function Detail({ label, value }: { label: string; value: React.ReactNode }) {
  return (
    <div>
      <dt className="text-xs uppercase tracking-wide text-muted-foreground">{label}</dt>
      <dd className="mt-1 font-medium">{value}</dd>
    </div>
  )
}
