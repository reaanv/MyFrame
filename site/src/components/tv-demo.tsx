"use client"

import { AnimatePresence, motion } from "motion/react"
import { Music2, Play, Store, X } from "lucide-react"
import { useState } from "react"
import { Button } from "@/components/ui/button"

type App = "Spotify" | "YouTube Music"

/**
 * A small, honest re-creation of what the TV shows when a music app is missing:
 * the tile says so, and OK opens a dialog that sends you to the Google Play page.
 */
export function MissingAppDemo() {
  const [open, setOpen] = useState<App | null>(null)
  const tiles: { name: App; label: string }[] = [
    { name: "Spotify", label: "Spotify" },
    { name: "YouTube Music", label: "YT Music" },
  ]

  return (
    <div className="relative min-h-[23rem] overflow-hidden rounded-2xl border bg-black/60 p-5 sm:min-h-[21rem] sm:p-7">
      <p className="mb-1 text-xs tracking-[0.2em] text-primary uppercase">On the TV</p>
      <p className="mb-5 text-sm text-muted-foreground">Try it: choose a tile that is not installed.</p>
      <div className="grid grid-cols-2 gap-3">
        {tiles.map((t) => (
          <motion.button
            key={t.name}
            type="button"
            onClick={() => setOpen(t.name)}
            whileHover={{ scale: 1.03 }}
            whileFocus={{ scale: 1.03 }}
            whileTap={{ scale: 0.98 }}
            className="flex min-h-28 flex-col justify-between rounded-xl border bg-card/80 p-4 text-left outline-none focus-visible:border-primary focus-visible:ring-3 focus-visible:ring-primary/40"
          >
            <Music2 className="size-6 text-muted-foreground" aria-hidden />
            <span>
              <span className="block font-medium">{t.label}</span>
              <span className="block text-xs text-muted-foreground">Not installed</span>
            </span>
          </motion.button>
        ))}
      </div>

      <AnimatePresence>
        {open && (
          <motion.div
            key="scrim"
            className="absolute inset-0 grid place-items-center bg-black/70 p-4"
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            exit={{ opacity: 0 }}
          >
            <motion.div
              role="dialog"
              aria-modal="true"
              aria-labelledby="demo-title"
              className="w-full max-w-sm rounded-2xl border bg-background p-5 shadow-2xl"
              initial={{ y: 20, scale: 0.96 }}
              animate={{ y: 0, scale: 1 }}
              exit={{ y: 10, scale: 0.98 }}
              transition={{ type: "spring", stiffness: 260, damping: 24 }}
            >
              <div className="flex items-start justify-between gap-3">
                <h3 id="demo-title" className="font-heading text-xl">
                  {open} is not installed
                </h3>
                <button type="button" aria-label="Close" onClick={() => setOpen(null)} className="rounded-md p-1 text-muted-foreground hover:text-foreground">
                  <X className="size-4" />
                </button>
              </div>
              <p className="mt-2 text-sm text-muted-foreground">
                MyFrame opens the real app but cannot include it. Install it from the Google Play Store, then come back and try again.
              </p>
              <div className="mt-5 flex gap-2">
                <Button variant="outline" onClick={() => setOpen(null)} autoFocus>
                  Cancel
                </Button>
                <Button onClick={() => setOpen(null)}>
                  <Store data-icon="inline-start" />
                  Open Play Store
                </Button>
              </div>
              <p className="mt-3 flex items-center gap-1.5 text-xs text-muted-foreground">
                <Play className="size-3" aria-hidden /> On the TV this opens the app&apos;s Google Play page.
              </p>
            </motion.div>
          </motion.div>
        )}
      </AnimatePresence>
    </div>
  )
}
