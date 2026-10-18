# Add a UI kit component

Something a screen needs that the kit doesn't have yet. Screens can't style things themselves (`UiKitRulesTest`
fails if they try), so the new piece goes in the kit, gets a gallery sample, and then the screen uses it. The
example is `TmKeyHint`, a small chip showing a keyboard shortcut like **F3**. [UI kit](../ui/kit.md) explains the
tokens and surfaces used here, and [Kit rules](../ui/kit-rules.md) why it works this way.

## 1. Check it isn't there already

Look through the [component table](../ui/kit.md#components) and the gallery (Settings > Appearance > Component
gallery). Often an existing component takes a parameter that does the job, or a small new parameter on one is
better than a new component.

## 2. Write it in the right folder

Pick the folder by what it's for (`feedback/`, `data/`, `inputs/`, ...). A shortcut hint is a small label, so
`feedback/TmKeyHint.kt`:

```kotlin
/**
 * A keyboard shortcut, shown beside the thing it does, like F3 by Check in. Only useful where there's a keyboard
 */
@Composable
fun TmKeyHint(
    key: String,
    modifier: Modifier = Modifier
) {
    TmSurface(
        modifier = modifier,
        level = TmSurfaceLevel.SUNKEN,
        shape = TmTheme.shapes.chip
    ) {
        Text(
            modifier = Modifier.padding(horizontal = TmTheme.spacing.xs, vertical = TmTheme.spacing.xxs),
            text = key,
            style = TmTheme.typography.caption,
            color = TmTheme.colors.textMuted
        )
    }
}
```

- **Name it `Tm<Something>`**, and give it KDoc that says when to use it.
- **Build from the kit**: colors from `TmTheme.colors` (states through `TmColors.tone(...)`), text from
  `TmTheme.typography` (the `numeric*` styles for numbers), corners from `TmTheme.shapes`, gaps from
  `TmTheme.spacing`; `TmSurface` or `Modifier.glass()` for backgrounds; `Modifier.tmClickable` for anything
  clickable; `TmFieldFrame` for anything that looks like a field; `TmControlSize` for anything that sits in a row
  with buttons.
- **Solid or glass**: solid `TmSurface` unless it floats over other content.
- **`modifier` first among the optional parameters**, defaulting to `Modifier`, and applied to the outermost
  element.
- Inside `ui/kit/` you may use Material and fixed sizes; the rules test only checks screens. Keep fixed sizes to
  what can't come from a token.

## 3. Add it to the gallery

Add a sample of each state to a section in `gallery/GallerySections.kt`, inside a `Specimen` that names it. A hint
belongs with the badges:

```kotlin
private fun BadgesSection() {
    GallerySection(title = "Badges", description = "Each state keeps its color in every theme") {
        ...
        Specimen("Keyboard shortcut") { listOf("F2", "F3", "Esc").forEach { TmKeyHint(it) } }
    }
}
```

A component that doesn't fit an existing section gets a new `...Section()` function, added to the list in
`gallerySections()`.

## 4. Look at it in every theme

```bash
./gradlew :composeApp:desktopTest --tests "*KitGalleryRenderTest"
```

Open the PNGs in `composeApp/build/kit-gallery/`: every theme in dark and light, every font, reduce transparency,
and the narrow one. Check it reads in light mode and in Mono, where the accent is grey. Or run the app and open the
gallery to switch themes live.

## 5. Use it

Replace whatever the screen was doing by hand with it, then run the rules test and the screen renders:

```bash
./gradlew :composeApp:desktopTest --tests "*UiKitRulesTest" --tests "*ScreenRenderTest"
```

## 6. Test any logic

Pure logic in a component (formatting, matching, strength checks) gets a plain test in
`composeApp/src/commonTest/.../ui/kit/`, like `HighlightMatchTest` and `PasswordStrengthTest`.

## 7. Docs

Add it to the right row of the [component table](../ui/kit.md#components).

## Checklist

- [ ] Not already in the kit
- [ ] `Tm`-prefixed, in the right folder, with KDoc
- [ ] Only theme tokens and kit pieces inside
- [ ] Gallery sample of each state
- [ ] Looked at in every theme, dark and light
- [ ] Used on the screen; `UiKitRulesTest` passes
- [ ] In the component table

## See also

- [UI kit](../ui/kit.md)
- [Kit rules](../ui/kit-rules.md)
- [Add a theme or font](add-a-theme-or-font.md)
