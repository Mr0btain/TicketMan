#!/usr/bin/env python3
"""The Flathub package (flatpak/): checks it, builds it locally and points it at a release.

Run from anywhere:

    python3 scripts/flatpak.py check              # the metainfo has the version being released and its screenshots
    python3 scripts/flatpak.py build [--install]  # build the jar and the Flatpak from this checkout, then lint both
    python3 scripts/flatpak.py build --jar FILE   # the same with a jar already built
    python3 scripts/flatpak.py update 0.9.0       # after the release: the manifest's jar url, sha256, tag and commit

`build` needs flatpak and the org.flatpak.Builder app (flatpak install flathub org.flatpak.Builder). It writes
build/flatpak/. `check` exits 1 when something is off, and CI runs it. docs/releasing.md#flathub has the steps.
"""
import argparse
import hashlib
import os
import re
import subprocess
import sys
import urllib.request

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
APP_ID = "org.Mr0btain.TicketMan"
REPO = "https://github.com/Mr0btain/TicketMan"
MANIFEST = os.path.join(ROOT, "flatpak", f"{APP_ID}.yml")
METAINFO = os.path.join(ROOT, "flatpak", f"{APP_ID}.metainfo.xml")
OUT = os.path.join(ROOT, "build", "flatpak")

# The files the manifest's build commands install from the git source, by their path in the repository
RELEASE_FILES = [
    "flatpak/ticketman.sh",
    f"flatpak/{APP_ID}.desktop",
    f"flatpak/{APP_ID}.metainfo.xml",
    "composeApp/icons/ticketman.png",
    "LICENSE",
]


def read(path):
    with open(path, encoding="utf-8") as f:
        return f.read()


def write(path, text):
    with open(path, "w", encoding="utf-8") as f:
        f.write(text)


def app_version():
    match = re.search(r"^ticketman\.version=(.+)$", read(os.path.join(ROOT, "gradle.properties")), re.M)
    return match.group(1).strip()


def check():
    """What's wrong with the metainfo for the version in gradle.properties, or an empty list"""
    version = app_version()
    metainfo = read(METAINFO)
    problems = []
    releases = re.findall(r'<release version="([^"]+)"', metainfo)
    if not releases or releases[0] != version:
        problems.append(f"the newest <release> is {releases[0] if releases else 'missing'}, not {version}")
    for tag in sorted(set(re.findall(rf"{re.escape(REPO.replace('github.com', 'raw.githubusercontent.com'))}/([^/]+)/", metainfo))):
        if tag != f"v{version}":
            problems.append(f"screenshots point at {tag}, not v{version}")
    for path in RELEASE_FILES:
        if not os.path.exists(os.path.join(ROOT, path)):
            problems.append(f"{path} is missing")
    return problems


def local_manifest(jar):
    """The manifest with its release sources swapped for the jar and files in this checkout"""
    manifest = read(MANIFEST)
    head, marker, _ = manifest.rpartition("\n    sources:\n")
    if not marker:
        sys.exit(f"No sources: in {MANIFEST}")
    sources = [f"      - type: file\n        path: {jar}\n        dest-filename: TicketMan-Desktop.jar\n"]
    for path in RELEASE_FILES:
        folder = os.path.dirname(path)
        sources.append(
            f"      - type: file\n        path: {os.path.join(ROOT, path)}\n"
            + (f"        dest: {folder}\n" if folder else "")
        )
    return head + marker + "".join(sources)


def run(*command, check=True):
    print("+", " ".join(command), flush=True)
    return subprocess.run(command, cwd=ROOT, check=check).returncode


