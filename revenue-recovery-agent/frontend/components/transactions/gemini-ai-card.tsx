"use client"

import { useEffect, useState, type ReactNode } from "react"
import {
  Check,
  Clock3,
  Copy,
  ExternalLink,
  Loader2,
  MessageCircle,
  RefreshCw,
  ShieldCheck,
  Sparkles,
} from "lucide-react"
import { Button } from "@/components/ui/button"
import { generateAiRecoveryMessage, getAiAnalysis } from "@/lib/api"
import type {
  AiAnalysisResponse,
  AiMessageChannel,
  AiMessageResponse,
  Transaction,
} from "@/lib/types"

const STAGE_LABELS = {
  OBSERVE: "Observe",
  CLASSIFY: "Classify",
  DECIDE: "Decide",
  GUARDRAIL_CHECK: "Guardrail Check",
  ACT: "Act",
} as const

const CHANNEL_LABELS: Record<AiMessageChannel, string> = {
  WHATSAPP: "WhatsApp",
  SMS: "SMS",
  EMAIL: "Email",
}

export function GeminiAiCard({ transaction }: { transaction: Transaction }) {
  const [analysis, setAnalysis] = useState<AiAnalysisResponse | null>(null)
  const [analysisLoading, setAnalysisLoading] = useState(true)
  const [analysisError, setAnalysisError] = useState<string | null>(null)
  const [channel, setChannel] = useState<AiMessageChannel>("WHATSAPP")
  const [generatedMessage, setGeneratedMessage] = useState<AiMessageResponse | null>(null)
  const [messageLoading, setMessageLoading] = useState(false)
  const [messageError, setMessageError] = useState<string | null>(null)
  const [copied, setCopied] = useState(false)

  const loadAnalysis = async () => {
    setAnalysisLoading(true)
    setAnalysisError(null)
    try {
      setAnalysis(await getAiAnalysis(transaction.event_id))
    } catch (error) {
      setAnalysisError(error instanceof Error ? error.message : "Gemini AI analysis is unavailable")
    } finally {
      setAnalysisLoading(false)
    }
  }

  useEffect(() => {
    let active = true
    setAnalysis(null)
    setAnalysisError(null)
    setGeneratedMessage(null)
    setMessageError(null)
    setCopied(false)
    setAnalysisLoading(true)
    void getAiAnalysis(transaction.event_id)
      .then(result => { if (active) setAnalysis(result) })
      .catch(error => {
        if (active) setAnalysisError(error instanceof Error ? error.message : "Gemini AI analysis is unavailable")
      })
      .finally(() => { if (active) setAnalysisLoading(false) })
    return () => { active = false }
  }, [transaction.event_id])

  const generateMessage = async () => {
    setMessageLoading(true)
    setMessageError(null)
    setGeneratedMessage(null)
    setCopied(false)
    try {
      setGeneratedMessage(await generateAiRecoveryMessage(
        transaction.event_id,
        channel,
        transaction.customer_ref,
      ))
    } catch (error) {
      setMessageError(error instanceof Error ? error.message : "AI recovery copy could not be generated")
    } finally {
      setMessageLoading(false)
    }
  }

  const copyMessage = async () => {
    if (!generatedMessage) return
    try {
      await navigator.clipboard.writeText(generatedMessage.message)
      setCopied(true)
      window.setTimeout(() => setCopied(false), 2000)
    } catch {
      setMessageError("Could not copy the message. Select the text and copy it manually.")
    }
  }

  const whatsappHref = generatedMessage
    ? `https://wa.me/?text=${encodeURIComponent(generatedMessage.message)}`
    : "#"
  const messageEligible = transaction.root_cause === "checkout_abandoned"

  return (
    <section className="overflow-hidden rounded-xl border border-violet-200 bg-gradient-to-br from-violet-50/80 via-background to-cyan-50/60 shadow-sm dark:border-violet-900 dark:from-violet-950/25 dark:to-cyan-950/20">
      <div className="flex flex-wrap items-center justify-between gap-2 border-b border-violet-200/70 px-4 py-3 dark:border-violet-900/70">
        <div className="flex items-center gap-2">
          <span className="rounded-md bg-violet-600 p-1.5 text-white shadow-sm">
            <Sparkles className="h-4 w-4" />
          </span>
          <div>
            <h4 className="text-sm font-bold text-foreground">
              {analysis?.analysis_source === "gemini" ? "Gemini AI Analysis" : "Rules-based policy diagnosis"}
            </h4>
            <p className="text-[11px] text-muted-foreground">Semantic failure analysis with policy guardrails</p>
          </div>
        </div>
        {analysis && (
          <span className="rounded-full border border-violet-200 bg-violet-100 px-2.5 py-1 text-[11px] font-semibold text-violet-800 dark:border-violet-800 dark:bg-violet-950 dark:text-violet-200">
            {analysis.analysis_source === "gemini" ? "Gemini AI Analysis" : "Rules-based policy diagnosis"} · {(analysis.confidence * 100).toFixed(0)}% Confidence
          </span>
        )}
      </div>

      <div className="space-y-4 p-4">
        {analysisLoading && <AnalysisSkeleton />}

        {analysisError && !analysisLoading && (
          <div role="alert" className="rounded-lg border border-amber-200 bg-amber-50 p-3 text-xs text-amber-900 dark:border-amber-900 dark:bg-amber-950/30 dark:text-amber-200">
            <p><strong>AI diagnosis unavailable.</strong> {analysisError}</p>
            <Button type="button" variant="outline" size="sm" className="mt-2 h-7 gap-1.5 text-xs" onClick={() => { void loadAnalysis() }}>
              <RefreshCw className="h-3 w-3" /> Retry analysis
            </Button>
          </div>
        )}

        {analysis && !analysisLoading && (
          <>
            <div className="rounded-lg border border-violet-200 bg-white/80 p-3 text-sm leading-6 text-foreground dark:border-violet-900 dark:bg-background/60">
              <p className="mb-1 text-[10px] font-bold uppercase tracking-[0.16em] text-violet-700 dark:text-violet-300">AI Explanation</p>
              {analysis.ai_explanation}
            </div>

            <div className="grid gap-2 sm:grid-cols-2">
              <Insight icon={<Clock3 className="h-4 w-4 text-cyan-600" />} label="Optimal Retry Timing" value={analysis.optimal_retry_timing} />
              <Insight icon={<ShieldCheck className="h-4 w-4 text-emerald-600" />} label="Risk Assessment" value={analysis.risk_assessment} />
            </div>

            <div>
              <h5 className="mb-2 text-xs font-bold uppercase tracking-wider text-muted-foreground">Agent Decision Trace</h5>
              <ol className="space-y-2 border-l border-violet-300 pl-4 dark:border-violet-800">
                {analysis.reasoning_trace.map((step, index) => (
                  <li key={step.stage} className="relative rounded-md bg-background/70 p-2.5 text-xs">
                    <span className="absolute -left-[21px] top-3 h-2.5 w-2.5 rounded-full bg-violet-600 ring-4 ring-violet-100 dark:ring-violet-950" />
                    <p className="font-semibold text-violet-800 dark:text-violet-200">
                      {index + 1}. {STAGE_LABELS[step.stage]}
                    </p>
                    <p className="mt-1 text-muted-foreground">{step.thought}</p>
                    <p className="mt-1"><span className="font-medium">Conclusion:</span> {step.conclusion}</p>
                  </li>
                ))}
              </ol>
            </div>
          </>
        )}

        <div className="border-t border-violet-200/70 pt-4 dark:border-violet-900/70">
          <div className="mb-3 flex items-center gap-2">
            <MessageCircle className="h-4 w-4 text-violet-600" />
            <h5 className="text-xs font-bold uppercase tracking-wider text-muted-foreground">Draft AI Recovery Message</h5>
          </div>

          {messageEligible ? (
            <>
              <div className="flex flex-wrap items-end gap-2">
                <label className="grid gap-1 text-xs font-medium">
                  Channel
                  <select
                    value={channel}
                    onChange={event => setChannel(event.target.value as AiMessageChannel)}
                    className="h-9 rounded-md border border-input bg-background px-3 text-xs outline-none focus:ring-2 focus:ring-ring"
                    aria-label="Recovery message channel"
                  >
                    {Object.entries(CHANNEL_LABELS).map(([value, label]) => (
                      <option key={value} value={value}>{label}</option>
                    ))}
                  </select>
                </label>
                <Button type="button" size="sm" className="gap-1.5 text-xs" disabled={messageLoading} onClick={() => { void generateMessage() }}>
                  {messageLoading ? <Loader2 className="h-3.5 w-3.5 animate-spin" /> : <Sparkles className="h-3.5 w-3.5" />}
                  {messageLoading ? "Generating…" : "Generate AI Recovery Copy"}
                </Button>
              </div>

              {messageError && <p role="alert" className="mt-2 text-xs text-red-700 dark:text-red-300">{messageError}</p>}

              {generatedMessage && (
                <div className="mt-3 rounded-lg border bg-background/90 p-3">
                  <div className="flex flex-wrap items-center justify-between gap-2 border-b pb-2">
                    <div>
                      <p className="text-[10px] font-semibold uppercase text-muted-foreground">{CHANNEL_LABELS[channel]} Preview</p>
                      <p className="text-sm font-semibold">{generatedMessage.subject}</p>
                    </div>
                    <span className="rounded bg-muted px-2 py-1 text-[10px] font-medium">{generatedMessage.suggested_cta}</span>
                  </div>
                  <p className="whitespace-pre-wrap py-3 text-sm leading-6">{generatedMessage.message}</p>
                  <div className="flex flex-wrap items-center gap-2 border-t pt-2">
                    <Button type="button" variant="outline" size="sm" className="gap-1.5 text-xs" onClick={() => { void copyMessage() }} title={copied ? "Copied!" : "Copy message"}>
                      {copied ? <Check className="h-3.5 w-3.5 text-emerald-600" /> : <Copy className="h-3.5 w-3.5" />}
                      {copied ? "Copied!" : "Copy Message"}
                    </Button>
                    <Button asChild type="button" variant="outline" size="sm" className="gap-1.5 text-xs">
                      <a href={whatsappHref} target="_blank" rel="noreferrer">
                        Open in WhatsApp <ExternalLink className="h-3.5 w-3.5" />
                      </a>
                    </Button>
                    {copied && <span role="status" className="text-xs font-medium text-emerald-700 dark:text-emerald-300">Copied!</span>}
                  </div>
                </div>
              )}
            </>
          ) : (
            <p className="rounded-md bg-muted/50 p-2.5 text-xs text-muted-foreground">
              Recovery copy is available for checkout-abandoned cases only. This transaction remains governed by its existing policy action.
            </p>
          )}
        </div>
      </div>
    </section>
  )
}

function Insight({ icon, label, value }: { icon: ReactNode; label: string; value: string }) {
  return (
    <div className="rounded-lg border bg-background/70 p-3">
      <p className="flex items-center gap-1.5 text-[10px] font-bold uppercase tracking-wide text-muted-foreground">{icon}{label}</p>
      <p className="mt-1.5 text-xs font-medium leading-5">{value}</p>
    </div>
  )
}

function AnalysisSkeleton() {
  return (
    <div role="status" aria-label="Loading Gemini AI analysis" className="space-y-3">
      <p className="flex items-center gap-2 text-xs text-muted-foreground"><Loader2 className="h-3.5 w-3.5 animate-spin" /> Gemini is analyzing the failure…</p>
      <div className="h-20 animate-pulse rounded-lg bg-muted" />
      <div className="grid gap-2 sm:grid-cols-2"><div className="h-16 animate-pulse rounded-lg bg-muted" /><div className="h-16 animate-pulse rounded-lg bg-muted" /></div>
    </div>
  )
}
