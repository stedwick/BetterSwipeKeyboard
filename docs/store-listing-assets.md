# Google Play Default Store Listing — Asset Checklist

Requirements for the Better Swipe Keyboard default store listing on Google
Play Console. Status: app name and short description entered; everything
else pending.

## Text assets

| Asset | Required | Limits | Current state |
|---|---|---|---|
| App name | Yes | Max 30 characters | "Better Swipe Keyboard" (21/30) — done |
| Short description | Yes | Max 80 characters | "Type, speak, and proofread with AI on this faster keyboard." (59/80) — done |
| Full description | Yes | Max 4,000 characters | `store-assets/full-description.txt` (2,570/4000) — done, review before upload |

Policy notes: follow the Metadata policy and Help Center guidance; review
all program policies before submitting.

## Visual assets

### App icon (required)

- Format: PNG or JPEG
- Max size: 1 MB
- Dimensions: 512 × 512 px
- Must meet Google Play design specifications and metadata policy
- Done: `store-assets/icon/ic_launcher_512.png` (512×512, 111 KB)

### Feature graphic (required)

- Format: PNG or JPEG
- Max size: 15 MB
- Dimensions: 1,024 × 500 px
- Used when featuring the app
- Done: `store-assets/feature-graphic/feature-graphic.png` (1024×500,
  108 KB) — regenerate with `store-assets/compose_feature_graphic.py`;
  a fancier art alternative lives in `store-assets/gpt-image-prompts.md`

### Video (optional)

- YouTube URL (`https://www.youtube.com/watch?v=...`)
- Video must be public or unlisted, ads turned off, not age restricted

## Screenshots

All screenshot sets:

- Format: PNG or JPEG
- Aspect ratio: 16:9 or 9:16

| Asset | Required | Count | Max size | Per-side pixel range |
|---|---|---|---|---|
| Phone screenshots | Yes | 2–8 | 8 MB each | 320–3,840 px per side |
| 7-inch tablet screenshots | Yes | up to 8 | 8 MB each | 320–3,840 px per side |
| 10-inch tablet screenshots | Yes | up to 8 | 8 MB each | 1,080–7,680 px per side |
| Chromebook screenshots | No | 4–8 | 8 MB each | 1,080–7,680 px per side |
| Android XR screenshots | No | 4–8 | 15 MB each | 720–7,680 px per side |

Done (all 1080×1920 9:16 PNGs, mid-swipe shots include a synthetic
thumb dot; raw captures + compositor in `store-assets/screenshots/`):

- Phone (6): `store-assets/screenshots/phone/` — mid-swipe light,
  mid-swipe dark, committed-state, emoji, clipboard, numpad
- 7-inch tablet (3): `store-assets/screenshots/tablet7/` — mid-swipe,
  committed, emoji
- 10-inch tablet (3): `store-assets/screenshots/tablet10/` — mid-swipe,
  committed, emoji
- Chromebook: skipped (optional). XR: skipped.
- Video: pending (script in `store-assets/video-script.md`)

Promotion eligibility: include at least 4 phone screenshots at a minimum
of 1,080 px on each side.

## Android XR videos (optional)

- Spatial XR video: YouTube URL; must be 360°, 180°, or 3D; public or
  unlisted; ads off; not age restricted
- Non-spatial XR video: YouTube URL; public or unlisted; ads off; not
  age restricted

## Localization note

If translations are added without localized graphics, Play uses the
default-language graphics for those listings.
