package io.github.korppujauho.pocketts

import androidx.annotation.StringRes

/**
 * TeamSpeak's own community servers, open to everyone and moderated by TeamSpeak: somewhere to try PocketTS without a
 * server of one's own. By the address or server nickname the TeamSpeak app shows for them (a nickname is looked up as
 * any typed one is). Only TeamSpeak's official ones: other servers are joined by their address, link or nickname.
 */
object CommunityServers {
    data class Server(val address: String, @StringRes val name: Int)

    val list = listOf(
        Server("ts-community", R.string.community_worldwide),
        Server("eu-east-1", R.string.community_eu_east),
        Server("emea", R.string.community_emea),
        Server("apac.tmspk.net", R.string.community_apac),
        Server("latam.tmspk.net", R.string.community_latam),
        Server("na-tmspk", R.string.community_na),
    )
}
