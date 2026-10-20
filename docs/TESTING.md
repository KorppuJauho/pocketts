# Testing MobileTS6 by hand

What the automatic tests can't cover: real voice, pictures, video and how it behaves on a phone. Tick each item
and note anything odd: what you did, what happened, roughly when. Test builds send their log to the development
machine, so the time is enough for the log to be found.

**Where each test happens:**
- **Phone**: MobileTS6 on the Android phone.
- **PC**: the TeamSpeak app on a computer, on the same server.
- **2nd phone**: a second phone with MobileTS6, or WebTS in a browser (optional).

Ticked items have been tested on the phone (Samsung Galaxy A56, Android 16) with the test builds of 20 October 2026.

Use the same server for all of them. A test server is fine, except that some things (pictures, streams) need
permissions guests may not have.

## 1. Install and connect (Phone)
- [x] The test build installs. Remove an older test build first if Android says the package conflicts.
- [x] Connect with a nickname and the server's **name** (no port): it connects.
- [x] Connect with **address:port**.
- [ ] A wrong server password shows the server's refusal, and the app stays on the connect screen.
- [x] Your name in the channel tree has the **YOU** badge, and your channel is outlined in blue.

## 2. Channels (Phone + PC)
- [x] Phone: tap another channel to move there. PC: you see the phone move.
- [ ] PC: move into the phone's channel. Phone: the chat shows "… joined coming from …".
- [ ] A password-protected channel asks for the password; a wrong one is refused.
- [ ] Spacers (if the server has any) look like they do in the TeamSpeak app.
- [ ] Channel banners (if the server has any) sit like in the TeamSpeak app: from the left, centred up and down.
- [ ] Icons and group badges match what the TeamSpeak app shows.

## 3. Hearing (Phone + PC)
- [ ] PC talks: the phone plays it, and the speaker's name glows blue with a green ring.
- [ ] Phone sound button off: nothing plays, and the PC shows the phone's sound as off.
- [ ] Tap the PC user on the phone and change "Volume for you": it gets quieter or louder.
- [ ] Settings › Playback › voice volume, and "Play test sound".
- [ ] **Screen off for 5 minutes** while the PC talks now and then: the phone keeps playing.
- [ ] Another app open (for example a browser) for 5 minutes: still playing.

## 4. Talking (Phone + PC)
- [ ] First connect: the phone asks for the microphone. Allow it.
- [ ] **Voice activation**: talk on the phone, and the PC hears it. The phone shows you glowing while you talk.
- [ ] Settings › Capture: the level bar moves when you talk. Move the activation level and check it cuts out background noise.
- [ ] **Push-to-talk**: hold the round floating button, and the PC hears it only while you hold.
- [ ] Push-to-talk with the **"Hold to talk" button** in the control bar.
- [ ] Push-to-talk with the **volume buttons** (Settings › General).
- [ ] **Talk button over other apps** (Settings › General, push-to-talk mode): Android asks to allow it. Go to another
      app: the round button stays on screen. Hold it, and the PC hears you. Drag it somewhere else: it stays there.
      Back in the app, it goes away.
- [ ] **Continuous transmission** works.
- [ ] Mic button off: the PC hears nothing and shows the phone's microphone as off.
- [ ] **Echo cancellation on**, loudspeaker: the PC doesn't hear its own voice back.
- [ ] Echo cancellation **off**, with headphones: sound quality is good.
- [ ] Wired or Bluetooth headset: voice goes to and from the headset.
- [ ] **Screen off** while talking with voice activation: the PC still hears you.

## 5. Chat (Phone + PC)
- [ ] Channel message from the phone: the PC sees it, and the other way round.
- [ ] Message with a link: the link can be tapped. A link to a picture shows the picture.
- [ ] **Bold** text (`[b]` from the PC, or `**bold**`) shows bold.
- [ ] Private message: on the phone, tap the PC user › Chat. The PC gets it, and its reply arrives in the phone's @tab.
- [x] Unread red dot on a tab, and on "Chat", when a message arrives in a tab you're not looking at.
- [ ] Read it (open the tab): the dots go away and stay away. People joining or leaving don't make a dot.
- [ ] **Poke**: the PC pokes the phone and a card appears. Poke the PC from the phone's person menu.
- [ ] With the app in the background, a private message and a poke come as **notifications**.
- [ ] Tap the server name: server info (welcome message, users, channels, website).

