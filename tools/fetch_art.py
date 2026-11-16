"""Download the bundled public-domain artwork for MyFrame.

Source: The Metropolitan Museum of Art Open Access (https://metmuseum.github.io),
which publishes these works under CC0.

Each entry names a Met object id plus a phrase its title must contain. A work is
only accepted when the museum's own record flags it as public domain AND the
title matches, so a wrong id can never bundle the wrong (or a restricted) work.
Images are resized to fit a 2048x1536 box and written to app/src/main/assets/art
together with catalog.json, which keeps the attribution shown in the app.

Usage:  python -I tools/fetch_art.py
"""
import io
import json
import re
import sys
import time
import urllib.request
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / "app" / "src" / "main" / "assets" / "art"
UA = {"User-Agent": "MyFrame-art-fetch/1.0", "Accept": "application/json, image/jpeg, */*"}
BOX = (2048, 1536)
BASE = "https://collectionapi.metmuseum.org/public/collection/v1"

# (candidate object ids, phrase the title must contain, file slug)
MET = [
    ([436535], "wheat field", "vangogh_wheat_field_cypresses"),
    ([436528], "irises", "vangogh_irises"),
    ([435809], "harvesters", "bruegel_harvesters"),
    ([438817], "dance class", "degas_dance_class"),
    ([437881], "water pitcher", "vermeer_water_pitcher"),
    ([11417], "washington crossing", "leutze_washington_delaware"),
    ([45434], "under the wave", "hokusai_great_wave"),
    ([437133], "sainte-adresse", "monet_garden_sainte_adresse"),
    ([11122], "gulf stream", "homer_gulf_stream"),
    ([10481], "heart of the andes", "church_heart_of_the_andes"),
    ([436575], "view of toledo", "elgreco_view_of_toledo"),
    ([436524], "sunflowers", "vangogh_sunflowers"),
    ([436532], "straw hat", "vangogh_straw_hat"),
    ([12127], "madame x", "sargent_madame_x"),
    ([10154], "rocky mountains", "bierstadt_rocky_mountains"),
    ([437135], "grenouill", "monet_la_grenouillere"),
    ([437310], "boulevard montmartre", "pissarro_boulevard_montmartre"),
    ([437980], "cypresses", "vangogh_cypresses"),
    ([435868], "card players", "cezanne_card_players"),
    ([436105], "socrates", "david_death_of_socrates"),
    ([10497], "oxbow", "cole_oxbow"),
    ([435877, 435882], "sainte-victoire", "cezanne_mont_sainte_victoire"),
    ([436947], "boating", "manet_boating"),
    ([437394, 437384], "aristotle", "rembrandt_aristotle"),
    ([438722], "circus sideshow", "seurat_circus_sideshow"),
    ([437127], "water lilies", "monet_bridge_water_lilies"),
]


def get(url, binary=False, tries=3):
    last = None
    for attempt in range(tries):
        try:
            req = urllib.request.Request(url, headers=UA)
            with urllib.request.urlopen(req, timeout=60) as r:
                data = r.read()
            return data if binary else json.loads(data.decode("utf-8"))
        except Exception as e:  # noqa: BLE001 - report and retry
            last = e
            time.sleep(1.5 * (attempt + 1))
    raise RuntimeError(f"GET failed {url}: {last}")


def save(img_bytes, slug):
    im = Image.open(io.BytesIO(img_bytes)).convert("RGB")
    im.thumbnail(BOX, Image.LANCZOS)
    path = OUT / f"{slug}.jpg"
    im.save(path, "JPEG", quality=86, optimize=True)
    return im.size, path.stat().st_size


def clean(s):
    return re.sub(r"\s+", " ", (s or "").replace("\n", " ")).strip()


def fetch(report):
    for ids, must, slug in MET:
        try:
            o = None
            seen = []
            for oid in ids:
                try:
                    c = get(f"{BASE}/objects/{oid}", tries=2)
                except RuntimeError:
                    continue
                seen.append(f"{oid}='{clean(c.get('title'))}' pd={c.get('isPublicDomain')}")
                if c.get("isPublicDomain") and must in clean(c.get("title")).lower() and c.get("primaryImage"):
                    o = c
                    break
                time.sleep(0.3)
            if o is None:
                report.append(f"SKIP {slug}: {'; '.join(seen) or 'no record'}")
                continue
            (w, h), size_b = save(get(o["primaryImage"], binary=True), slug)
            yield {
                "file": f"{slug}.jpg", "title": clean(o.get("title")), "artist": clean(o.get("artistDisplayName")),
                "date": clean(o.get("objectDate")), "width": w, "height": h,
                "credit": "The Metropolitan Museum of Art — " + clean(o.get("creditLine")),
                "license": "CC0 Public Domain (Met Open Access)", "source": clean(o.get("objectURL")),
            }
            report.append(f"OK   {slug}: {clean(o.get('title'))} / {o.get('artistDisplayName')} {w}x{h} {size_b // 1024}KB")
        except Exception as e:  # noqa: BLE001
            report.append(f"FAIL {slug}: {e}")
        time.sleep(0.5)


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    report = []
    items = list(fetch(report))
    keep = {i["file"] for i in items} | {"catalog.json"}
    for stale in OUT.iterdir():
        if stale.name not in keep:
            stale.unlink()
    # The version is the item count, so the app re-seeds whenever works are added.
    (OUT / "catalog.json").write_text(json.dumps({"version": len(items), "items": items}, indent=2, ensure_ascii=False), encoding="utf-8")
    report.append(f"TOTAL {len(items)} artworks, {sum((OUT / i['file']).stat().st_size for i in items) // 1024} KB")
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")  # artist names are not all ASCII
    print("\n".join(report))
    return 0 if items else 1


if __name__ == "__main__":
    sys.exit(main())
