package io.github.korppujauho.pocketts.proto

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Server groups as a TeamSpeak 6 server tells them (these lines are from one, shortened): only groups for people are
 * offered, in the server's order; which ones we may change follows our own powers, which the server sends again
 * whole when they change; the default group is never changed by hand; and a person's groups follow what the server says.
 */
class ServerGroupsTest {
    private fun model(): ServerModel {
        val m = ServerModel()
        fun cmd(s: String) = m.apply(Commands.parse(s))
        cmd("initserver virtualserver_name=Test aclid=2 virtualserver_default_server_group=8")
        cmd(
            "notifyservergrouplist sgid=1 name=Guest\\sServer\\sQuery type=2 iconid=0 sortid=0 n_member_addp=0 n_member_removep=0" +
                "|sgid=3 name=Server\\sAdmin type=0 iconid=300 sortid=0 n_member_addp=75 n_member_removep=75" +
                "|sgid=6 name=Server\\sAdmin type=1 iconid=300 sortid=0 n_member_addp=75 n_member_removep=75" +
                "|sgid=7 name=Normal type=1 iconid=0 sortid=0 n_member_addp=60 n_member_removep=60" +
                "|sgid=8 name=Guest type=1 iconid=0 sortid=0 n_member_addp=0 n_member_removep=0"
        )
        cmd(
            "notifycliententerview cfid=0 ctid=1 reasonid=0 clid=2 client_nickname=Jamie client_database_id=233 client_servergroups=8 client_type=0" +
                "|clid=3 client_nickname=Lee client_database_id=234 client_servergroups=8 client_type=0"
        )
        cmd(
            "notifychannelgrouplist cgid=1 name=Channel\\sAdmin type=0 iconid=100 sortid=0 n_member_addp=50 n_member_removep=50" +
                "|cgid=5 name=Channel\\sAdmin type=1 iconid=100 sortid=0 n_member_addp=50 n_member_removep=50" +
                "|cgid=6 name=Operator type=1 iconid=200 sortid=0 n_member_addp=30 n_member_removep=30" +
                "|cgid=7 name=Voice type=1 iconid=600 sortid=0 n_member_addp=25 n_member_removep=25" +
                "|cgid=8 name=Guest type=1 iconid=0 sortid=0 n_member_addp=0 n_member_removep=0"
        )
        cmd("notifyclientneededpermissions permid=182 permvalue=200000|permid=248 permvalue=25")
        return m
    }

    @Test
    fun onlyGroupsForPeopleInTheServersOrder() {
        assertEquals(listOf(6, 7, 8), model().serverGroupChoices().map { it.group.id })
    }

    @Test
    fun aGuestCanChangeNone() {
        assertEquals(emptyList(), model().serverGroupChoices().filter { it.canAdd || it.canRemove })
    }

    @Test
    fun anAdminsPowersReachTheirGroupsButNotTheDefaultOne() {
        val m = model()
        // Made an admin: the server sends what changed
        m.apply(Commands.parse("notifyclientneededpermissions permid=170 permvalue=75|permid=172 permvalue=75|permid=248 permvalue=75"))
        val choices = m.serverGroupChoices().associateBy { it.group.id }
        assertEquals(true to true, choices.getValue(6).let { it.canAdd to it.canRemove })
        assertEquals(true to true, choices.getValue(7).let { it.canAdd to it.canRemove })
        assertEquals(false to false, choices.getValue(8).let { it.canAdd to it.canRemove })
        // And no longer: the powers come back as 0
        m.apply(Commands.parse("notifyclientneededpermissions permid=170 permvalue=0|permid=172 permvalue=0|permid=248 permvalue=25"))
        assertEquals(emptyList(), m.serverGroupChoices().filter { it.canAdd || it.canRemove })
    }

    @Test
    fun aPowerBelowTheNeededOneIsntEnough() {
        val m = model()
        m.apply(Commands.parse("notifyclientneededpermissions permid=170 permvalue=60|permid=172 permvalue=50"))
        val normal = m.serverGroupChoices().first { it.group.id == 7 }
        assertEquals(true to false, normal.canAdd to normal.canRemove)
        assertEquals(false, m.serverGroupChoices().first { it.group.id == 6 }.canAdd)
    }

    @Test
    fun channelGroupsAreForPeopleOnly() {
        assertEquals(listOf(5, 6, 7, 8), model().channelGroupChoices().map { it.id })
    }