def build(jar, install):
    problems = check()
    for problem in problems:
        print(f"warning: {problem}")
    if jar is None:
        run(os.path.join(ROOT, "gradlew"), ":composeApp:packageUberJarForCurrentOS", "-PallPlatforms")
        jar = os.path.join(ROOT, "composeApp", "build", "compose", "jars", f"TicketMan-Desktop-{app_version()}.jar")
    os.makedirs(OUT, exist_ok=True)
    manifest = os.path.join(OUT, f"{APP_ID}.yml")
    write(manifest, local_manifest(os.path.abspath(jar)))

    builder = ["flatpak", "run", "org.flatpak.Builder"]
    lint = ["flatpak", "run", "--command=flatpak-builder-lint", "org.flatpak.Builder"]
    repo = os.path.join(OUT, "repo")
    # rofiles-fuse fails inside some sandboxes and only speeds up the build. Screenshots are mirrored the way
    # Flathub's build does, so one that can't be fetched fails here
    run(*builder, "--user", "--force-clean", "--install-deps-from=flathub", "--disable-rofiles-fuse",
        "--mirror-screenshots-url=https://dl.flathub.org/media/", f"--state-dir={os.path.join(OUT, 'state')}",
        f"--repo={repo}", *(["--install"] if install else []), os.path.join(OUT, "build-dir"), manifest)
    run(*lint, "manifest", MANIFEST)
    if run(*lint, "repo", repo, check=False) != 0:
        print("appstream-external-screenshot-url and appstream-remote-icon-not-mirrored are expected here: they only "
              "clear once Flathub's own build publishes the media. appstream-missing-screenshots means the screenshot "
              "URLs don't load yet, as before the release's tag is pushed to GitHub. Anything else needs fixing")
    if install:
        print(f"Installed: flatpak run {APP_ID}")


def sha256_of(url):
    digest = hashlib.sha256()
    with urllib.request.urlopen(url) as response:
        for chunk in iter(lambda: response.read(1 << 20), b""):
            digest.update(chunk)
    return digest.hexdigest()


def tag_commit(tag):
    """The commit a tag on GitHub points at, through an annotated tag's ^{} when it is one"""
    output = subprocess.run(
        ["git", "ls-remote", f"{REPO}.git", f"refs/tags/{tag}", f"refs/tags/{tag}^{{}}"],
        capture_output=True, text=True, check=True,
    ).stdout
    refs = dict(reversed(line.split("\t")) for line in output.splitlines())
    commit = refs.get(f"refs/tags/{tag}^{{}}") or refs.get(f"refs/tags/{tag}")
    if commit is None:
        sys.exit(f"{tag} isn't on {REPO} yet")
    return commit


def update(version):
    tag = f"v{version}"
    url = f"{REPO}/releases/download/{tag}/TicketMan-Desktop-{version}.jar"
    print(f"Hashing {url}", flush=True)
    sha = sha256_of(url)
    commit = tag_commit(tag)

    manifest = read(MANIFEST)
    # The hashes stay quoted, or YAML reads one made only of digits as a number
    replacements = [
        (r"(?m)^(        url: ).*/TicketMan-Desktop-.*\.jar$", rf"\g<1>{url}"),
        (r"(?m)^(        sha256: )'?[0-9a-f]{64}'?$", rf"\g<1>'{sha}'"),
        (r"(?m)^(        tag: ).*$", rf"\g<1>{tag}"),
        (r"(?m)^(        commit: )'?[0-9a-f]{40}'?$", rf"\g<1>'{commit}'"),
    ]
    for pattern, replacement in replacements:
        manifest, count = re.subn(pattern, replacement, manifest)
        if count != 1:
            sys.exit(f"Expected one match for {pattern} in {MANIFEST}, found {count}")
    write(MANIFEST, manifest)
    print(f"{os.path.relpath(MANIFEST, ROOT)}: {tag} at {commit[:10]}, jar {sha[:12]}...")


def main():
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    commands = parser.add_subparsers(dest="command", required=True)
    commands.add_parser("check", help="check the metainfo is ready for the version in gradle.properties")
    build_command = commands.add_parser("build", help="build and lint the Flatpak from this checkout")
    build_command.add_argument("--jar", help="a TicketMan-Desktop jar already built with -PallPlatforms")
    build_command.add_argument("--install", action="store_true", help="install it for this user too")
    update_command = commands.add_parser("update", help="point the manifest at a published release")
    update_command.add_argument("version")
    args = parser.parse_args()

    if args.command == "check":
        problems = check()
        for problem in problems:
            print(f"{os.path.relpath(METAINFO, ROOT)}: {problem}")
        sys.exit(1 if problems else 0)
    elif args.command == "build":
        build(args.jar, args.install)
    else:
        update(args.version)


if __name__ == "__main__":
    main()
