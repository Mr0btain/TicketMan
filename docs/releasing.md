# Releasing

How the packages are versioned and built, by hand or by the release workflows, and the one time setup the workflows
need.

## The version

`ticketman.version` in [`gradle.properties`](../gradle.properties) is the only place the version is written. Every
package is built from it:

| Package | Where it goes | Notes |
|---|---|---|
| Server | `server/build.gradle.kts` `version` | `AdminService.SERVER_VERSION` (admin screen, backup manifests) |
| Shared | The generated `BuildInfo.VERSION` (`shared/build.gradle.kts`), as `APP_VERSION` | The server sends it on `/api/ping` and every client compares it with its own (see [Versions](api/README.md#versions)) |
| Desktop installers | `composeApp/build.gradle.kts` `packageVersion` | A `-rc1` suffix is dropped, since installers only take numbers. macOS refuses a version starting with 0, so 0.x app bundles say 1.0.0 inside |
| Android | `androidApp/build.gradle.kts` `versionName`, and `versionCode` from it | `major * 10000 + minor * 100 + patch`, so 0.8.0 is 800 |

Pick the number with that check in mind: a client and server a major version apart (a minor version before 1.0)
refuse to work together, so a release with breaking API changes bumps the major (the minor while still on 0.x), and
one that only adds things bumps the minor. `API_VERSION` is separate: it only changes when older clients can't use
the server any more, and only ever warns. `iosApp/Configuration/Config.xcconfig`'s `MARKETING_VERSION` is updated by hand alongside it.

## The icon

[`scripts/AppIcon.java`](../scripts/AppIcon.java) draws the app icon (a blue ticket on graphite, the Bench theme's
colors) and writes every copy of it: `composeApp/icons/` for the MSI, DMG and DEB (`iconFile` in
`composeApp/build.gradle.kts`), `app_icon.png` in the client's resources for the desktop window and taskbar,
`favicon.png` and `apple-touch-icon.png` for the web client, Android's launcher icons (adaptive vectors, and PNGs for
Android 7) and the iOS app icon. To change it, edit the script and run `java scripts/AppIcon.java` from the
repository root.

## Building by hand

Each desktop installer has to be built on its own system, because jpackage can't make one for another OS:

```bash
./gradlew :composeApp:packageMsi        # Windows: composeApp/build/compose/binaries/main/msi/
./gradlew :composeApp:packageDmg        # macOS:   .../main/dmg/
./gradlew :composeApp:packageDeb        # Linux:   .../main/deb/ (needs dpkg-deb and fakeroot)
./gradlew :composeApp:createDistributable  # the app unpacked: .../main/app/TicketMan/ (bin/TicketMan starts it)
./gradlew :composeApp:packageTarball    # that packed: .../main/tar/
./gradlew :composeApp:packageDistributionForCurrentOS   # whichever of those fits this machine

./gradlew :androidApp:assembleRelease   # androidApp/build/outputs/apk/release/ (x86 only, see getting started)
./gradlew :server:distZip -PwithWeb     # server/build/distributions/, web client included
./gradlew :server:buildFatJar -PwithWeb # server/build/libs/, the same as one jar
./gradlew :composeApp:packageUberJarForCurrentOS -PallPlatforms   # composeApp/build/compose/jars/, any desktop
./gradlew :server:installDist -PwithWeb && docker build -f server/Dockerfile -t ticketman .
```

The build names each file for what it is, so the release workflows only collect them. The installers, tarball and a
desktop jar without `-PallPlatforms` are for the system and architecture that built them (`x64` or `arm64`); the rest
run anywhere:

| Package | File |
|---|---|
| Installers and tarball | `TicketMan-<OS>-<version>-<arch>.<ext>`: `TicketMan-Windows-0.8.0-x64.msi`, `TicketMan-macOS-0.8.0-arm64.dmg`, `TicketMan-macOS-0.8.0-x64.dmg`, `TicketMan-Linux-0.8.0-x64.deb`, `TicketMan-Linux-0.8.0-arm64.tar.gz` |
| Desktop jar | `TicketMan-Desktop-<version>.jar` with `-PallPlatforms`, otherwise `TicketMan-<OS>-<version>-<arch>.jar` |
| APK | `TicketMan-Android-<version>.apk` (`-unsigned` added without a keystore), a copy beside AGP's own `androidApp-release.apk` |
| Server zip and jar | `TicketMan-Server-Web-<version>.zip` / `.jar` with `-PwithWeb`, `TicketMan-Server-<version>.zip` / `.jar` without |

The version is the whole `ticketman.version`, a `-rc1` included. The installers' names are set by renaming what
jpackage makes once it's done (`AbstractJPackageTask` in `composeApp/build.gradle.kts`), the rest through the tasks'
`archiveFileName`.

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

Without one the APK comes out unsigned (`TicketMan-Android-<version>-unsigned.apk`), which Android won't install.

## Making a release

1. Set `ticketman.version` in `gradle.properties`
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
| [`.github/workflows/release.yml`](../.github/workflows/release.yml) | The same as Gitea's (the arm64 DEB and tarball built natively, the arm64 MSI on `windows-11-arm`), plus the DMGs (macOS arm64 on `macos-latest`, x64 for Intel Macs on `macos-15-intel`) and, when asked, the unsigned iOS app | GitHub release, `ghcr.io/<owner>/ticketman` |
| [`.gitea/workflows/release.yml`](../.gitea/workflows/release.yml) | MSI (Windows x64, and arm64 when asked, on your own Windows runners), DEB and tarball (x64, and arm64 through QEMU), APK, desktop jar, server zip, jar and Docker image, each with and without the web client | Gitea release, the Gitea container registry, Docker Hub |

Gitea doesn't read `.github/workflows` while `.gitea/workflows` exists, so a repo pushed to both only runs each
workflow in its own place. Each also has its own copy of the tests ([`.gitea/workflows/ci.yml`](../.gitea/workflows/ci.yml)
and [`.github/workflows/ci.yml`](../.github/workflows/ci.yml), which also compiles iOS on a Mac), so keep the two
release workflows and the two CIs in step. The Gitea one's `ubuntu-latest` runner has to be x86 for the APK, and the image step needs
a runner with Docker access. The MSI needs a Windows runner (below); there's no Mac one, so the DMGs are GitHub only.

The server zip, APK, web client and Docker image (built for both through QEMU) don't depend on the runner's
architecture, but a DEB is only for the architecture that built it, since it bundles a Java runtime and Compose's
native graphics. GitHub builds the arm64 one on its `ubuntu-24.04-arm` runner, which is free for public repos only.
On Gitea the runner is x86, so the arm64 DEB is built in an arm64 container emulated through QEMU
([`docker/deb.Dockerfile`](../docker/deb.Dockerfile)), which needs Docker access like the image step. Everything runs
emulated, so it's several times slower than the other jobs, and a release waits for it unless it's skipped (below).
Beside each DEB both workflows release `TicketMan-Linux-<version>-<arch>.tar.gz`, the same app unpacked, for
systems without dpkg: extract it anywhere and run `TicketMan/bin/TicketMan`. The same Dockerfile builds a DEB (and
the tarball) by hand:

```bash
docker buildx build --platform linux/arm64 -f docker/deb.Dockerfile --output type=local,dest=dist .
```

Both workflows release the server twice, with the web client built in and without it (`/` is then a plain up
check). GitHub pushes the same tags to `ghcr.io/<owner>/ticketman` in place of Gitea's registry; only Gitea pushes
to Docker Hub:

| Server | Zip | Jar | Gitea image tags | Docker Hub image |
|---|---|---|---|---|
| With the web client | `TicketMan-Server-Web-<version>.zip` | `TicketMan-Server-Web-<version>.jar` | `<version>`, `latest` | `<user>/ticketman:<version>`, `latest` |
| Without it | `TicketMan-Server-<version>.zip` | `TicketMan-Server-<version>.jar` | `<version>-noweb`, `latest-noweb` | `<user>/ticketman-server:<version>`, `latest` |

Any part can be left out, the same way in both workflows. Each has a box to tick when running the workflow by hand, and a variable that skips it for
tag pushes when set to `true`; the release is published with the rest, and isn't published if a build fails:

| Leaves out | Input | Variable |
|---|---|---|
| The amd64 DEB and the APK | `skip_amd64` | `SKIP_AMD64` |
| The arm64 DEB | `skip_arm64` | `SKIP_ARM64` |
| Only the Linux tarballs (the DEBs are still built) | `skip_tarball` | `SKIP_TARBALL` |
| The Windows x64 MSI | `skip_windows` | `SKIP_WINDOWS` |
| The Windows arm64 MSI (skipped by default on Gitea, below) | `skip_windows_arm64` | `SKIP_WINDOWS_ARM64` |
| The macOS arm64 DMG (GitHub only) | `skip_mac` | `SKIP_MAC` |
| The macOS x64 DMG for Intel Macs (GitHub only) | `skip_mac_x64` | `SKIP_MAC_X64` |
| The desktop jar | `skip_desktop_jar` | `SKIP_DESKTOP_JAR` |
| The server with the web client | `skip_server_web` | `SKIP_SERVER_WEB` |
| The server without the web client | `skip_server_noweb` | `SKIP_SERVER_NOWEB` |
| Only the server jars (the zips and images are still built) | `skip_server_jars` | `SKIP_SERVER_JARS` |

The GitHub workflow can also build the iOS app, unsigned (`TicketMan-iOS-<version>-unsigned.ipa`). That works the
other way round until it's been tried on a device: it's only built with the `build_ios` box or the `BUILD_IOS`
variable set to `true`, and a failed iOS build doesn't stop the release. It's installed by sideloading (AltStore,
Sideloadly), which signs it with the person's own Apple ID.

jpackage can't cross build, so the arm64 MSI (`TicketMan-Windows-<version>-arm64.msi`) needs a Windows on ARM machine.
GitHub builds it on its `windows-11-arm` runner, free for public repos only. On Gitea it needs a runner registered with
the `windows-arm64:host` label (set up like the x64 one below), so until there is one it's skipped by default: untick
`skip_windows_arm64` on a run by hand, and set `SKIP_WINDOWS_ARM64` to `false` for tag pushes. Without it, Windows on
ARM still runs the x64 MSI under emulation.

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
`ticketman` (with the web client) and `ticketman-server` (without it). Only the Gitea workflow pushes to Docker Hub,
so these go on Gitea only. With neither `REGISTRY_TOKEN` nor `DOCKERHUB_TOKEN` set, no image is built there.

GitHub pushes the image with its own `GITHUB_TOKEN`. The first time it does, the package is private: make it public
under the package's settings if anyone should pull it without logging in.

## Unsigned installers

The MSI and DMG aren't code signed. Windows SmartScreen warns about the MSI (More info > Run anyway), and macOS says
the app "can't be opened". Right click it and choose Open, or run `xattr -dr com.apple.quarantine
/Applications/TicketMan.app`. Signing the MSI needs a code signing certificate, and signing the DMG needs an Apple
Developer account plus notarization (Compose's `macOS { signing { } notarization { } }`).

## Snap

The desktop app is also packaged as the `ticketman` snap. Everything for it is in [`snap/`](../snap):
`snapcraft.yaml` and the menu entry in `gui/ticketman.desktop`. Unlike the Flatpak it doesn't use the jar: it packs
the tarball from `packageTarball`, which already has its own Java runtime and Skia, so there is no Java in the snap
besides the app's own. jpackage only builds for the machine it runs on, so each architecture's snap is packed on that
architecture (or in the same arm64 container as the arm64 DEB) from that machine's tarball:

```bash
./gradlew :composeApp:packageTarball          # composeApp/build/compose/binaries/main/tar/TicketMan-Linux-<version>-<arch>.tar.gz
snapcraft pack                                # needs snapcraft and LXD: sudo snap install snapcraft --classic; sudo snap install lxd; sudo lxd init --auto
sudo snap install --dangerous ./ticketman_<version>_<arch>.snap
snap connect ticketman:password-manager-service
ticketman
```

The version comes from the tarball's name. The snap is strictly confined, with the `gnome` extension (X11, GL,
fonts, portals for file dialogs), `network`, `cups` for printing, `home`, and `password-manager-service` for KSafe's
key in the keyring. That last one isn't connected on install unless the store grants it (ask on the
[Snapcraft forum](https://forum.snapcraft.io/c/store-requests/19)), so until then users connect it themselves.
Java reads its home folder from the user's account, not `$HOME`, so `JAVA_TOOL_OPTIONS` points `user.home` at the
snap's own folder, where KSafe keeps `~/.eu_anifantakis_ksafe` (`~/snap/ticketman/current/`). Settings from the DEB
or the Flatpak aren't carried over.

**Publishing.** Once: make an account on [snapcraft.io](https://snapcraft.io), `snapcraft login` and
`snapcraft register ticketman`. Each release: `snapcraft upload --release=edge ticketman_<version>_<arch>.snap` for
both architectures, try it, then promote it with `snapcraft release ticketman <revision> stable` (or from the
snapcraft.io dashboard).
