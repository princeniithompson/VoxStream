package com.example.ui.components

import android.content.Context
import android.os.Build
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.R
import com.example.service.FloatingBubbleManager

/**
 * Dynamic Aurora Color Palette extracted directly from the user's Android 12+ wallpaper Monet palette.
 * Automatically adapts on device (e.g. vibrant green on this device, pink on pink wallpapers, etc.).
 */
data class AuroraColorPalette(
    val primary: Color,
    val primaryLight: Color,
    val primaryVibrant: Color,
    val secondary: Color,
    val deep: Color
)

/**
 * Pure function to extract the dynamic color palette based on chosen tone.
 * Tones:
 * - "luminous": Bright, luminous dynamic wallpaper colors (default)
 * - "deep": Rich, saturated dark tones matching keyboard keys
 * - "muted": Soft, subtle secondary pastel dynamic colors
 * - "tertiary": Rich alternative tertiary dynamic accents
 */
fun getDynamicTonePalette(context: Context, toneId: String): AuroraColorPalette {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        try {
            when (toneId) {
                "deep" -> {
                    // Deep Keyboard Tone: rich darker dynamic hues matching Gboard key backgrounds
                    val c500 = ContextCompat.getColor(context, android.R.color.system_accent1_500)
                    val c600 = ContextCompat.getColor(context, android.R.color.system_accent1_600)
                    val c700 = ContextCompat.getColor(context, android.R.color.system_accent1_700)
                    val c800 = ContextCompat.getColor(context, android.R.color.system_accent1_800)
                    val c2_600 = ContextCompat.getColor(context, android.R.color.system_accent2_600)
                    return AuroraColorPalette(
                        primary = Color(c700),
                        primaryLight = Color(c500),
                        primaryVibrant = Color(c600),
                        secondary = Color(c2_600),
                        deep = Color(c800)
                    )
                }
                "muted" -> {
                    // Muted Tone: uses system_accent2 (secondary dynamic hue)
                    val a2_200 = ContextCompat.getColor(context, android.R.color.system_accent2_200)
                    val a2_300 = ContextCompat.getColor(context, android.R.color.system_accent2_300)
                    val a2_400 = ContextCompat.getColor(context, android.R.color.system_accent2_400)
                    val a2_600 = ContextCompat.getColor(context, android.R.color.system_accent2_600)
                    val a1_300 = ContextCompat.getColor(context, android.R.color.system_accent1_300)
                    return AuroraColorPalette(
                        primary = Color(a2_400),
                        primaryLight = Color(a2_200),
                        primaryVibrant = Color(a2_300),
                        secondary = Color(a1_300),
                        deep = Color(a2_600)
                    )
                }
                "tertiary" -> {
                    // Tertiary Accent Tone: uses system_accent3 (tertiary dynamic hue)
                    val a3_200 = ContextCompat.getColor(context, android.R.color.system_accent3_200)
                    val a3_300 = ContextCompat.getColor(context, android.R.color.system_accent3_300)
                    val a3_400 = ContextCompat.getColor(context, android.R.color.system_accent3_400)
                    val a3_600 = ContextCompat.getColor(context, android.R.color.system_accent3_600)
                    val a1_300 = ContextCompat.getColor(context, android.R.color.system_accent1_300)
                    return AuroraColorPalette(
                        primary = Color(a3_400),
                        primaryLight = Color(a3_200),
                        primaryVibrant = Color(a3_300),
                        secondary = Color(a1_300),
                        deep = Color(a3_600)
                    )
                }
                else -> {
                    // Luminous Tone (Default): light & bright shades (200, 300, 400)
                    val c200 = ContextCompat.getColor(context, android.R.color.system_accent1_200)
                    val c300 = ContextCompat.getColor(context, android.R.color.system_accent1_300)
                    val c400 = ContextCompat.getColor(context, android.R.color.system_accent1_400)
                    val c600 = ContextCompat.getColor(context, android.R.color.system_accent1_600)
                    val c2_300 = ContextCompat.getColor(context, android.R.color.system_accent2_300)
                    return AuroraColorPalette(
                        primary = Color(c400),
                        primaryLight = Color(c200),
                        primaryVibrant = Color(c300),
                        secondary = Color(c2_300),
                        deep = Color(c600)
                    )
                }
            }
        } catch (e: Throwable) {
            val darkDyn = dynamicDarkColorScheme(context)
            return when (toneId) {
                "deep" -> AuroraColorPalette(
                    primary = darkDyn.primaryContainer,
                    primaryLight = darkDyn.primary,
                    primaryVibrant = darkDyn.primaryContainer,
                    secondary = darkDyn.secondaryContainer,
                    deep = darkDyn.surfaceTint
                )
                "muted" -> AuroraColorPalette(
                    primary = darkDyn.secondary,
                    primaryLight = darkDyn.secondaryContainer,
                    primaryVibrant = darkDyn.secondary,
                    secondary = darkDyn.primary,
                    deep = darkDyn.surfaceTint
                )
                "tertiary" -> AuroraColorPalette(
                    primary = darkDyn.tertiary,
                    primaryLight = darkDyn.tertiaryContainer,
                    primaryVibrant = darkDyn.tertiary,
                    secondary = darkDyn.secondary,
                    deep = darkDyn.surfaceTint
                )
                else -> AuroraColorPalette(
                    primary = darkDyn.primary,
                    primaryLight = darkDyn.primaryContainer,
                    primaryVibrant = darkDyn.primary,
                    secondary = darkDyn.secondary,
                    deep = darkDyn.surfaceTint
                )
            }
        }
    } else {
        return when (toneId) {
            "deep" -> AuroraColorPalette(
                primary = Color(0xFF065F46),
                primaryLight = Color(0xFF047857),
                primaryVibrant = Color(0xFF059669),
                secondary = Color(0xFF0891B2),
                deep = Color(0xFF022C22)
            )
            "muted" -> AuroraColorPalette(
                primary = Color(0xFF0D9488),
                primaryLight = Color(0xFF5EEAD4),
                primaryVibrant = Color(0xFF2DD4BF),
                secondary = Color(0xFF38BDF8),
                deep = Color(0xFF134E4A)
            )
            "tertiary" -> AuroraColorPalette(
                primary = Color(0xFF8B5CF6),
                primaryLight = Color(0xFFC4B5FD),
                primaryVibrant = Color(0xFFA78BFA),
                secondary = Color(0xFFEC4899),
                deep = Color(0xFF4C1D95)
            )
            else -> AuroraColorPalette(
                primary = Color(0xFF10B981),
                primaryLight = Color(0xFF6EE7B7),
                primaryVibrant = Color(0xFF34D399),
                secondary = Color(0xFF06B6D4),
                deep = Color(0xFF064E3B)
            )
        }
    }
}

