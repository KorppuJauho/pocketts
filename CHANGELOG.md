# Changelog

## Unreleased

- Server names work as in TeamSpeak apps: the server's DNS entry first, then TeamSpeak's name service (TSDNS), then
  the name itself on port 9987.
- WebTS's look: its colours, a slim header, control buttons (microphone and sound on/off), the server box, channels as boxes
  with the people inside, a "YOU" badge, a glow for whoever is talking, mute, "can't talk here" and AFK marks, and
  channel spacers drawn as TeamSpeak draws them.
- Chat as in WebTS, under a Channels / Chat switch:
  - tabs for the channel and private conversations, with unread marks (no server chat: the TeamSpeak 6 app has none)
  - times and names, links you can tap, and **bold** and other styles (TeamSpeak's tags and WebTS's marks)
  - join and leave lines worded like the TeamSpeak app's
- Tap a person for WebTS's person menu: their volume for you (kept per person), poke, and private chat.
- Pokes show as cards until dismissed. When the app isn't on screen, pokes and private messages come as
  notifications.
- Talking:
  - the microphone, encoded as Opus with Android's own encoder, in the channel's codec
  - voice activation (with WebTS's level and delay), push-to-talk, or continuous transmission
  - push-to-talk: WebTS's "Hold to talk" button and floating button, and optionally the volume buttons
  - optionally the round talk button stays on screen over other apps (hold to talk, drag to move)
  - it keeps working with the screen off (the connection notification now also covers the microphone)
- WebTS's Settings (General, Playback, Capture), behind the gear in the top right corner (also before connecting):
  - nickname
  - voice volume and a test sound
  - microphone volume, activation, and a level meter with the activation threshold
  - noise removal, echo cancellation and automatic gain
  - With echo cancellation on, voices play like a phone call (loudspeaker or headset).
- Pictures, through TeamSpeak's file transfer (the server's files):
  - avatars in the channel tree, the chat and the person menu (a myTeamSpeak picture when there's no avatar)
  - the server's icons for channels and the server, group badges, and TeamSpeak's built-in shields
  - channel banners, and the server's banner in its info
  - pictures in the chat (the TeamSpeak app's format), shown full screen with zoom
  - links to pictures shown as pictures, other links as an "Open" card
- Sending pictures to the channel chat: choose one or take a photo, see it, then send. Photos are turned the right
  way up and saved again, which leaves out what the camera wrote in them (such as where the photo was taken).
- Your own profile picture (Settings > General), fitted inside 320 × 320, or removed.
- Favourites as in WebTS: name, address, the nickname used there, the password (kept on this phone only), and one
  that connects automatically when the app opens (set in its edit window). The list shows a favourite's name when it has one.
  The star menu on the server screen switches servers or adds the one you're on.
- A lost connection (Wi-Fi to mobile data, a dead spot) reconnects by itself, keeping the chat; sooner when a
  network comes back.
- Away, with a message (tap yourself).
- Warnings (such as a refused password) show on a background of their own, can be closed, and go away by themselves.
- A favourite can have its own file transfer port (its Advanced part), for servers where it isn't the usual one.
- The TeamSpeak identity can be exported (shared) and imported from another TeamSpeak app (Settings > General):
  pasted, or as the .ini file a TeamSpeak app saves (chosen with Android's file picker).
- The identity and the passwords the app keeps (the remembered server password, favourites' passwords and the relay
  password) are encrypted with a key that stays in the phone's Android Keystore. Ones saved by an earlier version are
  encrypted the first time they're read, so nothing is lost on update. If the saved identity can't be opened, the app
  says so and keeps it instead of quietly making a new one; Settings then offers Import or New identity.
- Moving to a new phone with Android's copy of app data brings the nickname, favourites and settings, but not the
  identity and passwords: they can't be opened on another phone. Export the identity first and import it on the new
  phone to stay the same user; otherwise the new phone makes a new identity.
- Chat history, as in WebTS: channel chats, private chats (by the other person's identity) and join and leave lines
  are kept on the phone per server and shown again when you connect to the same server, with the day on lines from
  before today ("21:36 - Mon", "21:36 - 6 Oct"). At most 90 days and 300 lines per conversation; older lines go.
  Pokes aren't kept. It's encrypted with the same Keystore key as the passwords, left out of Android's backups and
  moves to a new phone, and simply starts over if it can't be opened. Settings > General > Clear chat history removes
  it. Closing a private chat's tab keeps its lines: they're back when the tab opens again.
- Screen and camera streams (TeamSpeak 6's), as in WebTS:
  - WATCH someone streaming in your channel: in a box on the server screen with their sound (on/off, volume), a
    small window, and full screen turned the way the picture is (wide: sideways, tall: upright)
  - share your camera (front or back, 480p, 720p or 1080p, flip while sharing) or your screen; tap your small picture to
    make it bigger inside the share bar (WebTS's green bar with a live dot and round buttons)
  - the camera's picture stays upright for viewers however the phone is held, also with auto-rotate off (as in
    WebTS); the rotate button locks the current turn instead. Your preview stays the right way up to you, and the
    round buttons' icons turn with the phone, as in a camera app
  - both cameras at once on phones that can: the back camera big and the front camera small in a corner (Swap
    switches them)
  - a watched stream keeps playing in a small window over other apps when you leave the app (or with the player's
    small-window button); closing the window stops watching
  - the TeamSpeak app can watch and be watched; the stream mark shows in every channel
  - streams use TeamSpeak's own STUN servers, plus an optional relay (TURN) server in Settings > Advanced
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
