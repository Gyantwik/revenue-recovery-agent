import type { NextRecoveryActionDecision } from "./types.ts"

export type NextActionInteraction = "disabled" | "information" | "recovery_checkout" | "test_mode_checkout"

export function getNextActionInteraction(decision: NextRecoveryActionDecision): NextActionInteraction {
  if (!decision.is_action_allowed || decision.action_type === "NONE") return "disabled"
  if (decision.action_type === "DISPLAY_INFORMATION") return "information"
  if (decision.action_type === "OPEN_RECOVERY_CHECKOUT"
    && decision.mode === "razorpay_test_recovery") return "recovery_checkout"
  if (decision.action_type === "RESUME_RECOVERY_CHECKOUT"
    && decision.mode === "razorpay_test_recovery") return "recovery_checkout"
  if (decision.action_type === "OPEN_TEST_MODE_RECOVERY_CHECKOUT"
    && decision.mode === "razorpay_test_demo") return "test_mode_checkout"
  return "disabled"
}

export function getRecoveryDemoHref(decision: NextRecoveryActionDecision): string | null {
  return getNextActionInteraction(decision) === "test_mode_checkout"
    ? `/razorpay-test?eventId=${encodeURIComponent(decision.event_id)}`
    : null
}

export function formatRecoveryStatus(decision: NextRecoveryActionDecision): string {
  if (decision.recommended_action === "ALREADY_RECOVERED") return "Already recovered"
  if (decision.recommended_action === "STOPPED_BY_POLICY") return "Stopped by policy"
  const lifecycle = decision.lifecycle_state.replaceAll("_", " ")
  if (decision.lifecycle_state === "retry_exhausted") return "Not recovered — retry limit reached"
  return lifecycle.charAt(0).toUpperCase() + lifecycle.slice(1)
}
