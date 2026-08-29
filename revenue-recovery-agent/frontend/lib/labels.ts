export type RootCause =
  | "user_cancelled"
  | "incorrect_pin"
  | "bank_temp_error"
  | "weak_network"
  | "payment_pending"
  | "insufficient_balance"
  | "checkout_abandoned"
  | "merchant_gateway_issue"
  | "mandate_expired"
  | "mandate_failed_retryable"

export type TransactionOutcome =
  | "recovered"
  | "not_recovered"
  | "escalated"
  | "stopped_correctly"

export type CaseType =
  | "payment_degradation"
  | "mandate_renewal"

export type ActionTaken =
  | "retry_payment"
  | "schedule_mandate_retry"
  | "verify_status"
  | "send_alt_payment_link"
  | "send_recovery_link"
  | "escalate_merchant"
  | "no_action_stop"

export interface RootCauseMeta {
  label: string
  description: string
  color: string
  badgeVariant: "default" | "secondary" | "destructive" | "outline"
  standardPolicy: string
  defaultMaxAttempts: number
}

export const ROOT_CAUSE_CONFIG: Record<RootCause, RootCauseMeta> = {
  user_cancelled: {
    label: "User Cancelled",
    description: "Customer explicitly cancelled or aborted transaction. Retries are strictly forbidden by policy.",
    color: "text-slate-700 bg-slate-100 border-slate-300 dark:bg-slate-800 dark:text-slate-200 dark:border-slate-700",
    badgeVariant: "outline",
    standardPolicy: "User cancellation -> Stop",
    defaultMaxAttempts: 0,
  },
  incorrect_pin: {
    label: "Incorrect PIN / Auth Failure",
    description: "Authentication failed due to incorrect PIN entered. Automated retries stopped; requires customer manual retry.",
    color: "text-purple-700 bg-purple-50 border-purple-200 dark:bg-purple-950/50 dark:text-purple-300 dark:border-purple-800",
    badgeVariant: "outline",
    standardPolicy: "Auth failure (PIN) -> Stop",
    defaultMaxAttempts: 0,
  },
  bank_temp_error: {
    label: "Bank Temporary Error",
    description: "Issuer bank CBS offline or timeout. Eligible for automated retry under 30-min window.",
    color: "text-orange-700 bg-orange-50 border-orange-200 dark:bg-orange-950/50 dark:text-orange-300 dark:border-orange-800",
    badgeVariant: "outline",
    standardPolicy: "Bank temp error → Retry, max 2, 30 min window",
    defaultMaxAttempts: 2,
  },
  weak_network: {
    label: "Weak Network / Client Timeout",
    description: "Client-side packet loss, network drop, or 3G fallback. Retried with backoff.",
    color: "text-cyan-700 bg-cyan-50 border-cyan-200 dark:bg-cyan-950/50 dark:text-cyan-300 dark:border-cyan-800",
    badgeVariant: "outline",
    standardPolicy: "Weak network → Retry, max 2",
    defaultMaxAttempts: 2,
  },
  payment_pending: {
    label: "Payment Pending",
    description: "Transaction pending with bank or in-process with NPCI. Status verification polling triggered.",
    color: "text-blue-700 bg-blue-50 border-blue-200 dark:bg-blue-950/50 dark:text-blue-300 dark:border-blue-800",
    badgeVariant: "secondary",
    standardPolicy: "Payment pending → Verify status only",
    defaultMaxAttempts: 1,
  },
  insufficient_balance: {
    label: "Insufficient Balance",
    description: "Account has insufficient funds. Alternative instant payment link dispatched.",
    color: "text-amber-700 bg-amber-50 border-amber-200 dark:bg-amber-950/50 dark:text-amber-300 dark:border-amber-800",
    badgeVariant: "outline",
    standardPolicy: "Insufficient balance → Send alternative payment link",
    defaultMaxAttempts: 1,
  },
  checkout_abandoned: {
    label: "Checkout Abandoned",
    description: "Customer backgrounded app or closed tab during checkout. Recovery cart link sent.",
    color: "text-indigo-700 bg-indigo-50 border-indigo-200 dark:bg-indigo-950/50 dark:text-indigo-300 dark:border-indigo-800",
    badgeVariant: "outline",
    standardPolicy: "Checkout abandoned → Send recovery link",
    defaultMaxAttempts: 1,
  },
  merchant_gateway_issue: {
    label: "Merchant / Gateway Issue",
    description: "Gateway routing malfunction or MID issue. Escalated to merchant engineering/support.",
    color: "text-rose-700 bg-rose-50 border-rose-200 dark:bg-rose-950/50 dark:text-rose-300 dark:border-rose-800",
    badgeVariant: "destructive",
    standardPolicy: "Gateway issue -> Escalate",
    defaultMaxAttempts: 0,
  },
  mandate_expired: {
    label: "Mandate Expired / Revoked",
    description: "e-Mandate registration has expired or was revoked. New mandate setup required.",
    color: "text-red-700 bg-red-50 border-red-200 dark:bg-red-950/50 dark:text-red-300 dark:border-red-800",
    badgeVariant: "destructive",
    standardPolicy: "Mandate expired -> Escalate",
    defaultMaxAttempts: 0,
  },
  mandate_failed_retryable: {
    label: "Mandate Failed (Retryable)",
    description: "Recurring e-mandate debit failed with transient bank glitch. Automated retry scheduled (max 2).",
    color: "text-emerald-800 bg-emerald-50 border-emerald-300 dark:bg-emerald-950/50 dark:text-emerald-300 dark:border-emerald-800",
    badgeVariant: "secondary",
    standardPolicy: "Mandate failed retryable → Retry, max 2",
    defaultMaxAttempts: 2,
  },
}

export const OUTCOME_CONFIG: Record<TransactionOutcome, { label: string; color: string; badgeVariant: "default" | "secondary" | "destructive" | "outline" }> = {
  recovered: {
    label: "Recovered",
    color: "text-emerald-700 bg-emerald-50 border-emerald-200 dark:bg-emerald-950/50 dark:text-emerald-300 dark:border-emerald-800 font-semibold",
    badgeVariant: "default",
  },
  not_recovered: {
    label: "Not Recovered",
    color: "text-rose-700 bg-rose-50 border-rose-200 dark:bg-rose-950/50 dark:text-rose-300 dark:border-rose-800",
    badgeVariant: "destructive",
  },
  escalated: {
    label: "Escalated",
    color: "text-amber-700 bg-amber-50 border-amber-200 dark:bg-amber-950/50 dark:text-amber-300 dark:border-amber-800",
    badgeVariant: "outline",
  },
  stopped_correctly: {
    label: "Stopped (Policy)",
    color: "text-slate-700 bg-slate-100 border-slate-300 dark:bg-slate-800 dark:text-slate-300 dark:border-slate-700",
    badgeVariant: "outline",
  },
}

export const CASE_TYPE_LABELS: Record<CaseType, string> = {
  payment_degradation: "Payment Degradation",
  mandate_renewal: "Mandate Renewal",
}

export const ACTION_LABELS: Record<ActionTaken, string> = {
  retry_payment: "Retry Payment",
  schedule_mandate_retry: "Schedule Mandate Retry",
  verify_status: "Verify Status",
  send_alt_payment_link: "Send Alt Payment Link",
  send_recovery_link: "Send Recovery Link",
  escalate_merchant: "Escalate to Merchant",
  no_action_stop: "No Action (Stop Policy)",
}
