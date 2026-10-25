# The TicketMan server, built from the repo root after building the server on the host (no Android SDK needed):
#
#   ./gradlew :server:installDist -PwithWeb
#   docker build -f server/Dockerfile -t ticketman .
#
# The copied files are plain JVM jars, so the same build makes an amd64 or arm64 image
# (docker buildx build --platform linux/amd64,linux/arm64 ...). See docs/operations.md and docker/docker-compose.yml
FROM eclipse-temurin:21-jre

# Runs as its own user, which owns the data and backup folders. Named volumes take that ownership from the image;
# a bind mounted folder has to be owned by uid 10001
RUN groupadd --system --gid 10001 ticketman \
    && useradd --system --uid 10001 --gid ticketman --home-dir /data --no-create-home ticketman \
    && mkdir -p /data /backups \
    && chown ticketman:ticketman /data /backups

COPY server/build/install/server /app

ENV DATA_PATH=/data \
    PORT=8080 \
    RESTART_ON_EXIT=true \
    JAVA_OPTS="-XX:MaxRAMPercentage=75"

VOLUME /data
EXPOSE 8080
USER ticketman
WORKDIR /data

# The JRE image has no curl or wget, so bash asks /api/ping itself. A first start with a restore to unpack can take
# a while, hence the start period
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
    CMD ["bash", "-c", "exec 3<>/dev/tcp/127.0.0.1/${PORT:-8080} && printf 'GET /api/ping HTTP/1.0\\r\\nHost: localhost\\r\\n\\r\\n' >&3 && timeout 4 grep -q TicketMan <&3"]

# The start script execs java, so it's the one that gets docker stop's SIGTERM and shuts H2 down cleanly
ENTRYPOINT ["/app/bin/server"]
