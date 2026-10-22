# Changelog

## Unreleased

- **Admin functions:** with Settings › UI › Show admin functions on (off by default), a person's menu also offers
  Move (to a channel from the server's tree; admins need no channel password), Kick (from the channel or from the
  server, with a reason they see) and Ban (10 minutes to permanently, with a reason). A ban works as in the TeamSpeak
  app: it covers the person's identity and IP address, and removes them at once. The server decides who may: without
  the permission the app says "You don't have permission to …" instead of the server's raw error.
- A new **UI** tab in Settings, before Advanced.
- **Security level:** a server that needs a higher identity security level than yours no longer just refuses. PocketTS
  says which level it needs, offers to raise yours, shows the progress with a Stop button, and connects again when it
  gets there. Settings › Advanced shows your level, and Raise level raises it to a level you choose, with an estimate
  of how long it takes on your phone. Raising keeps the same identity (groups and permissions stay), uses all but one
  of the phone's cores, and Stop keeps the level reached so far.
- All of the app's text is in string resources (`res/values/strings.xml`), with placeholders and plural forms, ready
  for translations. How a connection ended (kicked, banned, no answer…) is now said by the app, so it can be
  translated too.

## 1.1.0 (2026-10-22)

- **Several pictures at once:** choose up to 10 from the gallery. They show in a row above the message box, each can
  be taken off, and a photo from the camera joins the row too. They're sent one message each in order, with the
  words on the first, and the chat shows which one is being sent.
- **Save and share pictures:** a picture opened on the whole screen has Save (into the gallery, under
  Pictures/PocketTS) and Share (Android's share menu) next to the close button.
- **Smoother chat with pictures:**
  - A picture's place is kept for it while it loads (from its size the time before), so the chat no longer jumps.
  - The chat shows pictures only as big as it draws them, and tapping one opens the whole picture, sharp when zoomed
    in.
  - Chat lines are no longer drawn again each time someone starts or stops talking.
  - The same picture is downloaded only once at a time, and big pictures are read from the phone's copy as they're
    drawn instead of first all into memory.
- The chat opens at its newest line straight away, instead of scrolling down past the older ones.
- A wrong server address or port now says "Couldn't connect: no answer from the server. Check the address and port."
  instead of "Connection lost", and a name that doesn't exist says that no server was found at it.
- Releases are built by GitHub Actions from the tagged commit, with a build attestation: anyone can check that a
  release APK came from this repository (see SECURITY.md). Security problems can be reported privately.
- Test builds log how smoothly the screen draws (frames, late frames, the slowest) every 10 seconds.

## 1.0.0 (2026-10-21)

The first release.

- Release builds: signed with the release key and shrunk (about 23 MB instead of 86). Making the key and the release
  are `scripts/dev.sh keystore` and `scripts/dev.sh release` (see DEVELOPING.md).
- The GitHub version checks for updates once a day and shows a card on the connect screen when a newer one is out
  (Download opens its release page, Not now hides it until a later one). Off in Settings > General > Check for
  updates. Test builds don't check.
- Reworked sounds for switching channel, being moved, people joining and leaving, messages in and out, and streams
  starting and stopping: low, short and soft, in the style of the sound buttons' (no sliding pitch, which stuttered).
- Settings > General > "Keep the screen on": the screen stays on while connected and the app is open. While you share
  or watch a stream, it stays on anyway.
- A ring light when sharing the front camera: the share bar's sun button turns the screen to full brightness with a
  white frame around it, lighting your face.
- A camera stream without "Stream sound" sends no sound at all: the microphone went out on its sound line anyway.
- A lost connection is noticed within seconds: at once when the phone's network goes or changes (Wi-Fi off,
  airplane mode), else after 10 seconds without a word from the server (it was 30, and took about a minute).
- Reconnecting while the server still has the old connection (it allows your identity on only once, and drops a
  dead connection only after a while) tries again every 3 seconds, for up to two minutes, and says why it's waiting.
- The big share preview is drawn by a renderer of its own, fitted while drawing: it was still cut and stretched.
  The front camera's preview also stays upright when the phone turns.
- Camera streams can carry sound: tick "Stream sound" when sharing the camera (remembered). Viewers hear your
  microphone with the picture (echo removed, surroundings kept). It goes quiet while your microphone is muted and
  while you talk in the channel, so you're not heard twice; the share bar's microphone mark shows which. Without it,
  the stream has no sound and you talk in the channel as usual. The stream opens the microphone the same way the
  voice does (call mode with echo cancellation, else the ordinary microphone), so both hear you, from as far away.
