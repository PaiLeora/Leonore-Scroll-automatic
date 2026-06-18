package com.leonore.tiktokcontroller

import kotlinx.coroutines.*

class AutoScrollManager {
    private var job: Job? = null
    private var isRunning = false
    private val scrollInterval = 5000L

    fun start() {
        if (isRunning) return
        
        isRunning = true
        job = CoroutineScope(Dispatchers.Main).launch {
            while (isRunning) {
                TikTokAccessibilityService.performSwipeUp()
                delay(scrollInterval)
            }
        }
    }

    fun stop() {
        isRunning = false
        job?.cancel()
        job = null
    }

    fun isRunning(): Boolean = isRunning
}
