package io.github.korppujauho.mobilets6

import androidx.compose.runtime.Composable
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.roborazziSystemPropertyOutputDirectory
import io.github.korppujauho.mobilets6.proto.Chat
import io.github.korppujauho.mobilets6.proto.Commands
import io.github.korppujauho.mobilets6.proto.ServerModel
import io.github.korppujauho.mobilets6.ui.ConnectScreen
import io.github.korppujauho.mobilets6.ui.MobileTsTheme
import io.github.korppujauho.mobilets6.ui.ServerActions
import io.github.korppujauho.mobilets6.ui.ServerScreen
import io.github.korppujauho.mobilets6.ui.View
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Pictures of the screens with example data, for checking the look without a phone
 * (scripts/dev.sh screenshots → docs/screenshots/). The size is a common phone's: 411 × 891 dp.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi", sdk = [36])
class ScreenshotTest {

    private fun shot(name: String, content: @Composable () -> Unit) =
        captureRoboImage("${roborazziSystemPropertyOutputDirectory()}/$name.png") { MobileTsTheme { content() } }

    @Test
    fun connect() = shot("connect") {
        ConnectScreen("Jamie", {}, "ts.example.com", {}, "", {}, null) {}
    }

    @Test
    fun server() = shot("server") { ServerScreen(Example.state(), Example.noActions) }

    @Test
    fun chat() = shot("chat") { ServerScreen(Example.chatState(), Example.noActions, startView = View.CHAT) }

    @Test
    fun poked() = shot("poke") {
        ServerScreen(Example.state().copy(pokes = listOf(Chat.Poke(1, Example.T0 + 300_000, 1, "Lee", "", "Come to Gaming!"))), Example.noActions)
    }

    @Test
    fun serverConnecting() = shot("server-connecting") {
        ServerScreen(Session.UiState(phase = Session.Phase.CONNECTING, address = "ts.example.com"), Example.noActions)
    }
}

/** An example server like the one in WebTS's screenshots. */
object Example {
    val noActions = object : ServerActions {
        override fun join(channel: ServerModel.Channel, password: String) {}
        override fun disconnect() {}
        override fun toggleMic() {}
        override fun toggleSound() {}
        override fun openSettings() {}
        override fun send(key: Chat.Key, text: String) {}
        override fun openPrivate(person: ServerModel.Client) {}
        override fun closePrivate(uid: String) {}
        override fun poke(person: ServerModel.Client, text: String) {}
        override fun dismissPoke(id: Long) {}
        override fun setVolume(person: ServerModel.Client, volume: Float) {}
        override fun viewing(key: Chat.Key?) {}
    }

    fun model(): ServerModel {
        val m = ServerModel()
        fun cmd(s: String) = m.apply(Commands.parse(s))
        cmd("initserver virtualserver_name=TeamSpeak\\s6\\sServer aclid=3")
        cmd(
            "channellist cid=1 cpid=0 channel_order=0 channel_name=Default\\sChannel channel_flag_default=1" +
                "|cid=9 cpid=0 channel_order=1 channel_name=[cspacer]Lobbies channel_flag_permanent=1" +
                "|cid=2 cpid=0 channel_order=9 channel_name=Gaming" +
                "|cid=6 cpid=2 channel_order=0 channel_name=Squad\\s1" +
                "|cid=7 cpid=2 channel_order=6 channel_name=Squad\\s2 channel_flag_password=1" +
                "|cid=10 cpid=0 channel_order=2 channel_name=[*spacer1]-=" +
                "|cid=3 cpid=0 channel_order=10 channel_name=Music channel_needed_talk_power=50" +
                "|cid=4 cpid=0 channel_order=3 channel_name=Study\\sroom" +
                "|cid=11 cpid=0 channel_order=4 channel_name=[spacer2]---" +
                "|cid=5 cpid=0 channel_order=11 channel_name=AFK channel_needed_talk_power=99",
        )
        fun person(id: Int, cid: Int, nick: String, extra: String = "") =
            cmd("notifycliententerview ctid=$cid clid=$id client_nickname=$nick client_type=0 client_talk_power=75 $extra")
        person(1, 1, "Lee")
        person(2, 1, "Noa", "client_input_muted=1")
        person(3, 1, "Jamie")
        person(4, 1, "Riley")
        person(5, 2, "Alex")
        person(8, 6, "Sam", "client_output_muted=1")
        person(9, 6, "Robin")
        person(10, 3, "Kai", "client_talk_power=0")
        person(11, 4, "Mia")
        person(12, 5, "Quinn", "client_away=1 client_talk_power=0")
        return m
    }

    const val T0 = 1_760_000_000_000L // a fixed time for the example lines

    fun chatState(): Session.UiState {
        val m = model()
        val chat = Chat()
        var t = T0
        chat.now = { t.also { t += 45_000 } }
        fun feed(s: String) { val c = Commands.parse(s); chat.handle(c, m); m.apply(c) }
        fun say(id: Int, name: String, msg: String) =
            feed(Commands.build("notifytextmessage", "targetmode" to 2, "msg" to msg, "invokerid" to id, "invokername" to name))
        say(4, "Riley", "Anyone up for a match later tonight?")
        say(3, "Jamie", "Count me in, after 8 pm")
        feed("notifyclientmoved ctid=1 reasonid=0 clid=5")
        say(4, "Riley", "Nice! I will set up a **lobby** at 8: https://example.com/lobby")
        feed(Commands.build("notifyclientleftview", "cfid" to 1, "ctid" to 0, "reasonid" to 8, "reasonmsg" to "see you", "clid" to 2))
        feed("notifytextmessage targetmode=1 msg=Psst invokerid=9 invokername=Robin invokeruid=robin=")
        val s = state()
        return s.copy(
            tree = m.tree(),
            chat = chat.keys().associateWith { chat.entries(it) },
            privateTabs = chat.privateNames.map { (uid, name) -> Session.PrivateTab(uid, name) },
            unread = setOf(Chat.Key.Private("robin=")),
        )
    }

    fun state(): Session.UiState {
        val m = model()
        return Session.UiState(
            phase = Session.Phase.CONNECTED,
            address = "ts.example.com",
            serverName = m.serverName,
            tree = m.tree(),
            ownId = 3,
            ownChannel = 1,
            talking = setOf(4, 9),
            cannotTalk = m.clients.values.filter { !m.canTalk(it) }.map { it.id }.toSet(),
        )
    }
}