@Composable
fun rememberDynamicAuroraPalette(toneIdOverride: String? = null): AuroraColorPalette {
    val context = LocalContext.current
    val globalTone by FloatingBubbleManager.selectedColorTone.collectAsState()
    val activeTone = toneIdOverride ?: globalTone
    return remember(context, activeTone) {
        getDynamicTonePalette(context, activeTone)
    }
}

/**
 * Custom Notch Shape that cuts an arc bite out of the top-right corner
 * to dock the lifebuoy ring cleanly into the container's border with smooth rounded fillet transitions.
 */
class NotchedPopupShape(
    private val cornerRadius: Dp = 24.dp,
    private val notchRadius: Dp = 25.5.dp,
    private val notchCenterOffsetX: Dp = 28.dp,
    private val notchCenterOffsetY: Dp = 2.dp,
    private val filletRadius: Dp = 8.dp
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        val path = createNotchedPath(
            size = size,
            density = density,
            cornerRadius = cornerRadius,
            notchRadius = notchRadius,
            notchCenterOffsetX = notchCenterOffsetX,
            notchCenterOffsetY = notchCenterOffsetY,
            filletRadius = filletRadius
        )
        return Outline.Generic(path)
    }
}

/**
 * Constructs the precise outer path including rounded corners and the top-right concave notch
 * with smooth convex fillet curves blending the notch seamlessly into the container's top and right edges.
 */
