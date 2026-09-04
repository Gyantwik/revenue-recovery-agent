export function formatCurrency(amount: number, currency: string = "INR"): string {
  return new Intl.NumberFormat("en-IN", {
    style: "currency",
    currency,
    maximumFractionDigits: 0,
  }).format(amount)
}

export function formatRecoveryRate(rate: number): string {
  return `${(rate * 100).toFixed(1)}%`
}

export function formatAttemptCount(attemptNumber: number, maxAttempts: number): string {
  if (attemptNumber === 0 && maxAttempts === 0) return "N/A (Policy)"
  return `${attemptNumber}/${maxAttempts}`
}

export function isAttemptCountValid(attemptNumber: number, maxAttempts: number): boolean {
  return attemptNumber >= 0 && maxAttempts >= 0 && attemptNumber <= maxAttempts
}
