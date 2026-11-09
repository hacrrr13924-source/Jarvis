package com.jarvis.app

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.webkit.JavascriptInterface
import android.webkit.WebView
import org.json.JSONArray
import org.json.JSONObject

class MainActivity : Activity() {
    private lateinit var web: WebView
    private lateinit var prefs: Prefs
    private lateinit var voice: Voice

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        voice = Voice(this, { prefs.lang }, { prefs.rate })
        window.statusBarColor = 0xFF07090F.toInt()
        window.navigationBarColor = 0xFF07090F.toInt()

        web = WebView(this)
        web.setBackgroundColor(0xFF07090F.toInt())
        web.settings.javaScriptEnabled = true
        web.settings.domStorageEnabled = true
        web.addJavascriptInterface(Bridge(), "Android")
        setContentView(web)
        web.loadUrl("file:///android_asset/index.html")

        requestPermissions(
            arrayOf(
                Manifest.permission.RECORD_AUDIO,
                Manifest.permission.CALL_PHONE,
                Manifest.permission.POST_NOTIFICATIONS
            ), 1
        )
    }

    private fun js(code: String) = runOnUiThread { web.evaluateJavascript(code, null) }

    override fun onBackPressed() {
        moveTaskToBack(true)
    }

    override fun onDestroy() {
        voice.destroy()
        super.onDestroy()
    }

    inner class Bridge {
        @JavascriptInterface
        fun getSettings(): String = JSONObject()
            .put("keySet", prefs.key.isNotBlank())
            .put("model", prefs.model).put("user", prefs.user).put("lang", prefs.lang)
            .put("rate", prefs.rate.toDouble()).put("contacts", prefs.contacts)
            .toString()

        @JavascriptInterface
        fun saveSettings(json: String) {
            val o = JSONObject(json)
            if (o.optString("key").isNotBlank()) prefs.key = o.getString("key").trim()
            prefs.model = o.optString("model", prefs.model).ifBlank { "claude-sonnet-4-6" }
            prefs.user = o.optString("user", prefs.user).ifBlank { "Sir" }
            prefs.lang = o.optString("lang", prefs.lang)
            prefs.rate = o.optDouble("rate", prefs.rate.toDouble()).toFloat()
            prefs.contacts = o.optString("contacts", prefs.contacts)
        }

        @JavascriptInterface
        fun listen() {
            voice.listen({ s -> js("onState(${JSONObject.quote(s)})") },
                { t -> js("onHeard(${JSONObject.quote(t)})") })
        }

        @JavascriptInterface
        fun stop() { voice.stopListening() }

        @JavascriptInterface
        fun ask(historyJson: String) {
            Thread {
                val reply = Brain.handle(this@MainActivity, prefs, JSONArray(historyJson))
                js("onReply(${JSONObject.quote(reply)})")
            }.start()
        }

        @JavascriptInterface
        fun speak(text: String) {
            voice.speak(text) { js("onSpoke()") }
        }

        @JavascriptInterface
        fun quick(command: String) {
            Thread {
                val h = JSONArray().put(JSONObject().put("role", "user").put("content", command))
                Brain.handle(this@MainActivity, prefs, h)
            }.start()
        }

        @JavascriptInterface
        fun bubble(on: Boolean) {
            runOnUiThread {
                val svc = Intent(this@MainActivity, BubbleService::class.java)
                if (!on) { stopService(svc); return@runOnUiThread }
                if (!Settings.canDrawOverlays(this@MainActivity)) {
                    startActivity(
                        Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:$packageName"))
                    )
                    return@runOnUiThread
                }
                if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                    requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 2)
                    return@runOnUiThread
                }
                startForegroundService(svc)
            }
        }
    }
}