fun createNotchedPath(
    size: Size,
    density: Density,
    cornerRadius: Dp = 24.dp,
    notchRadius: Dp = 25.5.dp,
    notchCenterOffsetX: Dp = 28.dp,
    notchCenterOffsetY: Dp = 2.dp,
    filletRadius: Dp = 8.dp
): Path {
    val path = Path()
    with(density) {
        val r = cornerRadius.toPx()
        val nr = notchRadius.toPx()
        val frLeft = filletRadius.toPx()
        val frRight = 14.dp.toPx() // Dedicated right fillet radius to blend notch into right edge
        val w = size.width
        val h = size.height
        val cx = w - notchCenterOffsetX.toPx()
        val cy = notchCenterOffsetY.toPx()

        // 1. Left Fillet Geometry (tangent to top horizontal edge y = 0 and notch circle)
        val sumRLeft = nr + frLeft
        val dyLeft = frLeft - cy
        val dxLeft = kotlin.math.sqrt((sumRLeft * sumRLeft - dyLeft * dyLeft).coerceAtLeast(0f))
        val fxLeft = cx - dxLeft
        val alphaRadLeft = kotlin.math.atan2(dyLeft.toDouble(), dxLeft.toDouble())
        val alphaDegLeft = Math.toDegrees(alphaRadLeft).toFloat()

        // 2. Right Fillet Geometry (tangent to right vertical edge x = w and notch circle)
        val offsetRight = (w - cx).coerceAtLeast(0f)
        val sumRRight = nr + frRight
        val dxRight = (offsetRight - frRight).coerceAtLeast(0f)
        val dyRight = kotlin.math.sqrt((sumRRight * sumRRight - dxRight * dxRight).coerceAtLeast(0f))
        val fyRight = cy + dyRight
        val fxRight = w - frRight

        val betaRadRight = kotlin.math.atan2(dyRight.toDouble(), dxRight.toDouble())
        val betaDegRight = Math.toDegrees(betaRadRight).toFloat()

        // 1. Start at top-left corner
        path.moveTo(r, 0f)

        // 2. Top horizontal edge running right to left fillet start
        val leftFilletStartX = fxLeft.coerceIn(r, cx)
        path.lineTo(leftFilletStartX, 0f)

        // 3. Left Fillet Arc: Smooth convex curve blending top edge down into notch
        path.arcTo(
            rect = Rect(fxLeft - frLeft, 0f, fxLeft + frLeft, 2 * frLeft),
            startAngleDegrees = 270f,
            sweepAngleDegrees = 90f - alphaDegLeft,
            forceMoveTo = false
        )

        // 4. Concave Notch Arc: Circular bite wrapping snugly around the lifebuoy ring
        path.arcTo(
            rect = Rect(cx - nr, cy - nr, cx + nr, cy + nr),
            startAngleDegrees = 180f - alphaDegLeft,
            sweepAngleDegrees = -(180f - alphaDegLeft - betaDegRight),
            forceMoveTo = false
        )

        // 5. Right Fillet Arc: Smooth convex curve blending notch directly into right vertical wall
        path.arcTo(
            rect = Rect(fxRight - frRight, fyRight - frRight, fxRight + frRight, fyRight + frRight),
            startAngleDegrees = 180f + betaDegRight,
            sweepAngleDegrees = 180f - betaDegRight,
            forceMoveTo = false
        )

        // 6. Right edge downwards to bottom-right corner
        val rightEdgeStartY = fyRight.coerceIn(r, h - r)
        path.lineTo(w, rightEdgeStartY)
        path.lineTo(w, h - r)

        // 7. Bottom-right rounded corner
        path.arcTo(
            rect = Rect(w - 2 * r, h - 2 * r, w, h),
            startAngleDegrees = 0f,
            sweepAngleDegrees = 90f,
            forceMoveTo = false
        )

        // 8. Bottom edge to bottom-left corner
        path.lineTo(r, h)

        // 9. Bottom-left rounded corner
        path.arcTo(
            rect = Rect(0f, h - 2 * r, 2 * r, h),
            startAngleDegrees = 90f,
            sweepAngleDegrees = 90f,
            forceMoveTo = false
        )

        // 10. Left edge up to top-left corner
        path.lineTo(0f, r)

        // 11. Top-left rounded corner
        path.arcTo(
            rect = Rect(0f, 0f, 2 * r, 2 * r),
            startAngleDegrees = 180f,
            sweepAngleDegrees = 90f,
            forceMoveTo = false
        )

        path.close()
    }
    return path
}

