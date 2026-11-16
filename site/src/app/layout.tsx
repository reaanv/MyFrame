import type { Metadata, Viewport } from "next"
import { Cormorant_Garamond, Geist, Geist_Mono } from "next/font/google"
import { MotionRoot } from "@/components/motion"
import "./globals.css"

// next/font downloads these at build time and serves them from this site: no font CDN at runtime.
const sans = Geist({ variable: "--font-sans", subsets: ["latin"] })
const mono = Geist_Mono({ variable: "--font-geist-mono", subsets: ["latin"] })
const serif = Cormorant_Garamond({ variable: "--font-serif", subsets: ["latin"], weight: ["500", "600"], style: ["normal", "italic"] })

export const metadata: Metadata = {
  title: "MyFrame: art and photo frame for Android TV",
  description:
    "MyFrame turns a Xiaomi Android TV or Google TV stick into an art and photo frame. Free Android app with a signed APK download and a setup guide.",
  icons: {
    icon: "data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 32 32'%3E%3Crect width='32' height='32' fill='%230e0d0c'/%3E%3Crect x='5' y='8' width='22' height='16' fill='none' stroke='%23c8a96a' stroke-width='2.5'/%3E%3Crect x='10' y='12.5' width='12' height='7' fill='none' stroke='%23c8a96a' stroke-width='1.5'/%3E%3C/svg%3E",
  },
}

export const viewport: Viewport = { themeColor: "#0e0d0c" }

export default function RootLayout({ children }: LayoutProps<"/">) {
  return (
    <html lang="en" className={`dark ${sans.variable} ${mono.variable} ${serif.variable} antialiased`}>
      <body className="min-h-screen bg-background text-foreground">
        <a href="#top" className="sr-only focus:not-sr-only focus:fixed focus:top-2 focus:left-2 focus:z-70 focus:rounded-md focus:bg-primary focus:px-3 focus:py-2 focus:text-primary-foreground">
          Skip to content
        </a>
        <MotionRoot>{children}</MotionRoot>
      </body>
    </html>
  )
}
