package com.lion.filebeam

import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.SocketTimeoutException

object Discovery {

    const val UDP_PORT = 47812
    const val TCP_PORT = 47813

    data class Beacon(
        val name: String,
        val ip: String,
        val port: Int,
        val pin: String,
        val seen: Long = System.currentTimeMillis(),
    )

    fun localIp(): String {
        try {
            val en = NetworkInterface.getNetworkInterfaces()
            while (en.hasMoreElements()) {
                val n = en.nextElement()
                val addrs = n.inetAddresses
                while (addrs.hasMoreElements()) {
                    val a = addrs.nextElement()
                    if (!a.isLoopbackAddress && a is Inet4Address) return a.hostAddress ?: ""
                }
            }
        } catch (_: Exception) {
        }
        return ""
    }

    /** AlÄ±cÄ± (TV): her 2 saniyede bir varlÄ±ÄÄ±nÄ± duyurur. */
    suspend fun broadcastLoop(port: Int, pin: String) = withContext(Dispatchers.IO) {
        DatagramSocket().use { sock ->
            sock.broadcast = true
            val name = Build.MODEL ?: "TV"
            val msg = "FILEBEAM1|" + name + "|" + localIp() + "|" + port + "|" + pin
            val data = msg.toByteArray(Charsets.UTF_8)
            while (isActive) {
                runCatching {
                    sock.send(DatagramPacket(data, data.size, InetAddress.getByName("255.255.255.255"), UDP_PORT))
                }
                delay(2000)
            }
        }
    }

    /** GÃ¶nderici (telefon): aÄdaki alÄ±cÄ±larÄ± dinler. */
    suspend fun listen(onBeacon: (Beacon) -> Unit) = withContext(Dispatchers.IO) {
        DatagramSocket(null).use { sock ->
            sock.reuseAddress = true
            sock.bind(InetSocketAddress(UDP_PORT))
            sock.soTimeout = 1000
            val buf = ByteArray(1024)
            while (isActive) {
                try {
                    val p = DatagramPacket(buf, buf.size)
                    sock.receive(p)
                    val msg = String(p.data, 0, p.length, Charsets.UTF_8)
                    val parts = msg.split("|")
                    if (parts.size == 5 && parts[0] == "FILEBEAM1") {
                        onBeacon(Beacon(parts[1], parts[2], parts[3].toInt(), parts[4]))
                    }
                } catch (_: SocketTimeoutException) {
                }
            }
        }
    }
}
