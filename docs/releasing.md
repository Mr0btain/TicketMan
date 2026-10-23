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
./gradlew :server:buildFatJar -PwithWeb # server/build/libs/server-all.jar, the same as one jar
./gradlew :composeApp:packageUberJarForCurrentOS -PallPlatforms   # composeApp/build/compose/jars/, any desktop
./gradlew :server:installDist -PwithWeb && docker build -f server/Dockerfile -t ticketman .
```

The installers bundle their own Java runtime, so the machines they go on don't need Java. The server zip does need
Java 21: unzip it and run `bin/server` (or `bin\server.bat`). [Running a server](operations.md) covers the settings.

The jars need Java 21 too, and run with `java -jar`. The server one runs anywhere. The desktop one only runs on the
system that built it, unless `-PallPlatforms` puts in the Skia graphics library for every desktop system (Windows,
Linux and macOS, x64 and arm64): that makes it about 100 MB, and one jar runs on all of them. Started from a jar, the
server's `RESTART_ON_EXIT` needs a service manager (systemd, a Windows service) that starts it again.

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
| [`.github/workflows/release.yml`](../.github/workflows/release.yml) | MSI (Windows x64), DMG (macOS arm64), DEB (x64 and arm64), APK, desktop jar, server zip and jar, Docker image for amd64 and arm64 | GitHub release, `ghcr.io/<owner>/ticketman` |
| [`.gitea/workflows/release.yml`](../.gitea/workflows/release.yml) | MSI (Windows x64, on your own Windows runner), DEB (x64, and arm64 through QEMU), APK, desktop jar, server zip, jar and Docker image, each with and without the web client | Gitea release, the Gitea container registry, Docker Hub |

Gitea doesn't read `.github/workflows` while `.gitea/workflows` exists, so a repo pushed to both only runs each
workflow in its own place. The Gitea one's `ubuntu-latest` runner has to be x86 for the APK, and the image step needs
a runner with Docker access. The MSI needs a Windows runner (below); there's no Mac one, so the DMG is GitHub only.

The server zip, APK, web client and Docker image (built for both through QEMU) don't depend on the runner's
architecture, but a DEB is only for the architecture that built it, since it bundles a Java runtime and Compose's
native graphics. GitHub builds the arm64 one on its `ubuntu-24.04-arm` runner, which is free for public repos only.
On Gitea the runner is x86, so the arm64 DEB is built in an arm64 container emulated through QEMU
([`docker/deb.Dockerfile`](../docker/deb.Dockerfile)), which needs Docker access like the image step. Everything runs
emulated, so it's several times slower than the other jobs, and a release waits for it unless it's skipped (below).
The same Dockerfile builds a DEB by hand:

```bash
docker buildx build --platform linux/arm64 -f docker/deb.Dockerfile --output type=local,dest=dist .
```

The Gitea workflow releases the server twice, with the web client built in and without it (`/` is then a plain up
check):

| Server | Zip | Jar | Gitea image tags | Docker Hub image |
|---|---|---|---|---|
| With the web client | `TicketMan-server-<version>.zip` | `TicketMan-server-<version>.jar` | `<version>`, `latest` | `<user>/ticketman:<version>`, `latest` |
| Without it | `TicketMan-server-noweb-<version>.zip` | `TicketMan-server-noweb-<version>.jar` | `<version>-noweb`, `latest-noweb` | `<user>/ticketman-server:<version>`, `latest` |

Any part can be left out. Each has a box to tick when running the workflow by hand, and a variable that skips it for
tag pushes when set to `true`; the release is published with the rest, and isn't published if a build fails:

| Leaves out | Input | Variable |
|---|---|---|
| The amd64 DEB and the APK | `skip_amd64` | `SKIP_AMD64` |
| The arm64 DEB | `skip_arm64` | `SKIP_ARM64` |
| The Windows MSI | `skip_windows` | `SKIP_WINDOWS` |
| The desktop jar | `skip_desktop_jar` | `SKIP_DESKTOP_JAR` |
| The server with the web client | `skip_server_web` | `SKIP_SERVER_WEB` |
| The server without the web client | `skip_server_noweb` | `SKIP_SERVER_NOWEB` |
| Only the server jars (the zips and images are still built) | `skip_server_jars` | `SKIP_SERVER_JARS` |

## One time setup

**Windows runner (Gitea).** The MSI job runs on a Windows machine with `act_runner` in host mode, so jobs run straight
on it with that user's rights (keep it to repos you trust). Without one online the job waits in the queue, and the
release with it, so set `SKIP_WINDOWS` while it's away. On the machine:

1. Install Git for Windows and add `C:\Program Files\Git\bin` to the system PATH, ahead of `C:\Windows\System32` if
   WSL is installed (the steps run in bash), and Node.js LTS (the actions run on it). If the MSI build complains about
   WiX, turn on .NET Framework 3.5 under Windows Features.
2. Turn on long paths: `git config --system core.longpaths true`, and `LongPathsEnabled` = 1 under
   `HKLM\SYSTEM\CurrentControlSet\Control\FileSystem`.
3. Download `act_runner` for Windows, then with a token from Settings > Actions > Runners > Create new runner:

   ```powershell
   .\act_runner.exe generate-config > config.yaml
   .\act_runner.exe register --no-interactive --config config.yaml --instance https://git.example.com `
     --token <token> --name windows --labels windows-latest:host
   ```

4. Start `act_runner.exe daemon --config config.yaml` with Windows, as your own user (not SYSTEM, so the Gradle cache
   stays in your profile): through NSSM (`winget install NSSM.NSSM`) or a scheduled task run at startup. Turn off
   sleep.

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
| `REGISTRY_TOKEN` | Gitea only: a token with `write:package`, for the image in Gitea's registry. Without it the image isn't pushed there |
| `DOCKERHUB_TOKEN` | Gitea only: a Docker Hub access token with Read & Write, for the images on Docker Hub. Without it they're skipped |

On Gitea, if the runner reaches Gitea by an internal name (the workflow sees a server URL like `http://gitea:3000`),
also add a `REGISTRY` variable under Settings > Actions > Variables set to Gitea's public HTTPS host, like
`git.example.com`. The host's Docker daemon pushes the image, and it can't resolve the internal name or push to a plain
HTTP registry.

For Docker Hub, also add a `DOCKERHUB_USERNAME` variable (like `mr0btain`) and create the two repositories there,
`ticketman` (with the web client) and `ticketman-server` (without it). With neither `REGISTRY_TOKEN` nor
`DOCKERHUB_TOKEN` set, no image is built.

GitHub pushes the image with its own `GITHUB_TOKEN`. The first time it does, the package is private: make it public
under the package's settings if anyone should pull it without logging in.

## Unsigned installers

The MSI and DMG aren't code signed. Windows SmartScreen warns about the MSI (More info > Run anyway), and macOS says
the app "can't be opened". Right click it and choose Open, or run `xattr -dr com.apple.quarantine
/Applications/TicketMan.app`. Signing the MSI needs a code signing certificate, and signing the DMG needs an Apple
Developer account plus notarization (Compose's `macOS { signing { } notarization { } }`).
