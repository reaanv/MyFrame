import Image from "next/image"
import { ArrowRight, BookOpen, Bug, Cpu, Download, ExternalLink, Code2, HardDrive, Music2, Palette, ShieldCheck, Wifi } from "lucide-react"
import { Accordion, AccordionContent, AccordionItem, AccordionTrigger } from "@/components/ui/accordion"
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert"
import { Badge } from "@/components/ui/badge"
import { buttonVariants } from "@/components/ui/button"
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
import { Separator } from "@/components/ui/separator"
import { FeatureTabs } from "@/components/feature-tabs"
import { HeroFrame, Reveal, ScrollProgress, Stagger, StaggerItem } from "@/components/motion"
import { FrameMark, SiteHeader } from "@/components/site-header"
import { MissingAppDemo } from "@/components/tv-demo"
import { cn } from "@/lib/utils"
import { APK_URL, APPS, DOCS, FAQ, LIMITS, RELEASE_URL, REPO, STEPS, UNTESTED, VERSION } from "@/lib/site"

function SectionTitle({ eyebrow, title, lede }: { eyebrow: string; title: string; lede?: string }) {
  return (
    <Reveal className="mb-10 max-w-2xl">
      <p className="mb-3 text-xs tracking-[0.22em] text-primary uppercase">{eyebrow}</p>
      <h2 className="font-heading text-4xl text-balance sm:text-5xl">{title}</h2>
      {lede && <p className="mt-4 text-lg text-muted-foreground">{lede}</p>}
    </Reveal>
  )
}

