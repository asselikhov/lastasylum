package com.lastasylum.alliance.data.network

import okhttp3.Dns
import java.net.Inet4Address
import java.net.InetAddress
import java.net.UnknownHostException

/**
 * Сначала IPv4: у части мобильных сетей IPv6 к бэкенду «висит», OkHttp уходит в таймаут.
 */
object PreferIpv4Dns : Dns {
    override fun lookup(hostname: String): List<InetAddress> {
        val all = try {
            Dns.SYSTEM.lookup(hostname)
        } catch (e: UnknownHostException) {
            throw e
        }
        val v4 = all.filter { it is Inet4Address }
        return if (v4.isNotEmpty()) v4 else all
    }
}
