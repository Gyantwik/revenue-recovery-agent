"use client"

import React, { useEffect, useRef, useState } from "react"
import { AlertTriangle, CreditCard, ShieldCheck } from "lucide-react"
import { Button } from "@/components/ui/button"
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
import {
  createRazorpayTestOrder,
  getRazorpayTestConfig,
  recordRazorpayCheckoutEvent,
  verifyRazorpayTestPayment,
  createRecoveryTestOrder,
  getRecoveryDemoCase,
  getRecoveryTestStatus,
} from "@/lib/api"
import {
  loadRazorpayCheckoutScript,
  createSingleFlightRunner,
  startCheckoutFlow,
  type CheckoutDisplayState,
  type RazorpayCheckoutOptions,
} from "@/lib/razorpay-checkout"
import { startRecoveryDemoFlow, type RecoveryDemoFlowState } from "@/lib/recovery-demo"
import type { RecoveryDemoCase, RecoveryPaymentStatus } from "@/lib/types"

export default function RazorpayTestPage() {
  const [amount, setAmount] = useState("500.00")
  const [busy, setBusy] = useState(false)
  const [state, setState] = useState<CheckoutDisplayState | null>(null)
  const runSingleFlow = useRef(createSingleFlightRunner())
  const runRecoveryFlow = useRef(createSingleFlightRunner())
  const [demoCase, setDemoCase] = useState<RecoveryDemoCase | null>(null)
  const [demoStatus, setDemoStatus] = useState<RecoveryPaymentStatus | null>(null)
  const [demoLoadingError, setDemoLoadingError] = useState<string | null>(null)
  const [recoveryBusy, setRecoveryBusy] = useState(false)
  const [recoveryState, setRecoveryState] = useState<RecoveryDemoFlowState | null>(null)

  useEffect(() => {
    void getRecoveryDemoCase().then(async demo => {
      setDemoCase(demo)
      setDemoStatus(await getRecoveryTestStatus(demo.event_id))
    }).catch(error => {
      setDemoLoadingError(error instanceof Error ? error.message : "Unable to load the recovery demo case.")
    })
  }, [])

  async function createAndOpenRecoveryCheckout() {
    if (!demoCase) return
    await runRecoveryFlow.current(async () => {
      setRecoveryBusy(true)
      setRecoveryState(null)
      try {
        await startRecoveryDemoFlow(demoCase.event_id, {
          createLinkedOrder: createRecoveryTestOrder,
          getConfig: getRazorpayTestConfig,
          recordEvent: recordRazorpayCheckoutEvent,
          verifyPayment: verifyRazorpayTestPayment,
          getStatus: getRecoveryTestStatus,
          loadScript: loadRazorpayCheckoutScript,
          createCheckout: (options: RazorpayCheckoutOptions) => {
            if (!window.Razorpay) throw new Error("Razorpay Checkout did not initialize.")
            return new window.Razorpay(options)
          },
          onState: next => {
            setRecoveryState(next)
            if (next.recovery) setDemoStatus(next.recovery)
          },
        })
      } catch (error) {
        setRecoveryState({
          message: error instanceof Error ? error.message : "Unable to open the recovery demo Checkout.",
          status: "error",
        })
      } finally {
        setRecoveryBusy(false)
      }
    })
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
          The standalone checkout remains isolated. The separately labelled recovery demo changes only its dedicated
          demo case after server-side signature verification; neither flow claims capture or settlement.
        </p>
      </div>

      <Card className="border-teal-300">
        <CardHeader>
          <CardTitle className="flex items-center gap-2 text-xl">
            <ShieldCheck className="h-5 w-5" /> Live Test Mode Recovery Demo
          </CardTitle>
          <CardDescription>
            Razorpay Test Mode — Recovery Demo. No real money is charged. Recovery status changes only after backend
            signature verification. The synthetic benchmark remains a separate 65-case scorecard.
          </CardDescription>
        </CardHeader>
        <CardContent className="space-y-5">
          {demoLoadingError && <p className="text-sm text-red-700">{demoLoadingError}</p>}
          {demoCase && (
            <dl className="grid gap-3 text-sm sm:grid-cols-2">
              <SafeValue label="Demo event" value={demoCase.event_id} />
              <SafeValue label="Amount" value={`₹${demoCase.amount.toFixed(2)} ${demoCase.currency}`} />
              <SafeValue label="Root cause" value={demoCase.failure_root_cause} />
              <SafeValue label="Policy action" value={demoCase.policy_action} />
              <SafeValue label="Recovery status" value={demoStatus?.recovery_status ?? demoCase.recovery_status} />
              <SafeValue label="Razorpay mode" value="TEST — sandbox only" />
              {demoStatus?.link_status && <SafeValue label="Link status" value={demoStatus.link_status} />}
              {demoStatus?.razorpay_order_id && (
                <SafeValue label="Razorpay order ID" value={demoStatus.razorpay_order_id} />
              )}
              {demoStatus?.recovered_at && <SafeValue label="Recovered at" value={demoStatus.recovered_at} />}
            </dl>
          )}
          <Button
            onClick={createAndOpenRecoveryCheckout}
            disabled={!demoCase || recoveryBusy || Boolean(recoveryState?.order)
              || demoStatus?.recovery_status === "recovered" || Boolean(demoStatus?.link_status)}
          >
            {recoveryBusy ? "Preparing Recovery Checkout…" : "Create Linked Recovery Order"}
          </Button>
          {recoveryState && (
            <div className="rounded-md border bg-muted/30 p-4 text-sm">
              <p className="font-semibold">{recoveryState.status}</p>
              <p className="mt-1 text-muted-foreground">{recoveryState.message}</p>
            </div>
          )}
          {demoStatus?.audit_history && demoStatus.audit_history.length > 0 && (
            <div className="space-y-2">
              <p className="text-sm font-semibold">Demo case audit trail</p>
              {demoStatus.audit_history.map(entry => (
                <div key={`${entry.timestamp}-${entry.action}`} className="rounded-md border p-3 text-xs">
                  <p>{entry.reason}</p>
                  <p className="mt-1 text-muted-foreground">{entry.timestamp} · {entry.actor}</p>
                </div>
              ))}
            </div>
          )}
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle className="flex items-center gap-2 text-xl">
            <CreditCard className="h-5 w-5" /> Standalone sandbox checkout
          </CardTitle>
          <CardDescription>Use Razorpay-hosted Checkout only. Do not enter real personal or payment data.</CardDescription>
        </CardHeader>
        <CardContent className="space-y-5">
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