export default function Home() {
  return (
    <>
      <ScrollProgress />
      <SiteHeader />
      <main id="top">
        {/* Hero */}
        <section className="grain relative overflow-hidden">
          <div className="mx-auto grid max-w-6xl items-center gap-12 px-5 pt-16 pb-20 sm:pt-24 lg:grid-cols-[0.9fr_1.1fr] lg:pb-28">
            <Reveal>
              <Badge variant="outline" className="mb-6 gap-1.5 border-primary/40 px-3 py-1 text-primary">
                <FrameMark className="h-3 w-4" /> Version {VERSION} · Android TV · Google TV
              </Badge>
              <h1 className="font-heading text-6xl leading-[0.95] text-balance sm:text-7xl lg:text-8xl">
                Your TV, <span className="text-primary italic">framed.</span>
              </h1>
              <p className="mt-6 max-w-xl text-xl text-muted-foreground">
                MyFrame turns a Xiaomi Android TV stick into an art and photo frame: public-domain paintings, your own photos, and your music apps one button away.
              </p>
              <div className="mt-8 flex flex-wrap items-center gap-3">
                <a href={APK_URL} className={cn(buttonVariants({ size: "lg" }), "h-12 px-6 text-base")}>
                  <Download data-icon="inline-start" />
                  Download MyFrame {VERSION} (APK)
                </a>
                <a href={REPO} className={cn(buttonVariants({ variant: "outline", size: "lg" }), "h-12 px-5 text-base")}>
                  <Code2 data-icon="inline-start" />
                  Source on GitHub
                </a>
              </div>
              <p className="mt-4 text-sm text-muted-foreground">Android 8 or newer · about 15 MB · free, no ads, no account</p>
            </Reveal>
            <HeroFrame>
              <div className="frame-shadow overflow-hidden rounded-xl border-8 border-[oklch(0.12_0.005_60)] bg-black">
                <Image src="/img/home.webp" alt="MyFrame home screen: a painting behind tiles for Art Mode, Photos, Mixed, Albums, YouTube, Spotify, YT Music, All Apps and Settings" width={1280} height={720} priority sizes="(min-width: 1024px) 55vw, 100vw" className="h-auto w-full" />
              </div>
            </HeroFrame>
          </div>
        </section>

        {/* Features */}
        <section id="features" className="border-t py-20 sm:py-28">
          <div className="mx-auto max-w-6xl px-5">
            <SectionTitle eyebrow="What it does" title="A gallery wall that changes with you" />
            <Reveal>
              <FeatureTabs />
            </Reveal>
            <Stagger className="mt-16 grid gap-4 sm:grid-cols-3">
              {[
                { icon: Palette, title: "Real frames", body: "Choose frame wood, mount and timing per preset." },
                { icon: Wifi, title: "Phone upload", body: "Send photos over home Wi-Fi. No app, no cloud." },
                { icon: Cpu, title: "Built for 2 GB", body: "Memory caps leave room for Android and music." },
              ].map(({ icon: Icon, title, body }) => (
                <StaggerItem key={title}>
                  <Card className="h-full bg-card/60 transition-colors hover:bg-card">
                    <CardHeader>
                      <Icon className="mb-2 size-5 text-primary" aria-hidden />
                      <CardTitle className="font-heading text-xl">{title}</CardTitle>
                      <CardDescription className="text-base">{body}</CardDescription>
                    </CardHeader>
                  </Card>
                </StaggerItem>
              ))}
            </Stagger>
          </div>
        </section>

        {/* Music */}
        <section id="music" className="border-t bg-card/30 py-20 sm:py-28">
          <div className="mx-auto max-w-6xl px-5">
            <SectionTitle
              eyebrow="Music and apps"
              title="Opens your apps. Never bundles them."
              lede="The tiles open the real Spotify, YouTube Music and YouTube apps installed on the stick. When one is missing, the tile tells you and offers its Google Play page."
            />
            <div className="grid gap-8 lg:grid-cols-[1fr_1fr]">
              <Stagger className="grid gap-4">
                {APPS.map((a) => (
                  <StaggerItem key={a.name}>
                    <Card className="bg-background/60">
                      <CardHeader>
                        <div className="flex items-center gap-2">
                          <Music2 className="size-4 text-primary" aria-hidden />
                          <CardTitle className="text-lg">{a.name}</CardTitle>
                        </div>
                        <CardDescription className="text-base text-foreground/80">{a.body}</CardDescription>
                      </CardHeader>
                      <CardContent>
                        <p className="text-sm text-muted-foreground">{a.note}</p>
                      </CardContent>
                    </Card>
                  </StaggerItem>
                ))}
              </Stagger>
              <Reveal delay={0.1}>
                <MissingAppDemo />
                <p className="mt-4 text-sm text-muted-foreground">
                  Why not include them? They belong to Spotify and Google, and Android does not let one app silently install another. The Play Store keeps them signed and up to date.
                </p>
              </Reveal>
            </div>
          </div>
        </section>

        {/* Setup */}
        <section id="setup" className="border-t py-20 sm:py-28">
          <div className="mx-auto max-w-6xl px-5">
            <SectionTitle eyebrow="Setup" title="Ten minutes with a computer" lede="MyFrame is an Android app. It does not replace Android or flash firmware." />
            <ol className="relative grid gap-6 border-l border-border pl-8">
              {STEPS.map((s, i) => (
                <Reveal key={s.title} delay={i * 0.04}>
                  <li className="relative">
                    <span className="absolute -left-[2.6rem] grid size-8 place-items-center rounded-full border border-primary/50 bg-background font-heading text-primary">
                      {i + 1}
                    </span>
                    <h3 className="text-lg font-medium">{s.title}</h3>
                    <p className="mt-1 text-muted-foreground">{s.body}</p>
                    {s.code && (
                      <pre className="mt-3 max-w-full overflow-x-auto rounded-lg border bg-black/60 p-4 font-mono text-sm">
                        <code>{s.code}</code>
                      </pre>
                    )}
                  </li>
                </Reveal>
              ))}
            </ol>
            <a href={DOCS.setup} className={cn(buttonVariants({ variant: "link" }), "mt-6 h-auto p-0 text-base")}>
              Full setup guide <ArrowRight data-icon="inline-end" />
            </a>
          </div>
        </section>

        {/* Phone upload */}
        <section id="phone-upload" className="border-t bg-card/30 py-20 sm:py-28">
          <div className="mx-auto grid max-w-6xl items-center gap-12 px-5 lg:grid-cols-2">
            <div>
              <SectionTitle eyebrow="Phone upload" title="Scan, type the PIN, send" />
              <Reveal>
                <p className="text-lg text-muted-foreground">
                  On the TV open Photos, Add photos. Scan the QR code with a phone on the same Wi-Fi and type the 6-digit PIN. The page opens at{" "}
                  <code className="rounded bg-black/50 px-1.5 py-0.5 font-mono text-foreground">http://&lt;stick-ip&gt;:8080</code>.
                </p>
                <Alert className="mt-6 border-primary/50 bg-primary/5">
                  <ShieldCheck className="text-primary" />
                  <AlertTitle>The upload page runs on your stick, inside your home network.</AlertTitle>
                  <AlertDescription>
                    This website cannot reach your stick, and it never sees your photos. Open the address shown on your TV, from a phone on the same Wi-Fi.
                  </AlertDescription>
                </Alert>
                <p className="mt-4 text-sm text-muted-foreground">Guest Wi-Fi or client isolation can block it. Do not forward port 8080 on your router.</p>
              </Reveal>
            </div>
            <Reveal delay={0.1}>
              <div className="frame-shadow overflow-hidden rounded-xl bg-black">
                <Image src="/img/phone-upload.webp" alt="Phone upload screen on the TV with a QR code, the address to open and the PIN" width={1280} height={720} sizes="(min-width: 1024px) 50vw, 100vw" className="h-auto w-full" />
              </div>
            </Reveal>
          </div>
        </section>

        {/* Limits + honesty */}
        <section id="limits" className="border-t py-20 sm:py-28">
          <div className="mx-auto grid max-w-6xl gap-12 px-5 lg:grid-cols-2">
            <div>
              <SectionTitle eyebrow="Limits" title="Sized for a small stick" lede="These caps leave room for Android and a music app on 2 GB of RAM." />
              <Reveal>
                <dl className="divide-y rounded-xl border">
                  {LIMITS.map((l) => (
                    <div key={l.label} className="flex items-baseline justify-between gap-4 px-5 py-4">
                      <dt className="flex items-center gap-2 text-muted-foreground">
                        <HardDrive className="size-4 text-primary/70" aria-hidden />
                        {l.label}
                      </dt>
                      <dd className="text-right font-medium">{l.value}</dd>
                    </div>
                  ))}
                </dl>
              </Reveal>
            </div>
            <div>
              <SectionTitle eyebrow="Honest status" title="Not yet tested" />
              <Stagger className="grid gap-3">
                {UNTESTED.map((u) => (
                  <StaggerItem key={u}>
                    <div className="rounded-xl border border-dashed p-4 text-muted-foreground">{u}</div>
                  </StaggerItem>
                ))}
              </Stagger>
              <a href={DOCS.checklist} className={cn(buttonVariants({ variant: "link" }), "mt-4 h-auto p-0")}>
                Hardware checklist <ArrowRight data-icon="inline-end" />
              </a>
            </div>
          </div>
        </section>

        {/* FAQ + help */}
        <section id="faq" className="border-t bg-card/30 py-20 sm:py-28">
          <div className="mx-auto grid max-w-6xl gap-12 px-5 lg:grid-cols-[1.4fr_1fr]">
            <div>
              <SectionTitle eyebrow="Questions" title="Good to know" />
              <Reveal>
                <Accordion className="rounded-xl border bg-background/60 px-5">
                  {FAQ.map((f) => (
                    <AccordionItem key={f.q} value={f.q}>
                      <AccordionTrigger className="py-4 text-base">{f.q}</AccordionTrigger>
                      <AccordionContent className="text-base text-muted-foreground">{f.a}</AccordionContent>
                    </AccordionItem>
                  ))}
                </Accordion>
              </Reveal>
            </div>
            <div id="help">
              <SectionTitle eyebrow="Help" title="Stuck?" />
              <Stagger className="grid gap-3">
                {[
                  { href: DOCS.troubleshooting, icon: BookOpen, label: "Troubleshooting" },
                  { href: DOCS.issues, icon: Bug, label: "Report a problem" },
                  { href: DOCS.credits, icon: Palette, label: "Artwork credits" },
                  { href: RELEASE_URL, icon: ShieldCheck, label: "Release page and SHA-256" },
                ].map(({ href, icon: Icon, label }) => (
                  <StaggerItem key={label}>
                    <a href={href} className="group flex items-center justify-between rounded-xl border bg-background/60 px-5 py-4 transition-colors hover:border-primary/60">
                      <span className="flex items-center gap-3">
                        <Icon className="size-4 text-primary" aria-hidden />
                        {label}
                      </span>
                      <ExternalLink className="size-4 text-muted-foreground transition-transform group-hover:translate-x-0.5" aria-hidden />
                    </a>
                  </StaggerItem>
                ))}
              </Stagger>
            </div>
          </div>
        </section>

        {/* Final CTA */}
        <section className="grain border-t py-20 text-center sm:py-28">
          <Reveal className="mx-auto max-w-2xl px-5">
            <FrameMark className="mx-auto mb-6 h-9 w-12 text-primary" />
            <h2 className="font-heading text-4xl text-balance sm:text-5xl">Hang something beautiful on your TV.</h2>
            <a href={APK_URL} className={cn(buttonVariants({ size: "lg" }), "mt-8 h-12 px-6 text-base")}>
              <Download data-icon="inline-start" />
              Download MyFrame {VERSION}
            </a>
            <p className="mt-4 text-sm text-muted-foreground">
              Check the file before installing: <a className="underline underline-offset-4 hover:text-foreground" href={RELEASE_URL}>the SHA-256 is on the release page</a>.
            </p>
          </Reveal>
        </section>
      </main>

      <footer className="border-t py-10">
        <div className="mx-auto max-w-6xl px-5 text-sm text-muted-foreground">
          <Separator className="mb-6 hidden" />
          <p>
            Paintings: The Metropolitan Museum of Art, Open Access (CC0). Typeface in the app: Cormorant Garamond (SIL OFL 1.1). MyFrame is not affiliated with Samsung, Xiaomi, Google, Spotify or The Met.
          </p>
        </div>
      </footer>
    </>
  )
}
