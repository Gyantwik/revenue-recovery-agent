"use client"

import React from "react"
import Link from "next/link"
import { usePathname } from "next/navigation"
import { ShieldCheck, Activity, ArrowRightLeft, CreditCard } from "lucide-react"
import { cn } from "@/lib/utils"

export function SiteNav() {
  const pathname = usePathname()

  const navItems = [
    { href: "/", label: "Recovery Overview", icon: Activity },
    { href: "/transactions", label: "All Transactions", icon: ArrowRightLeft },
    { href: "/razorpay-test", label: "Test Checkout", icon: CreditCard },
  ]

  return (
    <header className="sticky top-0 z-40 w-full border-b bg-background/95 backdrop-blur supports-[backdrop-filter]:bg-background/60">
      <div className="container flex h-16 items-center justify-between px-4 sm:px-8">
        <div className="flex items-center gap-6 md:gap-10">
          <Link href="/" className="flex items-center gap-2.5 group">
            <div className="flex h-9 w-9 items-center justify-center rounded-lg bg-primary text-primary-foreground shadow-sm transition-transform group-hover:scale-105">
              <ShieldCheck className="h-5 w-5" />
            </div>
            <div>
              <div className="text-base font-bold tracking-tight text-foreground flex items-center gap-1.5">
                RecovrAI <span className="text-xs font-semibold px-2 py-0.5 rounded bg-emerald-100 text-emerald-800 dark:bg-emerald-950/60 dark:text-emerald-400 border border-emerald-300 dark:border-emerald-800">Engine v2.2</span>
              </div>
              <p className="text-xs text-muted-foreground hidden sm:block">Smart Subscription & Mandate Recovery</p>
            </div>
          </Link>

          <nav className="flex items-center space-x-1 sm:space-x-2 text-sm font-medium">
            {navItems.map(item => {
              const Icon = item.icon
              const isActive = pathname === item.href
              return (
                <Link
                  key={item.href}
                  href={item.href}
                  className={cn(
                    "flex items-center gap-2 px-3 py-2 rounded-md transition-colors text-sm",
                    isActive
                      ? "bg-secondary text-secondary-foreground font-semibold shadow-xs"
                      : "text-muted-foreground hover:text-foreground hover:bg-accent/50"
                  )}
                >
                  <Icon className="h-4 w-4" />
                  {item.label}
                </Link>
              )
            })}
          </nav>
        </div>

        <div className="flex items-center gap-3">
          <div className="hidden md:flex items-center gap-2 px-3 py-1.5 rounded-full bg-emerald-50 dark:bg-emerald-950/50 border border-emerald-200 dark:border-emerald-800 text-xs text-emerald-700 dark:text-emerald-300">
            <span className="h-2 w-2 rounded-full bg-emerald-500 animate-pulse" />
            <span>AI Polling Active (NPCI / UPI 2.0)</span>
          </div>
        </div>
      </div>
    </header>
  )
}
