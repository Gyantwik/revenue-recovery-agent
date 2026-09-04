"use client"

import { useEffect, useState } from "react"
import { BarChart3, Loader2 } from "lucide-react"
import { getPolicyImpact } from "@/lib/api"
import type { PolicyImpactResponse } from "@/lib/types"
import { formatCurrency } from "@/lib/utils"

export function PolicyImpactSimulator() {
  const [impact, setImpact] = useState<PolicyImpactResponse | null>(null)
  const [error, setError] = useState<string | null>(null)
  useEffect(() => { void getPolicyImpact().then(setImpact).catch(e => setError(e instanceof Error ? e.message : "Unavailable")) }, [])
  return <section className="rounded-xl border bg-card p-5 shadow-xs">
    <div className="flex items-start gap-3"><BarChart3 className="mt-0.5 h-5 w-5 text-primary" /><div><h2 className="font-bold">Policy Impact Simulator</h2><p className="text-xs text-muted-foreground">Read-only projection. No payment or policy action is executed.</p></div></div>
    {!impact && !error && <p className="mt-4 flex items-center gap-2 text-sm text-muted-foreground"><Loader2 className="h-4 w-4 animate-spin" /> Calculating safe recovery potential…</p>}
    {error && <p className="mt-4 text-sm text-muted-foreground">Simulator unavailable: {error}</p>}
    {impact && <div className="mt-4 grid grid-cols-2 gap-3 text-sm md:grid-cols-4 xl:grid-cols-7">
      <Metric label="Total cases" value={String(impact.total_cases_considered)} />
      <Metric label="At-risk cases" value={String(impact.at_risk_cases)} />
      <Metric label="Safely settled" value={String(impact.not_at_risk_cases)} />
      <Metric label="Eligible cases" value={String(impact.eligible_cases)} />
      <Metric label="Recoverable amount" value={formatCurrency(impact.recoverable_amount, "INR")} />
      <Metric label="Expected recovery" value={`${(impact.expected_recovery_rate * 100).toFixed(1)}%`} />
      <Metric label="Safety-blocked" value={`${impact.cases_blocked_for_safety} (${formatCurrency(impact.blocked_amount, "INR")})`} />
    </div>}
  </section>
}
function Metric({ label, value }: { label: string; value: string }) { return <div className="rounded-lg border bg-muted/30 p-3"><p className="text-[11px] uppercase tracking-wide text-muted-foreground">{label}</p><p className="mt-1 font-semibold">{value}</p></div> }
