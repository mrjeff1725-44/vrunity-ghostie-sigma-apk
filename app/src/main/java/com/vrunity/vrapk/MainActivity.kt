package com.vrunity.vrapk

import android.app.Activity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.SystemClock
import android.util.Log
import android.view.KeyEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.WindowManager
import android.widget.Toast

class MainActivity : Activity(), SurfaceHolder.Callback {
    private var surface: VrSurfaceView? = null
    private var headset = false
    private var resumed = false
    private var windowReady = false
    private var started = false
    private var closing = false
    @Volatile private var libraryReady = false
    private var waitingSince = 0L
    private val handler = Handler(Looper.getMainLooper())
    private val stages = arrayOf(
        "Loading native renderer", "Initializing VR loader", "Creating VR instance",
        "Finding headset", "Creating graphics context", "Opening VR session",
        "Preparing eye images", "Waiting for headset session READY",
        "Waiting for first headset frame", "Waiting for valid headset tracking",
        "Waiting for eye images", "Submitting first VR frame", "VR running"
    )
    private val startupWatch = object : Runnable {
        override fun run() {
            if (closing || isDestroyed) return
            val state = if (libraryReady) Xr.startupState() else 0
            if (state == 12) return
            if (!resumed) waitingSince = SystemClock.elapsedRealtime()
            if (SystemClock.elapsedRealtime() - waitingSince >= 30000L) {
                failLaunch("VR startup stopped: " + stages.getOrElse(state) { "Unknown runtime state" })
                return
            }
            handler.postDelayed(this, 1000L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        headset = packageManager.hasSystemFeature("android.hardware.vr.headtracking")
        if (headset) {
            val view = SurfaceView(this)
            view.holder.addCallback(this)
            setContentView(view)
        } else {
            startScreenMode()
            windowReady = true
        }
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        windowReady = holder.surface.isValid
        startWhenReady()
    }
    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        windowReady = holder.surface.isValid && width > 0 && height > 0
        startWhenReady()
    }
    override fun surfaceDestroyed(holder: SurfaceHolder) { windowReady = false }

    private fun startWhenReady() {
        if (!resumed || !windowReady || started || closing) return
        started = true
        waitingSince = SystemClock.elapsedRealtime()
        if (headset) handler.postDelayed(startupWatch, 1000L)
        Thread({
            try {
                Xr.load()
                libraryReady = true
                val result = XrSession(this).run()
                if (headset) runOnUiThread {
                    if (result == 2) finish() else failLaunch("Unable to open the headset VR runtime.")
                }
            } catch (t: Throwable) {
                Log.e("VRUnityXR", "VR launch failed", t)
                if (headset) runOnUiThread { failLaunch(t.message ?: "VR startup failed.") }
            }
        }, "VRUnity-XR").start()
    }

    private fun failLaunch(message: String) {
        if (closing || isDestroyed) return
        closing = true
        handler.removeCallbacks(startupWatch)
        Log.e("VRUnityXR", message)
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        finishAndRemoveTask()
        // A native runtime call can be blocked. Do not tear down its session from
        // a second thread: exit this standalone game process so a relaunch is clean.
        handler.postDelayed({ Process.killProcess(Process.myPid()) }, 350L)
    }

    private fun startScreenMode() {
        if (surface != null) return
        val view = VrSurfaceView(this)
        surface = view
        setContentView(view)
        fullscreen()
    }
    override fun onResume() {
        super.onResume()
        resumed = true
        surface?.onResume()
        surface?.startSensors()
        fullscreen()
        startWhenReady()
    }
    override fun onPause() {
        resumed = false
        surface?.stopSensors()
        surface?.onPause()
        super.onPause()
    }
    override fun onDestroy() {
        closing = true
        handler.removeCallbacks(startupWatch)
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
