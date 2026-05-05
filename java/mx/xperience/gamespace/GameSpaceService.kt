/*
 * Copyright (C) 2026 The XPerience Project
 * SPDX-License-Identifier: Apache-2.0
 */
package mx.xperience.gamespace

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import mx.xperience.gamespace.controller.PerformanceController

/**
 * Main GameSpace foreground service.
 * Responsible for overlay lifecycle and service persistence.
 */
class GameSpaceService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var overlayView: View
    private lateinit var triggerView: View

    private lateinit var controller: PerformanceController

    override fun onCreate() {
        super.onCreate()

        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        controller = PerformanceController(this)

        controller.onRequestShowTrigger = { showTrigger() }

        createNotificationChannel()
        startForeground(1001, buildNotification())

        initOverlay()
        initTrigger()

        controller.startForegroundMonitoring { packageName ->
            if (isPackageAGame(packageName)) {
                controller.onGameEnter(packageName)
                if (overlayView.visibility != View.VISIBLE) {
                    triggerView.visibility = View.VISIBLE
                }
            } else {
                controller.onGameExit()
                triggerView.visibility = View.GONE
                overlayView.visibility = View.GONE
            }
        }

    }

    private fun isPackageAGame(packageName: String): Boolean {
        return try {
            val appInfo = packageManager.getApplicationInfo(packageName, PackageManager.GET_META_DATA)
            val isSystemGame = appInfo.category == ApplicationInfo.CATEGORY_GAME ||
            (appInfo.flags and ApplicationInfo.FLAG_IS_GAME) != 0

            // verify if was added manually
            val prefs = getSharedPreferences("gamespace_prefs", Context.MODE_PRIVATE)
            val manualGames = prefs.getStringSet("manual_games", emptySet()) ?: emptySet()
            val isManualGame = manualGames.contains(packageName)

            // If either of these conditions is met, we display the overlay
            isSystemGame || isManualGame
        } catch (e: Exception) {
            false
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    /**
     * Inflates and attaches the main GameSpace panel overlay.
     */
    private fun initOverlay() {
        overlayView = LayoutInflater.from(this)
        .inflate(R.layout.overlay_game_panel_original, null)

        controller.bindOverlay(overlayView)
        windowManager.addView(overlayView, controller.windowParams)

        overlayView.setOnTouchListener { v, event ->
            if (event.action == MotionEvent.ACTION_DOWN) {
                val panelBackground = overlayView.findViewById<View>(R.id.panel_background)
                val location = IntArray(2)
                panelBackground.getLocationOnScreen(location)
                val panelRect = android.graphics.Rect(
                    location[0],
                    location[1],
                    location[0] + panelBackground.width,
                    location[1] + panelBackground.height
                )

                if (!panelRect.contains(event.rawX.toInt(), event.rawY.toInt())) {
                    // Toque fuera del panel → cerrar
                    overlayView.visibility = View.GONE
                    triggerView.visibility = View.VISIBLE
                    return@setOnTouchListener true
                }
            }
            false
        }

        overlayView.visibility = View.GONE
    }

    /**
     * Inflates and attaches the floating trigger overlay.
     */
    private fun initTrigger() {
        triggerView = LayoutInflater.from(this)
            .inflate(R.layout.overlay_game_trigger, null)

        // Crear parámetros con la posición guardada
       // triggerWindowParams = controller.createTriggerParams()
        controller.triggerWindowParams = controller.createTriggerParams()
        windowManager.addView(triggerView, controller.triggerWindowParams)
        triggerView.visibility = View.GONE

        triggerView.setOnClickListener {
            overlayView.visibility = View.VISIBLE
            triggerView.visibility = View.GONE
            controller.onPanelOpened()
        }

        controller.enableTriggerDrag(triggerView, windowManager)

    }

    private fun showTrigger() {
        triggerView?.apply {
            visibility = View.VISIBLE
            alpha = 0f
            animate()
            .alpha(1f)
            .setDuration(200)
            .start()
        }
    }

    override fun onDestroy() {
        controller.release()

        if (::overlayView.isInitialized) {
            windowManager.removeView(overlayView)
        }
        if (::triggerView.isInitialized) {
            windowManager.removeView(triggerView)
        }

        super.onDestroy()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = getString(R.string.gs_channel_name)
            val channel = NotificationChannel(
                "gamespace",
                name,
                NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java)
                .createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        return Notification.Builder(this, "gamespace")
            .setContentTitle(getString(R.string.gs_notification_title))
            .setContentText(getString(R.string.gs_notification_text))
            .setSmallIcon(R.drawable.ic_game_controller)
            .build()
    }
}