- The big share preview isn't stretched any more (the picture kept the small preview's shape).
- Settings > Advanced > Identity: Wipe (with a warning first), Export and Import. Buttons lost their "…".
- Pictures (profile pictures and ones sent to the chat) are saved in plain sRGB colours, without a phone photo's
  embedded colour profile; with it, the TeamSpeak 6 app didn't show the profile picture.
- Sharing the front camera: your preview shows exactly what viewers see (it was mirrored like a selfie camera).
- A profile picture too big for the server (its max avatar file size, 200 KB as it comes) is sent again smaller,
  down to 96 × 96, before giving up with "This server allows only a very small profile picture, or none."
- A file or picture sent from the TeamSpeak app through myTeamSpeak shows as one short line ("TeamSpeak images
  aren't supported in PocketTS") instead of its raw note, in the chat and in notifications.
- Pictures sent from the app go into the channel's /PocketTS folder (they went into /webTS). Pictures in any folder
  still show.
- Channel passwords can be remembered: tick "Remember the password" when joining, and next time the channel joins with
  one tap. They're kept encrypted on the phone, per server. A refused password (mistyped, or changed on the server)
  shows the usual warning, is forgotten, and the password is asked again. Settings > Advanced > Channel passwords
  forgets them all.
- A password-protected channel is joined without asking when you're allowed to skip channel passwords (such as a
  server admin), as in the TeamSpeak app: the app tries without one first, and asks only if the server refuses.
- Channel banners fill their row: as tall as it, in their own proportions (never stretched), with what doesn't fit cut
  off on the right, which fades out.
- Settings fill nearly the whole screen and sit closer together (smaller checkboxes, sliders and gaps), so there's
  less scrolling.
- Typing in the chat: while the keyboard is open, the control buttons and the Channels / Chat switch step aside (and an
  attached picture gets smaller), so the message box stays in view.
- Spacer channels can be joined by tapping them when Settings > General > "Allow joining spacer channels" is on
  (some servers use one as their AFK channel). It's off at first, so a stray tap on a divider doesn't move you.
- The identity's import and export moved to Settings > Advanced.
- A picture for the channel chat shows as attached above the message box (with a button to remove it) and goes with
  the usual Send button, with any words typed under it, in one message (as the TeamSpeak app sends them). Taking a photo asks for the camera first
  instead of closing the app.
- The headset's button can be push-to-talk (Settings > General): one press starts talking, the next stops, also with
  the screen off.
- Commands to the server are spaced out a little, so busy servers don't refuse them as flooding. When one still is
  refused, it's sent again after the wait the server asks for.
- The server name has an icon like the channels: the server's own, or a plain server icon. Server info shows the
  server's banner also when it's a picture in the server's files.
- A new app icon: a headset peeking out of a blue pocket, with a one-colour version for Android's themed icons and a
  matching notification icon. The header shows it too.
- Exporting the identity warns first that the file is you on every server, then saves it as a file you choose (in
  the .ini format TeamSpeak apps open) instead of sharing it to an app. Import hides the identity while it's pasted.
