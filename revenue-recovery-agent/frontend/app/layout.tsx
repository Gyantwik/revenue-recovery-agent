import type { Metadata } from "next"
import { Inter } from "next/font/google"
import "./globals.css"
import { SiteNav } from "@/components/site-nav"

const inter = Inter({ subsets: ["latin"] })

export const metadata: Metadata = {
  title: "AI Revenue Recovery Dashboard | RecovrAI",
  description: "Autonomous smart retry, policy enforcement, and mandate recovery engine for recurring SaaS & fintech billing.",
}

export default function RootLayout({
  children,
}: {
  children: React.ReactNode
}) {
  return (
    <html lang="en">
      <body className={inter.className}>
        <div className="min-h-screen flex flex-col bg-slate-50/50 dark:bg-background">
          <SiteNav />
          <main className="flex-1 container mx-auto px-4 sm:px-8 py-6 max-w-7xl">
            {children}
          </main>
          <footer className="border-t py-4 text-center text-xs text-muted-foreground">
            RecovrAI Engine • NPCI e-Mandate & UPI AutoPay Policy Enforcer v2.2
          </footer>
        </div>
      </body>
    </html>
  )
}
