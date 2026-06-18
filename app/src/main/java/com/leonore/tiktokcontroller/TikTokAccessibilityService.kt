package com.leonore.tiktokcontroller

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.TextView

class TikTokAccessibilityService : AccessibilityService() {
    private lateinit var windowManager: WindowManager
    private var overlayView: View? = null
    private var isOverlayShowing = false
    private val handler = Handler(Looper.getMainLooper())

    companion object {
        private var instance: TikTokAccessibilityService? = null
        private var likeButtonBounds: android.graphics.Rect? = null

        fun performSwipeUp() {
            instance?.performSwipe(0.5f, 0.8f, 0.5f, 0.2f)
        }

        fun performSwipeDown() {
            instance?.performSwipe(0.5f, 0.2f, 0.5f, 0.8f)
        }

        fun performLike() {
            instance?.performTapOnLikeButton()
        }
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        showOverlay()
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        showOverlay()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event?.let {
            if (it.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED ||
                it.eventType == AccessibilityEvent.TYPE_VIEW_CLICKED) {
                findLikeButton()
            }
        }
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        removeOverlay()
    }

    private fun performSwipe(startX: Float, startY: Float, endX: Float, endY: Float) {
        val screenWidth = resources.displayMetrics.widthPixels
        val screenHeight = resources.displayMetrics.heightPixels

        val path = Path()
        path.moveTo(screenWidth * startX, screenHeight * startY)
        path.lineTo(screenWidth * endX, screenHeight * endY)

        val builder = GestureDescription.Builder()
        builder.addStroke(GestureDescription.StrokeDescription(path, 0, 300))

        val gesture = builder.build()
        dispatchGesture(gesture, null, null)
    }

    private fun performTapOnLikeButton() {
        likeButtonBounds?.let { bounds ->
            val x = bounds.centerX().toFloat()
            val y = bounds.centerY().toFloat()
            performTap(x, y)
        } ?: run {
            findLikeButton()
        }
    }

    private fun performTap(x: Float, y: Float) {
        val path = Path()
        path.moveTo(x, y)

        val builder = GestureDescription.Builder()
        builder.addStroke(GestureDescription.StrokeDescription(path, 0, 100))

        val gesture = builder.build()
        dispatchGesture(gesture, null, null)
    }

    private fun findLikeButton() {
        val root = rootInActiveWindow ?: return

        root.findAccessibilityNodeInfosByText("Like")?.forEach { node ->
            if (node.isClickable) {
                val rect = android.graphics.Rect()
                node.getBoundsInScreen(rect)
                if (rect.width() > 0 && rect.height() > 0) {
                    likeButtonBounds = rect
                    return
                }
            }
        }

        root.findAccessibilityNodeInfosByViewId("com.zhiliaoapp.musically:id/mj")?.forEach { node ->
            val rect = android.graphics.Rect()
            node.getBoundsInScreen(rect)
            if (rect.width() > 0 && rect.height() > 0) {
                likeButtonBounds = rect
            }
        }
    }

    private fun showOverlay() {
        if (isOverlayShowing) return

        try {
            val inflater = LayoutInflater.from(this)
            overlayView = inflater.inflate(R.layout.overlay_service, null)

            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                else
                    WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
            )

            params.gravity = Gravity.TOP or Gravity.START
            params.x = 10
            params.y = 100

            windowManager.addView(overlayView, params)
            isOverlayShowing = true

            updateOverlayStatus("Service Active")
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun removeOverlay() {
        try {
            overlayView?.let {
                windowManager.removeView(it)
                overlayView = null
                isOverlayShowing = false
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun updateOverlayStatus(status: String) {
        handler.post {
            overlayView?.findViewById<TextView>(R.id.overlay_status)?.text = status
        }
    }
}
