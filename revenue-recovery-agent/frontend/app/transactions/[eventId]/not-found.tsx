import Link from "next/link"

export default function TransactionNotFound() {
  return (
    <div className="rounded-lg border bg-card p-8 text-center">
      <h1 className="text-xl font-bold">Transaction not found</h1>
      <p className="mt-2 text-sm text-muted-foreground">The requested event ID does not exist.</p>
      <Link href="/transactions" className="mt-4 inline-block text-sm font-semibold text-primary hover:underline">
        Return to transactions
      </Link>
    </div>
  )
}
