package com.example.service

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Coordinates communication between:
 * - The UI / Settings
 * - VoxStreamAccessibilityService (detects text field focus & injects text)
 * - FloatingBubbleService (renders the floating 🛟 overlay & handles dictation)
 */
object FloatingBubbleManager {
    private const val TAG = "FloatingBubbleManager"
    private const val PREFS_NAME = "voxstream_bubble_prefs"
    private const val KEY_BUBBLE_ENABLED = "bubble_enabled"
    private const val KEY_GLOW_STYLE = "glow_animation_style"
    const val DEFAULT_GLOW_STYLE_ID = "gemini_live"

    private val _isBubbleEnabled = MutableStateFlow(false)
    val isBubbleEnabled: StateFlow<Boolean> = _isBubbleEnabled.asStateFlow()

    private val _selectedGlowStyleId = MutableStateFlow(DEFAULT_GLOW_STYLE_ID)
    val selectedGlowStyleId: StateFlow<String> = _selectedGlowStyleId.asStateFlow()

    private val _isAccessibilityConnected = MutableStateFlow(false)
    val isAccessibilityConnected: StateFlow<Boolean> = _isAccessibilityConnected.asStateFlow()

    private val _isFieldActive = MutableStateFlow(false)
    val isFieldActive: StateFlow<Boolean> = _isFieldActive.asStateFlow()

    private val _isKeyboardVisible = MutableStateFlow(false)
    val isKeyboardVisible: StateFlow<Boolean> = _isKeyboardVisible.asStateFlow()

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    private var initialized = false

    fun init(context: Context) {
        if (initialized) return
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        _isBubbleEnabled.value = prefs.getBoolean(KEY_BUBBLE_ENABLED, false)
        _selectedGlowStyleId.value = prefs.getString(KEY_GLOW_STYLE, DEFAULT_GLOW_STYLE_ID) ?: DEFAULT_GLOW_STYLE_ID
        initialized = true

        if (_isBubbleEnabled.value && canDrawOverlays(context)) {
            startBubbleService(context)
        }
    }

    fun setGlowStyle(context: Context, styleId: String) {
        _selectedGlowStyleId.value = styleId
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_GLOW_STYLE, styleId).apply()
    }

    fun canDrawOverlays(context: Context): Boolean {
        return Settings.canDrawOverlays(context)
    }

    fun setBubbleEnabled(context: Context, enabled: Boolean) {
        _isBubbleEnabled.value = enabled
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_BUBBLE_ENABLED, enabled).apply()

        if (enabled) {
            if (canDrawOverlays(context)) {
                startBubbleService(context)
            }
        } else {
            stopBubbleService(context)
        }
    }

    fun startBubbleService(context: Context) {
        try {
            val intent = Intent(context, FloatingBubbleService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start FloatingBubbleService", e)
        }
    }

    fun stopBubbleService(context: Context) {
        try {
            val intent = Intent(context, FloatingBubbleService::class.java)
            context.stopService(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to stop FloatingBubbleService", e)
        }
    }

    fun setAccessibilityConnected(connected: Boolean) {
        _isAccessibilityConnected.value = connected
    }

    fun notifyFieldFocus(isFocused: Boolean) {
        _isFieldActive.value = isFocused
        FloatingBubbleService.instance?.onFieldFocusChanged(isFocused)
    }

    fun notifyKeyboardVisibility(visible: Boolean) {
        _isKeyboardVisible.value = visible
        Log.d(TAG, "notifyKeyboardVisibility: $visible")
        FloatingBubbleService.instance?.onKeyboardVisibilityChanged(visible)
    }

    fun setRecordingState(recording: Boolean) {
        _isRecording.value = recording
    }

    /**
     * Attempts to inject text into the currently active text field via AccessibilityService.
     * If no active text field exists or injection fails, copies text to clipboard with a Toast.
     */
    fun injectOrFallbackToClipboard(context: Context, text: String): Boolean {
        if (text.isBlank()) return false

        val injected = VoxStreamAccessibilityService.instance?.injectText(text) ?: false
        val mainHandler = Handler(Looper.getMainLooper())

        if (injected) {
            mainHandler.post {
                Toast.makeText(context, "Text inserted into active field!", Toast.LENGTH_SHORT).show()
            }
            return true
        } else {
            // Fallback to clipboard
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            if (clipboard != null) {
                val clip = ClipData.newPlainText("VoxStream Transcription", text)
                clipboard.setPrimaryClip(clip)
            }
            mainHandler.post {
                Toast.makeText(
                    context,
                    "Copied to clipboard (no active text field found)",
                    Toast.LENGTH_LONG
                ).show()
            }
            return false
        }
    }
}
