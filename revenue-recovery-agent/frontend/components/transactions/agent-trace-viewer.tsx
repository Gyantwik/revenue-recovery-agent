"use client"

import { useEffect, useState } from "react"
import { getAgentTrace } from "@/lib/api"
import type { AgentDecisionTrace } from "@/lib/types"

const STAGE_LABELS = {
  observe: "Observe", classify: "Classify", decide: "Decide",
  guardrail_check: "Guardrail Check", act: "Act",
} as const

export function AgentTraceViewer({ eventId }: { eventId: string }) {
  const [trace, setTrace] = useState<AgentDecisionTrace[]>([])
  const [error, setError] = useState<string | null>(null)
  useEffect(() => {
    let active = true
    void getAgentTrace(eventId).then(items => { if (active) setTrace(items) })
      .catch(reason => { if (active) setError(reason instanceof Error ? reason.message : "Trace unavailable") })
    return () => { active = false }
  }, [eventId])
  return <section className="space-y-2" aria-label="Agent decision trace">
    <h4 className="text-xs font-bold uppercase tracking-wider text-muted-foreground">Agent Decision Trace</h4>
    {error && <p className="text-xs text-red-700">{error}</p>}
    <ol className="space-y-2 border-l pl-4">
      {trace.map((item, index) => <li key={`${item.timestamp}-${index}`} className="relative text-xs">
        <span className="absolute -left-[21px] top-1 h-2.5 w-2.5 rounded-full bg-teal-600" />
        <p className="font-semibold">{STAGE_LABELS[item.stage]} — {item.summary}</p>
        <p className="mt-0.5 text-muted-foreground">{item.detail}</p>
        <p className="mt-0.5 text-[10px] uppercase text-muted-foreground">Actor: {item.actor.replaceAll("_", " ")}</p>
      </li>)}
    </ol>
  </section>
}
