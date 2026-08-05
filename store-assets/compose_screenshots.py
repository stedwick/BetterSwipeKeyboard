#!/usr/bin/env python3
"""Store-asset compositor: thumb dots on mid-swipe shots + 9:16 canvas fitting.

Reads store-assets/screenshots/<device>/raw/*.png, writes final
1080x1920 (9:16) PNGs next to the raw dir. Thumb dots mark the parked
finger position at the end of the injected trail (coordinates are in
each device's raw screenshot pixels, from the SwipePoseInjector paths).
"""
from PIL import Image, ImageDraw
from pathlib import Path

ROOT = Path("store-assets/screenshots")
CANVAS = (1080, 1920)  # 9:16, >=1080 px per side for promotion eligibility

# file stem -> (thumb x, thumb y) in raw pixels; None = no dot
SHOTS = {
    "phone": {
        "swipe-keyboard-light": (322, 1877),
        "swipe-fast-dark": (475, 1723),
        "committed-keyboard-light": None,
        "emoji-panel-light": None,
        "clipboard-panel-light": None,
        "numpad-light": None,
    },
    "tablet7": {
        "swipe-keyboard-light": (364, 1518),
        "committed-keyboard-light": None,
        "emoji-panel-light": None,
    },
    "tablet10": {
        "swipe-keyboard-light": (484, 2158),
        "committed-keyboard-light": None,
        "emoji-panel-light": None,
    },
}


def add_thumb_dot(img: Image.Image, x: int, y: int) -> Image.Image:
    """Subtle iOS-style touch indicator at the parked fingertip."""
    img = img.convert("RGBA")
    overlay = Image.new("RGBA", img.size, (0, 0, 0, 0))
    d = ImageDraw.Draw(overlay)
    r = img.width // 22  # ~49 px on the phone raw
    # soft outer halo, then a translucent light core with a faint rim
    d.ellipse([x - r * 1.5, y - r * 1.5, x + r * 1.5, y + r * 1.5],
              fill=(255, 255, 255, 45))
    d.ellipse([x - r, y - r, x + r, y + r],
              fill=(255, 255, 255, 110), outline=(60, 60, 60, 90), width=3)
    return Image.alpha_composite(img, overlay).convert("RGB")


def fit_canvas(img: Image.Image) -> Image.Image:
    """Scale to fit inside the 9:16 canvas; fill margins with edge color."""
    img = img.convert("RGB")
    scale = min(CANVAS[0] / img.width, CANVAS[1] / img.height)
    size = (round(img.width * scale), round(img.height * scale))
    resized = img.resize(size, Image.LANCZOS)
    bg = img.getpixel((2, 2))  # top-left content color (app background)
    canvas = Image.new("RGB", CANVAS, bg)
    canvas.paste(resized, ((CANVAS[0] - size[0]) // 2, (CANVAS[1] - size[1]) // 2))
    return canvas


def main():
    for device, shots in SHOTS.items():
        out_dir = ROOT / device
        for stem, dot in shots.items():
            src = ROOT / device / "raw" / f"{stem}.png"
            img = Image.open(src)
            if dot:
                img = add_thumb_dot(img, *dot)
            final = fit_canvas(img)
            dst = out_dir / f"{stem}.png"
            final.save(dst, "PNG")
            print(f"{device}/{stem}.png  {final.size}  dot={dot}")


if __name__ == "__main__":
    main()
