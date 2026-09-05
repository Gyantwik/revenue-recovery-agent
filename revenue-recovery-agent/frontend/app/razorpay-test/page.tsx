"use client"

import React, { useRef, useState } from "react"
import { AlertTriangle, CreditCard, ShieldCheck } from "lucide-react"
import { Button } from "@/components/ui/button"
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
import {
  createRazorpayTestOrder,
  getRazorpayTestConfig,
  recordRazorpayCheckoutEvent,
  verifyRazorpayTestPayment,
  measureBackendLatency,
} from "@/lib/api"
import { DEMO_CARD_PRESETS, formatCardNumber, type DemoCardPresetId } from "@/lib/demo-card-presets"
import {
  loadRazorpayCheckoutScript,
  createSingleFlightRunner,
  startCheckoutFlow,
  type CheckoutDisplayState,
  type RazorpayCheckoutOptions,
} from "@/lib/razorpay-checkout"

export default function RazorpayTestPage() {
  const [amount, setAmount] = useState("500.00")
  const [busy, setBusy] = useState(false)
  const [state, setState] = useState<CheckoutDisplayState | null>(null)
  const runSingleFlow = useRef(createSingleFlightRunner())
  const [selectedPreset, setSelectedPreset] = useState<DemoCardPresetId>("bank_error")
  const [copyMessage, setCopyMessage] = useState<string | null>(null)
  const copyMessageTimer = useRef<number | null>(null)

  const selectPreset = (presetId: DemoCardPresetId) => {
    setSelectedPreset(presetId)
    setCopyMessage(null)
    if (copyMessageTimer.current !== null) window.clearTimeout(copyMessageTimer.current)
  }

  const copySelectedTestCard = () => {
    const preset = DEMO_CARD_PRESETS.find(item => item.id === selectedPreset) ?? DEMO_CARD_PRESETS[0]
    void navigator.clipboard.writeText(preset.card).then(() => {
      setCopyMessage("Test card copied.")
      if (copyMessageTimer.current !== null) window.clearTimeout(copyMessageTimer.current)
      copyMessageTimer.current = window.setTimeout(() => setCopyMessage(null), 2_000)
    }).catch(() => setCopyMessage("Could not copy the test card. Please copy it manually."))
  }

  async function createAndOpenCheckout() {
    const parsedAmount = Number(amount)
    if (!Number.isFinite(parsedAmount) || parsedAmount < 1 || parsedAmount > 10000) {
      setState({ message: "Enter an amount from ₹1 to ₹10,000.", status: "error" })
      return
    }

    await runSingleFlow.current(async () => {
      setBusy(true)
      setState(null)
      try {
        const preset = DEMO_CARD_PRESETS.find(item => item.id === selectedPreset) ?? DEMO_CARD_PRESETS[0]
        await startCheckoutFlow(parsedAmount, {
          createOrder: createRazorpayTestOrder,
          getConfig: getRazorpayTestConfig,
          recordEvent: recordRazorpayCheckoutEvent,
          verifyPayment: verifyRazorpayTestPayment,
          loadScript: loadRazorpayCheckoutScript,
          createCheckout: (options: RazorpayCheckoutOptions) => {
            if (!window.Razorpay) throw new Error("Razorpay Checkout did not initialize.")
            return new window.Razorpay(options)
          },
          onState: setState,
          measureLatency: measureBackendLatency,
          simulatedConnection: selectedPreset === "weak_network",
          failureReason: preset.expectedReason ?? undefined,
        })
      } catch (error) {
        setState({
          message: error instanceof Error ? error.message : "Unable to open Razorpay Test Mode Checkout.",
          status: "error",
        })
      } finally {
        setBusy(false)
      }
    })
  }

  const visible = state?.verification ?? state?.event ?? state?.order
  return (
    <div className="mx-auto max-w-3xl space-y-6">
      <div className="space-y-2">
        <div className="inline-flex items-center gap-2 rounded-full border border-amber-300 bg-amber-50 px-3 py-1 text-xs font-semibold text-amber-800">
          <AlertTriangle className="h-3.5 w-3.5" /> Sandbox only
        </div>
        <h1 className="text-3xl font-bold tracking-tight">Razorpay Test Mode — Checkout Demo</h1>
        <p className="text-sm leading-6 text-muted-foreground">
          This creates a sandbox order and opens Razorpay Test Mode Checkout. No real money is charged.
          Failure recovery is available from each eligible transaction’s detail drawer after server-side verification.
        </p>
      </div>

      <Card>
        <CardHeader>
          <CardTitle className="flex items-center gap-2 text-xl">
            <CreditCard className="h-5 w-5" /> Payment Failure Simulator (Razorpay Sandbox)
          </CardTitle>
          <CardDescription>Use Razorpay-hosted Checkout only. Do not enter real personal or payment data.</CardDescription>
        </CardHeader>
        <CardContent className="space-y-5">
          <div className="rounded-lg border border-dashed border-amber-400 bg-amber-50/60 p-4 text-sm text-amber-950">
            <p className="font-semibold">Demo aid: choose a Razorpay Test Mode scenario</p>
            <p className="mt-1 text-xs">Hosted Razorpay card fields cannot be safely auto-filled by this merchant page. Select a preset, copy its documented test card, then paste/type it into Checkout.</p>
            <div className="mt-3 flex flex-wrap gap-2">
              {DEMO_CARD_PRESETS.map(preset => <Button key={preset.id} type="button" size="sm"
                variant={selectedPreset === preset.id ? "default" : "outline"}
                onClick={() => selectPreset(preset.id)}>{preset.label}</Button>)}
            </div>
            <div className="mt-3 flex flex-wrap items-center gap-2">
              <code className="rounded bg-white px-2 py-1">{formatCardNumber(
                (DEMO_CARD_PRESETS.find(preset => preset.id === selectedPreset) ?? DEMO_CARD_PRESETS[0]).card,
              )}</code>
              <Button type="button" size="sm" variant="outline" onClick={copySelectedTestCard}>Copy test card</Button>
              {copyMessage && <span role="status" className="text-xs">{copyMessage}</span>}
            </div>
          </div>
          <label className="block space-y-2 text-sm font-medium" htmlFor="test-amount">
            Amount in INR
            <div className="flex items-center rounded-md border bg-background focus-within:ring-2 focus-within:ring-ring">
              <span className="px-3 text-muted-foreground">₹</span>
              <input
                id="test-amount"
                type="number"
                min="1"
                max="10000"
                step="0.01"
                value={amount}
                onChange={event => setAmount(event.target.value)}
                disabled={busy}
                className="h-11 flex-1 bg-transparent pr-3 outline-none disabled:opacity-50"
              />
            </div>
          </label>
          <Button onClick={createAndOpenCheckout} disabled={busy} className="w-full sm:w-auto">
            {busy ? "Preparing Test Checkout…" : "Create and Open Test Checkout"}
          </Button>
        </CardContent>
      </Card>

      {state && (
        <Card className={state.status === "error" ? "border-red-300" : "border-teal-300"}>
          <CardHeader>
            <CardTitle className="flex items-center gap-2 text-lg">
              <ShieldCheck className="h-5 w-5" /> {state.status}
            </CardTitle>
            <CardDescription>{state.message}</CardDescription>
          </CardHeader>
          {visible && (
            <CardContent>
              <dl className="grid gap-3 text-sm sm:grid-cols-2">
                <SafeValue label="Internal request ID" value={visible.internal_request_id} />
                <SafeValue label="Razorpay order ID" value={visible.razorpay_order_id} />
                {state.event?.razorpay_payment_id && (
                  <SafeValue label="Client-reported payment ID" value={state.event.razorpay_payment_id} />
                )}
                {state.event && <SafeValue label="Event type" value={state.event.event_type} />}
                {state.event && <SafeValue label="Timestamp" value={state.event.timestamp} />}
                {state.verification && (
                  <SafeValue label="Verification status" value={state.verification.verification_status} />
                )}
                {state.verification?.verified_at && (
                  <SafeValue label="Verified at" value={state.verification.verified_at} />
                )}
              </dl>
            </CardContent>
          )}
        </Card>
      )}
    </div>
  )
}

function SafeValue({ label, value }: { label: string; value: string }) {
  return (
    <div>
      <dt className="text-xs font-medium uppercase tracking-wide text-muted-foreground">{label}</dt>
      <dd className="mt-1 break-all font-mono text-xs">{value}</dd>
    </div>
  )
}
