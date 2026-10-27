# Writing these docs

How pages in `docs/` are written, so they read as one set.

## Who they're for

Developers working on TicketMan.

## Tone

- Plain, short sentences. Say what the code does, then why if it isn't obvious.

## Linking to code

- Link to files with a path relative to the page, like
  [`Database.kt`](../server/src/main/kotlin/org/mr0btain/ticketman/components/database/Database.kt).
- Long package paths get noisy. After the first link on a page, shorten paths by their module and the part after
  `ticketman/`, like `server/.../database/Database.kt`.
- Name classes and functions in backticks: `TicketDatabase`, `dbWrite()`.
- Don't link to line numbers.

## Diagrams

Use Mermaid code blocks (```` ```mermaid ````). They render on GitHub and Gitea and stay editable as text. Keep each
one small enough to read without zooming.

## Images

Images go in `docs/images/`. Screenshots come from the render tests (`ScreenRenderTest`, `KitGalleryRenderTest`)
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

## Todos

Unfinished or things to work on at a later time.

## See also

Links to related pages.
```

## Keeping them current

When a change makes a page wrong, fix the page in the same PR. When a page is added, add it to
[`README.md`](README.md)

- The pull request template (`.gitea/pull_request_template.md`) asks "Docs updated?" on every PR.
- The API pages are checked against the routes by `python3 scripts/check_api_docs.py`. Run it after any route
  change.
- You can test links in `docs/` and `CLAUDE.md` by running`python3 scripts/check_doc_links.py`.
- CI (`.gitea/workflows/ci.yml`) runs both checks on every pull request, with the tests.
- KDoc on the code covers what a class or function does; these pages cover how the pieces fit and why.
