# PocketTS

> **Made with AI assistance.** PocketTS is written with the help of an AI coding assistant. Every change is tested
> by hand on a real phone before it goes in.

A free, unofficial TeamSpeak client for Android, made for TeamSpeak 6 servers. Type a nickname and a server address
to join.

<img src="docs/screenshots/overview.png" alt="PocketTS screens: the channels and the people in them, the channel chat, a person's menu (volume, poke and private chat), and the settings">

## What it does

- **Voice:** voice activation, push-to-talk (also a floating button over other apps), echo and noise removal. Keeps
  working with the screen off.
- **Channels:** who is where, with avatars, icons, badges and banners. Password-protected channels ask for the
  password.
- **Chat:** channel and private chats, pictures, pokes, and a history kept on the phone for 90 days.
- **Streams:** watch others' screen and camera streams (also in a small window over other apps), and share your own
  camera (with your microphone) or screen (with what the phone plays).
- **Favourites**, settings, sounds for events, and reconnecting by itself when the network changes.

Your TeamSpeak identity and saved passwords are encrypted on the phone. No account, no ads, no tracking. The
GitHub version asks GitHub once a day whether there's a newer version (you can turn that off in Settings).

**Status:** early, being tested. See the [changelog](CHANGELOG.md), and the
[issues](https://github.com/KorppuJauho/pocketts/issues) for what's planned. Security problems: please report them
privately, see [SECURITY.md](SECURITY.md).

## Install

Download the APK from [Releases](https://github.com/KorppuJauho/pocketts/releases) and open it on your phone
(Android 10 or newer). Android asks once to allow installing apps from your browser.

## Known limitations

- **No myTeamSpeak features.** Pictures shared through myTeamSpeak by the TeamSpeak app can't be shown in PocketTS.
- **Pictures go to the server's own files.** PocketTS can't upload pictures to TeamSpeak's picture service. The
  pictures you send are saved in the channel's files on the server (file transfer), so you need permission to upload
  files in that channel. TeamSpeak app users see them as usual.

## Building it yourself

See [DEVELOPING.md](DEVELOPING.md).

## License

[GNU General Public License v3.0](LICENSE). You're welcome to fork PocketTS, change it and share your version, as
long as you share its source code under the same license.

The PocketTS name and icon are mine. If you release your own version as an app (in an app store or as your own
downloads), give it a different name and icon so nobody mistakes it for PocketTS.

PocketTS isn't affiliated with or endorsed by TeamSpeak Systems GmbH. TeamSpeak is their trademark.

PocketTS is free and stays free. If you'd like to say thanks, you can [buy me a coffee](https://buymeacoffee.com/jauho).
