# Builds the desktop DEB, and beside it TicketMan.tar.gz of the same app (--build-arg TARBALL=false leaves it out),
# inside a container, so a runner can make them for another architecture through QEMU:
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

ARG TARBALL=true

# The cache keeps Gradle and the dependencies between builds on the same Docker daemon. The tarball is made here
# rather than from the exported folder, so it keeps the files' permissions
RUN --mount=type=cache,target=/root/.gradle \
    ./gradlew --no-daemon :composeApp:packageDeb :composeApp:createDistributable \
    && mkdir /out \
    && cp composeApp/build/compose/binaries/main/deb/*.deb /out/ \
    && if [ "$TARBALL" = "true" ]; then \
         tar -czf /out/TicketMan.tar.gz -C composeApp/build/compose/binaries/main/app TicketMan; \
       fi

FROM scratch
COPY --from=build /out /
