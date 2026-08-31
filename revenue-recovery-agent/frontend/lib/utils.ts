import { type ClassValue, clsx } from "clsx"
import { twMerge } from "tailwind-merge"
export { formatCurrency, formatRecoveryRate, formatAttemptCount } from "@/lib/formatters"

export function cn(...inputs: ClassValue[]) {
  return twMerge(clsx(inputs))
}

export function formatDateTime(isoString: string): string {
  try {
    const date = new Date(isoString)
    return new Intl.DateTimeFormat("en-IN", {
      day: "numeric",
      month: "short",
      year: "numeric",
      hour: "2-digit",
      minute: "2-digit",
    }).format(date)
  } catch (e) {
    return isoString
  }
}
