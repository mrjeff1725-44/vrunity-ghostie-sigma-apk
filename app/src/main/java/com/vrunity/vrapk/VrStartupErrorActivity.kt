package com.vrunity.vrapk

import android.app.Activity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

class VrStartupErrorActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val enginePid = intent.getIntExtra("enginePid", -1)
        if (enginePid > 0 && enginePid != Process.myPid()) Process.killProcess(enginePid)
        VrLaunchFailure.success(this)
        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.gravity = Gravity.CENTER
        root.setPadding(32, 32, 32, 32)
        val heading = TextView(this)
        heading.text = "VR startup stopped"
        heading.textSize = 22f
        root.addView(heading)
        val message = TextView(this)
        message.text = intent.getStringExtra("reason") ?: "Unable to start the headset session."
        message.textSize = 16f
        message.setPadding(0, 24, 0, 24)
        root.addView(message)
        val close = Button(this)
        close.text = "Close"
        close.setOnClickListener {
            finishAffinity()
            Handler(Looper.getMainLooper()).postDelayed({ Process.killProcess(Process.myPid()) }, 300L)
        }
        root.addView(close)
        val scroll = ScrollView(this)
        scroll.isFillViewport = true
        scroll.addView(root)
        setContentView(scroll)
    }
}
