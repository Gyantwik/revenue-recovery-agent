import type { Transaction } from "@/lib/mock-data"

const API_BASE_URL = (process.env.NEXT_PUBLIC_API_BASE_URL ?? "http://localhost:8080")
  .replace(/\/$/, "")

interface BackendErrorBody {
  error?: string
}

export async function getTransactions(): Promise<Transaction[]> {
  const response = await fetch(`${API_BASE_URL}/api/transactions`, {
    cache: "no-store",
  })

  if (!response.ok) {
    let message = `Backend request failed with status ${response.status}`
    try {
      const body = (await response.json()) as BackendErrorBody
      if (body.error) {
        message = body.error
      }
    } catch {
      // The status code is still surfaced when the backend does not return JSON.
    }
    throw new Error(message)
  }

  const body: unknown = await response.json()
  if (!Array.isArray(body)) {
    throw new Error("Backend returned an invalid transactions response")
  }

  return body as Transaction[]
}