## 6. Pictures (Phone + PC)
- [ ] Avatars from the PC users show in the tree, the chat and the person menu.
- [ ] Settings › General › Profile picture › Change: the PC shows the new avatar. Remove: it's gone.
- [ ] PC sends a picture in channel chat: the phone shows it. Tap it for full screen and pinch to zoom.
- [ ] Phone sends a picture (Choose a picture) and the PC sees it.
- [ ] Phone sends a photo (Take a photo): it's the right way up on the PC.
- [ ] A GIF from the phone moves on the PC.

## 7. Streams (Phone + PC)
- [x] PC shares its screen in the phone's channel: the phone shows the stream mark and **WATCH**.
- [ ] WATCH: the PC's stream shows in a blue box on the server screen (like your own large preview), with its sound.
- [ ] The sound button: on/off and the volume slider work.
- [ ] Full screen: a wide stream turns the app sideways (even with auto-rotate off), a tall one stays upright. Tap
      for the buttons; leave full screen and the app turns back.
- [ ] While watching, go to the home screen: the stream keeps playing in a **small window** over other apps. Move it,
      tap it to open the app again. Close it with its ×: watching stops (the PC sees one viewer fewer).
- [ ] The small-window button in the player's top bar does the same.
- [ ] The PC stops sharing: the phone's player says it ended and closes.
- [x] Phone shares its **camera** (Share › Camera): the PC can watch it.
- [ ] While sharing the camera, **Flip** switches between the front and back camera.
- [x] Phone's auto-rotate **off**, sharing the camera: hold the phone sideways. The PC's picture turns upright after
      a moment.
- [ ] Tap the rotate button (it becomes a yellow lock): turning the phone no longer turns the picture. The lock is
      remembered for the next share.
- [ ] **Both cameras** (Share › Camera › Both, only shown if the phone can do it): the PC sees the back camera with
      the front camera small in the corner. **Swap** switches them. Turning the phone keeps both upright.
- [ ] HD and data saver both work.
- [x] The share bar looks like WebTS's. Tap your small picture: it grows inside the bar (at most half the screen). Tap again: small.
- [ ] Small picture: "Camera", the eye and the number of viewers sit on one line, with the buttons below.
- [ ] Auto-rotate **off**, sharing the camera, phone held sideways: your preview is the right way up to you, and the
      round buttons' icons turn to stay upright (the buttons stay where they are).
- [x] Phone shares its **screen** (Share › Screen, then allow): the PC can watch it.
- [ ] Phone screen off while sharing the camera: the PC keeps seeing it.
- [ ] On mobile data instead of Wi-Fi. If it doesn't connect, a relay (TURN) server may be needed (Settings › Advanced).
- [ ] Optional, 2nd phone: phone ↔ phone streams.

## 8. Favourites and settings (Phone)
- [x] When connecting, tick "Save to favourites": it shows in the Favourites list.
- [x] The edit button (pencil) on a favourite: name, nickname there, password, connect automatically.
- [x] Connect automatically (in the edit window): close the app (swipe it away), open it, and it connects by itself.
- [ ] In the edit window, Delete is on the left of Cancel and Save, and removes the favourite.
- [ ] Edit window › Advanced › File transfer port: set the test server's port. Avatars and pictures load there.
- [ ] Server screen header: Disconnect, a gap, then the star and the gear on the right.
- [ ] On the server: ★ menu › switch to another favourite.
- [ ] Settings › Nickname: the PC sees the new name.
- [ ] Tap yourself › Set away with a message: the PC shows you away. Then I'm back.
- [ ] Settings › Identity › Export: share it, for example to notes. Import it again: no complaint.
- [ ] Settings › Identity › Import › Choose a file…: pick the .ini file the TeamSpeak app on the PC exported. It says
  "Imported"; after reconnecting, the PC sees you with that identity's unique id (the same as the PC's own).
- [ ] Install this build over an older one (don't uninstall first): the server still knows you (same unique id), and
  the remembered server password, the favourites' passwords and the relay password (Settings › Advanced) are all still there.

## 9. Staying connected (Phone)
- [ ] Turn Wi-Fi off while connected (mobile data on): "Connection lost. Reconnecting…", then it's back with the chat kept.
- [ ] Airplane mode for 30 seconds, then off: it reconnects.
- [ ] The PC kicks the phone from the server: the app shows "Kicked…" and **doesn't** reconnect.
- [ ] Disconnect from the notification's button.
- [ ] Swipe the app away from recent apps: it leaves the server.
- [ ] An hour connected in the background: battery use looks reasonable (Settings › Battery).
