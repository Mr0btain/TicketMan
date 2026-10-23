# Builds the desktop DEB inside a container, so a runner can make one for another architecture through QEMU:
#
#   docker buildx build --platform linux/arm64 -f docker/deb.Dockerfile --output type=local,dest=dist .
#
# The DEB bundles the Java runtime and Compose's native graphics of the machine that builds it, so emulating arm64
# makes an arm64 DEB. Everything runs emulated, Gradle and the Kotlin compiler included, so it's slow.
# The release workflow (.gitea/workflows/release.yml) uses it, see docs/releasing.md
FROM eclipse-temurin:21-jdk AS build

# jpackage makes the DEB with these
RUN apt-get update \
    && apt-get install -y --no-install-recommends fakeroot binutils \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /src
COPY . .

# The cache keeps Gradle and the dependencies between builds on the same Docker daemon
RUN --mount=type=cache,target=/root/.gradle \
    ./gradlew --no-daemon :composeApp:packageDeb \
    && mkdir /out \
    && cp composeApp/build/compose/binaries/main/deb/*.deb /out/

FROM scratch
COPY --from=build /out /
