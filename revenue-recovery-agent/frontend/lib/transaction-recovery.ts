import type {
  RazorpayCheckoutEvent,
  RazorpayCheckoutEventRequest,
  RazorpayPaymentVerification,
  RazorpayPaymentVerificationRequest,
  RazorpayTestConfig,
  Transaction,
  TransactionRecoveryOrder,
} from "@/lib/types"
import type { RazorpayCheckoutInstance, RazorpayCheckoutOptions } from "@/lib/razorpay-checkout"

export interface TransactionRecoveryDependencies {
  createOrder(eventId: string): Promise<TransactionRecoveryOrder>
  getConfig(): Promise<RazorpayTestConfig>
  recordEvent(event: RazorpayCheckoutEventRequest): Promise<RazorpayCheckoutEvent>
  verifyPayment(request: RazorpayPaymentVerificationRequest): Promise<RazorpayPaymentVerification>
  getTransaction(eventId: string): Promise<Transaction>
  loadScript(): Promise<void>
  createCheckout(options: RazorpayCheckoutOptions): RazorpayCheckoutInstance
  onState(state: TransactionRecoveryFlowState): void
  onRecovered(transaction: Transaction): void
}

export interface TransactionRecoveryFlowState {
  message: string
  status: "order_created" | "checkout_opened" | "client_reported_unverified"
    | "recovered_by_verified_test_payment" | "verification_failed" | "error"
  order?: TransactionRecoveryOrder
}

export async function startTransactionRecoveryFlow(
  eventId: string,
  dependencies: TransactionRecoveryDependencies,
): Promise<void> {
  // Amount and currency are intentionally absent: the backend derives both from persisted data.
  const order = await dependencies.createOrder(eventId)
  dependencies.onState({ message: "Linked Test Mode recovery order created.", status: "order_created", order })
  const config = await dependencies.getConfig()
  await dependencies.loadScript()

  let reported = false
  const reportDismissal = async () => {
    if (reported) return
    reported = true
    try {
      await dependencies.recordEvent({
        internal_request_id: order.internal_request_id,
        razorpay_order_id: order.razorpay_order_id,
        event_type: "checkout_failed_or_dismissed",
        reason: "user_cancelled_or_test_failure",
      })
      dependencies.onState({
        message: "Payment was not completed. This transaction remains unrecovered.",
        status: "client_reported_unverified",
        order,
      })
    } catch {
      dependencies.onState({
        message: "Payment result could not be recorded. This transaction was not changed.",
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
    description: "Voluntary recovery payment — sandbox only",
    prefill: { name: "Test Customer", email: "test.customer@example.invalid", contact: "+919999999999" },
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
        .then(async () => {
          dependencies.onState({
            message: "Checkout reported success. Backend verification is pending.",
            status: "client_reported_unverified",
            order,
          })
          try {
            const verification = await dependencies.verifyPayment(verificationRequest)
            if (verification.recovery_event_id !== eventId
              || verification.recovery_status !== "recovered"
              || verification.link_status !== "recovered_by_verified_test_payment") {
              throw new Error("Recovery mapping was not finalized")
            }
            const transaction = await dependencies.getTransaction(eventId)
            if (transaction.outcome !== "recovered" || transaction.recovered_amount !== transaction.amount) {
              throw new Error("Recovered transaction state is inconsistent")
            }
            dependencies.onRecovered(transaction)
            dependencies.onState({
              message: "Test Mode payment verified. This transaction is now marked recovered.",
              status: "recovered_by_verified_test_payment",
              order,
            })
          } catch (error) {
            const invalid = typeof error === "object" && error !== null
              && "status" in error && error.status === 422
            dependencies.onState({
              message: "Payment could not be verified. This transaction was not changed.",
              status: invalid ? "verification_failed" : "client_reported_unverified",
              order,
            })
          }
        }).catch(() => dependencies.onState({
          message: "Payment could not be verified. This transaction was not changed.",
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
