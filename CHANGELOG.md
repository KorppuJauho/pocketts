# Changelog

## Unreleased

- Server names work as in TeamSpeak apps: the server's DNS entry first, then TeamSpeak's name service (TSDNS), then
  the name itself on port 9987.
- WebTS's look: its colours, header, control buttons (microphone and sound on/off), the server box, channels as boxes
  with the people inside, a "YOU" badge, a glow for whoever is talking, mute, "can't talk here" and AFK marks, and
  channel spacers drawn as TeamSpeak draws them.
- Chat as in WebTS, under a Channels / Chat switch:
  - tabs for the channel, the server and private conversations, with unread marks
  - times and names, links you can tap, and **bold** and other styles (TeamSpeak's tags and WebTS's marks)
  - join and leave lines worded like the TeamSpeak app's
- Tap a person for WebTS's person menu: their volume for you (kept per person), poke, and private chat.
- Pokes show as cards until dismissed. When the app isn't on screen, pokes and private messages come as
  notifications.
- Talking:
  - the microphone, encoded as Opus with Android's own encoder, in the channel's codec
  - voice activation (with WebTS's level and delay), push-to-talk, or continuous transmission
  - push-to-talk: WebTS's "Hold to talk" button and floating button, and optionally the volume buttons
  - it keeps working with the screen off (the connection notification now also covers the microphone)
- WebTS's Settings (General, Playback, Capture):
  - nickname
  - voice volume and a test sound
  - microphone volume, activation, and a level meter with the activation threshold
  - noise removal, echo cancellation and automatic gain
  - With echo cancellation on, voices play like a phone call (loudspeaker or headset).
- Tapping the server name shows the server's info: welcome message, users, channels, age, website and version.
- Screenshots of the screens are made with example data (`scripts/dev.sh screenshots`).

- Connects straight to a TeamSpeak server with a nickname, an address and an optional server password. The
  TeamSpeak identity is created on first use and kept, in the format TeamSpeak apps export.
- Shows the channel tree and who is in each channel, with talking, away and muted states. Tap a channel to join it;
  password-protected channels ask for the password.
- Plays voice (Opus), also with the screen off or another app open. A notification shows the connection and has a
  Disconnect button.
- Builds and tests in Docker. The tests check the connection code against WebTS's results and against a real
  TeamSpeak server.
