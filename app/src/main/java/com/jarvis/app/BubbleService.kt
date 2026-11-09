package com.jarvis.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.abs

/** Dusre apps ke upar tairta hua gol Jarvis button. Tap = bolo, lamba dabao = app kholo. */
class BubbleService : Service() {
    private lateinit var wm: WindowManager
    private lateinit var view: BubbleView
    private lateinit var params: WindowManager.LayoutParams
    private lateinit var prefs: Prefs
    private lateinit var voice: Voice
    private val main = Handler(Looper.getMainLooper())
    private val history = JSONArray()
    private var busy = false

    override fun onBind(intent: Intent?): IBinder? = null
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onCreate() {
        super.onCreate()
        prefs = Prefs(this)
        voice = Voice(this, { prefs.lang }, { prefs.rate })

        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("jarvis", "Jarvis", NotificationManager.IMPORTANCE_LOW))
        val n = Notification.Builder(this, "jarvis")
            .setContentTitle("Jarvis active hai")
            .setContentText("Gol button par tap karke bolein")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .build()
        if (Build.VERSION.SDK_INT >= 30) {
            startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        } else {
            startForeground(1, n)
        }

        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        view = BubbleView(this)
        val size = (64 * resources.displayMetrics.density).toInt()
        params = WindowManager.LayoutParams(
            size, size,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.START; x = 24; y = 300 }

        view.setOnTouchListener(object : View.OnTouchListener {
            var sx = 0; var sy = 0; var tx = 0f; var ty = 0f
            var moved = false; var held = false
            val longPress = Runnable { if (!moved) { held = true; openApp() } }

            override fun onTouch(v: View, e: MotionEvent): Boolean {
                when (e.action) {
                    MotionEvent.ACTION_DOWN -> {
                        sx = params.x; sy = params.y; tx = e.rawX; ty = e.rawY
                        moved = false; held = false
                        main.postDelayed(longPress, 700)
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = (e.rawX - tx).toInt()
                        val dy = (e.rawY - ty).toInt()
                        if (abs(dx) > 12 || abs(dy) > 12) { moved = true; main.removeCallbacks(longPress) }
                        if (moved) {
                            params.x = sx + dx; params.y = sy + dy
                            wm.updateViewLayout(view, params)
                        }
                    }
                    MotionEvent.ACTION_UP -> {
                        main.removeCallbacks(longPress)
                        if (!moved && !held) activate()
                    }
                }
                return true
            }
        })
        wm.addView(view, params)
    }

    private fun openApp() {
        startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    private fun activate() {
        if (busy) { voice.stopSpeaking(); voice.stopListening(); busy = false; view.mode = 0; return }
        busy = true
        view.mode = 1
        voice.listen({ }, { text ->
            if (text.isBlank()) {
                busy = false; view.mode = 0
            } else {
                view.mode = 2
                history.put(JSONObject().put("role", "user").put("content", text))
                Thread {
                    val reply = Brain.handle(this, prefs, history)
                    history.put(JSONObject().put("role", "assistant").put("content", reply))
                    main.post { view.mode = 3 }
                    voice.speak(reply) { busy = false; view.mode = 0 }
                }.start()
            }
        })
    }

    override fun onDestroy() {
        try { wm.removeView(view) } catch (e: Exception) { }
        voice.destroy()
        super.onDestroy()
    }
}
