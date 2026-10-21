#!/usr/bin/env bash
# Development commands. Everything runs in Docker (inside WSL on Windows), so no Java or Android SDK on the machine.
#
#   scripts/dev.sh image              build the build image (Java + Android SDK)
#   scripts/dev.sh test               unit tests
#   scripts/dev.sh apk                debug APK -> dist/
#   scripts/dev.sh apk-shrunk         the release's shrinking with the test key and log -> dist/
#   scripts/dev.sh screenshots        pictures of the screens (example data) -> docs/screenshots/
#   scripts/dev.sh ts-server up|down  start/stop the TeamSpeak test server container (TS_CONTAINER)
#   scripts/dev.sh it                 tests against that server (TS_TEST_SERVER)
#   scripts/dev.sh devserver up|down  the page phones install test builds from and send their log to (DEV_PORT)
#   scripts/dev.sh gradle <args>      any Gradle command
#   scripts/dev.sh keystore           make the release key (once; keep it safe, see DEVELOPING.md)
#   scripts/dev.sh release            the signed release APK -> dist/ (asks for the key's password)
#
# This machine's own settings go in dev.env next to this repo's README (never committed), for example:
#   TS_CONTAINER=my-teamspeak-test      an existing TeamSpeak test server container
#   TS_TEST_SERVER=127.0.0.1:9987       where the tests connect to it
#   TS_TEST_FT_PORT=30033               its file transfer port, when published under another number
#   DEV_PORT=8686
#   POCKETTS_KEYSTORE=~/.pocketts/release.keystore   where the release key is (outside the repo; this is the default)
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
[ -f "$ROOT/dev.env" ] && . "$ROOT/dev.env"

IMAGE=pocketts-build
KEYSTORE="${POCKETTS_KEYSTORE:-$HOME/.pocketts/release.keystore}"
KEYSTORE="${KEYSTORE/#\~/$HOME}"
EXTRA=()  # more docker run options for one command
DEV_NAME=pocketts-dev
DEV_PORT="${DEV_PORT:-8686}"
CPUS="${CPUS:-4}"   # keep builds from taking every core

gradle() {
  # The build output goes to a Docker volume (fast), not next to the sources on the Windows drive.
  # Host networking, so the tests reach a test server published on this machine.
  docker run --rm -t --network host --cpus "$CPUS" --memory 6g "${EXTRA[@]}" \
    -v "$ROOT":/work -v pocketts-gradle:/gradle -v pocketts-out:/out \
    -e ANDROID_USER_HOME=/gradle/android-user \
    -e POCKETTS_BUILD_ROOT=/out -e TS_TEST_SERVER="${TS_TEST_SERVER_FOR_TESTS:-}" -e TS_TEST_FT_PORT="${TS_TEST_FT_PORT:-}" \
    "$IMAGE" ./gradlew --no-daemon --console=plain "$@"
}

need() { [ -n "${!1:-}" ] || { echo "Set $1 in dev.env"; exit 1; }; }

