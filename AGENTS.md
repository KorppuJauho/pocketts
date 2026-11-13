# Instructions for coding agents

For AI coding agents working on PocketTS. People: [CONTRIBUTING.md](CONTRIBUTING.md) has the same rules in short.

## The project

PocketTS is an Android client for TeamSpeak servers, in Kotlin with Jetpack Compose (Android 10, API 29, and later).

- `tsproto/`: the TeamSpeak connection in plain Kotlin, without Android. Most of the logic and most tests are here.
- `app/`: the Android app. `Session.kt` holds the connection's state; voice is in `MicSender.kt`, `VoicePlayer.kt`
  and `AudioRoute.kt`; screens are in `ui/`.
- `scripts/dev.sh`: every build and test command. [DEVELOPING.md](DEVELOPING.md) explains them.

## Commands

```bash
ACCEPT_ANDROID_SDK_LICENSE=yes scripts/dev.sh image   # the build image, once
scripts/dev.sh test                                   # tsproto's tests
scripts/dev.sh gradle :app:testDebugUnitTest          # the app's tests
scripts/dev.sh apk                                    # a test build in dist/
scripts/dev.sh screenshots                            # pictures of the screens in docs/screenshots/
```

The image build accepts the Android SDK licence: ask the person before running it, don't accept it for them.

Without Docker: JDK 17 or later, Android SDK platform 37, NDK 29.0.14206865 and CMake 3.31.5, then
`./gradlew :tsproto:test :app:testDebugUnitTest`.

## Before writing code

- For any change people can see (screens, settings, texts), describe what will change and get agreement first.
- Stay inside the task. One change per pull request. Note other problems you find instead of fixing them in the
  same change.
- Don't remove or rewrite working behaviour you weren't asked to change, such as defaults or the update check. If
  it looks wrong, say so.
- Check whether an open issue or pull request already covers the work.

## Rules for the code

- Follow the style of the surrounding code. Comments say why, in plain English.
- Visible text only through string resources. A new string goes into every `values*/strings.xml` file (the build
  fails otherwise). Translate it, or copy the English text and say so in the pull request. Keep placeholders such
  as `%1$s`.
- Log with `AppLog.log`. Mark private values with `LogPrivacy.person`, `.server`, `.channel`, `.folder`, `.message`
  or `.text`. Never log chat text, passwords, keys or the identity.
- Passwords and keys are stored only through the existing encrypted storage (`putSecret` and `getSecret` in
  `Secrets.kt`).
- No new network connections, analytics, tracking or libraries without asking. [PRIVACY.md](PRIVACY.md) lists every
  outside connection, and must stay true.
- Sound depends on the phone maker. Don't claim a sound fix works on phones it wasn't tried on, and say which phones
  it was tried on.
- Add or update tests: protocol tests in `tsproto/src/test`, app tests (Robolectric and Compose) in `app/src/test`.
  Run both test commands before calling the work done, and report failures as they are.
- Tests and examples use made-up data (the nickname "Jamie", `example.com`), never real people, servers or
  addresses.
- Don't commit `dist/`, `dev.env`, keystores, or regenerated pictures in `docs/screenshots/`.

## Pull requests

- Against `main`. The title starts with `fix:`, `feat:`, `docs:`, `perf:`, `build:` or `chore:`, followed by a
  short description in lowercase.
- The description says what changed, why and how it was tested (phone and Android version). Plain and short.
- Changes on screen need screenshots of the changed parts in the description, before and after. `scripts/dev.sh
  screenshots` makes pictures of the screens with example data; attach the ones that changed, but don't commit the
  regenerated files.
- When the pull request changes, update its description to match.
