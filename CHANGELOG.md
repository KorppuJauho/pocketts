# Changelog

## Unreleased

- Connects straight to a TeamSpeak server with a nickname, an address and an optional server password. The
  TeamSpeak identity is created on first use and kept, in the format TeamSpeak apps export.
- Shows the channel tree and who is in each channel, with talking, away and muted states. Tap a channel to join it;
  password-protected channels ask for the password.
- Plays voice (Opus), also with the screen off or another app open. A notification shows the connection and has a
  Disconnect button.
- Builds and tests in Docker. The tests check the connection code against WebTS's results and against a real
  TeamSpeak server.
