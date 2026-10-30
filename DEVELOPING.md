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
ACCEPT_ANDROID_SDK_LICENSE=yes scripts/dev.sh image   # the build image: Java, the Android SDK and NDK (once)
scripts/dev.sh test                                   # tests
scripts/dev.sh apk                                    # a test build in dist/
scripts/dev.sh screenshots                            # pictures of the screens (example data) in docs/screenshots/
scripts/dev.sh gradle <task>                          # any other Gradle task
```

The build image accepts the Android SDK license, which is why that variable has to be set.

Builds keep Gradle's downloads, the build output and Robolectric's Android jars (which the app's tests run on, about
150 MB) in Docker volumes: `pocketts-gradle`, `pocketts-out` and `pocketts-m2`. Later builds are then faster, and the
tests don't download those jars every time (Maven Central starts refusing repeated downloads).

`scripts/dev.sh screenshots` also makes the Google Play pictures (`docs/screenshots/play/`) and GitHub's social
preview (`docs/screenshots/social-preview.png`, the picture shown when the repository is shared or found in search).
When the social preview changes, upload it again in the repository's Settings › General › Social preview.

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

## Testing streams

`scripts/dev.sh viewer up` starts a test viewer on the test server (`TS_CONTAINER`): WebTS and a headless Chromium,
inside the server container's own network, so nothing is opened to the outside. It joins as "Stream viewer", goes
to the channel of whoever starts a stream and watches it, taking the video as H.264 like the TeamSpeak app
(`scripts/dev.sh viewer up webts` lets WebTS choose instead). `scripts/dev.sh viewer down` stops it.

The test page shows what it sees at `/viewer`: a log line a second (picture size, frame rate, data rate, losses,
freezes, sound, route) and a picture every few seconds (also in `dist/viewer/`). To measure the delay, open the test
page's `/clock` on the phone and share the screen: the viewer reads the clock from the stream and logs the delay from
the phone's screen to its own.

## Releasing

A release is a signed APK attached to a GitHub release, built by GitHub Actions from the tagged commit
(`.github/workflows/release.yml`). Test builds are a separate app (their ID ends in `.debug`), so both can be on one
phone.

### A release

1. In `app/build.gradle.kts`, raise `versionCode` by one and set `versionName` (for example `1.1.0`).
2. Move the changelog's "Unreleased" lines under a new heading `## <version> (<date>)`.
3. Merge that to `main`, then tag the merged commit and push the tag:
   ```bash
   git tag v1.1.0 origin/main
   git push origin v1.1.0
   ```
4. The workflow checks that the tag matches `versionName`, runs the tests, builds and signs the APK and the Google
   Play bundle, checks their signatures, records build attestations and makes a **draft** release with the APK, its
   SHA-256 and the changelog lines. The Play bundle stays with the run (Artifacts).
5. Download the APK from the draft, install it on a phone and try the basics: connect, talk, chat, a picture, a
   stream. If the release crashes where a test build doesn't, shrinking removed something: `scripts/dev.sh apk-shrunk`
   makes the same shrunk app with the test key, sending its log to the test page like a test build, so the error
   shows (a crash in native code leaves no line: then look for a class the native code needs, see
   `app/proguard-rules.pro`).
6. Publish the draft.
7. Google Play: the workflow uploads the bundle to the closed test as a draft (once set up); send it for review in
   Play Console. See [docs/play/README.md](docs/play/README.md).

Anyone can check that a release APK was built by this repository's workflow:

```bash
gh attestation verify PocketTS-1.1.0.apk --repo KorppuJauho/pocketts
```

The GitHub version checks `releases/latest` once a day, so a published release reaches users who have update checks
on. Drafts and pre-releases aren't offered.

### The release key

The workflow signs with the key in the `release` environment's secrets (Settings › Environments › release), which
only `v*` tags can use:

- `POCKETTS_KEYSTORE_BASE64`: the key file as base64
- `POCKETTS_KEYSTORE_PASSWORD`: its password (the key's password is the same)

The key was made once with `scripts/dev.sh keystore`, and the file is kept outside the repo.

**Back up the key file and its password somewhere safe.** Every update has to be signed with the same key: without
it, people can't update to a new version and would have to uninstall first, which loses their settings.

To put the key into the secrets (from Windows, with the key in WSL; it goes straight to GitHub, not through the
screen or the clipboard):

```bash
wsl -e bash -c "base64 -w0 ~/.pocketts/release.keystore" | gh secret set POCKETTS_KEYSTORE_BASE64 --env release --repo KorppuJauho/pocketts
gh secret set POCKETTS_KEYSTORE_PASSWORD --env release --repo KorppuJauho/pocketts
```

### Building a release on this machine

`scripts/dev.sh release` still builds the same signed APK locally (for example if GitHub Actions is unavailable).

### Making the release key (once)

```bash
scripts/dev.sh keystore
```

This asks for a password and makes the key at `~/.pocketts/release.keystore` (inside WSL on Windows; set
`POCKETTS_KEYSTORE` in `dev.env` to keep it somewhere else). It never goes into the repo.
