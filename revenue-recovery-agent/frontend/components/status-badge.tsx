import React from "react"
import { Badge } from "@/components/ui/badge"
import { RootCause, TransactionOutcome, ROOT_CAUSE_CONFIG, OUTCOME_CONFIG } from "@/lib/labels"
import { cn } from "@/lib/utils"

interface StatusBadgeProps {
  outcome: TransactionOutcome
  className?: string
}

export function StatusBadge({ outcome, className }: StatusBadgeProps) {
  const config = OUTCOME_CONFIG[outcome] || {
    label: outcome,
    color: "text-gray-600 bg-gray-50 border-gray-200",
    badgeVariant: "outline",
  }

  return (
    <Badge
      variant={config.badgeVariant}
      className={cn("font-medium shadow-none border text-xs px-2.5 py-0.5", config.color, className)}
    >
      {config.label}
    </Badge>
  )
}

interface CauseBadgeProps {
  cause: RootCause
  className?: string
}

export function CauseBadge({ cause, className }: CauseBadgeProps) {
  const config = ROOT_CAUSE_CONFIG[cause] || {
    label: cause,
    color: "text-gray-600 bg-gray-50 border-gray-200",
    badgeVariant: "outline",
  }

  return (
    <Badge
      variant={config.badgeVariant}
      className={cn("font-medium shadow-none border text-xs px-2 py-0.5", config.color, className)}
    >
      {config.label}
    </Badge>
  )
}
