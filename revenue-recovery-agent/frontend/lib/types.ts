import type { ActionTaken, CaseType, RootCause, TransactionOutcome } from "@/lib/labels"

export interface Transaction {
  event_id: string
  case_type: CaseType
  amount: number
  currency: string
  timestamp: string
  is_at_risk: boolean
  risk_amount: number
  root_cause: RootCause
  classification_confidence: number
  signals_used: string[]
  policy_rule_matched: string
  action_taken: ActionTaken
  attempt_number: number
  max_attempts_allowed: number
  outcome: TransactionOutcome
  recovered_amount: number
  stop_or_escalate_reason: string | null
}

export interface CauseSummary {
  root_cause: RootCause
  count: number
  total_amount: number
  recovered_amount: number
}

export interface EscalatedSummary {
  event_id: string
  root_cause: RootCause
  amount: number
  stop_or_escalate_reason: string | null
}

export interface BatchSummary {
  total_at_risk: number
  total_recovered: number
  recovery_rate: number
  total_cases: number
  by_cause: CauseSummary[]
  escalated_summary: EscalatedSummary[]
}
