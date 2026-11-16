import { Download } from "lucide-react"
import { buttonVariants } from "@/components/ui/button"
import { cn } from "@/lib/utils"
import { APK_URL } from "@/lib/site"

const LINKS = [
  { href: "#features", label: "Features" },
  { href: "#music", label: "Music" },
  { href: "#setup", label: "Setup" },
  { href: "#phone-upload", label: "Phone upload" },
  { href: "#faq", label: "FAQ" },
]

export function FrameMark({ className }: { className?: string }) {
  return (
    <svg viewBox="0 0 32 24" className={className} aria-hidden>
      <rect x="1.5" y="1.5" width="29" height="21" fill="none" stroke="currentColor" strokeWidth="2.5" />
      <rect x="8" y="7" width="16" height="10" fill="none" stroke="currentColor" strokeWidth="1.5" />
    </svg>
  )
}

export function SiteHeader() {
  return (
    <header className="sticky top-0 z-50 border-b bg-background/80 backdrop-blur-md">
      <div className="mx-auto flex h-16 max-w-6xl items-center justify-between gap-4 px-5">
        <a href="#top" className="flex items-center gap-2.5 text-primary" aria-label="MyFrame, back to top">
          <FrameMark className="h-5 w-7" />
          <span className="font-heading text-xl text-foreground">MyFrame</span>
        </a>
        <nav aria-label="Sections" className="hidden items-center gap-1 md:flex">
          {LINKS.map((l) => (
            <a key={l.href} href={l.href} className="rounded-md px-3 py-2 text-sm text-muted-foreground transition-colors hover:bg-card hover:text-foreground">
              {l.label}
            </a>
          ))}
        </nav>
        <a href={APK_URL} className={cn(buttonVariants({ size: "lg" }), "px-4")}>
          <Download data-icon="inline-start" />
          Download
        </a>
      </div>
    </header>
  )
}
