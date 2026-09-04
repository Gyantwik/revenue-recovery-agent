export const DEMO_CARD_PRESETS = [
  { id: "bank_error", label: "Bank Error", card: "4100280000020007", expectedReason: "gateway_technical_error" },
  { id: "insufficient_balance", label: "Insufficient Balance", card: "4100280000080001", expectedReason: "insufficient_fund" },
  { id: "wrong_pin", label: "Wrong OTP/PIN", card: "4100280000000009", expectedReason: "authentication_failed" },
  { id: "weak_network", label: "Weak Network / Timeout", card: "4100280000090000", expectedReason: "payment_timed_out" },
] as const

export type DemoCardPresetId = (typeof DEMO_CARD_PRESETS)[number]["id"]

export function formatCardNumber(card: string): string {
  return card.replace(/(.{4})/g, "$1 ").trim()
}
