# Sound packs

PocketTS plays its own soft tones for events. In their place it can play a **sound pack of your own**: the kind of
pack TeamSpeak uses, a folder of sound files. PocketTS comes with no sound pack and downloads none: you bring the
files, and they stay on your phone.

## Importing one

Settings › Playback › **Sound pack** › **Import**, then choose:

- **a folder**: the pack's own folder (the one with the sound files in it), or
- **a file**: a `.zip` of that folder, or a `.ts3_soundpack` file (a zip under another name).

Then switch **Tones | (the pack's name)** to the pack, and Save & Apply. Remove brings the tones back.

Both kinds of pack work:

- **TeamSpeak 5 and 6:** files named after events, such as `connected.wav` and `you_were_poked.wav`.
- **TeamSpeak 3:** a `settings.ini` that says which file plays for which event, such as
  `CONNECTION_CONNECTED = play("connected.wav")`. Where a file is named with `${clientType}` (a different sound for
  friends and blocked people), PocketTS plays the `neutral` one.

Sounds can be WAV, OGG, MP3, Opus, FLAC or M4A. A pack can be 20 MB at most, and one file 5 MB.

## Where to find a pack

- **A TeamSpeak 6 installation on a computer.** On Windows its pack is in
  `C:\Program Files\TeamSpeak\html\client_ui\sound\default`. Zip that `default` folder, copy the zip to your phone
  (by cable, a cloud drive or a chat with yourself), and import it.
- **A TeamSpeak 3 installation.** Its packs are in the `sound` folder where TeamSpeak 3 is installed, and packs you
  added are in `%APPDATA%\TS3Client\sound` on Windows. Each pack is a folder of its own: zip the one you want.
- **Community packs** made for TeamSpeak 3 are shared as `.ts3_soundpack` files, for example through myTeamSpeak's
  add-ons. Import the file as it is.

Use packs you're allowed to use, and keep their authors' terms.

## Quieting an event

An event without a sound in the pack is quiet. To quiet one, such as pokes, take its file out of the pack (or, in a
TeamSpeak 3 pack, its line out of `settings.ini`) before importing.

## Which file plays when

PocketTS looks for each event's names in this order. An event the pack has nothing for plays a broader event's sound
when there is one (someone banned from the server: kicked from the server, then disconnected), except where a
TeamSpeak 3 pack's `settings.ini` lists the event without a sound: then it's quiet on purpose.

| Event | TeamSpeak 5/6 file (any of) | TeamSpeak 3 key (any of) |
|---|---|---|
| You connected | `connected` | `CONNECTION_CONNECTED` |
| You disconnected | `disconnected` | `CONNECTION_DISCONNECTED` |
| Your connection was lost | `connection_lost` | `CONNECTION_LOST_CONNECTION` |
| You were kicked from the channel | `you_kicked_channel` | `YOU_WERE_KICKED_FROM_CHANNEL` |
| You were kicked from the server | `you_kicked_server` | `YOU_WERE_KICKED_FROM_SERVER` |
| You were banned | `you_were_banned` | `YOU_WERE_BANNED` |
| You switched channel | `channel_switched` | `YOU_SWITCHED_CHANNEL` |
| You were moved | `you_were_moved` | `YOU_WERE_MOVED_TO_DIFFERENT_CHANNEL` |
| Someone joined your channel | `neutral_switched_tocurrentchannel`, `neutral_moved_tocurrentchannel`, `client_joined` | `CLIENT_SWITCHED_TO_CURRENT_CHANNEL_STAYS`, `CLIENT_SWITCHED_TO_CURRENT_CHANNEL_APPEARS`, `CLIENT_MOVED_TO_CURRENT_CHANNEL_STAYS`, `CLIENT_MOVED_TO_CURRENT_CHANNEL_APPEARS` |
| Someone connected to your channel | `neutral_connection_connected_currentchannel` | `CLIENT_CONNECTION_CONNECTED_CURRENT_CHANNEL` |
| Someone left your channel | `neutral_switched_awayfromcurrentchannel`, `neutral_moved_awayfromcurrentchannel`, `client_left` | `CLIENT_SWITCHED_FROM_CURRENT_CHANNEL_STAYS`, `CLIENT_SWITCHED_FROM_CURRENT_CHANNEL_DISAPPEARS`, `CLIENT_MOVED_FROM_CURRENT_CHANNEL_STAYS`, `CLIENT_MOVED_FROM_CURRENT_CHANNEL_DISAPPEARS` |
| Someone in your channel disconnected | `neutral_connection_disconnected_currentchannel` | `CLIENT_CONNECTION_DISCONNECTED_CURRENT_CHANNEL` |
| Someone in your channel lost the connection | `neutral_connection_connectionlost_currentchannel` | `CLIENT_CONNECTION_LOST_CONNECTION_CURRENT_CHANNEL` |
| A message arrived | `chat_message_inbound`, `chat_inbound` | `CHAT_RECEIVED_MESSAGE_CLIENT`, `CHAT_RECEIVED_MESSAGE_CHANNEL` |
| You sent a message | `chat_message_outbound`, `chat_outbound` | `CHAT_SENT_MESSAGE_CLIENT`, `CHAT_SENT_MESSAGE_CHANNEL` |
| You were poked | `you_were_poked` | `OTHER_RECEIVED_POKE` |
| Microphone off | `mic_muted` | `SOUND_CAPTURE_MUTED` |
| Microphone on | `mic_activated` | `SOUND_CAPTURE_UNMUTED` |
| Sound off | `sound_muted` | `SOUND_PLAYBACK_MUTED` |
| Sound on | `sound_resumed` | `SOUND_PLAYBACK_UNMUTED` |
| A stream started | `stream_started` | – |
| A stream stopped | `stream_stopped` | – |
| Someone was kicked from your channel | `neutral_kicked_channel_awayfromcurrentchannel` | `CLIENT_WAS_KICKED_FROM_CHANNEL_FROM_CURRENT_CHANNEL_STAYS`, `CLIENT_WAS_KICKED_FROM_CHANNEL_FROM_CURRENT_CHANNEL_DISAPPEARS` |
| Someone in your channel was kicked from the server | `neutral_kicked_server_currentchannel` | `CLIENT_WAS_KICKED_FROM_SERVER_CURRENT_CHANNEL` |
| Someone in your channel was banned | `neutral_banned_server_currentchannel` | `CLIENT_WAS_BANNED_CURRENT_CHANNEL` |
| Someone whispers to you | `whisper_received` | `SPECIAL_WHISPER_NOTIFY` |
| You set Away | `away_activated` | `STATUS_SET_AWAY` |
| You're back from Away | `away_deactivated` | `STATUS_SET_PRESENT` |
| You were given talk power | `talkpower_granted` | `YOU_WERE_GRANTED_TALK_POWER` |
| Your talk power was taken | `talkpower_revoked` | `YOU_WERE_REVOKED_TALK_POWER` |
| You were put in a server group | `servergroup_assigned` | `YOU_SERVERGROUP_ADDED_BY_USER`, `YOU_SERVERGROUP_ADDED_BY_SERVER` |
| You were taken out of a server group | `servergroup_revoked` | `YOU_SERVERGROUP_REMOVED_BY_USER`, `YOU_SERVERGROUP_REMOVED_BY_SERVER` |
| Your channel group changed | `channelgroup_changed` | `YOU_CHANNELGROUP_CHANGED_BY_USER`, `YOU_CHANNELGROUP_CHANGED_BY_SERVER` |
| The server refused something for want of permission | `insufficient_permissions` | `SERVER_INSUFFICIENT_PERMISSIONS` |
| A channel was created | `channel_created` | `CHANNEL_CREATED_BY_OTHER`, `CHANNEL_CREATED_BY_YOU` |
| A channel was deleted | `channel_deleted` | `CHANNEL_DELETED_BY_OTHER`, `CHANNEL_DELETED_BY_YOU`, `CHANNEL_DELETED_BY_SERVER` |
| A channel was edited | `channel_edited` | `CHANNEL_EDITED_OTHER_BY_OTHER`, `CHANNEL_EDITED_OTHER_BY_YOU`, `CHANNEL_EDITED_OTHER_BY_SERVER` |
| Your channel was edited | `your_channel_was_edited` | `CHANNEL_EDITED_CURRENT_BY_OTHER`, `CHANNEL_EDITED_CURRENT_BY_YOU` |
| A channel was moved | `channel_moved` | `CHANNEL_MOVED_BY_OTHER`, `CHANNEL_MOVED_BY_YOU` |

With the tones, whispers, Away, talk power, your groups, refusals and channel changes are quiet: they have a sound
only in a sound pack.
