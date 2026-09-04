export interface TransactionFilters {
  searchTerm: string
  caseType: string
  rootCause: string
  outcome: string
  source?: string
}

export interface FilterableTransaction {
  event_id: string
  case_type: string
  root_cause: string
  outcome: string
  source?: string
  policy_rule_matched: string
  signals_used: string[]
}

export function filterTransactions<T extends FilterableTransaction>(
  transactions: T[],
  filters: TransactionFilters,
): T[] {
  const search = filters.searchTerm.trim().toLowerCase()

  return transactions.filter((transaction) => {
    const matchesSearch = search.length === 0
      || transaction.event_id.toLowerCase().includes(search)
      || transaction.policy_rule_matched.toLowerCase().includes(search)
      || transaction.signals_used.some((signal) => signal.toLowerCase().includes(search))
    const matchesCaseType = filters.caseType === "all" || transaction.case_type === filters.caseType
    const matchesRootCause = filters.rootCause === "all" || transaction.root_cause === filters.rootCause
    const matchesOutcome = filters.outcome === "all" || transaction.outcome === filters.outcome
    const matchesSource = !filters.source || filters.source === "all" || transaction.source === filters.source

    return matchesSearch && matchesCaseType && matchesRootCause && matchesOutcome && matchesSource
  })
}
