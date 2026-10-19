# Kit rules

The rules that keep screens on the [UI kit](kit.md), and the test that checks them. Read this when
`UiKitRulesTest` fails, or before adding something to the kit.

## The rules

1. **Screens style through the kit.** Colors come from `TmTheme.colors`, text styles from `TmTheme.typography`,
   corners from `TmTheme.shapes` and gaps from `TmTheme.spacing`. Nothing is hard coded on a screen.
2. **Screens use kit components, not Material ones.** `Text`, `Icon` and `SnackbarHostState` are the only Material
   pieces a screen imports.
3. **UI used by more than one screen goes in the kit.** It gets a `Tm` prefix, lives in `ui/kit/`, and gets a sample
   in the gallery.
4. **Numbers use the `numeric*` styles.** Ticket and invoice numbers, money, PINs and SKUs are monospace.
5. **Glass is for floating layers only.** Cards and lists are solid `TmSurface`s.

The reason for all of them is the same: a change made once in the kit should change every screen. A color typed on
a screen doesn't follow the theme picker, the light mode or a future palette, and a Material button on a screen
doesn't get the kit's hover, focus and sizing.

## What the test checks

[`UiKitRulesTest`](../../composeApp/src/desktopTest/kotlin/org/mr0btain/ticketman/ui/UiKitRulesTest.kt) reads every
`.kt` file under `ui/` except `ui/kit/`, plus `App.kt`, line by line, with comments and the insides of strings
blanked out first. It fails on:

| Found | Why it's refused | Use instead |
|-------|------------------|-------------|
| `import androidx.compose.material3.X` where `X` isn't `Text`, `Icon` or `SnackbarHostState` | A Material component on a screen | The kit's version, or add one |
| `import androidx.compose.material3.<subpackage>...`, other than `windowsizeclass` | The same, from a subpackage | The kit's version, or add one |
| `import androidx.compose.material.` | Material 2 | The kit |
| `Color(0x` | A hard coded color | `TmTheme.colors`, or a tone through `TmColors.tone(...)` |
| `Color.Red` and the other named colors, except `Transparent` and `Unspecified` | A hard coded color | `TmTheme.colors` |
| `fontSize =` | A hard coded text size | A `TmTheme.typography` style |
| `TextStyle(` | A hand made text style | A `TmTheme.typography` style, `.copy(...)` it if needed |
| `RoundedCornerShape(` | A hand made shape | `TmTheme.shapes` |
| `MaterialTheme.` | Reading Material's theme | `TmTheme` |

Import lines are only checked for Material; the other patterns are looked for on every other line. The failure
message lists each hit as `path:line has ...`. `rulesCatchWhatTheyShould` runs the same checks on a made up file,
so a rule that stops matching fails too.

It runs with the client tests:

```bash
./gradlew :composeApp:desktopTest --tests "*UiKitRulesTest"
```

It also checks that it found more than 20 files, so a moved source folder fails the test instead of quietly
passing it.

## When it fails

Don't work around it. Either:

- use the kit component or token that already does the job (the [component table](kit.md#components) and the
  gallery list them), or
- add what's missing to the kit, as below, and use that.

## Adding to the kit

### A component

1. Put it in the `ui/kit/` folder it fits, named `Tm<Something>`, with KDoc saying when to use it.
2. Build it from the kit's own pieces: `TmTheme` tokens, `TmSurface` or `glass()`, `tmClickable`, `TmFieldFrame`
   for anything field shaped, `TmControlSize` for anything that sits in a row with buttons.
3. Add a sample of each state to a section in `gallery/GallerySections.kt`.
4. Run `KitGalleryRenderTest` and look over the PNGs in `composeApp/build/kit-gallery/`, light and dark.
5. Use it on the screen.

Kit files can use Material, hard coded `dp`s and shapes; the test doesn't look inside `ui/kit/`.

### A theme or font

- **Theme**: make a `TmPalette` in `TmPalettes.kt` with a dark and a light `TmColors`, and add it to
  `TmPalettes.all`. It shows up in the theme picker and the gallery.
- **Font**: put its `.ttf` files in `composeApp/src/commonMain/composeResources/font/`, make a `TmFont` in
  `TmFonts.kt` listing the files and their weights, and add it to `TmFonts.all`.

Pick an `id` you won't change: it's what's saved in settings, and an unknown `id` falls back to the default.

## Gotchas

- **`dp` values aren't checked.** Fixed widths like a 360 dp queue panel are fine on screens; gaps and padding
  should still come from `TmTheme.spacing`.
- **It's still a text search.** It reads the code, not what it compiles to, so a color or style built in a helper
  outside `ui/` (or reached through an alias) isn't caught. A `'"'` character literal confuses its string
  blanking for the rest of that line.
- **Paths are relative.** It reads `src/commonMain/...` from the `composeApp` folder, which is where Gradle runs
  it. Running it from an IDE with another working directory fails the "found more than 20 files" check.

## See also

- [UI kit](kit.md)
- [Conventions in `CLAUDE.md`](../../CLAUDE.md)
- [Add a UI kit component](../how-to/add-a-kit-component.md)
- [Add a theme or font](../how-to/add-a-theme-or-font.md)
