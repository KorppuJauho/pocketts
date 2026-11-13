# Contributing to PocketTS

Thank you for helping. PocketTS is a small project, and these rules keep reviews quick and changes easy to follow.

## Before you start

- **A bug:** open an [issue](https://github.com/KorppuJauho/pocketts/issues/new/choose) with the bug report
  template. The phone, the Android version and the PocketTS version matter most.
- **A small fix** (a typo, a translation, an obvious bug): a pull request straight away is fine.
- **Anything bigger, and every change people can see** (screens, settings, texts): open an issue first and describe
  what would change. Agree on it before building it, which saves work on both sides.
- **A security problem:** not in a public issue. See [SECURITY.md](SECURITY.md).

## Pull requests

- **One change per pull request.** Fixes to different things go in separate pull requests, even small ones.
- Against `main`, from a branch in your fork.
- The title starts with the kind of change: `fix:`, `feat:`, `docs:`, `perf:`, `build:` or `chore:`.
  For example: `fix: the earpiece setting is ignored while connected`.
- The description says what changed, why, and how it was tested: which phone, which Android version, what you did.
- **Changes on screen need screenshots** of the changed parts in the description, before and after, so they can be
  checked at a glance. A short video for anything that moves.
- When the pull request changes, its description is updated to match.

## Building and testing

[DEVELOPING.md](DEVELOPING.md) explains building. Everything builds in Docker, so Java and the Android SDK aren't
needed. Both test runs have to pass:

```bash
scripts/dev.sh test                            # the TeamSpeak connection (tsproto)
scripts/dev.sh gradle :app:testDebugUnitTest   # the app
```

Try the change on a real phone as well. [docs/TESTING.md](docs/TESTING.md) lists what to try by hand. Sound
especially behaves differently from one phone maker to another.

## Rules for the code

- **Privacy comes first.** PocketTS has no analytics, no tracking and no ads, and it connects only to what
  [PRIVACY.md](PRIVACY.md) lists. A change that needs a new connection is discussed in an issue first, and updates
  PRIVACY.md.
- **The log.** Names, server addresses, channel names and the like are marked in log lines with `LogPrivacy`
  (`LogPrivacy.person(name)`, `LogPrivacy.server(address)`, …), so they can be taken out when a log is shared. Chat
  text, passwords and keys never go into the log.
- **New libraries:** ask first. Each one adds size and something more to trust.
- **Text on screen** comes from `app/src/main/res/values/strings.xml`, never written into the code. A new text needs
  the same name in every language file, or the build fails. Translate it if you can; otherwise copy the English text
  and say so in the pull request. [TRANSLATIONS.md](TRANSLATIONS.md) has the details.
- **Comments** say why, in plain English.
- **Tests** for new behaviour wherever it can be tested without a phone (most of the TeamSpeak protocol can).
- The picture files in `docs/screenshots/` (for the README and Google Play) are made again for each release, so
  pull requests leave them unchanged. The screenshots for review go in the pull request's description.

## AI tools

Using them is fine; PocketTS itself is written with AI help. The person who opens the pull request is responsible
for it: it does one thing, it has been tried on a phone, and they can explain what it changes and why. Coding
agents find their instructions in [AGENTS.md](AGENTS.md).

## Credits and licence

Contributors are thanked in [CREDITS.md](CREDITS.md). PocketTS is published under the
[GNU General Public License v3.0](LICENSE), and contributions are published under the same licence.
