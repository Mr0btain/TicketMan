# Releasing

How the packages are versioned and built, by hand or by the release workflows, and the one time setup the workflows
need.

## The version

`ticketman.version` in [`gradle.properties`](../gradle.properties) is the only place the version is written. Every
package is built from it:

| Package | Where it goes | Notes |
|---|---|---|
| Server | `server/build.gradle.kts` `version`, and the generated `BuildInfo.VERSION` | `AdminService.SERVER_VERSION` (admin screen, backup manifests) |
| Desktop installers | `composeApp/build.gradle.kts` `packageVersion` | A `-rc1` suffix is dropped, since installers only take numbers. macOS refuses a version starting with 0, so 0.x app bundles say 1.0.0 inside |
| Android | `androidApp/build.gradle.kts` `versionName`, and `versionCode` from it | `major * 10000 + minor * 100 + patch`, so 0.8.0 is 800 |

`API_VERSION` is separate: it only changes when older clients can't use the server any more (see
[decisions](decisions.md)). The iOS app isn't built, so `iosApp/Configuration/Config.xcconfig` is updated by hand.

## Building by hand

Each desktop installer has to be built on its own system, because jpackage can't make one for another OS:

```bash
./gradlew :composeApp:packageMsi        # Windows: composeApp/build/compose/binaries/main/msi/
./gradlew :composeApp:packageDmg        # macOS:   .../main/dmg/
./gradlew :composeApp:packageDeb        # Linux:   .../main/deb/ (needs dpkg-deb and fakeroot)
./gradlew :composeApp:packageDistributionForCurrentOS   # whichever of those fits this machine

./gradlew :androidApp:assembleRelease   # androidApp/build/outputs/apk/release/ (x86 only, see getting started)
./gradlew :server:distZip -PwithWeb     # server/build/distributions/server-<version>.zip, web client included
./gradlew :server:installDist -PwithWeb && docker build -f server/Dockerfile -t ticketman .
```

The installers bundle their own Java runtime, so the machines they go on don't need Java. The server zip does need
Java 21: unzip it and run `bin/server` (or `bin\server.bat`). [Running a server](operations.md) covers the settings.

The release APK is signed when a keystore is given, through env vars or `~/.gradle/gradle.properties` (never the
repo's):

| Env var | Gradle property |
|---|---|
| `TICKETMAN_KEYSTORE` (path to the `.jks`) | `ticketman.keystore` |
| `TICKETMAN_KEYSTORE_PASSWORD` | `ticketman.keystorePassword` |
| `TICKETMAN_KEY_ALIAS` | `ticketman.keyAlias` |
| `TICKETMAN_KEY_PASSWORD` | `ticketman.keyPassword` |

Without one the APK comes out as `androidApp-release-unsigned.apk`, which Android won't install.

## Making a release

1. Set `ticketman.version` in `gradle.properties` and merge that to master.
2. Tag the commit and push the tag:

   ```bash
   git tag v0.9.1
   git push origin v0.9.1
   ```

The release workflows then check that the tag matches `ticketman.version` (a mismatch stops them), build everything,
and publish a release with the files and a `SHA256SUMS`. Versions starting with `0.` or with a `-` suffix are marked
as pre-releases, and only plain `x.y.z` versions get the `latest` Docker tag. Running a workflow by hand (Run workflow)
builds everything without publishing, which is a good way to try it before tagging.

| Workflow | Builds | Publishes to |
|---|---|---|
| [`.github/workflows/release.yml`](../.github/workflows/release.yml) | MSI (Windows x64), DMG (macOS arm64), DEB (x64 and arm64), APK, server zip, Docker image for amd64 and arm64 | GitHub release, `ghcr.io/<owner>/ticketman` |
| [`.gitea/workflows/release.yml`](../.gitea/workflows/release.yml) | DEB (x64, and arm64 through QEMU), APK, server zip, Docker image | Gitea release, the Gitea container registry |

Gitea doesn't read `.github/workflows` while `.gitea/workflows` exists, so a repo pushed to both only runs each
workflow in its own place. The Gitea one only builds on Linux, and its `ubuntu-latest` runner has to be x86 for the
APK. The image step needs a runner with Docker access.

The server zip, APK, web client and Docker image (built for both through QEMU) don't depend on the runner's
architecture, but a DEB is only for the architecture that built it, since it bundles a Java runtime and Compose's
native graphics. GitHub builds the arm64 one on its `ubuntu-24.04-arm` runner, which is free for public repos only.
On Gitea the runner is x86, so the arm64 DEB is built in an arm64 container emulated through QEMU
([`docker/deb.Dockerfile`](../docker/deb.Dockerfile)), which needs Docker access like the image step. Everything runs
emulated, so it's several times slower than the other jobs, and a release waits for it. To leave it out, tick "Skip the
arm64 DEB" when running the workflow by hand, or set a `SKIP_ARM64` variable (Settings > Actions > Variables) to `true`
for tag pushes; the release is then published without it. The same Dockerfile builds a DEB by hand:

```bash
docker buildx build --platform linux/arm64 -f docker/deb.Dockerfile --output type=local,dest=dist .
```

## One time setup

**Android keystore.** Make it once and keep it, with its passwords, somewhere safe. Android only installs an update
signed with the same key, so losing it means everyone uninstalls and starts over:

```bash
keytool -genkeypair -v -keystore ticketman-release.jks -alias ticketman -keyalg RSA -keysize 4096 -validity 10000
base64 -w0 ticketman-release.jks          # the value for ANDROID_KEYSTORE_BASE64
```

**Secrets.** On GitHub under Settings > Secrets and variables > Actions, and on Gitea under the repo's Settings >
Actions > Secrets:

| Secret | Value |
|---|---|
| `ANDROID_KEYSTORE_BASE64` | The keystore, base64 |
| `ANDROID_KEYSTORE_PASSWORD` | The keystore's password |
| `ANDROID_KEY_ALIAS` | `ticketman`, or the alias you used |
| `ANDROID_KEY_PASSWORD` | The key's password (the same as the keystore's unless you set it apart) |
| `REGISTRY_TOKEN` | Gitea only: a token with `write:package`, for the image. Without it the image is skipped |

On Gitea, if the runner reaches Gitea by an internal name (the workflow sees a server URL like `http://gitea:3000`),
also add a `REGISTRY` variable under Settings > Actions > Variables set to Gitea's public HTTPS host, like
`git.example.com`. The host's Docker daemon pushes the image, and it can't resolve the internal name or push to a plain
HTTP registry.

GitHub pushes the image with its own `GITHUB_TOKEN`. The first time it does, the package is private: make it public
under the package's settings if anyone should pull it without logging in.

## Unsigned installers

The MSI and DMG aren't code signed. Windows SmartScreen warns about the MSI (More info > Run anyway), and macOS says
the app "can't be opened". Right click it and choose Open, or run `xattr -dr com.apple.quarantine
/Applications/TicketMan.app`. Signing the MSI needs a code signing certificate, and signing the DMG needs an Apple
Developer account plus notarization (Compose's `macOS { signing { } notarization { } }`).
