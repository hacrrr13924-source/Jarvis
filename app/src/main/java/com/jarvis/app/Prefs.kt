package com.jarvis.app

import android.content.Context

class Prefs(ctx: Context) {
    private val p = ctx.applicationContext.getSharedPreferences("jarvis", Context.MODE_PRIVATE)

    var key: String
        get() = p.getString("key", "") ?: ""
        set(v) { p.edit().putString("key", v).apply() }
    var model: String
        get() = p.getString("model", "claude-sonnet-4-6") ?: "claude-sonnet-4-6"
        set(v) { p.edit().putString("model", v).apply() }
    var user: String
        get() = p.getString("user", "Sir") ?: "Sir"
        set(v) { p.edit().putString("user", v).apply() }
    var lang: String
        get() = p.getString("lang", "hi-IN") ?: "hi-IN"
        set(v) { p.edit().putString("lang", v).apply() }
    var rate: Float
        get() = p.getFloat("rate", 1.0f)
        set(v) { p.edit().putFloat("rate", v).apply() }
    var contacts: String
        get() = p.getString("contacts", "Abhishek,अभिषेक=") ?: ""
        set(v) { p.edit().putString("contacts", v).apply() }
}
