"""Generate the launcher icon and Android TV banner. Usage: python -I tools/make_branding.py"""
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parent.parent
RES = ROOT / "app" / "src" / "main" / "res"
FONT = RES / "font" / "cormorant.ttf"
INK, IVORY, BRASS = (14, 13, 11), (242, 237, 228), (201, 166, 107)


def frame_mark(d, box, w):
    x0, y0, x1, y1 = box
    d.rectangle(box, outline=BRASS, width=w)
    pad = w * 3
    d.rectangle((x0 + pad, y0 + pad, x1 - pad, y1 - pad), outline=IVORY, width=max(1, w // 2))


def icon(size):
    im = Image.new("RGB", (size, size), INK)
    m = size // 5
    frame_mark(ImageDraw.Draw(im), (m, m + size // 16, size - m, size - m - size // 16), max(2, size // 32))
    return im


def banner():
    im = Image.new("RGB", (640, 360), INK)
    d = ImageDraw.Draw(im)
    frame_mark(d, (64, 112, 200, 248), 6)
    font = ImageFont.truetype(str(FONT), 112)
    try:
        font.set_variation_by_axes([500])
    except Exception:
        pass
    d.text((240, 180), "MyFrame", font=font, fill=IVORY, anchor="lm")
    return im


for name, size in (("mipmap-xhdpi", 96), ("mipmap-xxhdpi", 144), ("mipmap-xxxhdpi", 192)):
    (RES / name).mkdir(parents=True, exist_ok=True)
    icon(size).save(RES / name / "ic_launcher.png")
(RES / "drawable-xhdpi").mkdir(parents=True, exist_ok=True)
banner().save(RES / "drawable-xhdpi" / "tv_banner.png")
print("branding written")
