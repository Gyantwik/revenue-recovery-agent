"use client"

import { Button } from "@/components/ui/button"

export function ApiErrorState({ message }: { message: string }) {
  return (
    <div role="alert" className="rounded-lg border border-red-200 bg-red-50 p-4 text-sm text-red-800">
      <p>Backend unavailable: {message}. Confirm Spring Boot is running on the configured API URL.</p>
      <Button className="mt-3" size="sm" variant="outline" onClick={() => window.location.reload()}>
        Retry
      </Button>
    </div>
  )
}
