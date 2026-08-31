import type {
  RazorpayCheckoutEvent,
  RazorpayCheckoutEventRequest,
  RazorpayPaymentVerification,
  RazorpayPaymentVerificationRequest,
  RazorpayTestConfig,
  RecoveryLinkedOrder,
  RecoveryPaymentStatus,
} from "@/lib/types"
import type { RazorpayCheckoutInstance, RazorpayCheckoutOptions } from "@/lib/razorpay-checkout"

export interface RecoveryDemoDependencies {
  createLinkedOrder(eventId: string): Promise<RecoveryLinkedOrder>
  getConfig(): Promise<RazorpayTestConfig>
  recordEvent(event: RazorpayCheckoutEventRequest): Promise<RazorpayCheckoutEvent>
  verifyPayment(request: RazorpayPaymentVerificationRequest): Promise<RazorpayPaymentVerification>
  getStatus(eventId: string): Promise<RecoveryPaymentStatus>
  loadScript(): Promise<void>
  createCheckout(options: RazorpayCheckoutOptions): RazorpayCheckoutInstance
  onState(state: RecoveryDemoFlowState): void
}

export interface RecoveryDemoFlowState {
  message: string
  status: "order_created" | "checkout_opened" | "client_reported_unverified"
    | "recovered_by_verified_test_payment" | "verification_failed" | "error"
  order?: RecoveryLinkedOrder
  event?: RazorpayCheckoutEvent
  verification?: RazorpayPaymentVerification
  recovery?: RecoveryPaymentStatus
}

export async function startRecoveryDemoFlow(
  eventId: string,
  dependencies: RecoveryDemoDependencies,
): Promise<void> {
  const order = await dependencies.createLinkedOrder(eventId)
  dependencies.onState({ message: "Linked Test Mode recovery order created.", status: "order_created", order })
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
        message: "Checkout was dismissed or failed. No recovery case changed.",
        status: "client_reported_unverified",
        order,
        event,
      })
    } catch {
      dependencies.onState({ message: "Unable to record checkout result. No recovery case changed.", status: "error", order })
    }
  }

  const checkout = dependencies.createCheckout({
    key: config.key_id,
    amount: order.amount,
    currency: order.currency,
    order_id: order.razorpay_order_id,
    name: "RecoverAI Test Mode",
    description: "Recovery demo — sandbox only",
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
      void dependencies.recordEvent({ ...verificationRequest, event_type: "checkout_success" })
        .then(async event => {
          dependencies.onState({
            message: "Checkout reported success. Recovery verification is pending.",
            status: "client_reported_unverified",
            order,
            event,
          })
          try {
            const verification = await dependencies.verifyPayment(verificationRequest)
            if (verification.recovery_event_id !== eventId
              || verification.recovery_status !== "recovered"
              || verification.link_status !== "recovered_by_verified_test_payment") {
              throw new Error("Recovery mapping was not finalized")
            }
            const recovery = await dependencies.getStatus(eventId)
            if (recovery.recovery_status !== "recovered"
              || recovery.link_status !== "recovered_by_verified_test_payment") {
              throw new Error("Recovery mapping status is inconsistent")
            }
            dependencies.onState({
              message: "Verified Test Mode recovery payment — case recovered.",
              status: "recovered_by_verified_test_payment",
              order,
              event,
              verification,
              recovery,
            })
          } catch (error) {
            const invalid = typeof error === "object" && error !== null
              && "status" in error && error.status === 422
            dependencies.onState({
              message: invalid
                ? "Checkout callback could not be verified. No recovery case changed."
                : "Checkout was reported, but recovery verification could not be completed. No recovery case changed.",
              status: invalid ? "verification_failed" : "client_reported_unverified",
              order,
              event,
            })
          }
        }).catch(() => dependencies.onState({
          message: "Checkout was reported, but recovery verification could not be completed. No recovery case changed.",
          status: "client_reported_unverified",
          order,
        }))
    },
    modal: { ondismiss: () => { void reportDismissal() } },
  })
  checkout.on("payment.failed", () => { void reportDismissal() })
  dependencies.onState({ message: "Razorpay Test Mode Recovery Checkout opened.", status: "checkout_opened", order })
  checkout.open()
}
