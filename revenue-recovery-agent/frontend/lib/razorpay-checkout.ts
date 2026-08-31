import type {
  RazorpayCheckoutEvent,
  RazorpayCheckoutEventRequest,
  RazorpayTestConfig,
  RazorpayTestOrder,
  RazorpayPaymentVerification,
  RazorpayPaymentVerificationRequest,
} from "@/lib/types"

export const CHECKOUT_SCRIPT_URL = "https://checkout.razorpay.com/v1/checkout.js"

export interface RazorpaySuccessResponse {
  razorpay_payment_id: string
  razorpay_order_id: string
  razorpay_signature: string
}

export interface RazorpayCheckoutOptions {
  key: string
  amount: number
  currency: string
  order_id: string
  name: string
  description: string
  prefill: { name: string; email: string; contact: string }
  theme: { color: string }
  handler: (response: RazorpaySuccessResponse) => void
  modal: { ondismiss: () => void }
}

export interface RazorpayCheckoutInstance {
  open(): void
  on(event: "payment.failed", handler: () => void): void
}

export type RazorpayConstructor = new (options: RazorpayCheckoutOptions) => RazorpayCheckoutInstance

declare global {
  interface Window {
    Razorpay?: RazorpayConstructor
  }
}

let checkoutScriptPromise: Promise<void> | null = null

export function createSingleFlightRunner() {
  let inFlight = false
  return async (task: () => Promise<void>): Promise<boolean> => {
    if (inFlight) return false
    inFlight = true
    try {
      await task()
      return true
    } finally {
      inFlight = false
    }
  }
}

export function loadRazorpayCheckoutScript(doc: Document = document): Promise<void> {
  if (typeof window !== "undefined" && window.Razorpay) return Promise.resolve()
  if (checkoutScriptPromise) return checkoutScriptPromise

  checkoutScriptPromise = new Promise((resolve, reject) => {
    const existing = doc.querySelector<HTMLScriptElement>(`script[src="${CHECKOUT_SCRIPT_URL}"]`)
    const script = existing ?? doc.createElement("script")
    script.src = CHECKOUT_SCRIPT_URL
    script.async = true
    script.onload = () => resolve()
    script.onerror = () => {
      checkoutScriptPromise = null
      if (!existing) script.remove()
      reject(new Error("Razorpay Checkout could not be loaded. Check your connection and try again."))
    }
    if (!existing) doc.body.appendChild(script)
  })
  return checkoutScriptPromise
}

export interface CheckoutFlowDependencies {
  createOrder(amount: number): Promise<RazorpayTestOrder>
  getConfig(): Promise<RazorpayTestConfig>
  recordEvent(event: RazorpayCheckoutEventRequest): Promise<RazorpayCheckoutEvent>
  verifyPayment(request: RazorpayPaymentVerificationRequest): Promise<RazorpayPaymentVerification>
  loadScript(): Promise<void>
  createCheckout(options: RazorpayCheckoutOptions): RazorpayCheckoutInstance
  onState(state: CheckoutDisplayState): void
}

export interface CheckoutDisplayState {
  message: string
  status: "order_created" | "checkout_opened" | "client_reported_unverified"
    | "verified_test_payment" | "verification_failed" | "error"
  event?: RazorpayCheckoutEvent
  verification?: RazorpayPaymentVerification
  order?: RazorpayTestOrder
}

export async function startCheckoutFlow(amount: number, dependencies: CheckoutFlowDependencies): Promise<void> {
  const order = await dependencies.createOrder(amount)
  dependencies.onState({ message: "Test order created.", status: "order_created", order })
  const config = await dependencies.getConfig()
  await dependencies.loadScript()

  let reported = false
  const reportDismissal = async () => {
    if (reported) return
    reported = true
    try {
      const event = await dependencies.recordEvent({
        internal_request_id: order.internal_request_id,
        razorpay_order_id: order.razorpay_order_id,
        event_type: "checkout_failed_or_dismissed",
        reason: "user_cancelled_or_test_failure",
      })
      dependencies.onState({
        message: "Checkout was dismissed or failed. No payment was verified.",
        status: "client_reported_unverified",
        event,
        order,
      })
    } catch (error) {
      dependencies.onState({
        message: error instanceof Error ? error.message : "Unable to record checkout dismissal.",
        status: "error",
        order,
      })
    }
  }

  const checkout = dependencies.createCheckout({
    key: config.key_id,
    amount: order.amount,
    currency: order.currency,
    order_id: order.razorpay_order_id,
    name: "RecoverAI Test Mode",
    description: "Sandbox payment only",
    prefill: {
      name: "Test Customer",
      email: "test.customer@example.invalid",
      contact: "+919999999999",
    },
    theme: { color: "#0f766e" },
    handler: response => {
      if (reported) return
      reported = true
      const verificationRequest = {
        internal_request_id: order.internal_request_id,
        razorpay_order_id: response.razorpay_order_id,
        razorpay_payment_id: response.razorpay_payment_id,
        razorpay_signature: response.razorpay_signature,
      }
      void dependencies.recordEvent({
        ...verificationRequest,
        event_type: "checkout_success",
      }).then(async event => {
        dependencies.onState({
          message: "Checkout reported success. Server verification is pending.",
          status: "client_reported_unverified",
          event,
          order,
        })
        try {
          const verification = await dependencies.verifyPayment(verificationRequest)
          dependencies.onState({
            message: "Razorpay Test Mode payment signature verified. This verifies checkout authenticity only; it does not update a recovery case in Phase 4C.",
            status: "verified_test_payment",
            event,
            verification,
            order,
          })
        } catch (error) {
          const invalidSignature = typeof error === "object" && error !== null
            && "status" in error && error.status === 422
          dependencies.onState({
            message: invalidSignature
              ? "Checkout callback could not be verified. No recovery case was changed."
              : "Checkout was reported, but verification could not be completed. No recovery case was changed.",
            status: invalidSignature ? "verification_failed" : "client_reported_unverified",
            event,
            order,
          })
        }
      }).catch(() => dependencies.onState({
        message: "Checkout was reported, but verification could not be completed. No recovery case was changed.",
        status: "client_reported_unverified",
        order,
      }))
    },
    modal: { ondismiss: () => { void reportDismissal() } },
  })
  checkout.on("payment.failed", () => { void reportDismissal() })
  dependencies.onState({ message: "Razorpay Test Mode Checkout opened.", status: "checkout_opened", order })
  checkout.open()
}
