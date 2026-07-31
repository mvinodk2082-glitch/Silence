package com.silence.blocker.lists

import android.content.Context
import com.silence.blocker.filter.FilterCategory
import com.silence.blocker.util.DomainNormalizer
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Hosts / domain-list loader with on-disk cache under filesDir/lists/.
 */
class ListRepository(
    private val client: OkHttpClient = defaultClient(),
) {
    /** @deprecated use [PackCatalog.all] */
    val catalog: List<RemotePack> get() = PackCatalog.all

    fun parseText(text: String, format: ListFormat = ListFormat.AUTO): Set<String> {
        return when (format) {
            ListFormat.HOSTS -> parseHostsFile(text)
            ListFormat.DOMAINS -> parseDomainLines(text)
            ListFormat.ADBLOCK_DOMAINS -> parseAdblockDomains(text)
            ListFormat.AUTO -> parseAuto(text)
        }
    }

    fun parseHostsFile(text: String): Set<String> {
        val out = LinkedHashSet<String>()
        for (rawLine in text.lineSequence()) {
            var line = rawLine.trim()
            if (line.isEmpty() || line.startsWith('#')) continue
            val hash = line.indexOf('#')
            if (hash >= 0) line = line.substring(0, hash).trim()
            if (line.isEmpty()) continue

            val tokens = line.split(Regex("\\s+"))
            val hostToken = when {
                tokens.size >= 2 && isIpLiteral(tokens[0]) -> tokens[1]
                tokens.size == 1 -> tokens[0]
                else -> tokens.last()
            }
            addHost(out, hostToken)
        }
        return out
    }

    fun parseDomainLines(text: String): Set<String> {
        val out = LinkedHashSet<String>()
        for (rawLine in text.lineSequence()) {
            var line = rawLine.trim()
            if (line.isEmpty() || line.startsWith('#') || line.startsWith('!')) continue
            val hash = line.indexOf('#')
            if (hash >= 0) line = line.substring(0, hash).trim()
            addHost(out, line)
        }
        return out
    }

    /**
     * Extracts hostnames from simple adblock-style rules:
     * `||ads.example.com^`, `||example.com^$third-party`, plain domains.
     * Skips regex, cosmetic, and path-heavy rules.
     */
    fun parseAdblockDomains(text: String): Set<String> {
        val out = LinkedHashSet<String>()
        for (rawLine in text.lineSequence()) {
            val line = rawLine.trim()
            if (line.isEmpty() || line.startsWith('!') || line.startsWith('[') || line.startsWith('#')) continue
            if (line.startsWith("@@")) continue // exception rules — skip for DNS blocklists
            if (line.contains('/') && !line.startsWith("||")) continue
            if (line.startsWith("/") && line.endsWith("/")) continue // regex

            val host = when {
                line.startsWith("||") -> {
                    val body = line.removePrefix("||")
                    val end = body.indexOfAny(charArrayOf('^', '/', '$', '*'))
                    if (end >= 0) body.substring(0, end) else body
                }
                line.startsWith("|") -> null
                else -> {
                    // plain domain or hosts-like
                    val tokens = line.split(Regex("\\s+"))
                    if (tokens.size >= 2 && isIpLiteral(tokens[0])) tokens[1] else tokens[0]
                }
            }
            if (host != null) addHost(out, host)
        }
        return out
    }

    fun parseAuto(text: String): Set<String> {
        val sample = text.lineSequence().take(40).joinToString("\n")
        val hostsHits = sample.lineSequence().count { line ->
            val t = line.trim()
            t.startsWith("0.0.0.0") || t.startsWith("127.0.0.1")
        }
        val adblockHits = sample.lineSequence().count { it.trim().startsWith("||") }
        return when {
            hostsHits >= 3 -> parseHostsFile(text)
            adblockHits >= 3 -> parseAdblockDomains(text)
            else -> {
                // union domain lines + light hosts/adblock
                LinkedHashSet<String>().apply {
                    addAll(parseDomainLines(text))
                    addAll(parseHostsFile(text))
                    addAll(parseAdblockDomains(text))
                }
            }
        }
    }

    fun downloadRaw(url: String): Result<String> = runCatching {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Silence/0.3 (Android local DNS filter)")
            .get()
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("HTTP ${response.code}")
            response.body?.string() ?: error("empty body")
        }
    }

    fun downloadAndParse(url: String, format: ListFormat = ListFormat.AUTO): Result<Set<String>> =
        downloadRaw(url).map { parseText(it, format) }

    // --- disk cache ---

    fun cacheDir(context: Context): File =
        File(context.filesDir, "lists").also { it.mkdirs() }

    fun packFile(context: Context, packId: String): File =
        File(cacheDir(context), "$packId.txt")

    fun metaFile(context: Context, packId: String): File =
        File(cacheDir(context), "$packId.json")

    fun writeCache(context: Context, packId: String, body: String, hostCount: Int): PackCacheMeta {
        packFile(context, packId).writeText(body)
        val meta = PackCacheMeta(
            packId = packId,
            hostCount = hostCount,
            updatedAtEpochMs = System.currentTimeMillis(),
            byteSize = body.length.toLong(),
        )
        metaFile(context, packId).writeText(meta.toJson())
        return meta
    }

    fun readCacheHosts(context: Context, pack: RemotePack): Set<String>? {
        val file = packFile(context, pack.id)
        if (!file.isFile || file.length() == 0L) return null
        return runCatching { parseText(file.readText(), pack.format) }.getOrNull()
    }

    fun readMeta(context: Context, packId: String): PackCacheMeta? {
        val file = metaFile(context, packId)
        if (!file.isFile) return null
        return runCatching { PackCacheMeta.fromJson(file.readText()) }.getOrNull()
    }

    fun clearCache(context: Context, packId: String) {
        packFile(context, packId).delete()
        metaFile(context, packId).delete()
    }

    private fun addHost(out: MutableSet<String>, raw: String) {
        val host = DomainNormalizer.normalize(raw.removePrefix("*.").trim())
        if (host.isEmpty()) return
        if (host == "localhost" || host == "local" || host == "broadcasthost") return
        if (host.contains('/')) return
        if (host.contains('*')) return
        if (isIpLiteral(host)) return
        // Require at least one dot for real FQDNs (skip junk tokens)
        if (!host.contains('.')) return
        if (host.length > 253) return
        out.add(host)
    }

    private fun isIpLiteral(s: String): Boolean {
        if (s.contains(':')) return true // IPv6-ish
        val parts = s.split('.')
        if (parts.size != 4) return false
        return parts.all { p -> p.toIntOrNull()?.let { it in 0..255 } == true }
    }

    companion object {
        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(25, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()
    }
}

data class PackCacheMeta(
    val packId: String,
    val hostCount: Int,
    val updatedAtEpochMs: Long,
    val byteSize: Long,
) {
    fun toJson(): String = JSONObject()
        .put("packId", packId)
        .put("hostCount", hostCount)
        .put("updatedAtEpochMs", updatedAtEpochMs)
        .put("byteSize", byteSize)
        .toString()

    companion object {
        fun fromJson(text: String): PackCacheMeta {
            val o = JSONObject(text)
            return PackCacheMeta(
                packId = o.getString("packId"),
                hostCount = o.getInt("hostCount"),
                updatedAtEpochMs = o.getLong("updatedAtEpochMs"),
                byteSize = o.optLong("byteSize", 0L),
            )
        }
    }
}