case "${1:-}" in
  image)
    docker build -f "$ROOT/docker/build.Dockerfile" -t "$IMAGE" \
      --build-arg ACCEPT_ANDROID_SDK_LICENSE="${ACCEPT_ANDROID_SDK_LICENSE:-no}" "$ROOT/docker"
    ;;
  test)
    gradle :tsproto:test
    ;;
  screenshots)
    gradle :app:recordRoborazziDebug
    ;;
  apk)
    gradle :app:assembleDebug
    mkdir -p "$ROOT/dist"
    docker run --rm -v pocketts-out:/out -v "$ROOT/dist":/dist "$IMAGE" \
      bash -c 'cp /out/app/outputs/apk/debug/*.apk /dist/ && ls -l /dist'
    ;;
  apk-shrunk)
    # The release's shrinking, with the test key and the test log: for finding what shrinking broke
    gradle :app:assembleShrunk
    mkdir -p "$ROOT/dist"
    docker run --rm -v pocketts-out:/out -v "$ROOT/dist":/dist "$IMAGE" \
      bash -c 'cp /out/app/outputs/apk/shrunk/app-shrunk.apk /dist/PocketTS-shrunk-$(date +%Y%m%d-%H%M).apk && ls /dist | tail -1'
    ;;
  ts-server)
    need TS_CONTAINER
    case "${2:-}" in
      up) docker start "$TS_CONTAINER" >/dev/null && echo "$TS_CONTAINER started" ;;
      down) docker stop "$TS_CONTAINER" >/dev/null && echo "$TS_CONTAINER stopped" ;;
      *) echo "ts-server up|down"; exit 1 ;;
    esac
    ;;
  it)
    need TS_TEST_SERVER
    TS_TEST_SERVER_FOR_TESTS="$TS_TEST_SERVER" gradle :tsproto:test --tests '*ServerTest*' --rerun
    ;;
  devserver)
    case "${2:-}" in
      up)
        mkdir -p "$ROOT/dist"
        docker rm -f "$DEV_NAME" >/dev/null 2>&1 || true
        docker run -d --name "$DEV_NAME" --cpus 1 -p "$DEV_PORT":8686 \
          -v "$ROOT/tools/devserver":/app:ro -v "$ROOT/dist":/dist \
          node:22-slim node /app/server.mjs >/dev/null
        echo "Test builds and phone logs: http://<this machine's address>:$DEV_PORT"
        ;;
      down) docker rm -f "$DEV_NAME" >/dev/null 2>&1 || true ;;
      *) echo "devserver up|down"; exit 1 ;;
    esac
    ;;
  gradle)
    shift
    gradle "$@"
    ;;
  keystore)
    # The release key: every update must be signed with it, so losing it means users can't update. Back it up.
    if [ -e "$KEYSTORE" ]; then echo "$KEYSTORE already exists: not made again."; exit 1; fi
    mkdir -p "$(dirname "$KEYSTORE")"
    chmod 700 "$(dirname "$KEYSTORE")"
    docker run --rm -it -v "$(dirname "$KEYSTORE")":/keys "$IMAGE" \
      keytool -genkeypair -keystore "/keys/$(basename "$KEYSTORE")" -storetype PKCS12 -alias pocketts \
      -keyalg RSA -keysize 4096 -validity 10000 -dname "CN=PocketTS"
    chmod 600 "$KEYSTORE"
    echo "Made $KEYSTORE. Back it up somewhere safe (with its password): updates need it."
    ;;
  release)
    [ -f "$KEYSTORE" ] || { echo "No release key at $KEYSTORE: make one with scripts/dev.sh keystore"; exit 1; }
    read -r -s -p "Release key password: " KEYPASS; echo
    # The password goes to the build only, through the environment (not on the command line, not into files)
    export POCKETTS_KEYSTORE_PASSWORD="$KEYPASS"
    EXTRA=(-v "$(dirname "$KEYSTORE")":/keys:ro -e POCKETTS_KEYSTORE_FILE="/keys/$(basename "$KEYSTORE")" -e POCKETTS_KEYSTORE_PASSWORD)
    gradle :app:assembleRelease
    unset POCKETTS_KEYSTORE_PASSWORD KEYPASS
    VERSION=$(sed -n 's/.*versionName = "\(.*\)".*/\1/p' "$ROOT/app/build.gradle.kts" | head -1)
    mkdir -p "$ROOT/dist"
    docker run --rm -v pocketts-out:/out -v "$ROOT/dist":/dist "$IMAGE" bash -c \
      "cp /out/app/outputs/apk/release/app-release.apk /dist/PocketTS-$VERSION.apk && cd /dist && sha256sum PocketTS-$VERSION.apk"
    echo "dist/PocketTS-$VERSION.apk is ready to attach to the GitHub release."
    ;;
  *)
    sed -n '2,21p' "$0"
    exit 1
    ;;
esac
