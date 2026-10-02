package com.vrunity.vrapk

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import android.view.Gravity
import android.view.KeyEvent
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {
    private var surface: VrSurfaceView? = null
    private var headset = false
    private var resumed = false
    private var started = false
    private var closing = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        headset = packageManager.hasSystemFeature("android.hardware.vr.headtracking")
        // Nothing is put on screen on a headset yet: the game opens in the bare
        // window, which is the only shape the headset's runtime accepts as a VR app.
        if (!headset) startScreenMode(null)
    }

    override fun onResume() {
        super.onResume()
        resumed = true
        surface?.onResume()
        surface?.startSensors()
        fullscreen()
        if (headset && resumed && !started && !closing) {
            started = true
            Thread({ openHeadsetSession() }, "VRUnity-XR").start()
        }
    }

    // The runtime can refuse a session in the moment right after the activity is
    // resumed. A fresh attempt a moment later is what turns that refusal into a game.
    private fun openHeadsetSession() {
        var result = 0
        var failure: String? = null
        try {
            Xr.load()
            for (attempt in 0 until 6) {
                result = XrSession(this).run()
                if (result != 0) break
                SystemClock.sleep(500L)
            }
        } catch (t: Throwable) {
            Log.e("VRUnityXR", "VR launch failed", t)
            failure = t.message ?: "VR startup failed."
        }
        if (failure == null && result == 2) {
            runOnUiThread { if (!closing && !isDestroyed) finish() }
            return
        }
        val reason = failure ?: if (result == 0)
            "The headset's VR runtime did not answer."
        else
            "VR opened but the headset never handed over a frame."
        runOnUiThread {
            if (closing || isDestroyed) return@runOnUiThread
            Log.e("VRUnityXR", reason)
            Toast.makeText(this, reason, Toast.LENGTH_LONG).show()
            startScreenMode(reason)
        }
    }

    // A visible way out: when VR cannot start, the same game is shown on the screen
    // with the reason above it, instead of the launch screen staying up for ever.
    private fun startScreenMode(reason: String?) {
        if (surface != null) return
        val view = VrSurfaceView(this)
        surface = view
        val root = FrameLayout(this)
        root.addView(view, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        if (reason != null) {
            val note = TextView(this)
            note.text = reason
            note.setTextColor(Color.WHITE)
            note.setBackgroundColor(0xCC000000.toInt())
            note.setPadding(24, 16, 24, 16)
            root.addView(note, FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.TOP or Gravity.CENTER_HORIZONTAL))
        }
        setContentView(root)
        view.startSensors()
        fullscreen()
    }

    override fun onPause() {
        resumed = false
        surface?.stopSensors()
        surface?.onPause()
        super.onPause()
    }

    override fun onDestroy() {
        closing = true
        super.onDestroy()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) fullscreen()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        val view = surface
        if (view != null && keyCode == KeyEvent.KEYCODE_VOLUME_UP) { view.walk(1f); return true }
        if (view != null && keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) { view.walk(-1f); return true }
        return super.onKeyDown(keyCode, event)
    }

    private fun fullscreen() {
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = (android.view.View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            or android.view.View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            or android.view.View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            or android.view.View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            or android.view.View.SYSTEM_UI_FLAG_FULLSCREEN
            or android.view.View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY)
    }
}