    @Test
    fun switchingAChannelGroupNeedsBothPowers() {
        val m = model()
        val groups = m.channelGroupChoices().associateBy { it.id }
        // A channel's operator: enough for Voice (25), not to make anyone a channel admin (50)
        m.apply(Commands.parse("notifyclientneededpermissions permid=170 permvalue=30|permid=172 permvalue=30"))
        val powers = m.groupPowers
        assertEquals(true, powers.canSwitch(groups[8], groups.getValue(7)))
        assertEquals(false, powers.canSwitch(groups[8], groups.getValue(5)))
        // Nor take it from one
        assertEquals(false, powers.canSwitch(groups[5], groups.getValue(8)))
        // A guest's powers: none
        assertEquals(false, ServerModel.GroupPowers().canSwitch(groups[8], groups.getValue(7)))
    }

    @Test
    fun aChannelGroupInAnotherChannelIsntTheOneShown() {
        val m = model()
        m.apply(Commands.parse("notifyclientchannelgroupchanged invokerid=2 cgid=5 cid=9 clid=3 cgi=9"))
        assertEquals(0, m.clients.getValue(3).channelGroup)
        val here = m.clients.getValue(3).channel
        m.apply(Commands.parse("notifyclientchannelgroupchanged invokerid=2 cgid=7 cid=$here clid=3 cgi=$here"))
        assertEquals(7, m.clients.getValue(3).channelGroup)
    }

    @Test
    fun aPersonsGroupsFollowTheServer() {
        val m = model()
        assertEquals(234, m.clients.getValue(3).databaseId)
        m.apply(Commands.parse("notifyservergroupclientadded invokerid=2 sgid=7 clid=3 name=Lee"))
        m.apply(Commands.parse("notifyclientupdated clid=3 client_servergroups=7"))
        assertEquals(listOf(7), m.clients.getValue(3).serverGroups)
        m.apply(Commands.parse("notifyservergroupclientdeleted invokerid=2 sgid=7 clid=3 name=Lee"))
        m.apply(Commands.parse("notifyclientupdated clid=3 client_servergroups=8"))
        assertEquals(listOf(8), m.clients.getValue(3).serverGroups)
    }

    /** Which channels we may create, from the permissions a TeamSpeak 6 server lists (only those we have). */
    @Test
    fun whatChannelsWeMayCreate() {
        val m = ServerModel()
        assertEquals(ServerModel.ChannelCreatePowers(), m.channelCreatePowers)
        assertTrue(m.channelCreatePowers.any) // not said yet: offered, the server decides
        // A guest on the test server: temporary channels only, last in their place
        m.apply(Commands.parse("notifyclientneededpermissions permid=94 permvalue=1|permid=95 permvalue=1|permid=97 permvalue=1"))
        assertEquals(ServerModel.ChannelCreatePowers(known = true, temporary = true), m.channelCreatePowers)
        // Nothing to create: taken away (0)
        m.apply(Commands.parse("notifyclientneededpermissions permid=94 permvalue=0"))
        assertFalse(m.channelCreatePowers.any)
        // An admin: everything
        m.apply(Commands.parse("notifyclientneededpermissions permid=91 permvalue=1|permid=92 permvalue=1|permid=93 permvalue=1|permid=94 permvalue=1|permid=105 permvalue=1"))
        assertEquals(ServerModel.ChannelCreatePowers(true, true, true, true, true, true), m.channelCreatePowers)
    }

    /**
     * As a TeamSpeak 6 server tells it (seen on the test server): the whole set after connecting, then only what
     * changed. Entering one's own new temporary channel gives the channel admin's powers, leaving takes them (0):
     * creating channels, which the server group gives, stays.
     */
    @Test
    fun laterListsAreOnlyWhatChanged() {
        val m = ServerModel()
        m.apply(Commands.parse("notifyclientneededpermissions permid=94 permvalue=1|permid=95 permvalue=1|permid=248 permvalue=25"))
        m.apply(Commands.parse("notifyclientneededpermissions permid=170 permvalue=50|permid=172 permvalue=50|permid=114 permvalue=1"))
        assertTrue(m.channelCreatePowers.temporary)
        assertEquals(ServerModel.GroupPowers(50, 50), m.groupPowers)
        m.apply(Commands.parse("notifyclientneededpermissions permid=170 permvalue=0|permid=172 permvalue=0|permid=114 permvalue=0"))
        assertTrue(m.channelCreatePowers.temporary)
        assertEquals(ServerModel.GroupPowers(0, 0), m.groupPowers)
    }
}
