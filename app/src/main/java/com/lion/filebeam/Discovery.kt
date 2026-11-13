package com.lion.filebeam

import android.content.Context
import android.net.wifi.WifiManager
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.SocketTimeoutException

object Discovery {
    const val UDP_PORT = 47812
    const val TCP_PORT = 47813

    data class Beacon(
        val name: String,
        val ip: String,
        val port: Int,
        // PIN is deliberately never broadcast.
        val pin: String = "",
        val seen: Long = System.currentTimeMillis(),
    )

    private fun activeIpv4(): List<Pair<String, InetAddress>> {
        val result = mutableListOf<Pair<String, InetAddress>>()
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val network = interfaces.nextElement()
                if (!network.isUp || network.isLoopback || network.isVirtual) continue
                val addresses = network.inetAddresses
                while (addresses.hasMoreElements()) {
                    val address = addresses.nextElement()
                    if (address is Inet4Address && !address.isLoopbackAddress && !address.isLinkLocalAddress) {
                        result += network.name to address
                    }
                }
            }
        } catch (_: Exception) {
        }
        return result.sortedBy { (name, _) ->
            when {
                name.startsWith("wlan", true) -> 0
                name.startsWith("eth", true) -> 1
                name.startsWith("ap", true) -> 2
                else -> 3
            }
        }
    }

    fun localIp(): String = activeIpv4().firstOrNull()?.second?.hostAddress ?: ""

    /** TV: announce device details without exposing the pairing PIN on the LAN. */
    suspend fun broadcastLoop(port: Int) = withContext(Dispatchers.IO) {
        DatagramSocket().use { socket ->
            socket.broadcast = true
            val name = (Build.MODEL ?: "Android TV").replace("|", " ").take(80)
            val ip = localIp()
            val data = "FILEBEAM2|$name|$ip|$port".toByteArray(Charsets.UTF_8)
            val destinations = activeIpv4().mapNotNull { (interfaceName, address) ->
                runCatching {
                    NetworkInterface.getByName(interfaceName)?.interfaceAddresses
                        ?.firstOrNull { it.address == address }?.broadcast
                }.getOrNull()
            }.distinct().ifEmpty { listOf(InetAddress.getByName("255.255.255.255")) }

            while (isActive) {
                destinations.forEach { destination ->
                    runCatching { socket.send(DatagramPacket(data, data.size, destination, UDP_PORT)) }
                }
                delay(2000)
            }
        }
    }

    /** Sender: acquire a Wi-Fi multicast lock while listening for LAN broadcasts. */
    suspend fun listen(context: Context, onBeacon: (Beacon) -> Unit) = withContext(Dispatchers.IO) {
        val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        val lock = wifi?.createMulticastLock("FileBeam-discovery")?.apply {
            setReferenceCounted(false)
            acquire()
        }
        try {
            DatagramSocket(null).use { socket ->
                socket.reuseAddress = true
                socket.bind(InetSocketAddress(UDP_PORT))
                socket.soTimeout = 1000
                val buffer = ByteArray(1024)
                while (isActive) {
                    try {
                        val packet = DatagramPacket(buffer, buffer.size)
                        socket.receive(packet)
                        val parts = String(packet.data, 0, packet.length, Charsets.UTF_8).split("|")
                        if (parts.size == 4 && parts[0] == "FILEBEAM2") {
                            val port = parts[3].toIntOrNull() ?: continue
                            val ip = parts[2]
                            if (port in 1..65535 && ip.matches(Regex("[0-9.]{7,15}"))) {
                                onBeacon(Beacon(parts[1].take(80), ip, port))
                            }
                        }
                    } catch (_: SocketTimeoutException) {
                        // Re-check cancellation.
                    }
                }
            }
        } finally {
            runCatching { if (lock?.isHeld == true) lock.release() }
        }
    }
}
