/*
 * Copyright (C) 2026 The XPerience Project
 * SPDX-License-Identifier: Apache-2.0
 */
package mx.xperience.gamespace.utils

import android.app.ActivityTaskManager
import android.content.Context
import android.view.Choreographer
import android.view.FrameMetrics
import android.view.Window
import android.view.WindowManager
import android.window.TaskFpsCallback
import java.io.File
import kotlin.math.roundToInt

/**
 * FPSMonitor – gives you the *actual* rendering rate of the game,
 * not the UI thread's pulse or the panel's max refresh rate.
 *
 * Priority order for Android 17:
 *   1. TaskFpsCallback – system API, per‑task FPS (most accurate).
 *   2. FrameMetrics (API 24+) – frame duration of a given Window.
 *   3. Choreographer – UI thread tick, last resort (not GPU‑aware).
 *
 * Sysfs is intentionally NOT used for game FPS because it often reports
 * the panel's max refresh rate (e.g., 240) instead of actual rendered frames.
 * We keep it only as a debug helper (getPanelRefreshRate()).
 */
class FPSMonitor(private val context: Context) {

    // ------------------------------------------------------------------------
    // 1. TaskFpsCallback – primary source (system API)
    // ------------------------------------------------------------------------
    private val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val taskManager = ActivityTaskManager.getService()

    private var taskFps = 60
    private var taskFpsReady = false

    private val taskFpsCallback = object : TaskFpsCallback() {
        override fun onFpsReported(fps: Float) {
            taskFps = fps.roundToInt().coerceIn(10, 240)
            taskFpsReady = true
        }
    }

    // ------------------------------------------------------------------------
    // 2. FrameMetrics – fallback (API 24+)
    // ------------------------------------------------------------------------
    private var attachedWindow: Window? = null
    private var frameMetricsFps = 60
    private var frameMetricsReady = false

    private val frameMetricsListener = Window.OnFrameMetricsAvailableListener { _, frameMetrics, _ ->
        val totalDuration = frameMetrics.getMetric(FrameMetrics.TOTAL_DURATION)
        if (totalDuration > 0) {
            val fps = (1_000_000_000.0 / totalDuration).roundToInt()
            frameMetricsFps = fps.coerceIn(10, 240)
            frameMetricsReady = true
        }
    }

    // ------------------------------------------------------------------------
    // 3. Choreographer – last resort (UI thread only)
    // ------------------------------------------------------------------------
    private var lastFrameTimeNs = 0L
    private var frameCount = 0
    private var choreoFps = 60

    private val frameCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            if (lastFrameTimeNs > 0) {
                frameCount++
                val delta = frameTimeNanos - lastFrameTimeNs
                if (delta >= 1_000_000_000L) {
                    val fps = (frameCount * 1_000_000_000.0 / delta).roundToInt()
                    choreoFps = fps.coerceIn(30, 240)
                    frameCount = 0
                    lastFrameTimeNs = frameTimeNanos
                }
            } else {
                lastFrameTimeNs = frameTimeNanos
            }
            Choreographer.getInstance().postFrameCallback(this)
        }
    }

    // ------------------------------------------------------------------------
    // Public API – start / stop
    // ------------------------------------------------------------------------

    /**
     * Start monitoring FPS.
     * Tries TaskFpsCallback first; if that fails, falls back to FrameMetrics
     * (requires a Window) and finally Choreographer.
     *
     * @param window Optional Window for FrameMetrics fallback.
     *               If null, only TaskFpsCallback and Choreographer are used.
     */
    fun start(window: Window? = null) {
        // 1. Try TaskFpsCallback.
        val taskId = taskManager?.focusedRootTaskInfo?.taskId
        if (taskId != null) {
            try {
                wm.registerTaskFpsCallback(taskId, Runnable::run, taskFpsCallback)
                return // We're done – the callback will push FPS.
            } catch (e: Exception) {
                // Fall through to FrameMetrics.
            }
        }

        // 2. If we have a Window, attach FrameMetrics.
        if (window != null) {
            attachedWindow = window
            window.addOnFrameMetricsAvailableListener(frameMetricsListener, null)
            frameMetricsReady = false
            // Also start Choreographer as a safety net.
            startChoreographer()
            return
        }

        // 3. Last resort: Choreographer only.
        startChoreographer()
    }

    fun stop() {
        // Unregister everything.
        runCatching { wm.unregisterTaskFpsCallback(taskFpsCallback) }
        attachedWindow?.removeOnFrameMetricsAvailableListener(frameMetricsListener)
        attachedWindow = null
        frameMetricsReady = false
        stopChoreographer()
        taskFpsReady = false
    }

    // ------------------------------------------------------------------------
    // The one method you actually care about: get the current FPS.
    // ------------------------------------------------------------------------

    /**
     * Returns the most reliable FPS we can get, in this order:
     *   - TaskFpsCallback (if active and we've received a frame).
     *   - FrameMetrics (if we have a Window and it's produced at least one frame).
     *   - Choreographer (UI thread approximation – better than nothing).
     */
    fun getCurrentFps(): Int {
        // 1. TaskFpsCallback – the golden path.
        if (taskFpsReady) {
            return taskFps
        }

        // 2. FrameMetrics – solid fallback.
        if (frameMetricsReady && attachedWindow != null) {
            return frameMetricsFps
        }

        // 3. Choreographer – meh, but it's something.
        return if (choreoFps > 0) choreoFps else 60
    }

    // ------------------------------------------------------------------------
    // Internal helpers for Choreographer
    // ------------------------------------------------------------------------

    private fun startChoreographer() {
        lastFrameTimeNs = 0
        frameCount = 0
        Choreographer.getInstance().postFrameCallback(frameCallback)
    }

    private fun stopChoreographer() {
        Choreographer.getInstance().removeFrameCallback(frameCallback)
    }

    // ------------------------------------------------------------------------
    // Sysfs – kept ONLY for informational purposes (panel refresh rate).
    // NEVER mixed with game FPS.
    // ------------------------------------------------------------------------

    private val advancedFpsPath = "/sys/class/drm/sde-crtc-0/measured_fps"
    private val fallbackPaths = arrayOf(
        "/sys/class/graphics/fb0/fps",
        "/sys/class/drm/card0/sde_crtc_fps"
    )

    private fun readFpsFromSysfs(path: String): Int? {
        return try {
            val file = File(path)
            if (!file.exists()) return null
            val value = file.readText().trim().toFloat().roundToInt()
            if (value in 10..240) value else null
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Debug helper: reads the panel's reported refresh rate from sysfs.
     * This is NOT the game's FPS, just the display's max capability.
     * Use this only for UI info, never for performance throttling.
     */
    fun getPanelRefreshRate(): Int {
        readFpsFromSysfs(advancedFpsPath)?.let { return it }
        for (path in fallbackPaths) {
            readFpsFromSysfs(path)?.let { return it }
        }
        return 60 // fallback
    }
}