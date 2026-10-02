package com.vrunity.vrapk

import android.app.Activity
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.util.Log
import android.widget.Toast

// A native Android VR game. On a headset the app opens straight into the headset's
// own VR session; on a device without one it falls back to screen mode.
class MainActivity : Activity() {
    private var surface: VrSurfaceView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val headset = packageManager.hasSystemFeature("android.hardware.vr.headtracking")
        // Do not run a second EGL renderer behind an immersive headset session.
        if (headset) setContentView(View(this)) else startScreenMode()
        val attempt = Thread {
            try {
                val result = XrSession(this).run()
                if (headset) {
                    if (result != 2) throw IllegalStateException("Unable to start the headset VR runtime.")
                    // run() returns only after the immersive session ends.
                    runOnUiThread { finish() }
                }
            } catch (t: Throwable) {
                Log.e("VRUnityXR", "VR launch or rendering failed", t)
                if (headset) runOnUiThread {
                    Toast.makeText(this, t.message ?: "VR could not start.", Toast.LENGTH_LONG).show()
                    // A failed VR launch must return to the headset menu, not keep
                    // a hidden flat window and its launch spinner alive forever.
                    finish()
                }
            }
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
