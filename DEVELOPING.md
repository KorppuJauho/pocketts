# Developing PocketTS

## The parts

- `tsproto/`: the TeamSpeak connection in plain Kotlin (no Android): packets, encryption, commands, the channel and
  people model, chat, file transfer and streams. Most of the tests are here.
- `app/`: the Android app, in Jetpack Compose: screens, voice (Opus), streams (WebRTC), settings and favourites.
- `scripts/dev.sh`: every build, test and helper command.
- `docs/TESTING.md`: what to test by hand on a phone.

## Building

Everything builds in Docker, so the only things needed are Docker and a Bash shell (on Windows: Docker inside
WSL). There's no need to install Java or the Android SDK.

```bash
ACCEPT_ANDROID_SDK_LICENSE=yes scripts/dev.sh image   # the build image: Java and the Android SDK (once)
scripts/dev.sh test                                   # tests
scripts/dev.sh apk                                    # a test build in dist/
scripts/dev.sh screenshots                            # pictures of the screens (example data) in docs/screenshots/
scripts/dev.sh gradle <task>                          # any other Gradle task
```

The build image accepts the Android SDK license, which is why that variable has to be set.

`dev.env` holds this computer's own settings (addresses, container names) and is never committed.

## Testing on a phone

`scripts/dev.sh devserver up` starts a small page on port 8686. Open `http://<this computer's address>:8686` on the
phone, tap the newest build and install it. Android asks once to allow installing apps from the browser.
`scripts/dev.sh devserver down` stops it.

Test builds send their log to that page (`dist/phone.log`) when `DEV_LOG_URL` is set in `dev.env`. Release builds
never do.

## Tests against a real server

`scripts/dev.sh it` connects two clients to a TeamSpeak server and checks the following:

- they see each other and each other's channel moves
- one hears exactly what the other sends
- chat messages arrive
- both leave cleanly

Put the test server's address in `dev.env` as `TS_TEST_SERVER=host:port`. If the server runs in a container, add
`TS_CONTAINER=<name>` as well, so `scripts/dev.sh ts-server up` and `down` can start and stop it.

## Releasing

A release is a signed APK attached to a GitHub release. Test builds are a separate app (their ID ends in `.debug`),
so both can be on one phone.

### The release key (once)

```bash
scripts/dev.sh keystore
```

This asks for a password and makes the key at `~/.pocketts/release.keystore` (inside WSL on Windows; set
`POCKETTS_KEYSTORE` in `dev.env` to keep it somewhere else). It never goes into the repo.

**Back up the key file and its password somewhere safe.** Every update has to be signed with the same key: without
it, people can't update to a new version and would have to uninstall first, which loses their settings.

### A release

1. In `app/build.gradle.kts`, raise `versionCode` by one and set `versionName` (for example `0.2.0`).
2. Move the changelog's "Unreleased" lines under the new version.
3. Run `scripts/dev.sh release`. It asks for the key's password and makes `dist/PocketTS-<version>.apk`, with its
   SHA-256.
4. Install it on a phone and try the basics: connect, talk, chat, a picture, a stream. If the release crashes where a
   test build doesn't, shrinking removed something: `scripts/dev.sh apk-shrunk` makes the same shrunk app with the test
   key, sending its log to the test page like a test build, so the error shows (a crash in native code leaves no
   line: then look for a class the native code needs, see `app/proguard-rules.pro`).
5. On GitHub: Releases › Draft a new release, tag `v<version>` (for example `v0.2.0`). Paste the changelog lines and
   the SHA-256, attach the APK, and publish.

The GitHub version checks `releases/latest` once a day, so a published release reaches users who have update checks
on. Pre-releases aren't offered.

