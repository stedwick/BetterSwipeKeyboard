# GPT-Image 2 prompts for assets that can't be captured/composited locally

## Feature graphic background (1,024 × 500, required)

Use this if the locally-composited version (`feature-graphic/`) looks too
plain. Generate the art WITHOUT any text or logo — genAI text is
unreliable — then overlay the real wordmark
(`app/src/main/res/drawable-xxxhdpi/ic_logo_light.png`) center-left
before uploading.

> Clean, modern hero banner for an Android keyboard app. A smooth
> glowing blue-violet swipe trail curving across a minimal dark QWERTY
> keyboard, subtle word suggestion chips floating above the keys. Flat,
> iOS-like aesthetic, deep navy background (#1A1B2E), gentle gradient,
> soft bloom on the trail. No text, no letters other than on the keys,
> no logos. Generous empty negative space in the center-left for a logo
> overlay. Wide banner composition, 1024×500 pixels.

Notes for the overlay step afterwards:

- Wordmark on the left third, vertically centered, ~55% of banner
  height.
- Keep the keyboard art on the right two-thirds; nudge the crop if the
  trail crosses the wordmark.
- Export as PNG or JPEG under 15 MB.
