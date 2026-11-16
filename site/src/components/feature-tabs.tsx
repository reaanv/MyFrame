"use client"

import { AnimatePresence, motion } from "motion/react"
import Image from "next/image"
import { useState } from "react"
import { Tabs, TabsList, TabsTrigger } from "@/components/ui/tabs"
import { FEATURES } from "@/lib/site"

/** Tabbed tour of the TV screens; the screenshot crossfades between them. */
export function FeatureTabs() {
  const [value, setValue] = useState(FEATURES[0].key)
  const current = FEATURES.find((f) => f.key === value) ?? FEATURES[0]

  return (
    <Tabs value={value} onValueChange={(v) => setValue(String(v))} className="gap-6">
      <TabsList className="h-auto w-full flex-wrap justify-start gap-1 bg-card/60 p-1 sm:w-fit">
        {FEATURES.map((f) => (
          <TabsTrigger key={f.key} value={f.key} className="px-4 py-2 text-sm">
            {f.title}
          </TabsTrigger>
        ))}
      </TabsList>

      <div className="grid items-center gap-8 lg:grid-cols-[1fr_1.5fr]">
        <AnimatePresence mode="wait">
          <motion.div
            key={current.key}
            initial={{ x: -12 }}
            animate={{ x: 0 }}
            exit={{ x: 12 }}
            transition={{ duration: 0.25 }}
          >
            <h3 className="font-heading text-3xl">{current.title}</h3>
            <p className="mt-3 text-lg text-muted-foreground">{current.body}</p>
          </motion.div>
        </AnimatePresence>

        <div className="frame-shadow relative aspect-video overflow-hidden rounded-xl bg-black">
          <AnimatePresence initial={false}>
            <motion.div
              key={current.key}
              className="absolute inset-0"
              initial={{ opacity: 0, scale: 1.03 }}
              animate={{ opacity: 1, scale: 1 }}
              exit={{ opacity: 0 }}
              transition={{ duration: 0.45 }}
            >
              <Image src={current.shot.src} alt={current.shot.alt} fill sizes="(min-width: 1024px) 60vw, 100vw" className="object-cover" />
            </motion.div>
          </AnimatePresence>
        </div>
      </div>
    </Tabs>
  )
}
