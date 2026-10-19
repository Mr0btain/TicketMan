#!/usr/bin/env python3
"""Checks that every relative link and heading anchor in docs/ (and CLAUDE.md) points somewhere real.

Run from the repository root:

    python3 scripts/check_doc_links.py

Prints each broken link as `file link` and exits 1 if there are any.
"""
import glob
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))


def anchors(path):
    """The anchors a markdown file's headings make, the way Gitea and GitHub write them"""
    with open(path, encoding="utf-8") as f:
        return {
            re.sub(r"[^\w\- ]", "", m.group(1).strip().lower()).replace(" ", "-")
            for m in (re.match(r"^#+\s+(.*)", line) for line in f)
            if m
        }


def broken_links(files):
    for f in files:
        with open(f, encoding="utf-8") as handle:
            text = handle.read()
        for link in re.findall(r"\]\(([^)\s]+)\)", text):
            if link.startswith(("http://", "https://", "mailto:")):
                continue
            path, _, anchor = link.partition("#")
            target = os.path.normpath(os.path.join(os.path.dirname(f), path)) if path else f
            if not os.path.exists(target):
                yield f, link
            elif anchor and target.endswith(".md") and anchor not in anchors(target):
                yield f, link


def main():
    os.chdir(ROOT)
    files = sorted(glob.glob("docs/**/*.md", recursive=True)) + ["CLAUDE.md"]
    problems = list(broken_links(files))

    for f, link in problems:
        print(f, link)

    if problems:
        print(f"{len(problems)} broken link(s)")
        return 1

    print(f"docs links resolve: {len(files)} files")
    return 0


if __name__ == "__main__":
    sys.exit(main())
