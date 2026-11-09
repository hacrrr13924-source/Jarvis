package com.jarvis.app

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** Pehle phone ke kaam (call, app kholna), warna AI se jawab. Background thread par chalayein. */
object Brain {
    private val OPEN = Regex("\\b(open|launch|start|play|search|kholo|khol|chalu|chalao|chala|bajao)\\b|खोल|ओपन|चालू|चलाओ|बजाओ")
    private val CALL = Regex("\\b(call|dial|phone)\\b|कॉल|कोल|फोन|फ़ोन")
    private val FILLER = Regex("youtube|\\b(open|launch|start|play|search|kholo|khol|chalu|chalao|chala|bajao|karo|do|pe|par|on|me|mein|ko|ka|ki)\\b|खोल|ओपन|चालू|चलाओ|बजाओ|करो|पर|में|को")
    private val ALIAS = mapOf(
        "इंस्टाग्राम" to "instagram", "यूट्यूब" to "youtube", "यूटयूब" to "youtube",
        "व्हाट्सएप" to "whatsapp", "व्हाट्सऐप" to "whatsapp", "क्रोम" to "chrome",
        "कैमरा" to "camera", "गैलरी" to "gallery", "सेटिंग" to "settings"
    )

    fun handle(ctx: Context, p: Prefs, history: JSONArray): String {
        val text = history.optJSONObject(history.length() - 1)?.optString("content") ?: ""
        return local(ctx, p, text) ?: askClaude(p, history)
    }

    private fun go(ctx: Context, i: Intent) {
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        ctx.startActivity(i)
    }

    private fun contacts(p: Prefs): List<Pair<List<String>, String>> =
        p.contacts.lines().mapNotNull { line ->
            val i = line.indexOf('=')
            if (i < 0) return@mapNotNull null
            val names = line.substring(0, i).split(',').map { it.trim().lowercase() }.filter { it.isNotEmpty() }
            val num = line.substring(i + 1).filter { it.isDigit() || it == '+' }
            if (names.isEmpty() || num.isEmpty()) null else names to num
        }

    private fun local(ctx: Context, p: Prefs, text: String): String? {
        val who = p.user
        var t = text.lowercase()
        ALIAS.forEach { (k, v) -> t = t.replace(k, v) }

        // 1) Call
        if (CALL.containsMatchIn(t)) {
            val hit = contacts(p).firstOrNull { (names, _) -> names.any { t.contains(it) } }
                ?: return "Kaunsa contact, $who? Settings mein naam aur number jodiye."
            if (ctx.checkSelfPermission(Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED)
                return "Call ki permission nahi hai. App kholkar Phone permission allow kijiye."
            go(ctx, Intent(Intent.ACTION_CALL, Uri.parse("tel:" + hit.second)))
            return "${hit.first.first().replaceFirstChar { it.uppercase() }} ko call laga raha hoon, $who."
        }

        // 2) YouTube (search ke saath)
        if (t.contains("youtube") && OPEN.containsMatchIn(t)) {
            val q = FILLER.replace(t, " ").trim().replace(Regex("\\s+"), " ")
            if (q.isNotEmpty()) {
                val url = "https://www.youtube.com/results?search_query=" + URLEncoder.encode(q, "UTF-8")
                try {
                    go(ctx, Intent(Intent.ACTION_VIEW, Uri.parse(url)).setPackage("com.google.android.youtube"))
                } catch (e: Exception) {
                    go(ctx, Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                }
                return "YouTube par $q chala raha hoon."
            }
            val i = ctx.packageManager.getLaunchIntentForPackage("com.google.android.youtube")
            if (i != null) { go(ctx, i); return "YouTube khol raha hoon, $who." }
        }

        // 3) Koi bhi app: "open Instagram", "WhatsApp kholo"
        if (OPEN.containsMatchIn(t)) {
            val pm = ctx.packageManager
            val li = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            val best = pm.queryIntentActivities(li, 0)
                .map { it.loadLabel(pm).toString().lowercase() to it.activityInfo.packageName }
                .filter { it.first.length >= 3 && t.contains(it.first) }
                .maxByOrNull { it.first.length }
            if (best != null) {
                val i = pm.getLaunchIntentForPackage(best.second)
                if (i != null) {
                    go(ctx, i)
                    return "${best.first.replaceFirstChar { it.uppercase() }} khol raha hoon, $who."
                }
            }
        }
        return null
    }

    private fun askClaude(p: Prefs, history: JSONArray): String {
        if (p.key.isBlank()) return "Pehle Settings mein API key daaliye."
        return try {
            val msgs = JSONArray()
            val from = maxOf(0, history.length() - 12)
            for (i in from until history.length()) {
                val m = history.getJSONObject(i)
                if (msgs.length() == 0 && m.optString("role") != "user") continue
                msgs.put(m)
            }
            val system = "You are JARVIS, a calm, witty voice assistant on the user's phone. " +
                "Address the user as ${p.user}. Reply in the same language the user speaks " +
                "(Hindi, Hinglish or English). Keep replies to 1-3 short spoken sentences. " +
                "No markdown, no lists, no emojis."
            val body = JSONObject().put("model", p.model).put("max_tokens", 400)
                .put("system", system).put("messages", msgs)
            val c = URL("https://api.anthropic.com/v1/messages").openConnection() as HttpURLConnection
            c.requestMethod = "POST"
            c.connectTimeout = 15000
            c.readTimeout = 40000
            c.doOutput = true
            c.setRequestProperty("content-type", "application/json")
            c.setRequestProperty("x-api-key", p.key)
            c.setRequestProperty("anthropic-version", "2023-06-01")
            c.outputStream.use { it.write(body.toString().toByteArray()) }
            val code = c.responseCode
            val stream = if (code in 200..299) c.inputStream else c.errorStream
            val txt = stream.bufferedReader().readText()
            if (code !in 200..299) {
                return if (code == 401) "API key galat hai. Settings mein check kijiye." else "API error $code."
            }
            val arr = JSONObject(txt).getJSONArray("content")
            val sb = StringBuilder()
            for (i in 0 until arr.length()) sb.append(arr.getJSONObject(i).optString("text"))
            sb.toString().trim()
        } catch (e: Exception) {
            "Internet ya connection mein dikkat hai."
        }
    }
}
