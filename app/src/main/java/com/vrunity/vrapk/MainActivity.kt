package com.vrunity.vrapk

import android.app.Activity
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager

// A native Android VR game. On a headset the app opens straight into the headset's
// own VR session; on a device without one it falls back to screen mode.
class MainActivity : Activity() {
    private var surface: VrSurfaceView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        // The scene is put on screen straight away, before anything else is tried, so
        // the app draws from its very first moment instead of waiting on a screen that
        // may never come. Opening the headset's VR session happens alongside this and
        // takes the app over when it succeeds.
        startScreenMode()
        val attempt = Thread {
            // 2 = the headset ran the game, anything else leaves the scene on screen.
            // Nothing this thread can do is allowed to end in a blank window, so every
            // failure — including one this thread never sees coming — is caught here.
            var tookVr = false
            try {
                tookVr = XrSession(this).run() == 2
            } catch (t: Throwable) {
                tookVr = false
            }
            if (tookVr) runOnUiThread { finish() }
        }
        attempt.start()
    }

    private fun startScreenMode() {
        if (surface != null) return
        val s = VrSurfaceView(this)
        surface = s
        setContentView(s)
        s.onResume()
        s.startSensors()
        fullscreen()
    }

    override fun onResume() {
        super.onResume()
        surface?.onResume()
        surface?.startSensors()
        fullscreen()
    }

    override fun onPause() {
        surface?.stopSensors()
        surface?.onPause()
        super.onPause()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) fullscreen()
    }

    // Screen mode only: volume up walks forward, volume down walks back, reachable
    // by touch while the device sits in a phone holder.
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        val s = surface
        if (s != null) {
            if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
                s.walk(1f)
                return true
            }
            if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
                s.walk(-1f)
                return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    private fun fullscreen() {
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            or View.SYSTEM_UI_FLAG_FULLSCREEN
            or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY)
    }
}
