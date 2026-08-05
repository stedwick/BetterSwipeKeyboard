#!/usr/bin/env python3
"""Regenerate the Play feature graphic (1024x500).

Light brand-lavender gradient, wordmark on the left, mid-swipe phone
screenshot crop on the right. Run from the repo root with a Pillow venv:
  /tmp/sshot-venv/bin/python store-assets/compose_feature_graphic.py
"""
from PIL import Image, ImageDraw

W, H = 1024, 500
TOP = (247, 248, 255)
BOTTOM = (196, 200, 250)  # soft brand lavender

bg = Image.new("RGB", (W, H))
d = ImageDraw.Draw(bg)
for y in range(H):
    t = y / H
    d.line([(0, y), (W, y)], fill=tuple(round(TOP[i] + (BOTTOM[i] - TOP[i]) * t) for i in range(3)))

logo = Image.open("app/src/main/res/drawable-xxxhdpi/ic_logo_light.png").convert("RGBA")
lw = 430
lh = round(logo.height * lw / logo.width)
logo = logo.resize((lw, lh), Image.LANCZOS)
bg.paste(logo, (45, (H - lh) // 2), logo)

shot = Image.open("store-assets/screenshots/phone/raw/swipe-keyboard-light.png").convert("RGB")
# keep the alternates strip + keyboard
crop = shot.crop((0, 1380, 1080, 2400))
ch = 430
cw = round(crop.width * ch / crop.height)
crop = crop.resize((cw, ch), Image.LANCZOS)
mask = Image.new("L", (cw, ch), 0)
ImageDraw.Draw(mask).rounded_rectangle([0, 0, cw, ch], radius=26, fill=255)
bg.paste(crop, (W - cw - 40, (H - ch) // 2), mask)

bg.save("store-assets/feature-graphic/feature-graphic.png", "PNG")
print("saved", (W, H))
