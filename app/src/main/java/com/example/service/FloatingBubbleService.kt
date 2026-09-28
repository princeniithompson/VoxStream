package com.example.service

import android.Manifest
import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.StyleSpan
import android.util.DisplayMetrics
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.WindowManager
import android.view.animation.LinearInterpolator
import android.view.animation.OvershootInterpolator
import android.view.animation.PathInterpolator
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.core.animation.addListener
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.example.BuildConfig
import com.example.MainActivity
import com.example.R
import com.example.audio.AudioRecorder
import com.example.data.AppLogRepository
import com.example.data.ConnectionState
import com.example.data.LogLevel
import com.example.websocket.GeminiLiveWebSocketClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs

class FloatingBubbleService : Service(), androidx.lifecycle.LifecycleOwner, androidx.savedstate.SavedStateRegistryOwner, androidx.lifecycle.ViewModelStoreOwner {

    companion object {
        private const val TAG = "FloatingBubbleService"
        private const val CHANNEL_ID = "voxstream_floating_channel"
        private const val NOTIFICATION_ID = 2001

        var instance: FloatingBubbleService? = null
            private set
    }

    private val lifecycleRegistry = androidx.lifecycle.LifecycleRegistry(this)
    private val savedStateRegistryController = androidx.savedstate.SavedStateRegistryController.create(this)
    private val store = androidx.lifecycle.ViewModelStore()

    override val lifecycle: androidx.lifecycle.Lifecycle get() = lifecycleRegistry
    override val savedStateRegistry: androidx.savedstate.SavedStateRegistry get() = savedStateRegistryController.savedStateRegistry
    override val viewModelStore: androidx.lifecycle.ViewModelStore get() = store

    private val overlayTranscript = kotlinx.coroutines.flow.MutableStateFlow("")
    private val overlayRecording = kotlinx.coroutines.flow.MutableStateFlow(false)
    private val overlayPendingFinalizing = kotlinx.coroutines.flow.MutableStateFlow(false)
    private val overlayPolishing = kotlinx.coroutines.flow.MutableStateFlow(false)
    private val overlayExpanded = kotlinx.coroutines.flow.MutableStateFlow(false)
    private val overlayShrunk = kotlinx.coroutines.flow.MutableStateFlow(false)
    private val overlayAudioAmplitude = kotlinx.coroutines.flow.MutableStateFlow(0f)

    enum class SettlePosition { TOP, BOTTOM }
    private var currentSettlePosition = SettlePosition.BOTTOM

    private enum class HapticFeedbackType {
        TRANSCRIPTION_START,
        FINAL_SENTENCE,
        TRANSCRIPTION_STOP,
        BUBBLE_HOLD,
        BUBBLE_MOVE,
        BUBBLE_SETTLE
    }

