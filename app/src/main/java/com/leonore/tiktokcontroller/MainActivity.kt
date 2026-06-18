package com.leonore.tiktokcontroller

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.leonore.tiktokcontroller.databinding.ActivityMainBinding
import kotlinx.coroutines.*

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private var speechRecognizer: SpeechRecognizer? = null
    private var isListening = false
    private var isAccessibilityEnabled = false
    private var isAutoScrollRunning = false
    private val autoScrollManager = AutoScrollManager()
    private val REQUEST_RECORD_AUDIO = 1
    private val REQUEST_OVERLAY_PERMISSION = 2

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupUI()
        checkPermissions()
        checkAccessibilityStatus()
    }

    private fun setupUI() {
        binding.btnStartService.setOnClickListener {
            if (!isAccessibilityEnabled) {
                openAccessibilitySettings()
            } else {
                Toast.makeText(this, "Accessibility Service already enabled", Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnToggleAutoScroll.setOnClickListener {
            if (isAutoScrollRunning) {
                stopAutoScroll()
            } else {
                startAutoScroll()
            }
        }

        binding.btnToggleVoiceControl.setOnClickListener {
            if (isListening) {
                stopVoiceControl()
            } else {
                startVoiceControl()
            }
        }

        binding.btnOpenTikTok.setOnClickListener {
            val intent = packageManager.getLaunchIntentForPackage("com.zhiliaoapp.musically")
            if (intent != null) {
                startActivity(intent)
            } else {
                Toast.makeText(this, "TikTok not installed", Toast.LENGTH_SHORT).show()
            }
        }

        GlobalScope.launch(Dispatchers.Main) {
            while (true) {
                updateStatus()
                delay(1000)
            }
        }
    }

    private fun updateStatus() {
        checkAccessibilityStatus()
        binding.statusAccessibility.text = if (isAccessibilityEnabled) 
            "Accessibility: Active" else "Accessibility: Inactive"
        
        binding.statusVoice.text = if (isListening) 
            "Voice Control: Active" else "Voice Control: Inactive"
        
        binding.statusAutoScroll.text = if (isAutoScrollRunning) 
            "Auto-Scroll: Running" else "Auto-Scroll: Stopped"
    }

    private fun checkAccessibilityStatus() {
        try {
            val enabledServices = Settings.Secure.getString(
                contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            )
            isAccessibilityEnabled = enabledServices?.contains("com.leonore.tiktokcontroller") == true
        } catch (e: Exception) {
            isAccessibilityEnabled = false
        }
    }

    private fun openAccessibilitySettings() {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        startActivity(intent)
        Toast.makeText(this, "Enable Leonore TikTok Controller from Accessibility", Toast.LENGTH_LONG).show()
    }

    private fun checkPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!Settings.canDrawOverlays(this)) {
                val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
                startActivityForResult(intent, REQUEST_OVERLAY_PERMISSION)
            }
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.RECORD_AUDIO),
                REQUEST_RECORD_AUDIO
            )
        }
    }

    private fun startAutoScroll() {
        if (!isAccessibilityEnabled) {
            Toast.makeText(this, "Please enable Accessibility Service first", Toast.LENGTH_SHORT).show()
            return
        }
        autoScrollManager.start()
        isAutoScrollRunning = true
        binding.btnToggleAutoScroll.text = "Stop Auto-Scroll"
        Toast.makeText(this, "Auto-scroll started", Toast.LENGTH_SHORT).show()
    }

    private fun stopAutoScroll() {
        autoScrollManager.stop()
        isAutoScrollRunning = false
        binding.btnToggleAutoScroll.text = "Start Auto-Scroll"
        Toast.makeText(this, "Auto-scroll stopped", Toast.LENGTH_SHORT).show()
    }

    private fun startVoiceControl() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "Microphone permission required", Toast.LENGTH_SHORT).show()
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "Overlay permission required for voice control", Toast.LENGTH_SHORT).show()
            return
        }

        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        speechRecognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                binding.voiceStatus.text = "Listening..."
            }

            override fun onBeginningOfSpeech() {}

            override fun onRmsChanged(rmsdB: Float) {}

            override fun onBufferReceived(buffer: ByteArray?) {}

            override fun onEndOfSpeech() {
                binding.voiceStatus.text = "Processing..."
            }

            override fun onError(error: Int) {
                binding.voiceStatus.text = "Error: $error"
                isListening = false
                binding.btnToggleVoiceControl.text = "Start Voice Control"
                startVoiceControl()
            }

            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!matches.isNullOrEmpty()) {
                    val command = matches[0].lowercase()
                    handleVoiceCommand(command)
                }
                startVoiceControl()
            }

            override fun onPartialResults(partialResults: Bundle?) {}

            override fun onEvent(eventType: Int, params: Bundle?) {}
        })

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-US")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
        }

        speechRecognizer?.startListening(intent)
        isListening = true
        binding.btnToggleVoiceControl.text = "Stop Voice Control"
        binding.voiceStatus.text = "Voice Control Active"
        Toast.makeText(this, "Voice control started", Toast.LENGTH_SHORT).show()
    }

    private fun stopVoiceControl() {
        speechRecognizer?.stopListening()
        speechRecognizer?.destroy()
        speechRecognizer = null
        isListening = false
        binding.btnToggleVoiceControl.text = "Start Voice Control"
        binding.voiceStatus.text = "Voice Control Inactive"
        Toast.makeText(this, "Voice control stopped", Toast.LENGTH_SHORT).show()
    }

    private fun handleVoiceCommand(command: String) {
        when {
            command.contains("up") || command.contains("next") -> {
                TikTokAccessibilityService.performSwipeUp()
                Toast.makeText(this, "Command: Next", Toast.LENGTH_SHORT).show()
            }
            command.contains("down") || command.contains("previous") || command.contains("back") -> {
                TikTokAccessibilityService.performSwipeDown()
                Toast.makeText(this, "Command: Previous", Toast.LENGTH_SHORT).show()
            }
            command.contains("like") -> {
                TikTokAccessibilityService.performLike()
                Toast.makeText(this, "Command: Like", Toast.LENGTH_SHORT).show()
            }
            command.contains("pause") -> {
                if (isAutoScrollRunning) stopAutoScroll()
                Toast.makeText(this, "Command: Pause", Toast.LENGTH_SHORT).show()
            }
            command.contains("resume") -> {
                if (!isAutoScrollRunning) startAutoScroll()
                Toast.makeText(this, "Command: Resume", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            REQUEST_RECORD_AUDIO -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Toast.makeText(this, "Microphone permission granted", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Microphone permission required for voice control", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        speechRecognizer?.destroy()
        autoScrollManager.stop()
    }
}
