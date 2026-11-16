package com.myframe.tv.server

import java.net.InetAddress
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * PIN pairing and session tokens for the phone dashboard.
 * Pure logic with an injectable clock so the lock-out rules can be unit-tested.
 */
class Pairing(
    private val clock: () -> Long = System::currentTimeMillis,
    private val random: SecureRandom = SecureRandom(),
) {
    data class Device(val name: String, val address: String, val lastSeen: Long)

    sealed interface Result {
        data class Ok(val token: String) : Result
        data class BadPin(val remaining: Int) : Result
        data class Locked(val retryAfterSec: Int) : Result
    }

    var pin: String = newPin()
        private set

    private val sessions = LinkedHashMap<String, Device>()
    private var failures = 0
    private var lockedUntil = 0L

    // Built without String.format: on a TV set to Arabic, "%06d" prints Arabic-Indic digits that a phone keypad cannot type.
    private fun newPin(): String = random.nextInt(1_000_000).toString().padStart(6, '0')

    /** Any Unicode decimal digit becomes its ASCII form, so a PIN typed on a localised keyboard still matches. */
    private fun asciiDigits(raw: String): String =
        raw.trim().map { c -> Character.digit(c, 10).let { d -> if (d in 0..9) '0' + d else c } }.joinToString("")

    /** New PIN and no paired devices: called whenever the upload service starts. */
    @Synchronized
    fun reset() {
        pin = newPin()
        sessions.clear()
        failures = 0
        lockedUntil = 0
    }

    @Synchronized
    fun pair(candidate: String, name: String, address: String): Result {
        val now = clock()
        if (now < lockedUntil) return Result.Locked(((lockedUntil - now + 999) / 1000).toInt())
        if (!constantTimeEquals(asciiDigits(candidate), pin)) {
            failures++
            if (failures >= MAX_ATTEMPTS) {
                failures = 0
                lockedUntil = now + LOCK_MS
                return Result.Locked((LOCK_MS / 1000).toInt())
            }
            return Result.BadPin(MAX_ATTEMPTS - failures)
        }
        failures = 0
        val token = ByteArray(24).also(random::nextBytes).joinToString("") { "%02x".format(java.util.Locale.ROOT, it) }
        if (sessions.size >= MAX_SESSIONS) sessions.remove(sessions.keys.first())
        sessions[token] = Device(name.filter { !it.isISOControl() }.take(40).ifBlank { "Device" }, address, now)
        return Result.Ok(token)
    }

    @Synchronized
    fun validate(token: String?): Boolean {
        val device = sessions[token ?: return false] ?: return false
        sessions[token] = device.copy(lastSeen = clock())
        return true
    }

    @Synchronized
    fun revoke(token: String?) {
        sessions.remove(token ?: return)
    }

    @Synchronized
    fun revokeAll() = sessions.clear()

    @Synchronized
    fun devices(): List<Device> = sessions.values.toList()

    private fun constantTimeEquals(a: String, b: String): Boolean =
        MessageDigest.isEqual(a.toByteArray(), b.toByteArray())

    companion object {
        const val MAX_ATTEMPTS = 5
        const val LOCK_MS = 60_000L
        const val MAX_SESSIONS = 8
    }
}

/** Network rules that keep the dashboard on the local network. */
object NetGuard {
    /** True for loopback, private (RFC 1918), link-local and IPv6 unique-local addresses only. */
    fun isLocalAddress(ip: String?): Boolean {
        if (ip.isNullOrBlank()) return false
        val literal = ip.substringBefore('%').trim('[', ']')
        // Only literals are accepted, so this never triggers a DNS lookup.
        if (!literal.all { it.isLetterOrDigit() || it == '.' || it == ':' } || literal.none { it == '.' || it == ':' }) return false
        if (literal.any { it.isLetter() } && !literal.contains(':')) return false
        val addr = runCatching { InetAddress.getByName(literal) }.getOrNull() ?: return false
        if (addr.isLoopbackAddress || addr.isSiteLocalAddress || addr.isLinkLocalAddress) return true
        val bytes = addr.address
        return bytes.size == 16 && (bytes[0].toInt() and 0xFE) == 0xFC
    }

    /**
     * The Host header must name this device by IP. A page on the public internet that tricks a
     * browser into calling us (DNS rebinding) would arrive with its own host name and is refused.
     */
    fun hostAllowed(hostHeader: String?, localIps: Collection<String>): Boolean {
        val host = hostHeader?.trim()?.lowercase() ?: return false
        val name = when {
            host.startsWith("[") -> host.substringAfter('[').substringBefore(']')
            host.count { it == ':' } == 1 -> host.substringBefore(':')
            else -> host
        }
        return name == "localhost" || name == "127.0.0.1" || name == "::1" || name in localIps
    }

    /** Ids in URLs are numeric only, so a request can never name a file path. */
    fun parseId(segment: String?): Long? = segment?.takeIf { it.length in 1..18 && it.all(Char::isDigit) }?.toLongOrNull()
}
