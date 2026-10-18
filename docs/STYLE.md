# Writing these docs

How pages in `docs/` are written, so they read as one set.

## Who they're for

Developers working on TicketMan. Assume the reader knows Kotlin and has the repo open, but not this codebase.

## Tone

- Plain, short sentences. Say what the code does, then why if it isn't obvious.
- Explain the shape and the reasons. Don't restate code line by line; link to it instead.
- Write about the code as it is now. If something is unfinished or odd, say so in a **Known gaps** section rather
  than hiding it.

## Linking to code

- Link to files with a path relative to the page, like
  [`Database.kt`](../server/src/main/kotlin/org/mr0btain/ticketman/components/database/Database.kt).
- Long package paths get noisy. After the first link on a page, shorten paths by their module and the part after
  `ticketman/`, like `server/.../database/Database.kt`.
- Name classes and functions in backticks: `TicketDatabase`, `dbWrite()`.
- Don't link to line numbers. They go stale on the next edit.

## Diagrams

Use Mermaid code blocks (```` ```mermaid ````). They render on GitHub and Gitea and stay editable as text. Keep each
one small enough to read without zooming.

## Images

Put images in `docs/images/`. Screenshots come from the render tests (`ScreenRenderTest`, `KitGalleryRenderTest`)
rather than the running app, so they can be redone the same way after a UI change.

## Page template

```markdown
# Title

One or two sentences: what this part of the code does and when you'd touch it.

## How it works

The main idea, with a diagram if it helps.

## Where it lives

| What | Where |
|------|-------|
| ...  | ...   |

## Gotchas

Things that have caught people out.

## Known gaps

Unfinished or odd parts, if any.

## See also

Links to related pages.
```

Sections that would be empty are left out.

## Keeping them current

When a change makes a page wrong, fix the page in the same PR. When a page is added, add it to
[`README.md`](README.md) and tick it in [`PLAN.md`](PLAN.md).
