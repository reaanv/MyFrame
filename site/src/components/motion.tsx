"use client"

import { MotionConfig, motion, useScroll, useSpring, useTransform, type Variants } from "motion/react"
import { useRef, type ReactNode } from "react"

/** Honours the visitor's reduced-motion setting for every animation below. */
export function MotionRoot({ children }: { children: ReactNode }) {
  return <MotionConfig reducedMotion="user">{children}</MotionConfig>
}

// Content is rendered visible in the HTML; animations only add a gentle lift as it enters.
const rise: Variants = {
  hidden: { y: 24 },
  show: { y: 0, transition: { type: "spring", stiffness: 120, damping: 20 } },
}

export function Reveal({ children, className, delay = 0 }: { children: ReactNode; className?: string; delay?: number }) {
  return (
    <motion.div
      className={className}
      variants={rise}
      initial="hidden"
      whileInView="show"
      viewport={{ once: true, margin: "-60px" }}
      transition={{ delay }}
    >
      {children}
    </motion.div>
  )
}

export function Stagger({ children, className }: { children: ReactNode; className?: string }) {
  return (
    <motion.div
      className={className}
      initial="hidden"
      whileInView="show"
      viewport={{ once: true, margin: "-60px" }}
      variants={{ hidden: {}, show: { transition: { staggerChildren: 0.08 } } }}
    >
      {children}
    </motion.div>
  )
}

export function StaggerItem({ children, className }: { children: ReactNode; className?: string }) {
  return (
    <motion.div className={className} variants={rise}>
      {children}
    </motion.div>
  )
}

/** Tilts the hero screenshot slightly as the page scrolls, like a picture catching the light. */
export function HeroFrame({ children }: { children: ReactNode }) {
  const ref = useRef<HTMLDivElement>(null)
  const { scrollYProgress } = useScroll({ target: ref, offset: ["start start", "end start"] })
  const rotate = useSpring(useTransform(scrollYProgress, [0, 1], [0, 6]), { stiffness: 80, damping: 20 })
  const y = useTransform(scrollYProgress, [0, 1], [0, 60])
  return (
    <motion.div ref={ref} style={{ rotateX: rotate, y, transformPerspective: 1200 }}>
      {children}
    </motion.div>
  )
}

/** Thin brass progress line under the header. */
export function ScrollProgress() {
  const { scrollYProgress } = useScroll()
  const scaleX = useSpring(scrollYProgress, { stiffness: 120, damping: 30 })
  return <motion.div aria-hidden className="fixed inset-x-0 top-0 z-60 h-0.5 origin-left bg-primary" style={{ scaleX }} />
}