- Sounds for events, made for the app (low, soft tones in the style of chat apps, not TeamSpeak's own): connecting and disconnecting, switching channel,
  people coming and going in your channel, messages, pokes, the microphone and sound buttons, and streams starting
  and stopping. On/off and volume in Settings > Playback.
- Popups and the person menu have a new rounded look, with an icon, pill buttons and softer fields.
- The app is now called **PocketTS** (it was MobileTS6), with a new app ID, `io.github.korppujauho.pocketts`. Android
  sees it as a new app: remove the old test build, and import your identity again if you exported it.
- Server names work as in TeamSpeak apps: the server's DNS entry first, then TeamSpeak's name service (TSDNS), then
  the name itself on port 9987.
- A dark look: a slim header, control buttons (microphone and sound on/off), the server box, channels as boxes
  with the people inside, a "YOU" badge, a glow for whoever is talking, mute, "can't talk here" and AFK marks, and
  channel spacers drawn as TeamSpeak draws them.
- Chat, under a Channels / Chat switch:
  - tabs for the channel and private conversations, with unread marks (no server chat: the TeamSpeak 6 app has none)
  - times and names, links you can tap, and **bold** and other styles (TeamSpeak's tags and Markdown-style marks)
  - join and leave lines worded like the TeamSpeak app's
- Tap a person for their menu: their volume for you (kept per person), poke, and private chat.
- Pokes show as cards until dismissed. When the app isn't on screen, pokes and private messages come as
  notifications.
- Talking:
  - the microphone, encoded as Opus with Android's own encoder, in the channel's codec
  - voice activation (with a level and a delay), push-to-talk, or continuous transmission
  - push-to-talk: a "Hold to talk" button and a floating button, and optionally the volume buttons
  - optionally the round talk button stays on screen over other apps (hold to talk, drag to move)
  - it keeps working with the screen off (the connection notification now also covers the microphone)
- Settings (General, Playback, Capture), behind the gear in the top right corner (also before connecting):
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
- Favourites: name, address, the nickname used there, the password (kept on this phone only), and one
  that connects automatically when the app opens (set in its edit window). The list shows a favourite's name when it has one.
  The star menu on the server screen switches servers or adds the one you're on.
- A lost connection (Wi-Fi to mobile data, a dead spot) reconnects by itself, keeping the chat; sooner when a
  network comes back.
- Away, with a message (tap yourself).
- Warnings (such as a refused password) show on a background of their own, can be closed, and go away by themselves.
- A favourite can have its own file transfer port (its Advanced part), for servers where it isn't the usual one.
- The TeamSpeak identity can be exported (shared) and imported from another TeamSpeak app (Settings > Advanced):
  pasted, or as the .ini file a TeamSpeak app saves (chosen with Android's file picker).
- The identity and the passwords the app keeps (the remembered server password, favourites' passwords and the relay
  password) are encrypted with a key that stays in the phone's Android Keystore. Ones saved by an earlier version are
  encrypted the first time they're read, so nothing is lost on update. If the saved identity can't be opened, the app
  says so and keeps it instead of quietly making a new one; Settings then offers Import or New identity.
- Moving to a new phone with Android's copy of app data brings the nickname, favourites and settings, but not the
  identity and passwords: they can't be opened on another phone. Export the identity first and import it on the new
  phone to stay the same user; otherwise the new phone makes a new identity.
- Chat history: channel chats, private chats (by the other person's identity) and join and leave lines
  are kept on the phone per server and shown again when you connect to the same server, with the day on lines from
  before today ("21:36 - Mon", "21:36 - 6 Oct"). At most 90 days and 300 lines per conversation; older lines go.
  Pokes aren't kept. It's encrypted with the same Keystore key as the passwords, left out of Android's backups and
  moves to a new phone, and simply starts over if it can't be opened. Settings > General > Clear chat history removes
  it. Closing a private chat's tab keeps its lines: they're back when the tab opens again.
- Screen and camera streams (TeamSpeak 6's):
  - WATCH someone streaming in your channel: in a box on the server screen with their sound (on/off, volume), a
    small window, and full screen turned the way the picture is (wide: sideways, tall: upright)
  - share your camera (front or back, 480p, 720p or 1080p, flip while sharing) or your screen; tap your small picture to
    make it bigger inside the share bar (a green bar with a live dot and round buttons)
  - the camera's picture stays upright for viewers however the phone is held, also with auto-rotate off; the
    rotate button locks the current turn instead. Your preview stays the right way up to you, and the
    round buttons' icons turn with the phone, as in a camera app
  - both cameras at once on phones that can: the back camera big and the front camera small in a corner (Swap
    switches them)
  - a watched stream keeps playing in a small window over other apps when you leave the app (or with the player's
    small-window button); closing the window stops watching
  - the TeamSpeak app can watch and be watched; the stream mark shows in every channel
  - streams use TeamSpeak's own STUN servers, plus an optional relay (TURN) server in Settings > Advanced
- Tapping the server name shows the server's info: welcome message, users, channels, age, website and version.
- Screenshots of the screens are made with example data (`scripts/dev.sh screenshots`).

- Connects to a TeamSpeak server with a nickname, an address and an optional server password. The
  TeamSpeak identity is created on first use and kept, in the format TeamSpeak apps export.
- Shows the channel tree and who is in each channel, with talking, away and muted states. Tap a channel to join it;
  password-protected channels ask for the password.
- Plays voice (Opus), also with the screen off or another app open. A notification shows the connection and has a
  Disconnect button.
- Builds and tests in Docker. The tests check the connection code against known-good results and against a
  real TeamSpeak server.