class NotchedContainerShape(
    private val cornerRadius: Dp,
    private val notchRadius: Dp,
    private val notchCenterOffsetX: Dp,
    private val notchCenterOffsetY: Dp,
    private val filletRadius: Dp
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        val path = createNotchedPath(
            size = size,
            density = density,
            cornerRadius = cornerRadius,
            notchRadius = notchRadius,
            notchCenterOffsetX = notchCenterOffsetX,
            notchCenterOffsetY = notchCenterOffsetY,
            filletRadius = filletRadius
        )
        return Outline.Generic(path)
    }
}

/**
 * Floating Dictation Popup matching the exact reference UI design:
 * - Highly transparent dark frosted glass container (underlying screen content visibly readable)
 * - Soft dynamic Aurora Glow bloom diffusing from within the container and around the notch
 * - Semicircular cutout notch in top-right corner with nested, completely unclipped lifebuoy ring
 * - Left-aligned text input area with active blinking cursor and auto-scroll
 * - Compact button row (Cancel | Polish | Complete)
 * - Drag handle on the Lifebuoy Bubble: drag anywhere on screen freely, stays wherever released
 */
@Composable
fun FloatingDictationPopup(
    transcriptText: String,
    isRecording: Boolean,
    isPendingFinalizing: Boolean,
    isPolishing: Boolean,
    audioAmplitude: Float = 0f,
    glowStyleId: String = GlowAnimationCatalogue.DEFAULT_ID,
    finishingStyleId: String = FinishingAnimationCatalogue.DEFAULT_ID,
    onCancelClick: () -> Unit,
    onPolishClick: () -> Unit,
    onCompleteClick: () -> Unit,
    onLifebuoyClick: () -> Unit,
    onDragStart: () -> Unit = {},
    onDrag: (Float, Float) -> Unit = { _, _ -> },
    onDragEnd: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val palette = rememberDynamicAuroraPalette()

    // Aurora pulse animation for live speech
    val infiniteTransition = rememberInfiniteTransition(label = "auroraGlow")
    val glowPulse by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glowPulse"
    )

    val phase1 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2f * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(2600, easing = androidx.compose.animation.core.LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase1"
    )

    val phase2 by infiniteTransition.animateFloat(
        initialValue = (2f * Math.PI).toFloat(),
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(3600, easing = androidx.compose.animation.core.LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase2"
    )

    // Asymmetric audio amplitude tracking: instant attack surge with smooth organic decay
    val rawTargetAmp = if (isRecording) audioAmplitude.coerceIn(0f, 1f) else 0f
    var prevTargetAmp by remember { mutableFloatStateOf(0f) }
    val isAttacking = rawTargetAmp > prevTargetAmp

    val animatedAmplitude by androidx.compose.animation.core.animateFloatAsState(
        targetValue = rawTargetAmp,
        animationSpec = if (isAttacking) {
            androidx.compose.animation.core.spring(
                dampingRatio = androidx.compose.animation.core.Spring.DampingRatioNoBouncy,
                stiffness = androidx.compose.animation.core.Spring.StiffnessHigh // 10,000: immediate attack surge
            )
        } else {
            androidx.compose.animation.core.spring(
                dampingRatio = androidx.compose.animation.core.Spring.DampingRatioNoBouncy,
                stiffness = androidx.compose.animation.core.Spring.StiffnessLow // Smooth natural release decay
            )
        },
        label = "animatedAmplitude"
    )

    LaunchedEffect(rawTargetAmp) {
        prevTargetAmp = rawTargetAmp
    }

    // Gentle ambient breathing pulse for idle state when silent
    val idleBreathing by infiniteTransition.animateFloat(
        initialValue = 0.12f,
        targetValue = 0.24f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "idleBreathing"
    )

    // Local completing state for instantaneous UI feedback on tap
    var isCompletingLocally by remember { mutableStateOf(false) }
    val isFinalizing = isPendingFinalizing || isCompletingLocally
    val isFinishingActive = isFinalizing || isPolishing

    // Exit progression for Sunset Mirage reverse logic (takes 1400ms):
    // 0..0.42: contracts inward to Cancel "N" and Complete "P"
    // 0.42..1.0: settled wave block glides smoothly to the right and fades out
    val exitAnim = remember { androidx.compose.animation.core.Animatable(0f) }
    LaunchedEffect(isFinishingActive) {
        if (isFinishingActive) {
            exitAnim.animateTo(
                targetValue = 1f,
                animationSpec = androidx.compose.animation.core.tween(
                    durationMillis = 1400,
                    easing = androidx.compose.animation.core.LinearEasing
                )
            )
        } else {
            exitAnim.snapTo(0f)
        }
    }
    val morphProgress = exitAnim.value

    // Slow-network waiting loop:
    // If complete is clicked and the initial exit has finished (1400ms) but loading/injection
    // is still ongoing, repeatedly and gently sweep across from left to right (~1800ms per pass).
    val waitingLoopAnim by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "waitingLoopAnim"
    )
    val waitingLoopProgress = if (isFinishingActive && morphProgress >= 0.999f) waitingLoopAnim else 0f

    // Entrance sequence for Sunset Mirage:
    // 1. Container appears -> wait 350ms (1/3 second) -> softly expand from center outward to full width in 280ms
    val sunsetEntranceAnim = remember { androidx.compose.animation.core.Animatable(0f) }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(350L)
        sunsetEntranceAnim.animateTo(
            targetValue = 1f,
            animationSpec = androidx.compose.animation.core.tween(
                durationMillis = 280,
                easing = FastOutSlowInEasing
            )
        )
    }
    val sunsetEntranceProgress = sunsetEntranceAnim.value

    LaunchedEffect(isRecording) {
        if (isRecording) {
            isCompletingLocally = false
        }
    }

    // Cursor blink animation
    val cursorAlpha by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 550, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "cursorBlink"
    )

    val cornerRadius = 24.dp
    val notchRadius = 25.5.dp
    val notchCenterOffsetX = 28.dp
    val notchCenterOffsetY = 2.dp
    val filletRadius = 8.dp

    val scrollState = rememberScrollState()

    // Auto-scroll to keep most recent text & cursor in view as transcript expands
    LaunchedEffect(transcriptText) {
        if (transcriptText.isNotEmpty()) {
            scrollState.animateScrollTo(scrollState.maxValue)
        }
    }

    // Outer container without clip: lifebuoy ring renders in top 22dp margin without clipping
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 4.dp)
    ) {
        val notchedShape = remember(cornerRadius, notchRadius, notchCenterOffsetX, notchCenterOffsetY, filletRadius) {
            NotchedContainerShape(
                cornerRadius = cornerRadius,
                notchRadius = notchRadius,
                notchCenterOffsetX = notchCenterOffsetX,
                notchCenterOffsetY = notchCenterOffsetY,
                filletRadius = filletRadius
            )
        }

        // 1. Container Card with Notched Shape, Aurora Internal Bloom & Translucent Glass Fill
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 22.dp) // Leave headroom for the docked lifebuoy ring
                .clip(notchedShape)
                .drawBehind {
                    val path = createNotchedPath(
                        size = size,
                        density = this,
                        cornerRadius = cornerRadius,
                        notchRadius = notchRadius,
                        notchCenterOffsetX = notchCenterOffsetX,
                        notchCenterOffsetY = notchCenterOffsetY,
                        filletRadius = filletRadius
                    )

                    val cx = size.width - notchCenterOffsetX.toPx()
                    val cy = notchCenterOffsetY.toPx()

                    // All background layers strictly clipped inside the container notched path
                    clipPath(path) {
                        // Aurora Bloom Layer 1: Ambient light diffusing across the interior
                        drawRect(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    palette.primaryVibrant.copy(alpha = 0.38f * glowPulse),
                                    palette.secondary.copy(alpha = 0.18f * glowPulse),
                                    Color.Transparent
                                ),
                                center = Offset(size.width * 0.38f, size.height * 0.45f),
                                radius = size.width * 0.75f
                            )
                        )

                        // Aurora Bloom Layer 2: Focused light bloom softly wrapping the inside of the cutout notch
                        drawRect(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    palette.primaryLight.copy(alpha = 0.35f * glowPulse),
                                    palette.primary.copy(alpha = 0.12f * glowPulse),
                                    Color.Transparent
                                ),
                                center = Offset(cx, cy + 12.dp.toPx()),
                                radius = 48.dp.toPx()
                            )
                        )

                        // Frosted Glass Background Fill: Highly transparent dark glass
                        drawRect(
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    Color(0x800A1513), // ~50% opacity translucent dark glass
                                    Color(0x94060F0E)  // ~58% opacity bottom glass
                                )
                            )
                        )

                        // Dynamic Glowing Container Animation (Configurable across 21 ambient & wave animation styles)
                        val baseAmp = if (isRecording) {
                            maxOf(animatedAmplitude, idleBreathing)
                        } else {
                            0.06f
                        }

                        // All 21 styles use the universal continuous lifecycle (entrance -> live speech -> contract & glide exit -> wait loop)
                        val effectiveAmp = baseAmp
                        val glowBrightness = (0.38f + 0.62f * effectiveAmp)

                        if (glowBrightness > 0.005f) {
                            renderGlowAnimation(
                                styleId = glowStyleId,
                                palette = palette,
                                effectiveAmp = effectiveAmp,
                                glowBrightness = glowBrightness,
                                phase1 = phase1,
                                phase2 = phase2,
                                completionMorphProgress = morphProgress,
                                entranceProgress = sunsetEntranceProgress,
                                waitingLoopProgress = waitingLoopProgress
                            )
                        }
                    }

                    // Aurora Edge Catch: Soft ambient luminous border following the notch and container perimeter
                    drawPath(
                        path = path,
                        brush = Brush.horizontalGradient(
                            colors = listOf(
                                palette.primary.copy(alpha = 0.55f * glowPulse),
                                palette.primaryLight.copy(alpha = 0.80f * glowPulse),
                                palette.secondary.copy(alpha = 0.50f * glowPulse)
                            )
                        ),
                        style = Stroke(
                            width = 1.35.dp.toPx(),
                            cap = StrokeCap.Round,
                            join = StrokeJoin.Round
                        )
                    )
                }
                .padding(top = 16.dp, bottom = 12.dp, start = 16.dp, end = 16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight()
            ) {
                // Top Text Input Row: Left-aligned with auto-scroll and blinking cursor
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 38.dp, max = 110.dp)
                        .padding(end = 40.dp) // Space for docked lifebuoy in top right
                        .verticalScroll(scrollState)
                ) {
                    if (transcriptText.isBlank()) {
                        // Placeholder state: "Type your message..." with active cursor
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            // Blinking cursor
                            Box(
                                modifier = Modifier
                                    .width(2.dp)
                                    .height(18.dp)
                                    .background(palette.primaryVibrant.copy(alpha = cursorAlpha))
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Type your message...",
                                style = TextStyle(
                                    color = Color(0xB3CBD5E1),
                                    fontSize = 15.sp,
                                    fontFamily = FontFamily.SansSerif,
                                    fontWeight = FontWeight.Normal,
                                    shadow = Shadow(
                                        color = Color(0xCC000000),
                                        blurRadius = 4f
                                    )
                                ),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    } else {
                        // Active live transcript: Crisp high-contrast white text with shadow & trailing cursor
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.Top
                        ) {
                            Text(
                                text = transcriptText,
                                style = TextStyle(
                                    color = Color(0xFFFFFFFF), // Pure readable high-contrast white
                                    fontSize = 15.sp,
                                    fontFamily = FontFamily.SansSerif,
                                    fontWeight = FontWeight.Normal,
                                    lineHeight = 21.sp,
                                    shadow = Shadow(
                                        color = Color(0xE6000000), // Dark shadow guarantees 100% contrast over any background
                                        offset = Offset(0f, 1f),
                                        blurRadius = 6f
                                    )
                                ),
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Box(
                                modifier = Modifier
                                    .width(2.dp)
                                    .height(18.dp)
                                    .background(palette.primaryVibrant.copy(alpha = cursorAlpha))
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Bottom Button Row: Cancel | Polish | Complete (matching reference image)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 1. Cancel Button: Muted dark pill with Red X icon
                    PillActionButton(
                        onClick = onCancelClick,
                        backgroundColor = Color(0x381E1E24),
                        borderColor = Color(0x26FF5252),
                        enabled = !isFinalizing && !isPolishing,
                        modifier = Modifier.weight(1f)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = "Cancel",
                                tint = Color(0xFFFF5252),
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = "Cancel",
                                color = Color(0xFFFF7B7B),
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    // 2. Polish Button: Translucent pill with Sparkle icon
                    PillActionButton(
                        onClick = onPolishClick,
                        backgroundColor = Color(0x24FFFFFF),
                        borderColor = Color(0x24FFFFFF),
                        enabled = !isPolishing && !isFinalizing,
                        modifier = Modifier.weight(1f)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            if (isPolishing) {
                                Text(
                                    text = "Polishing...",
                                    color = Color(0xFFE2E8F0),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            } else {
                                Text(
                                    text = "✦",
                                    color = palette.primaryVibrant,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.width(5.dp))
                                Text(
                                    text = "Polish",
                                    color = Color(0xFFE2E8F0),
                                    fontSize = 13.5.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }

                    // 3. Complete Button: Solid Dynamic Accent Pill with Checkmark
                    val onAccentColor = if (isColorDark(palette.primaryVibrant)) Color.White else Color(0xFF042F2E)

                    PillActionButton(
                        onClick = {
                            if (!isFinalizing && !isPolishing) {
                                isCompletingLocally = true
                                onCompleteClick()
                            }
                        },
                        backgroundColor = if (isFinalizing) palette.primaryVibrant.copy(alpha = 0.85f) else palette.primaryVibrant,
                        borderColor = Color.Transparent,
                        enabled = !isFinalizing && !isPolishing,
                        modifier = Modifier.weight(1.2f)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            if (isFinalizing) {
                                Text(
                                    text = "Completing...",
                                    color = onAccentColor,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Filled.Check,
                                    contentDescription = "Complete",
                                    tint = onAccentColor,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(5.dp))
                                Text(
                                    text = "Complete",
                                    color = onAccentColor,
                                    fontSize = 13.5.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }

                // Tiny Context Status Text: Centered directly beneath the buttons (e.g. "AI · Grok")
                val sessionContext by FloatingBubbleManager.lockedSessionContext.collectAsState()
                val currentPkg by FloatingBubbleManager.currentForegroundPackage.collectAsState()
                val context = LocalContext.current
                val displayContext = sessionContext ?: remember(currentPkg) {
                    com.example.service.AppContextResolver.resolve(context, currentPkg)?.formatted
                }

                if (!displayContext.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(5.dp))
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = displayContext,
                            style = TextStyle(
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
                                fontSize = 10.5.sp,
                                fontFamily = FontFamily.SansSerif,
                                fontWeight = FontWeight.Normal,
                                letterSpacing = 0.2.sp
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

        // 2. Docked Lifebuoy Ring: Completely SEPARATE unclipped composable with direct drag handling
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(end = 4.dp) // Matches notch centerX offset (24dp from right)
                .size(48.dp)
                .clip(CircleShape)
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { onDragStart() },
                        onDragEnd = { onDragEnd() },
                        onDragCancel = { onDragEnd() },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            onDrag(dragAmount.x, dragAmount.y)
                        }
                    )
                }
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onLifebuoyClick
                ),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(id = R.drawable.ic_lifebuoy_ring),
                contentDescription = "Lifebuoy voice typing",
                modifier = Modifier.size(46.dp)
            )
        }
    }
}

/**
 * Compact Pill-shaped Action Button matching the reference image's tight height and rounded contours.
 */
@Composable
fun PillActionButton(
    onClick: () -> Unit,
    backgroundColor: Color,
    borderColor: Color,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable () -> Unit
) {
    val shape = RoundedCornerShape(20.dp)
    Box(
        modifier = modifier
            .height(36.dp)
            .clip(shape)
            .background(backgroundColor)
            .then(
                if (borderColor != Color.Transparent) {
                    Modifier.border(1.dp, borderColor, shape)
                } else Modifier
            )
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}

/**
 * Collapsed floating lifebuoy bubble when the full popup is minimized.
 * Wispr Flow behavior:
 * - Starts at full 56dp size upon every appearance.
 * - After 3s idle inactivity, smoothly shrinks to a compact 45dp bubble (comfortably tappable).
 * - When dragged in shrunk state, remains compact and small without expanding.
 * - Only expands back to full 56dp size when tapped by the user.
 * - Aligns flush against the screen bezel (left or right) without leaving gaps.
 */
@Composable
fun FloatingCollapsedBubble(
    isRecording: Boolean,
    isShrunk: Boolean = false,
    isSnappedToRight: Boolean = true,
    onClick: () -> Unit,
    onDragStart: () -> Unit = {},
    onDrag: (Float, Float) -> Unit = { _, _ -> },
    onDragEnd: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val palette = rememberDynamicAuroraPalette()
    val infiniteTransition = rememberInfiniteTransition(label = "collapsedGlow")
    val pulse by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    val animatedBubbleSize by androidx.compose.animation.core.animateDpAsState(
        targetValue = if (isShrunk) 45.dp else 56.dp,
        animationSpec = androidx.compose.animation.core.spring(
            dampingRatio = androidx.compose.animation.core.Spring.DampingRatioMediumBouncy,
            stiffness = androidx.compose.animation.core.Spring.StiffnessMediumLow
        ),
        label = "bubbleSize"
    )

    val animatedImageSize by androidx.compose.animation.core.animateDpAsState(
        targetValue = if (isShrunk) 40.dp else 48.dp,
        animationSpec = androidx.compose.animation.core.spring(
            dampingRatio = androidx.compose.animation.core.Spring.DampingRatioMediumBouncy,
            stiffness = androidx.compose.animation.core.Spring.StiffnessMediumLow
        ),
        label = "imageSize"
    )

    val animatedAlpha by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (isShrunk) 0.85f else 1.0f,
        animationSpec = androidx.compose.animation.core.tween(300),
        label = "bubbleAlpha"
    )

    Box(
        modifier = modifier
            .size(56.dp)
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { onDragStart() },
                    onDragEnd = { onDragEnd() },
                    onDragCancel = { onDragEnd() },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        onDrag(dragAmount.x, dragAmount.y)
                    }
                )
            }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            ),
        contentAlignment = if (isSnappedToRight) Alignment.CenterEnd else Alignment.CenterStart
    ) {
        Box(
            modifier = Modifier
                .size(animatedBubbleSize)
                .clip(CircleShape),
            contentAlignment = Alignment.Center
        ) {
            if (isRecording) {
                Box(
                    modifier = Modifier
                        .size(animatedBubbleSize * 0.95f * pulse)
                        .background(palette.primaryVibrant.copy(alpha = 0.35f), CircleShape)
                )
            }
            Image(
                painter = painterResource(id = R.drawable.ic_lifebuoy_ring),
                contentDescription = "Floating voice bubble",
                alpha = animatedAlpha,
                modifier = Modifier.size(animatedImageSize)
            )
        }
    }
}

/**
 * Helper to determine if a color is dark to ensure high-contrast on-color selection.
 */
private fun isColorDark(color: Color): Boolean {
    val darkness = 1 - (0.299 * color.red + 0.587 * color.green + 0.114 * color.blue)
    return darkness >= 0.5
}
