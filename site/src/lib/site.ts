// Every fact on the site comes from here. Keep it in step with the app and PROJECT_STATUS.md.
export const VERSION = "1.1.2"
export const REPO = "https://github.com/reaanv/MyFrame"
export const APK_URL = `${REPO}/releases/latest/download/MyFrame.apk`
export const RELEASE_URL = `${REPO}/releases/latest`
export const DOCS = {
  setup: `${REPO}/blob/main/docs/XIAOMI_STICK_SETUP.md`,
  troubleshooting: `${REPO}/blob/main/docs/TROUBLESHOOTING.md`,
  credits: `${REPO}/blob/main/docs/ARTWORK_CREDITS.md`,
  checklist: `${REPO}/blob/main/docs/HARDWARE_CHECKLIST.md`,
  issues: `${REPO}/issues`,
}

export type Shot = { src: string; alt: string }

export const FEATURES: { key: string; title: string; body: string; shot: Shot }[] = [
  {
    key: "art",
    title: "Art Mode",
    body: "22 public-domain paintings from The Met, shown in frames and mounts. More arrive over Wi-Fi, a few each day.",
    shot: { src: "/img/art-viewer.webp", alt: "Van Gogh's Irises in a black frame with a cream mount" },
  },
  {
    key: "photos",
    title: "My Photos",
    body: "Your own pictures: albums, favourites, a hand-picked set or a slideshow.",
    shot: { src: "/img/select-photos.webp", alt: "My Photos screen with three pictures selected" },
  },
  {
    key: "mixed",
    title: "Mixed",
    body: "Paintings and photos together, saved as presets with their own frame and timing.",
    shot: { src: "/img/frame-editor.webp", alt: "Frame and mount editor with a live preview" },
  },
  {
    key: "albums",
    title: "Albums",
    body: "Arrange your own collections and choose which one the TV shows.",
    shot: { src: "/img/collections.webp", alt: "Albums screen listing photo collections" },
  },
]

export const APPS = [
  {
    name: "Spotify",
    body: "Opens the Spotify TV app. Start a playlist, press Home, and go back to your pictures.",
    note: "Music while art is on screen is untested on real hardware.",
  },
  {
    name: "YouTube Music",
    body: "Opens YouTube Music, or the Music menu inside YouTube when it has no app of its own.",
    note: "On TV it runs inside YouTube and may stop when you leave it.",
  },
  {
    name: "YouTube",
    body: "Opens the YouTube app already on the stick.",
    note: "Most Google TV sticks ship with it.",
  },
]

export const STEPS = [
  { title: "Set up the stick", body: "Finish Google TV setup and join Wi-Fi." },
  {
    title: "Turn on debugging",
    body: "Settings, About, press the build number seven times. Then enable debugging in Developer options.",
  },
  {
    title: "Install from a computer",
    body: "Some sticks use Wireless debugging instead: run adb pair, then adb connect, with the addresses shown on the TV.",
    code: "adb connect <stick-ip>:5555\nadb install -r MyFrame.apk",
  },
  { title: "Open MyFrame", body: "It appears in the TV's app row." },
  {
    title: "Optional: make it the Home screen",
    body: "Run this from the GitHub repository. undo puts the stock launcher back.",
    code: "bash tools/appliance.sh apply\nbash tools/appliance.sh undo",
  },
]

export const LIMITS = [
  { label: "Full-picture cache", value: "24 MB" },
  { label: "Thumbnail cache", value: "8 MB" },
  { label: "Picture decoding", value: "Full HD, even on 4K output" },
  { label: "New photos", value: "Resized to a 1920-pixel long edge" },
  { label: "Storage", value: "Imports stop before the last 1 GB" },
]

export const UNTESTED = [
  "Tested on the Android TV emulator (Android 12, 2 GB RAM). Not yet tested on a physical Xiaomi stick.",
  "Spotify playing while art is shown is untested on real hardware.",
  "YouTube Music on TV runs inside YouTube and may stop when you leave it.",
]

export const FAQ = [
  {
    q: "Can MyFrame come with Spotify and YouTube Music built in?",
    a: "No. Those apps belong to Spotify and Google, and Android does not let one app silently install another. MyFrame opens the real apps. If one is missing, its tile says so and offers a button that opens its Google Play page.",
  },
  {
    q: "Does this replace Android on the stick?",
    a: "No. MyFrame is an ordinary Android app. It can become the Home screen and the screensaver, and both can be undone.",
  },
  {
    q: "Can this website send photos to my TV?",
    a: "No. The upload page runs on the stick inside your home network. Open the address shown on the TV, from a phone on the same Wi-Fi.",
  },
  {
    q: "Is there an account, subscription or ads?",
    a: "No. Art and your photos work offline. Music and video need their own apps and accounts.",
  },
]
