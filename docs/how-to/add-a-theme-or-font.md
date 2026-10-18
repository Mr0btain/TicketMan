# Add a theme or font

Themes (palettes) and interface fonts are lists in the kit. Adding one to the list is all it takes for it to show
in Settings > Appearance and in the component gallery. [UI kit](../ui/kit.md) explains what each color is for.

## A theme

1. **Make a `TmPalette`** in
   [`TmPalettes.kt`](../../composeApp/src/commonMain/kotlin/org/mr0btain/ticketman/ui/kit/theme/TmPalettes.kt), copying
   the closest existing one. It needs a dark and a light `TmColors`:

   ```kotlin
   val Harbor = TmPalette(
       id = "harbor",
       name = "Harbor",
       description = "Deep teal with a sea green accent",
       dark = TmColors(
           isDark = true,
           background = Color(0xFF0F1719),
           glow = Color(0xFF5FD3C0).copy(alpha = 0.14f),
           glass = Color(0xFF172124).copy(alpha = 0.72f),
           surface = Color(0xFF162022),
           surfaceRaised = Color(0xFF1F2A2D),
           surfaceSunken = Color(0xFF111A1C),
           hairline = Color.White.copy(alpha = 0.08f),
           outline = Color.White.copy(alpha = 0.18f),
           text = Color(0xFFE4EEEE),
           textMuted = Color(0xFF93A6A8),
           textFaint = Color(0xFF5D6E70),
           accent = TmTone.of(Color(0xFF5FD3C0), Color(0xFF07231F), DARK_SOFT),
           success = TmTone.of(...),
           warning = TmTone.of(...),
           danger = TmTone.of(...),
           neutral = TmTone.of(...)
       ),
       light = TmColors(isDark = false, ...)
   )
   ```

2. **Add it to `TmPalettes.all`**, in the order it should appear in the picker.
3. **Pick an `id` you won't change.** It's what's saved in settings; an unknown `id` falls back to Bench.

### Choosing the colors

- **Keep Bench's steps between layers.** In its dark version the `background` is darkest, then `surfaceSunken`,
  `surface` and `surfaceRaised` each a little lighter. In its light version the surfaces are near white on a grey
  `background`, with `surfaceRaised` and `surfaceSunken` a touch darker than `surface`. Close enough that cards
  don't shout, far enough that a hovered row shows.
- **Text has to read on every surface**, including `textMuted` on `surfaceRaised`. Aim for a contrast of at least
  4.5:1 for `text` and `textMuted`; `textFaint` is for hints and can be lower.
- **Each tone's `onColor` has to read on its `color`**, since solid buttons and badges put one on the other. Its
  `soft` (made by `TmTone.of` at `DARK_SOFT` or `LIGHT_SOFT` alpha) has to let `color` text read on it.
- **Keep the tones meaning the same thing**: success green, warning amber, danger red, neutral grey. Ticket
  statuses use them, and staff learn the colors, so a theme changes their shade, not their hue. Only `accent` is
  free.
- **`glass` is translucent** (about 0.72 alpha) and `glow` faint (0.10 to 0.16): they're blurred over the
  background.

### Check it

```bash
./gradlew :composeApp:desktopTest --tests "*KitGalleryRenderTest"
```

The gallery is drawn in every palette, dark and light, to `composeApp/build/kit-gallery/<id>-<dark|light>-barlow.png`.
Look at the badges, buttons, notices and the Colors section at the bottom, and at a screen render or two. The
theme cards in Settings draw themselves from the palette, so there's nothing to add there.

## A font

1. **Get the files under a licence that allows bundling** (the SIL Open Font License is the usual one), and put the
   licence text in `composeApp/licenses/`, beside the others.
2. **Put the `.ttf` files** in `composeApp/src/commonMain/composeResources/font/`, named in lowercase with
   underscores (`inter_regular.ttf`). Compose generates `Res.font.inter_regular` from the name.
3. **Make a `TmFont`** in
   [`TmFonts.kt`](../../composeApp/src/commonMain/kotlin/org/mr0btain/ticketman/ui/kit/theme/TmFonts.kt), listing
   each file with its weight:

   ```kotlin
   val Inter = TmFont(
       id = "inter",
       name = "Inter",
       description = "Neutral and even, easy on small screens",
       faces = listOf(
           Res.font.inter_regular to FontWeight.Normal,
           Res.font.inter_medium to FontWeight.Medium,
           Res.font.inter_semibold to FontWeight.SemiBold,
           Res.font.inter_bold to FontWeight.Bold
       )
   )
   ```

4. **Add it to `TmFonts.all`.** As with themes, keep the `id` once released.

### Weights

The type scale uses **Normal** (body, captions), **Medium** (labels) and **SemiBold** (titles). Include those
three files if the font has them. A missing weight falls back to the nearest one in the family, which is why
Atkinson Hyperlegible (regular and bold only) shows labels in regular and titles in bold.

### Numbers

Ticket numbers, money, PINs and SKUs don't use the picked font: they use `TmFonts.mono()`, JetBrains Mono, so
digits line up. Change that function to change the number font everywhere.

### Check it

`KitGalleryRenderTest.everyFont()` draws the gallery once per font to
`composeApp/build/kit-gallery/bench-dark-<id>.png`. Look at the Type section and at long labels in the Fields and
Navigation sections, since a wider font can wrap where Barlow didn't.

## See also

- [UI kit](../ui/kit.md#palettes-and-fonts)
- [Settings screen](../ui/screens/settings.md)
- [Add a kit component](add-a-kit-component.md)
