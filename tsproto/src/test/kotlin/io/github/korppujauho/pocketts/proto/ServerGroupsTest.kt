package io.github.korppujauho.pocketts.proto

import kotlin.test.Test
import kotlin.test.assertEquals

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
        // Made an admin: the server sends the whole set again
        m.apply(Commands.parse("notifyclientneededpermissions permid=170 permvalue=75|permid=172 permvalue=75|permid=248 permvalue=75"))
        val choices = m.serverGroupChoices().associateBy { it.group.id }
        assertEquals(true to true, choices.getValue(6).let { it.canAdd to it.canRemove })
        assertEquals(true to true, choices.getValue(7).let { it.canAdd to it.canRemove })
        assertEquals(false to false, choices.getValue(8).let { it.canAdd to it.canRemove })
        // And no longer: what isn't in the new set is gone
        m.apply(Commands.parse("notifyclientneededpermissions permid=248 permvalue=25"))
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
}