    private fun triggerHapticFeedback(type: HapticFeedbackType) {
        try {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vibratorManager?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            } ?: return

            if (!vibrator.hasVibrator()) return

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val effect = when (type) {
                    HapticFeedbackType.TRANSCRIPTION_START -> {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK)
                        } else {
                            VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE)
                        }
                    }
                    HapticFeedbackType.FINAL_SENTENCE -> {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK)
                        } else {
                            VibrationEffect.createOneShot(20, 150)
                        }
                    }
                    HapticFeedbackType.TRANSCRIPTION_STOP -> {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            VibrationEffect.createPredefined(VibrationEffect.EFFECT_DOUBLE_CLICK)
                        } else {
                            VibrationEffect.createWaveform(longArrayOf(0, 40, 40, 40), -1)
                        }
                    }
                    HapticFeedbackType.BUBBLE_HOLD -> {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK)
                        } else {
                            VibrationEffect.createOneShot(30, VibrationEffect.DEFAULT_AMPLITUDE)
                        }
                    }
                    HapticFeedbackType.BUBBLE_MOVE -> {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK)
                        } else {
                            VibrationEffect.createOneShot(12, 100)
                        }
                    }
                    HapticFeedbackType.BUBBLE_SETTLE -> {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK)
                        } else {
                            VibrationEffect.createOneShot(40, 180)
                        }
                    }
                }
                vibrator.vibrate(effect)
            } else {
                @Suppress("DEPRECATION")
                val millis = when (type) {
                    HapticFeedbackType.TRANSCRIPTION_START -> 50L
                    HapticFeedbackType.FINAL_SENTENCE -> 20L
                    HapticFeedbackType.TRANSCRIPTION_STOP -> 60L
                    HapticFeedbackType.BUBBLE_HOLD -> 30L
                    HapticFeedbackType.BUBBLE_MOVE -> 12L
                    HapticFeedbackType.BUBBLE_SETTLE -> 40L
                }
                vibrator.vibrate(millis)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error executing haptic feedback vibration", e)
        }
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val mainHandler = Handler(Looper.getMainLooper())

    private var windowManager: WindowManager? = null
    private var overlayView: View? = null
    private var layoutParams: WindowManager.LayoutParams? = null

    // UI elements
    private var rootContainer: LinearLayout? = null
    private var bubbleImageView: ImageView? = null
    private var expandedPanel: LinearLayout? = null
    private var statusDot: View? = null
    private var tvStatus: TextView? = null
    private var tvDuration: TextView? = null
    private var tvTranscript: TextView? = null
    private var scrollTranscript: ScrollView? = null
    private var btnCancel: Button? = null
    private var btnPolish: Button? = null
    private var btnConfirm: Button? = null

    // Dynamic Color & State Transition Animation
    private var panelBackgroundDrawable: GradientDrawable? = null
    private var containerBackgroundDrawable: GradientDrawable? = null
    private var colorShiftAnimator: ValueAnimator? = null
    private var currentTransitionProgress: Float = 0f
    private val argbEvaluator = ArgbEvaluator()

    private data class BubbleColorPalette(
        val idleCardBg: Int,
        val idleCardStroke: Int,
        val activeCardBg: Int,
        val activeCardStroke: Int,
        val idleContainerAura: Int,
        val activeContainerAura: Int,
        val statusDotActive: Int,
        val statusDotIdle: Int,
        val textPrimary: Int,
        val textSecondary: Int
    )

    private fun resolveDynamicPalette(): BubbleColorPalette {
        val isNight = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (isNight) {
                val neutralDark = ContextCompat.getColor(this, android.R.color.system_neutral1_900)
                val neutralContainer = ContextCompat.getColor(this, android.R.color.system_neutral1_800)
                val neutralStroke = ContextCompat.getColor(this, android.R.color.system_neutral2_700)
                val accentDark = ContextCompat.getColor(this, android.R.color.system_accent1_900)
                val accentPrimary = ContextCompat.getColor(this, android.R.color.system_accent1_300)
                val accentStroke = ContextCompat.getColor(this, android.R.color.system_accent1_400)
                val textOnSurface = ContextCompat.getColor(this, android.R.color.system_neutral1_100)
                val textVariant = ContextCompat.getColor(this, android.R.color.system_neutral2_200)

                val blendedActiveBg = ColorUtils.blendARGB(neutralContainer, accentDark, 0.65f)

                BubbleColorPalette(
                    idleCardBg = neutralContainer,
                    idleCardStroke = neutralStroke,
                    activeCardBg = blendedActiveBg,
                    activeCardStroke = accentStroke,
                    idleContainerAura = 0x00000000,
                    activeContainerAura = ColorUtils.setAlphaComponent(accentPrimary, 38),
                    statusDotActive = accentPrimary,
                    statusDotIdle = neutralStroke,
                    textPrimary = textOnSurface,
                    textSecondary = textVariant
                )
            } else {
                val neutralLight = ContextCompat.getColor(this, android.R.color.system_neutral1_50)
                val neutralContainer = ContextCompat.getColor(this, android.R.color.system_neutral1_100)
                val neutralStroke = ContextCompat.getColor(this, android.R.color.system_neutral2_200)
                val accentLight = ContextCompat.getColor(this, android.R.color.system_accent1_100)
                val accentPrimary = ContextCompat.getColor(this, android.R.color.system_accent1_600)
                val accentStroke = ContextCompat.getColor(this, android.R.color.system_accent1_500)
                val textOnSurface = ContextCompat.getColor(this, android.R.color.system_neutral1_900)
                val textVariant = ContextCompat.getColor(this, android.R.color.system_neutral2_700)

                val blendedActiveBg = ColorUtils.blendARGB(neutralContainer, accentLight, 0.70f)

                BubbleColorPalette(
                    idleCardBg = neutralContainer,
                    idleCardStroke = neutralStroke,
                    activeCardBg = blendedActiveBg,
                    activeCardStroke = accentStroke,
                    idleContainerAura = 0x00000000,
                    activeContainerAura = ColorUtils.setAlphaComponent(accentPrimary, 40),
                    statusDotActive = accentPrimary,
                    statusDotIdle = neutralStroke,
                    textPrimary = textOnSurface,
                    textSecondary = textVariant
                )
            }
        } else {
            if (isNight) {
                BubbleColorPalette(
                    idleCardBg = 0xFF1E232A.toInt(),
                    idleCardStroke = 0xFF374151.toInt(),
                    activeCardBg = 0xFF102837.toInt(),
                    activeCardStroke = 0xFF00E5FF.toInt(),
                    idleContainerAura = 0x00000000,
                    activeContainerAura = 0x3000E5FF.toInt(),
                    statusDotActive = 0xFF00E5FF.toInt(),
                    statusDotIdle = 0xFF374151.toInt(),
                    textPrimary = 0xFFF3F4F6.toInt(),
                    textSecondary = 0xFF9CA3AF.toInt()
                )
            } else {
                BubbleColorPalette(
                    idleCardBg = 0xFFF1F5F9.toInt(),
                    idleCardStroke = 0xFFCBD5E1.toInt(),
                    activeCardBg = 0xFFE0F7FA.toInt(),
                    activeCardStroke = 0xFF00838F.toInt(),
                    idleContainerAura = 0x00000000,
                    activeContainerAura = 0x3500838F.toInt(),
                    statusDotActive = 0xFF00838F.toInt(),
                    statusDotIdle = 0xFFCBD5E1.toInt(),
                    textPrimary = 0xFF0F172A.toInt(),
                    textSecondary = 0xFF475569.toInt()
                )
            }
        }
    }

    private fun applyColorProgress(progress: Float) {
        currentTransitionProgress = progress
        val palette = resolveDynamicPalette()
        val density = resources.displayMetrics.density

        val currentCardBg = argbEvaluator.evaluate(progress, palette.idleCardBg, palette.activeCardBg) as Int
        val currentCardStroke = argbEvaluator.evaluate(progress, palette.idleCardStroke, palette.activeCardStroke) as Int
        val currentAura = argbEvaluator.evaluate(progress, palette.idleContainerAura, palette.activeContainerAura) as Int

        panelBackgroundDrawable?.setColor(currentCardBg)
        panelBackgroundDrawable?.setStroke((1.5f * density).toInt(), currentCardStroke)

        containerBackgroundDrawable?.setColor(currentAura)

        val currentDotColor = argbEvaluator.evaluate(progress, palette.statusDotIdle, palette.statusDotActive) as Int
        statusDot?.backgroundTintList = ColorStateList.valueOf(currentDotColor)

        tvStatus?.setTextColor(palette.textPrimary)
        tvTranscript?.setTextColor(palette.textPrimary)
        tvDuration?.setTextColor(palette.textSecondary)
    }

    private fun animateContainerColorTransition(toActive: Boolean, durationMs: Long = 500L) {
        colorShiftAnimator?.cancel()
        val targetProgress = if (toActive) 1f else 0f
        val startProgress = currentTransitionProgress

        colorShiftAnimator = ValueAnimator.ofFloat(startProgress, targetProgress).apply {
            duration = durationMs
            interpolator = PathInterpolator(0.2f, 0f, 0f, 1f) // Smooth and gentle Material 3 curve
            addUpdateListener { anim ->
                val p = anim.animatedValue as Float
                applyColorProgress(p)
            }
        }
        colorShiftAnimator?.start()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        applyColorProgress(currentTransitionProgress)
    }

    // Positioning and edge-snapping state
    private var isSnappedToRight = true
    private var savedY = 0
    private var snapAnimator: ValueAnimator? = null

    // Inactivity & Shrink Management (shrinks to compact size after 3 seconds of inactivity)
    private val shrinkTimeoutMs = 3000L
    private val shrinkRunnable = Runnable { applyShrink() }

    private fun resetInactivityTimer(keepShrunk: Boolean = false) {
        mainHandler.removeCallbacks(shrinkRunnable)
        if (!isRecording && !overlayExpanded.value) {
            if (!keepShrunk) {
                overlayShrunk.value = false
                mainHandler.postDelayed(shrinkRunnable, shrinkTimeoutMs)
            } else {
                if (!overlayShrunk.value) {
                    mainHandler.postDelayed(shrinkRunnable, shrinkTimeoutMs)
                }
            }
        }
    }

    private fun expandToFullSize() {
        mainHandler.removeCallbacks(shrinkRunnable)
        overlayShrunk.value = false
    }

    private fun applyShrink() {
        if (!isRecording && !overlayExpanded.value) {
            overlayShrunk.value = true
        }
    }

    // Audio & Streaming
    private val audioQueue = ConcurrentLinkedQueue<ByteArray>()
    private var webSocketClient: GeminiLiveWebSocketClient? = null
    private var wakeLock: PowerManager.WakeLock? = null

    private val audioRecorder = AudioRecorder(
        onChunkReady = { chunk ->
            if (isRecording) {
                audioQueue.offer(chunk)
                val ws = webSocketClient
                if (ws != null && ws.setupComplete) {
                    while (audioQueue.isNotEmpty()) {
                        val c = audioQueue.poll() ?: break
                        val sent = ws.sendAudioChunk(c)
                        if (sent) {
                            sessionChunksSent++
                            sessionBytesSent += c.size
                        }
                    }
                }
            }
        },
        onAmplitudeChanged = { amp ->
            if (isRecording) {
                overlayAudioAmplitude.value = amp.coerceIn(0f, 1f)
            } else {
                overlayAudioAmplitude.value = 0f
            }
        },
        onError = { err ->
            Log.e(TAG, "AudioRecorder error: $err")
            com.example.data.AppLogRepository.logEvent(
                com.example.data.DiagnosticSource.BUBBLE,
                com.example.data.DiagnosticType.ERROR,
                "AudioRecorder: $err"
            )
        }
    )

    private var isRecording = false
    private var sessionChunksSent = 0
    private var sessionBytesSent = 0L
    private val isPolishingInProgress = AtomicBoolean(false)
    private var lastPolishClickTime = 0L
    private val polishDebounceMs = 800L
    private var finalizedTranscript = StringBuilder()
    private var interimTranscript = ""
    private var durationSeconds = 0
    private var durationJob: Job? = null

    @Volatile
    private var isWaitingForLiveSmartModeCompletion = false
    @Volatile
    private var isPendingInjectionOnSmartCompletion = false
    @Volatile
    private var lastTranscriptUpdateTimestamp = 0L
    private var pendingCompletionTimeoutJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
        Log.d(TAG, "FloatingBubbleService onCreate")

        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(androidx.lifecycle.Lifecycle.Event.ON_CREATE)
        lifecycleRegistry.handleLifecycleEvent(androidx.lifecycle.Lifecycle.Event.ON_START)
        lifecycleRegistry.handleLifecycleEvent(androidx.lifecycle.Lifecycle.Event.ON_RESUME)

        startForegroundNotification()
        initOverlayWindow()
    }

    private fun buildNotification(contentText: String): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("VoxStream Voice Typing")
            .setContentText(contentText)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun startForegroundNotification() {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "VoxStream Voice Typing Bubble",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps the floating voice typing bubble active across apps"
            }
            notificationManager.createNotificationChannel(channel)
        }

        val notification = buildNotification("Floating voice bubble is active")

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                )
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    0
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to startForeground", e)
        }
    }

    private fun saveBubblePreferences(snappedRight: Boolean, y: Int) {
        getSharedPreferences("voxstream_settings", Context.MODE_PRIVATE)
            .edit()
            .putBoolean("bubble_snapped_right", snappedRight)
            .putInt("bubble_pos_y", y)
            .apply()
    }

    private fun loadBubblePreferences() {
        val prefs = getSharedPreferences("voxstream_settings", Context.MODE_PRIVATE)
        isSnappedToRight = prefs.getBoolean("bubble_snapped_right", true)
        val posStr = prefs.getString("settle_position", SettlePosition.BOTTOM.name) ?: SettlePosition.BOTTOM.name
        currentSettlePosition = try {
            SettlePosition.valueOf(posStr)
        } catch (e: Exception) {
            SettlePosition.BOTTOM
        }
        val displayMetrics = resources.displayMetrics
        val screenHeight = displayMetrics.heightPixels
        val defaultY = (screenHeight * 0.52f).toInt()
        savedY = prefs.getInt("bubble_pos_y", defaultY)
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun initOverlayWindow() {
        val accessService = VoxStreamAccessibilityService.instance
        windowManager = if (accessService != null) {
            accessService.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        } else {
            getSystemService(Context.WINDOW_SERVICE) as WindowManager
        }
        loadBubblePreferences()

        val displayMetrics = resources.displayMetrics
        val density = displayMetrics.density
        val screenHeight = displayMetrics.heightPixels
        val screenWidth = displayMetrics.widthPixels
        val bubbleSize = (56 * density).toInt()

        val topY = (36 * density).toInt()
        val maxY = (screenHeight - (80 * density).toInt()).coerceAtLeast(topY + (60 * density).toInt())
        val initialY = savedY.coerceIn(topY, maxY)
        val initialX = if (isSnappedToRight) (screenWidth - bubbleSize) else 0

        val windowType = if (accessService != null) {
            Log.d(TAG, "Using TYPE_ACCESSIBILITY_OVERLAY for highest z-order above notification shade")
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        layoutParams = WindowManager.LayoutParams(
            bubbleSize,
            bubbleSize,
            windowType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = initialX
            y = initialY
        }

        val composeView = androidx.compose.ui.platform.ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@FloatingBubbleService)
            setViewTreeSavedStateRegistryOwner(this@FloatingBubbleService)
            setViewTreeViewModelStoreOwner(this@FloatingBubbleService)
            setViewCompositionStrategy(androidx.compose.ui.platform.ViewCompositionStrategy.DisposeOnDetachedFromWindowOrReleasedFromPool)
            setContent {
                com.example.ui.theme.MyApplicationTheme(darkTheme = true, dynamicColor = true) {
                    val transcript by overlayTranscript.collectAsState()
                    val recording by overlayRecording.collectAsState()
                    val pendingFinalizing by overlayPendingFinalizing.collectAsState()
                    val polishing by overlayPolishing.collectAsState()
                    val isExpanded by overlayExpanded.collectAsState()
                    val isShrunk by overlayShrunk.collectAsState()
                    val audioAmplitude by overlayAudioAmplitude.collectAsState()
                    val selectedGlowStyleId by FloatingBubbleManager.selectedGlowStyleId.collectAsState()

                    if (isExpanded) {
                        com.example.ui.components.FloatingDictationPopup(
                            transcriptText = transcript,
                            isRecording = recording,
                            isPendingFinalizing = pendingFinalizing,
                            isPolishing = polishing,
                            audioAmplitude = audioAmplitude,
                            glowStyleId = selectedGlowStyleId,
                            onCancelClick = { onCancelClicked() },
                            onPolishClick = { onPolishClicked() },
                            onCompleteClick = { onConfirmClicked() },
                            onLifebuoyClick = { onRingClicked() },
                            onDragStart = {
                                triggerHapticFeedback(HapticFeedbackType.BUBBLE_HOLD)
                                resetInactivityTimer(keepShrunk = true)
                            },
                            onDrag = { dx, dy ->
                                handleOverlayDrag(dx, dy)
                            },
                            onDragEnd = {
                                handleOverlayDragEnd()
                            }
                        )
                    } else {
                        com.example.ui.components.FloatingCollapsedBubble(
                            isRecording = recording,
                            isShrunk = isShrunk,
                            isSnappedToRight = isSnappedToRight,
                            onClick = { onRingClicked() },
                            onDragStart = {
                                triggerHapticFeedback(HapticFeedbackType.BUBBLE_HOLD)
                                resetInactivityTimer(keepShrunk = true)
                            },
                            onDrag = { dx, dy ->
                                handleOverlayDrag(dx, dy)
                            },
                            onDragEnd = {
                                handleOverlayDragEnd()
                            }
                        )
                    }
                }
            }
        }
        overlayView = composeView

        // The floating bubble visibility strictly tracks keyboard visibility!
        val isKeyboardVisible = FloatingBubbleManager.isKeyboardVisible.value
        composeView.visibility = if (isKeyboardVisible) View.VISIBLE else View.GONE

        try {
            windowManager?.addView(overlayView, layoutParams)
            Log.d(TAG, "Compose overlay view added to WindowManager successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to add overlay view to WindowManager", e)
        }

        resetInactivityTimer()
    }

    private var lastMoveVibrateTime = 0L

    private fun handleOverlayDrag(dx: Float, dy: Float) {
        val lp = layoutParams ?: return
        val displayMetrics = resources.displayMetrics
        val screenHeight = displayMetrics.heightPixels
        val screenWidth = displayMetrics.widthPixels
        val density = displayMetrics.density

        val isExpanded = overlayExpanded.value
        val topY = (32 * density).toInt()
        val maxY = if (isExpanded) {
            (screenHeight - (210 * density).toInt()).coerceAtLeast(topY + (60 * density).toInt())
        } else {
            (screenHeight - (80 * density).toInt()).coerceAtLeast(topY + (60 * density).toInt())
        }

        lp.y = (lp.y + dy.toInt()).coerceIn(topY, maxY)

        if (!isExpanded) {
            val bubbleSize = (56 * density).toInt()
            val maxX = (screenWidth - bubbleSize).coerceAtLeast(0)
            val proposedX = (lp.x + dx.toInt()).coerceIn(0, maxX)

            val magneticCenterX = (screenWidth - bubbleSize) / 2
            val magneticCenterY = maxY
            val magneticRadius = 135 * density

            val distToMagnetic = kotlin.math.hypot(
                (proposedX - magneticCenterX).toDouble(),
                (lp.y - magneticCenterY).toDouble()
            ).toFloat()

            if (distToMagnetic < magneticRadius) {
                // Magnetic attraction pulls toward bottom-center coffre zone
                val factor = (1f - (distToMagnetic / magneticRadius)).coerceIn(0f, 1f)
                val pullStrength = factor * factor * 0.70f
                lp.x = (proposedX + (magneticCenterX - proposedX) * pullStrength).toInt().coerceIn(0, maxX)
                lp.y = (lp.y + (magneticCenterY - lp.y) * pullStrength).toInt().coerceIn(topY, maxY)
            } else {
                lp.x = proposedX
            }
        } else {
            lp.x = 0
        }

        try {
            windowManager?.updateViewLayout(overlayView, lp)
        } catch (e: Exception) {
            Log.e(TAG, "Error updating overlay layout during drag", e)
        }

        val now = System.currentTimeMillis()
        if (now - lastMoveVibrateTime > 75) {
            lastMoveVibrateTime = now
            triggerHapticFeedback(HapticFeedbackType.BUBBLE_MOVE)
        }
    }

    private fun handleOverlayDragEnd() {
        val lp = layoutParams ?: return
        val displayMetrics = resources.displayMetrics
        val screenWidth = displayMetrics.widthPixels
        val density = displayMetrics.density
        val isExpanded = overlayExpanded.value

        savedY = lp.y

        if (!isExpanded) {
            val bubbleSize = (56 * density).toInt()
            val magneticCenterX = (screenWidth - bubbleSize) / 2
            val maxY = (displayMetrics.heightPixels - (80 * density).toInt())
            val magneticRadius = 135 * density

            val distToMagnetic = kotlin.math.hypot(
                (lp.x - magneticCenterX).toDouble(),
                (lp.y - maxY).toDouble()
            ).toFloat()

            val targetX: Int
            val targetY: Int
            val snapRight: Boolean

            if (distToMagnetic < magneticRadius * 0.85f) {
                targetX = magneticCenterX
                targetY = maxY
                snapRight = lp.x >= screenWidth / 2
            } else {
                snapRight = (lp.x + bubbleSize / 2) >= screenWidth / 2
                targetX = if (snapRight) (screenWidth - bubbleSize) else 0
                targetY = lp.y
            }

            val startX = lp.x
            val startY = lp.y

            isSnappedToRight = snapRight
            savedY = targetY
            saveBubblePreferences(snapRight, savedY)

            // Triple Bounce Collision Physics:
            // 1. Approaches screen edge and collides squarely at full velocity
            // 2. Performs 3 distinct rebound arches with decreasing amplitude
            val totalDistX = (targetX - startX).toFloat()
            val totalDistY = (targetY - startY).toFloat()

            val bounceDirX = if (targetX >= screenWidth / 2) -1f else 1f
            val maxAmplitude = (38 * density).coerceAtLeast(kotlin.math.abs(totalDistX) * 0.32f).coerceAtMost(65 * density)
            val amp1 = maxAmplitude
            val amp2 = maxAmplitude * 0.40f
            val amp3 = maxAmplitude * 0.15f

            val t0 = 0.28f
            val t1 = 0.56f
            val t2 = 0.80f
            var lastBounceImpactIndex = -1

            snapAnimator?.cancel()
            snapAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
                duration = 680L
                interpolator = android.view.animation.LinearInterpolator()
                addUpdateListener { anim ->
                    val fraction = anim.animatedFraction

                    var mainProgress = 0f
                    var reboundOffset = 0f

                    if (fraction <= t0) {
                        // Phase 1: Accelerate cleanly and collide with edge at full velocity
                        val tNorm = fraction / t0
                        mainProgress = tNorm * tNorm
                        reboundOffset = 0f

                        if (lastBounceImpactIndex < 0 && fraction >= t0 * 0.92f) {
                            lastBounceImpactIndex = 0
                            triggerHapticFeedback(HapticFeedbackType.BUBBLE_SETTLE)
                        }
                    } else {
                        // Phase 2: Sits flush at edge, bouncing 3 times with decreasing arch
                        mainProgress = 1.0f

                        if (fraction <= t1) {
                            val u = (fraction - t0) / (t1 - t0)
                            reboundOffset = amp1 * kotlin.math.sin(u * Math.PI).toFloat()
                            if (lastBounceImpactIndex < 1 && fraction >= (t0 + (t1 - t0) * 0.90f)) {
                                lastBounceImpactIndex = 1
                                triggerHapticFeedback(HapticFeedbackType.BUBBLE_SETTLE)
                            }
                        } else if (fraction <= t2) {
                            val u = (fraction - t1) / (t2 - t1)
                            reboundOffset = amp2 * kotlin.math.sin(u * Math.PI).toFloat()
                            if (lastBounceImpactIndex < 2 && fraction >= (t1 + (t2 - t1) * 0.90f)) {
                                lastBounceImpactIndex = 2
                                triggerHapticFeedback(HapticFeedbackType.BUBBLE_SETTLE)
                            }
                        } else {
                            val u = (fraction - t2) / (1.0f - t2)
                            reboundOffset = amp3 * kotlin.math.sin(u * Math.PI).toFloat()
                            if (lastBounceImpactIndex < 3 && fraction >= (t2 + (1.0f - t2) * 0.90f)) {
                                lastBounceImpactIndex = 3
                                triggerHapticFeedback(HapticFeedbackType.BUBBLE_SETTLE)
                            }
                        }
                    }

                    val currentMainX = startX + totalDistX * mainProgress
                    val currentMainY = startY + totalDistY * mainProgress
                    val finalX = currentMainX + bounceDirX * reboundOffset

                    lp.x = finalX.toInt().coerceIn(0, screenWidth - bubbleSize)
                    lp.y = currentMainY.toInt()

                    try {
                        windowManager?.updateViewLayout(overlayView, lp)
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed updating layout during snap", e)
                    }
                }
                addListener(object : android.animation.AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: android.animation.Animator) {
                        lp.x = targetX
                        lp.y = targetY
                        try {
                            windowManager?.updateViewLayout(overlayView, lp)
                        } catch (e: Exception) {
                            Log.w(TAG, "Failed final layout update", e)
                        }
                        triggerHapticFeedback(HapticFeedbackType.BUBBLE_SETTLE)
                    }
                })
                start()
            }
        } else {
            saveBubblePreferences(isSnappedToRight, savedY)
            triggerHapticFeedback(HapticFeedbackType.BUBBLE_SETTLE)
        }

        resetInactivityTimer(keepShrunk = true)
    }

    private fun expandPanel() {
        overlayExpanded.value = true
        val lp = layoutParams ?: return
        val displayMetrics = resources.displayMetrics
        val density = displayMetrics.density
        val screenHeight = displayMetrics.heightPixels

        val topY = (36 * density).toInt()
        val maxY = (screenHeight - (210 * density).toInt()).coerceAtLeast(topY + (60 * density).toInt())

        lp.width = WindowManager.LayoutParams.MATCH_PARENT
        lp.height = WindowManager.LayoutParams.WRAP_CONTENT
        lp.gravity = Gravity.TOP or Gravity.START
        lp.x = 0
        lp.y = savedY.coerceIn(topY, maxY)

        try {
            windowManager?.updateViewLayout(overlayView, lp)
        } catch (e: Exception) {
            Log.e(TAG, "Error updating window layout on expand", e)
        }
    }

    private fun collapsePanel() {
        overlayExpanded.value = false
        val lp = layoutParams ?: return
        val displayMetrics = resources.displayMetrics
        val density = displayMetrics.density
        val screenWidth = displayMetrics.widthPixels
        val screenHeight = displayMetrics.heightPixels
        val bubbleSize = (56 * density).toInt()

        val topY = (36 * density).toInt()
        val maxY = (screenHeight - (80 * density).toInt()).coerceAtLeast(topY + (60 * density).toInt())

        lp.width = bubbleSize
        lp.height = bubbleSize
        lp.gravity = Gravity.TOP or Gravity.START
        lp.x = if (isSnappedToRight) (screenWidth - bubbleSize) else 0
        lp.y = savedY.coerceIn(topY, maxY)

        try {
            windowManager?.updateViewLayout(overlayView, lp)
        } catch (e: Exception) {
            Log.e(TAG, "Error updating window layout on collapse", e)
        }
        resetInactivityTimer()
    }

    private fun onRingClicked() {
        if (overlayShrunk.value) {
            expandToFullSize()
            triggerHapticFeedback(HapticFeedbackType.BUBBLE_HOLD)
            resetInactivityTimer()
            return
        }
        if (!isRecording && !overlayExpanded.value) {
            expandToFullSize()
            expandPanel()
            startVoiceTyping()
        } else if (!isRecording) {
            startVoiceTyping()
        } else {
            triggerHapticFeedback(HapticFeedbackType.BUBBLE_HOLD)
        }
    }

    private fun startVoiceTyping() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(
                this,
                "Microphone permission required. Please grant it in VoxStream.",
                Toast.LENGTH_LONG
            ).show()
            val appIntent = Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            startActivity(appIntent)
            return
        }

        val apiKey = getEffectiveApiKey()
        if (apiKey.isBlank() || apiKey == "MY_GEMINI_API_KEY") {
            Toast.makeText(
                this,
                "Gemini API key is required. Please set it in VoxStream app first.",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        // Upgrade foreground service type to microphone while actively recording
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            try {
                startForeground(
                    NOTIFICATION_ID,
                    buildNotification("Listening..."),
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                )
            } catch (e: Exception) {
                Log.w(TAG, "Could not upgrade FGS to microphone: ${e.message}")
            }
        }

        isRecording = true
        expandToFullSize()
        FloatingBubbleManager.setRecordingState(true)
        triggerHapticFeedback(HapticFeedbackType.TRANSCRIPTION_START)
        expandPanel()
        tvStatus?.text = "Connecting..."
        statusDot?.setBackgroundResource(R.drawable.bg_dot_recording)
        durationSeconds = 0
        tvDuration?.text = "00:00"
        finalizedTranscript.clear()
        interimTranscript = ""
        lastTranscriptUpdateTimestamp = 0L
        isWaitingForLiveSmartModeCompletion = false
        isPendingInjectionOnSmartCompletion = false
        overlayPendingFinalizing.value = false
        pendingCompletionTimeoutJob?.cancel()
        pendingCompletionTimeoutJob = null
        btnConfirm?.isEnabled = true
        btnConfirm?.text = "Confirm"
        btnPolish?.isEnabled = true
        btnPolish?.text = "Polish ✨"
        btnCancel?.isEnabled = true
        updateTranscriptDisplay()
        sessionChunksSent = 0
        sessionBytesSent = 0L

        // Read Audio & Smart Mode preferences
        val prefs = getSharedPreferences("voxstream_settings", Context.MODE_PRIVATE)
        val isSmartMode = prefs.getBoolean("smart_mode", false)
        val isAecEnabled = prefs.getBoolean("aec_enabled", true)
        val isNoiseSuppressorEnabled = prefs.getBoolean("noise_suppressor_enabled", true)
        val selectedModel = prefs.getString("selected_model", GeminiLiveWebSocketClient.DEFAULT_MODEL) ?: GeminiLiveWebSocketClient.DEFAULT_MODEL

        val modeLabel = if (isSmartMode) "SMART" else "VERBATIM"
        com.example.data.AppLogRepository.logEvent(
            com.example.data.DiagnosticSource.BUBBLE,
            com.example.data.DiagnosticType.SESSION_START,
            "Mode: $modeLabel, Model: $selectedModel"
        )

        // Acquire WakeLock to keep audio recording alive even if user turns off screen
        try {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "voxstream:floating_voice_typing_wakelock"
            ).apply {
                acquire(10 * 60 * 1000L /* 10 minutes max */)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not acquire WakeLock", e)
        }

        // Start AudioRecorder
        audioRecorder.start(
            serviceScope,
            aecEnabled = isAecEnabled,
            noiseSuppressorEnabled = isNoiseSuppressorEnabled,
            source = com.example.data.DiagnosticSource.BUBBLE
        )

        // Open Gemini Live WebSocket
        webSocketClient = GeminiLiveWebSocketClient(
            onSetupComplete = {
                mainHandler.post {
                    if (!isPendingInjectionOnSmartCompletion) {
                        tvStatus?.text = "Listening..."
                        statusDot?.setBackgroundResource(R.drawable.bg_dot_recording)
                    }
                    val ws = webSocketClient
                    if (ws != null && ws.setupComplete) {
                        while (audioQueue.isNotEmpty()) {
                            audioQueue.poll()?.let { ws.sendAudioChunk(it) }
                        }
                    }
                }
            },
            onInterimTranscription = { text ->
                mainHandler.post {
                    lastTranscriptUpdateTimestamp = SystemClock.elapsedRealtime()
                    isWaitingForLiveSmartModeCompletion = true
                    interimTranscript = text
                    updateTranscriptDisplay()
                    if (isSmartMode && !isPendingInjectionOnSmartCompletion) {
                        statusDot?.setBackgroundResource(R.drawable.bg_dot_pending)
                        tvStatus?.text = "Pending..."
                    }
                }
            },
            onFinalizedTranscription = { text ->
                mainHandler.post {
                    lastTranscriptUpdateTimestamp = SystemClock.elapsedRealtime()
                    isWaitingForLiveSmartModeCompletion = false
                    val trimmed = text.trim()
                    if (trimmed.isNotEmpty()) {
                        com.example.data.AppLogRepository.logEvent(
                            com.example.data.DiagnosticSource.BUBBLE,
                            com.example.data.DiagnosticType.TRANSCRIPT_FINAL,
                            trimmed
                        )
                    }
                    if (finalizedTranscript.isNotEmpty() && !finalizedTranscript.endsWith(" ")) {
                        finalizedTranscript.append(" ")
                    }
                    finalizedTranscript.append(text)
                    interimTranscript = ""
                    updateTranscriptDisplay()
                    statusDot?.setBackgroundResource(R.drawable.bg_dot_recording)
                    if (isRecording && !isPendingInjectionOnSmartCompletion) {
                        tvStatus?.text = "Listening..."
                    }
                    triggerHapticFeedback(HapticFeedbackType.FINAL_SENTENCE)

                    if (isPendingInjectionOnSmartCompletion) {
                        Log.d(TAG, "Final polished transcript arrived while awaiting completion -> proceeding with injection")
                        isPendingInjectionOnSmartCompletion = false
                        pendingCompletionTimeoutJob?.cancel()
                        pendingCompletionTimeoutJob = null
                        performInjectionAndClose()
                    }
                }
            },
            onStateChanged = { state ->
                mainHandler.post {
                    when (state) {
                        is ConnectionState.Connecting,
                        is ConnectionState.ConnectedWaitingSetup -> {
                            if (!isPendingInjectionOnSmartCompletion) {
                                tvStatus?.text = "Connecting..."
                            }
                        }
                        is ConnectionState.Streaming -> {
                            if (!isPendingInjectionOnSmartCompletion) {
                                if (isSmartMode && isWaitingForLiveSmartModeCompletion) {
                                    tvStatus?.text = "Pending..."
                                    statusDot?.setBackgroundResource(R.drawable.bg_dot_pending)
                                } else {
                                    tvStatus?.text = "Listening..."
                                    statusDot?.setBackgroundResource(R.drawable.bg_dot_recording)
                                }
                            }
                        }
                        is ConnectionState.Error -> {
                            tvStatus?.text = "Connection Error"
                            if (isPendingInjectionOnSmartCompletion) {
                                isPendingInjectionOnSmartCompletion = false
                                pendingCompletionTimeoutJob?.cancel()
                                pendingCompletionTimeoutJob = null
                                performInjectionAndClose()
                            }
                        }
                        is ConnectionState.Idle -> {
                            if (!isPendingInjectionOnSmartCompletion) {
                                tvStatus?.text = "Ready"
                            }
                        }
                        else -> {}
                    }
                }
            },
            onLog = { _, _, _, _ -> },
            onError = { err ->
                Log.e(TAG, "Gemini Live Error: $err")
                com.example.data.AppLogRepository.logEvent(
                    com.example.data.DiagnosticSource.BUBBLE,
                    com.example.data.DiagnosticType.ERROR,
                    err
                )
                if (isPendingInjectionOnSmartCompletion) {
                    mainHandler.post {
                        isPendingInjectionOnSmartCompletion = false
                        pendingCompletionTimeoutJob?.cancel()
                        pendingCompletionTimeoutJob = null
                        performInjectionAndClose()
                    }
                }
            }
        ).apply {
            connect(apiKey = apiKey, model = selectedModel, smartMode = isSmartMode)
        }

        startDurationTimer()
    }

    private fun stopVoiceTyping() {
        if (!isRecording) return
        isRecording = false
        triggerHapticFeedback(HapticFeedbackType.TRANSCRIPTION_STOP)
        FloatingBubbleManager.setRecordingState(false)
        if (!isPendingInjectionOnSmartCompletion) {
            tvStatus?.text = "Ready to confirm or cancel"
        }

        val durationAtEnd = durationSeconds
        val chunksAtEnd = sessionChunksSent
        val bytesAtEnd = sessionBytesSent

        durationJob?.cancel()
        durationJob = null
        resetInactivityTimer()

        // Revert foreground service type to specialUse when not recording
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            try {
                startForeground(
                    NOTIFICATION_ID,
                    buildNotification("Floating voice bubble is active"),
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                )
            } catch (e: Exception) {
                Log.w(TAG, "Could not revert FGS to specialUse: ${e.message}")
            }
        }

        // Release hardware and socket asynchronously so main thread is never blocked
        val ws = webSocketClient
        webSocketClient = null
        val wl = wakeLock
        wakeLock = null

        serviceScope.launch(Dispatchers.IO) {
            try {
                audioRecorder.stop()
                ws?.signalStreamEnd()
                delay(400) // Allow in-flight final transcript frames to arrive and log TRANSCRIPT_FINAL first
                ws?.disconnect()
            } catch (e: Exception) {
                Log.e(TAG, "Error stopping audio/websocket", e)
            } finally {
                // SESSION_END is logged last, strictly after any in-flight TRANSCRIPT_FINAL
                com.example.data.AppLogRepository.logEvent(
                    com.example.data.DiagnosticSource.BUBBLE,
                    com.example.data.DiagnosticType.SESSION_END,
                    "Duration: ${durationAtEnd}s, Chunks: $chunksAtEnd, Streamed: ${bytesAtEnd / 1024} KB"
                )
            }

            try {
                if (wl?.isHeld == true) {
                    wl.release()
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error releasing WakeLock", e)
            }
        }
    }

    private fun onConfirmClicked() {
        overlayPendingFinalizing.value = true
        val prefs = getSharedPreferences("voxstream_settings", Context.MODE_PRIVATE)
        val isSmartMode = prefs.getBoolean("smart_mode", false)

        val now = SystemClock.elapsedRealtime()
        val timeSinceLastUpdate = if (lastTranscriptUpdateTimestamp > 0L) now - lastTranscriptUpdateTimestamp else Long.MAX_VALUE
        val silenceDurationMs = 2000L

        // Check if smart-waiting state applies:
        // When Smart Mode is active, check if an update occurred within the last 2 seconds (the silenceDurationMs window),
        // or if uncommitted interim text is present, or if still awaiting Smart Mode completion frame.
        val needsSmartWaiting = isSmartMode && (
            (lastTranscriptUpdateTimestamp > 0L && timeSinceLastUpdate < silenceDurationMs) ||
            isWaitingForLiveSmartModeCompletion ||
            interimTranscript.isNotBlank() ||
            (isRecording && getFullTranscriptText().isNotBlank())
        )

        if (needsSmartWaiting) {
            val remainingWaitMs = (silenceDurationMs - timeSinceLastUpdate).coerceIn(350L, silenceDurationMs)
            Log.d(TAG, "Confirm tapped within smart-waiting window (${timeSinceLastUpdate}ms elapsed, waiting remainder ${remainingWaitMs}ms) -> displaying Finalizing... loader")
            isPendingInjectionOnSmartCompletion = true

            // Stop sending further microphone audio so the server detects end of speech immediately and finalizes
            if (isRecording) {
                isRecording = false
                FloatingBubbleManager.setRecordingState(false)
                audioRecorder.stop()
                serviceScope.launch(Dispatchers.IO) {
                    try {
                        webSocketClient?.signalStreamEnd()
                    } catch (e: Exception) {
                        Log.e(TAG, "Error signaling stream end on confirm", e)
                    }
                }
            }

            // Disable all UI controls and display "Finalizing..."
            btnConfirm?.isEnabled = false
            btnConfirm?.text = "Finalizing..."
            btnPolish?.isEnabled = false
            btnCancel?.isEnabled = false
            tvStatus?.text = "Finalizing..."
            statusDot?.setBackgroundResource(R.drawable.bg_dot_pending)

            // Safety timeout: wait for the remainder of the silenceDurationMs window + buffer
            pendingCompletionTimeoutJob?.cancel()
            pendingCompletionTimeoutJob = serviceScope.launch {
                delay(remainingWaitMs + 800L)
                if (isPendingInjectionOnSmartCompletion) {
                    Log.w(TAG, "Smart-waiting window elapsed -> injecting available transcript")
                    isPendingInjectionOnSmartCompletion = false
                    isWaitingForLiveSmartModeCompletion = false
                    performInjectionAndClose()
                }
            }
            return
        }

        // Standard flow: immediate injection when already outside the smart-waiting window
        performInjectionAndClose()
    }

    private fun performInjectionAndClose() {
        val textToInject = getFullTranscriptText()
        if (isRecording) {
            stopVoiceTyping()
        }

        if (textToInject.isNotBlank()) {
            FloatingBubbleManager.injectOrFallbackToClipboard(this, textToInject)
        }

        resetAndCollapse()
    }

    private fun onPolishClicked() {
        val now = SystemClock.elapsedRealtime()
        if (now - lastPolishClickTime < polishDebounceMs) {
            Log.d(TAG, "Ignoring rapid Polish tap (debounced)")
            return
        }
        lastPolishClickTime = now

        if (!isPolishingInProgress.compareAndSet(false, true)) {
            Log.d(TAG, "Polish operation already in progress, ignoring extra tap")
            return
        }

        val rawTranscript = getFullTranscriptText()

        if (rawTranscript.isBlank()) {
            Toast.makeText(this, "No text to polish", Toast.LENGTH_SHORT).show()
            isPolishingInProgress.set(false)
            resetAndCollapse()
            return
        }

        // Show loading state on Polish button INSTANTLY on touch
        btnPolish?.isEnabled = false
        btnConfirm?.isEnabled = false
        btnCancel?.isEnabled = false
        btnPolish?.text = "Polishing..."
        tvStatus?.text = "Polishing transcript..."

        if (isRecording) {
            stopVoiceTyping()
        }

        serviceScope.launch {
            try {
                val apiKey = getEffectiveApiKey()
                var result: PolishResult? = null

                AppLogRepository.addLog(
                    LogLevel.INFO,
                    "PolishAPI",
                    "User tapped Polish ✨ for transcript (${rawTranscript.length} chars)",
                    rawTranscript
                )
                com.example.data.AppLogRepository.logEvent(
                    com.example.data.DiagnosticSource.BUBBLE,
                    com.example.data.DiagnosticType.POLISH_CALLED,
                    "Transcript length: ${rawTranscript.length} chars"
                )

                if (apiKey.isBlank() || apiKey == "MY_GEMINI_API_KEY") {
                    val err = "API key is missing or default placeholder ('$apiKey'). Please set GEMINI_API_KEY in app Settings."
                    Log.e(TAG, "Polish Error: $err")
                    AppLogRepository.addLog(LogLevel.ERROR, "PolishAPI", err)
                    com.example.data.AppLogRepository.logEvent(
                        com.example.data.DiagnosticSource.BUBBLE,
                        com.example.data.DiagnosticType.POLISH_FAILED,
                        err
                    )
                    result = PolishResult(null, err)
                } else {
                    withContext(Dispatchers.IO) {
                        result = callGeminiPolishApi(apiKey, rawTranscript)
                    }
                }

                val polishedText = result?.text
                if (!polishedText.isNullOrBlank()) {
                    AppLogRepository.addLog(LogLevel.INFO, "PolishAPI", "Polish completed successfully!", polishedText)
                    com.example.data.AppLogRepository.logEvent(
                        com.example.data.DiagnosticSource.BUBBLE,
                        com.example.data.DiagnosticType.POLISH_SUCCESS,
                        "Result: ${polishedText.take(60)}..."
                    )
                    FloatingBubbleManager.injectOrFallbackToClipboard(this@FloatingBubbleService, polishedText)
                    Toast.makeText(this@FloatingBubbleService, "Polished ✨", Toast.LENGTH_SHORT).show()
                } else {
                    // Fallback to raw transcript as Confirm would
                    val fallbackText = getFullTranscriptText()
                    if (fallbackText.isNotBlank()) {
                        FloatingBubbleManager.injectOrFallbackToClipboard(this@FloatingBubbleService, fallbackText)
                    }
                    val errorMsg = result?.errorDetail ?: "Unknown error"
                    Log.e(TAG, "Polish failed: $errorMsg")
                    AppLogRepository.addLog(LogLevel.ERROR, "PolishAPI", "Polish failed completely: $errorMsg")
                    com.example.data.AppLogRepository.logEvent(
                        com.example.data.DiagnosticSource.BUBBLE,
                        com.example.data.DiagnosticType.POLISH_FAILED,
                        errorMsg
                    )
                    Toast.makeText(this@FloatingBubbleService, "Polish failed: $errorMsg", Toast.LENGTH_LONG).show()
                }
            } finally {
                // Restore button UI & unlock polishing
                btnPolish?.isEnabled = true
                btnConfirm?.isEnabled = true
                btnCancel?.isEnabled = true
                btnPolish?.text = "Polish ✨"
                isPolishingInProgress.set(false)
                resetAndCollapse()
            }
        }
    }

    private data class PolishResult(
        val text: String?,
        val errorDetail: String?
    )

    @Volatile
    private var lastSuccessfulPolishModel: String = "gemini-3.5-flash-lite"

    private fun callGeminiPolishApi(apiKey: String, rawTranscript: String): PolishResult {
        val baseModels = listOf(
            "gemini-3.5-flash-lite",
            "gemini-3.1-flash-lite",
            "gemini-2.5-flash-lite",
            "gemini-2.5-flash"
        )
        val modelsToTry = listOf(lastSuccessfulPolishModel) + baseModels.filter { it != lastSuccessfulPolishModel }

        val systemInstructionText = """
You are Flow, a transcript cleaner. Input: a raw spoken transcript. Output: ONLY the cleaned final text — nothing else.

Never answer questions asked in the transcript — transcribe them as questions, don't respond to them.
Never explain your changes.
Never copy any word, number, or item from the examples below into your output — those are for pattern reference only. Every number and item in your output must come from the transcript you are given, not from these examples.
Preserve whatever language(s) the speaker used — do not translate.
Add natural punctuation and capitalization to whatever text isn't otherwise changed by the rules below.

RULES
1. Remove filler words and verbal hesitations (um, uh, like, so, okay, yeah, yes yeah, I think, you know, kind of, sort of).
2. When the speaker corrects a stated value (actually, no wait, I mean, sorry, scratch that), replace it — never keep the original, incorrect version.
3. When the speaker states a quantity needed, then separately mentions an amount already owned/available, calculate the true remaining amount and output ONLY that final number — don't show the math or mention what they already have. Only apply this when the "already have X" framing is unambiguous; if it's not clearly that pattern, leave the numbers exactly as spoken rather than guessing.
4. When the speaker names 2 or more discrete items, output them as a markdown bulleted list.

EXAMPLE 1 (simple correction)
Raw: "Let's meet at 5, actually 6."
Output: Let's meet at 6.

EXAMPLE 2 (list + correction, unrelated domain)
Raw: "I want to buy two no three books, a lamp, and a rug. Actually skip the rug."
Output: I want to buy:
- 3 books
- a lamp

EXAMPLE 3 (quantity adjustment, unrelated domain)
Raw: "I need 10 chairs for the event. Wait, I already have 4 chairs at home, so I'd only need 6."
Output: I need 6 chairs for the event.
""".trimIndent()

        fun buildJsonBody(modelName: String): String {
            return JSONObject().apply {
                put("systemInstruction", JSONObject().apply {
                    put("parts", JSONArray().put(JSONObject().put("text", systemInstructionText)))
                })
                put("contents", JSONArray().put(
                    JSONObject().apply {
                        put("role", "user")
                        put("parts", JSONArray().put(JSONObject().put("text", rawTranscript)))
                    }
                ))
                put("generationConfig", JSONObject().apply {
                    put("temperature", 0.1)
                    if (modelName.contains("3.")) {
                        put("thinkingConfig", JSONObject().apply {
                            put("thinkingLevel", "MINIMAL")
                        })
                    } else {
                        put("thinkingConfig", JSONObject().apply {
                            put("thinkingBudget", 0)
                        })
                    }
                })
            }.toString()
        }

        val errorsLog = mutableListOf<String>()

        for (modelName in modelsToTry) {
            var connection: HttpURLConnection? = null
            try {
                val jsonBody = try {
                    buildJsonBody(modelName)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed creating Polish JSON request body for $modelName", e)
                    return PolishResult(null, "JSON build error: ${e.message}")
                }

                val urlString = "https://generativelanguage.googleapis.com/v1beta/models/$modelName:generateContent?key=$apiKey"
                val url = URL(urlString)

                AppLogRepository.addLog(
                    LogLevel.SENT,
                    "PolishAPI",
                    "Sending POST request to model '$modelName' (Key len=${apiKey.length})",
                    jsonBody
                )

                connection = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                    setRequestProperty("x-goog-api-key", apiKey)
                    setRequestProperty("Connection", "keep-alive")
                    connectTimeout = 5000
                    readTimeout = 8000
                    doOutput = true
                }

                connection.outputStream.use { os ->
                    os.write(jsonBody.toByteArray(Charsets.UTF_8))
                }

                val responseCode = connection.responseCode
                if (responseCode == 200) {
                    val responseString = connection.inputStream.bufferedReader().use { it.readText() }
                    Log.d(TAG, "Polish API success response from $modelName: $responseString")
                    AppLogRepository.addLog(
                        LogLevel.RECEIVED,
                        "PolishAPI",
                        "HTTP 200 OK from $modelName",
                        responseString
                    )

                    val rootJson = JSONObject(responseString)
                    val candidates = rootJson.optJSONArray("candidates")
                    if (candidates != null && candidates.length() > 0) {
                        val firstCandidate = candidates.getJSONObject(0)
                        val content = firstCandidate.optJSONObject("content")
                        val parts = content?.optJSONArray("parts")
                        if (parts != null && parts.length() > 0) {
                            val sb = StringBuilder()
                            for (i in 0 until parts.length()) {
                                val partObj = parts.getJSONObject(i)
                                val text = partObj.optString("text", "")
                                if (text.isNotEmpty()) {
                                    sb.append(text)
                                }
                            }
                            val textResult = sb.toString().trim()
                            if (textResult.isNotBlank()) {
                                Log.d(TAG, "Polish call succeeded using model $modelName")
                                lastSuccessfulPolishModel = modelName
                                return PolishResult(textResult, null)
                            }
                        }
                    }
                    errorsLog.add("$modelName returned empty candidates/parts")
                } else {
                    val errorStreamText = connection.errorStream?.bufferedReader()?.use { it.readText() }
                        ?: connection.inputStream?.bufferedReader()?.use { it.readText() }
                        ?: "No error body"
                    val errLogMsg = "HTTP $responseCode ($modelName): $errorStreamText"
                    Log.e(TAG, "Polish API Error: $errLogMsg")
                    AppLogRepository.addLog(
                        LogLevel.ERROR,
                        "PolishAPI",
                        "HTTP $responseCode Error ($modelName)",
                        errorStreamText
                    )
                    errorsLog.add(errLogMsg)
                }
            } catch (e: Exception) {
                val excMsg = "Exception ($modelName): ${e.message}"
                Log.e(TAG, "Error in Polish call for model $modelName", e)
                AppLogRepository.addLog(
                    LogLevel.ERROR,
                    "PolishAPI",
                    "Exception connecting to $modelName: ${e.message}",
                    e.stackTraceToString()
                )
                errorsLog.add(excMsg)
            } finally {
                connection?.disconnect()
            }
        }
        return PolishResult(null, errorsLog.joinToString(" | "))
    }

    private fun onCancelClicked() {
        overlayPendingFinalizing.value = false
        isPendingInjectionOnSmartCompletion = false
        isWaitingForLiveSmartModeCompletion = false
        pendingCompletionTimeoutJob?.cancel()
        pendingCompletionTimeoutJob = null
        stopVoiceTyping()
        Toast.makeText(this, "Voice typing cancelled", Toast.LENGTH_SHORT).show()
        resetAndCollapse()
    }

    private fun resetAndCollapse() {
        overlayPendingFinalizing.value = false
        isPolishingInProgress.set(false)
        isWaitingForLiveSmartModeCompletion = false
        isPendingInjectionOnSmartCompletion = false
        pendingCompletionTimeoutJob?.cancel()
        pendingCompletionTimeoutJob = null
        btnConfirm?.isEnabled = true
        btnConfirm?.text = "Confirm"
        btnPolish?.isEnabled = true
        btnPolish?.text = "Polish ✨"
        btnCancel?.isEnabled = true
        finalizedTranscript.clear()
        interimTranscript = ""
        updateTranscriptDisplay()
        collapsePanel()

        // If keyboard is not currently visible, hide the bubble entirely
        if (!FloatingBubbleManager.isKeyboardVisible.value) {
            overlayView?.visibility = View.GONE
        }
    }

    fun onKeyboardVisibilityChanged(isVisible: Boolean) {
        mainHandler.post {
            val root = overlayView ?: return@post
            if (isVisible) {
                if (!isRecording && !overlayExpanded.value) {
                    collapsePanel()
                }
                expandToFullSize()
                root.visibility = View.VISIBLE
                resetInactivityTimer(keepShrunk = false)
            } else {
                // Keyboard disappeared:
                // Only hide if NOT currently recording AND NOT reviewing transcript!
                if (!isRecording && !overlayExpanded.value) {
                    root.visibility = View.GONE
                }
            }
        }
    }

    fun onFieldFocusChanged(isFocused: Boolean) {
        // No-op: Visibility is strictly governed by TYPE_INPUT_METHOD window presence in onKeyboardVisibilityChanged
    }

    /**
     * Re-attaches overlay window using TYPE_ACCESSIBILITY_OVERLAY from AccessibilityService.
     * Guarantees the bubble floats above notifications, the notification shade, and status bar.
     */
    fun attachToAccessibilityService(accessService: VoxStreamAccessibilityService) {
        mainHandler.post {
            try {
                val currentOverlay = overlayView ?: return@post
                val currentLp = layoutParams ?: return@post

                // Remove from previous window manager
                try {
                    windowManager?.removeView(currentOverlay)
                } catch (e: Exception) {
                    Log.w(TAG, "Notice removing view for accessibility re-attachment: ${e.message}")
                }

                // Switch to Accessibility WindowManager & TYPE_ACCESSIBILITY_OVERLAY
                val newWm = accessService.getSystemService(Context.WINDOW_SERVICE) as WindowManager
                windowManager = newWm
                currentLp.type = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY

                newWm.addView(currentOverlay, currentLp)
                Log.d(TAG, "Re-attached overlay to TYPE_ACCESSIBILITY_OVERLAY successfully!")
            } catch (e: Exception) {
                Log.e(TAG, "Error attaching to accessibility window manager", e)
            }
        }
    }

    private fun updateTranscriptDisplay() {
        val full = getFullTranscriptText()
        overlayTranscript.value = full
        overlayRecording.value = isRecording
        overlayPendingFinalizing.value = isPendingInjectionOnSmartCompletion
        overlayPolishing.value = isPolishingInProgress.get()
    }

    private fun getFullTranscriptText(): String {
        val sb = StringBuilder()
        if (finalizedTranscript.isNotEmpty()) {
            sb.append(finalizedTranscript)
        }
        if (interimTranscript.isNotEmpty()) {
            if (sb.isNotEmpty() && !sb.endsWith(" ")) {
                sb.append(" ")
            }
            sb.append(interimTranscript)
        }
        return sb.toString().trim()
    }

    private fun startDurationTimer() {
        durationJob?.cancel()
        durationJob = serviceScope.launch {
            while (isActive && isRecording) {
                delay(1000)
                durationSeconds++
                val mins = durationSeconds / 60
                val secs = durationSeconds % 60
                tvDuration?.text = String.format("%02d:%02d", mins, secs)
            }
        }
    }

    private fun getEffectiveApiKey(): String {
        val prefs = getSharedPreferences("voxstream_settings", Context.MODE_PRIVATE)
        val customKey = prefs.getString("custom_api_key", "")?.trim() ?: ""
        if (customKey.isNotBlank()) return customKey

        val buildKey = BuildConfig.GEMINI_API_KEY.trim()
        if (buildKey.isNotBlank() && buildKey != "MY_GEMINI_API_KEY") return buildKey

        return ""
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "FloatingBubbleService onDestroy")
        lifecycleRegistry.handleLifecycleEvent(androidx.lifecycle.Lifecycle.Event.ON_PAUSE)
        lifecycleRegistry.handleLifecycleEvent(androidx.lifecycle.Lifecycle.Event.ON_STOP)
        lifecycleRegistry.handleLifecycleEvent(androidx.lifecycle.Lifecycle.Event.ON_DESTROY)
        store.clear()

        isPendingInjectionOnSmartCompletion = false
        isWaitingForLiveSmartModeCompletion = false
        pendingCompletionTimeoutJob?.cancel()
        pendingCompletionTimeoutJob = null
        mainHandler.removeCallbacks(shrinkRunnable)
        snapAnimator?.cancel()
        snapAnimator = null
        colorShiftAnimator?.cancel()
        colorShiftAnimator = null
        stopVoiceTyping()
        serviceScope.cancel()

        if (overlayView != null) {
            try {
                windowManager?.removeView(overlayView)
            } catch (e: Exception) {
                Log.e(TAG, "Error removing overlayView from WindowManager", e)
            }
            overlayView = null
        }

        if (instance == this) {
            instance = null
        }
    }
}
