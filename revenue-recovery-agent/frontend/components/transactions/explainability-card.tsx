"use client"

import { useEffect, useState } from "react"
import { Loader2, ShieldCheck } from "lucide-react"
import { getExplainability } from "@/lib/api"
import type { ExplainabilityResponse } from "@/lib/types"

export function ExplainabilityCard({ eventId }: { eventId: string }) {
  const [data, setData] = useState<ExplainabilityResponse | null>(null)
  const [error, setError] = useState<string | null>(null)
  useEffect(() => {
    let active = true
    setData(null); setError(null)
    void getExplainability(eventId).then(value => { if (active) setData(value) })
      .catch(reason => { if (active) setError(reason instanceof Error ? reason.message : "Unavailable") })
    return () => { active = false }
  }, [eventId])
  return <section className="rounded-lg border border-violet-200 bg-violet-50/40 p-3.5 dark:border-violet-900 dark:bg-violet-950/20">
    <h4 className="flex items-center gap-1.5 text-xs font-bold uppercase tracking-wider text-violet-800 dark:text-violet-300"><ShieldCheck className="h-3.5 w-3.5" /> Policy Explainability</h4>
    {!data && !error && <p className="mt-2 flex items-center gap-2 text-xs text-muted-foreground"><Loader2 className="h-3.5 w-3.5 animate-spin" /> Loading decision evidence…</p>}
    {error && <p className="mt-2 text-xs text-muted-foreground">Explainability unavailable: {error}</p>}
    {data && <div className="mt-3 space-y-2 text-xs">
      <p><strong>Signal → Cause:</strong> {data.signals_used.join(" · ") || "No signal recorded"} → {data.root_cause.replaceAll("_", " ")}</p>
      <p><strong>Confidence:</strong> {(data.classification_confidence * 100).toFixed(0)}% · <strong>Policy:</strong> {data.policy_rule_matched}</p>
      <p><strong>Allowed action:</strong> {data.allowed_action.replaceAll("_", " ")} · {data.attempt_number}/{data.max_attempts_allowed} attempts</p>
      {data.was_blocked && <p className="rounded border border-amber-300 bg-amber-50 p-2 text-amber-900 dark:bg-amber-950/30 dark:text-amber-200"><strong>Blocked by safety policy:</strong> {data.block_reason ?? "A policy guardrail withheld this action."}</p>}
    </div>}
  </section>
}
