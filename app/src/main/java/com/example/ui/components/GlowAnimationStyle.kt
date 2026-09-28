package com.example.ui.components

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Metadata and catalogue for ambient glowing container animations.
 */
data class GlowAnimationStyle(
    val id: String,
    val name: String,
    val subtitle: String,
    val category: String
)

object GlowAnimationCatalogue {
    const val DEFAULT_ID = "gemini_live"

    val styles: List<GlowAnimationStyle> = listOf(
        GlowAnimationStyle(
            id = "gemini_live",
            name = "Gemini Live Aurora",
            subtitle = "Authentic diffused ambient light bloom surging from the bottom edge",
            category = "Default"
        ),
        GlowAnimationStyle(
            id = "gentle_slice",
            name = "Gentle Wave Slice",
            subtitle = "Luminous flowing slice ribbon rippling softly along the bottom base",
            category = "Wave Slice"
        ),
        GlowAnimationStyle(
            id = "northern_lights",
            name = "Northern Lights Curtain",
            subtitle = "Dancing vertical curtains of emerald, cyan, and violet aurora shimmer",
            category = "Aurora"
        ),
        GlowAnimationStyle(
            id = "pulsing_spotlight",
            name = "Flashlight Core Beacon",
            subtitle = "Focused central flashlight beam swelling dynamically with voice intensity",
            category = "Ambient Beam"
        ),
        GlowAnimationStyle(
            id = "dual_orbit",
            name = "Dual Orbital Fusion",
            subtitle = "Two complementary glowing light spheres orbiting and blending fluidly",
            category = "Orbital"
        ),
        GlowAnimationStyle(
            id = "breathing_horizon",
            name = "Breathing Horizon",
            subtitle = "Calm sweeping horizontal band of ambient diffused light rising calmly",
            category = "Horizon"
        ),
        GlowAnimationStyle(
            id = "liquid_neon",
            name = "Liquid Neon Pool",
            subtitle = "Organic fluid neon pool morphing along the container contours",
            category = "Fluid Neon"
        ),
        GlowAnimationStyle(
            id = "ethereal_vapor",
            name = "Ethereal Nebula Vapor",
            subtitle = "Soft mystical nebula fog drifting upward behind the text",
            category = "Ethereal"
        ),
        GlowAnimationStyle(
            id = "prism_ripple",
            name = "Prism Ripple Slice",
            subtitle = "Multi-chromatic harmonic light ripples fanning outward across the base",
            category = "Prism"
        ),
        GlowAnimationStyle(
            id = "electric_sunburst",
            name = "Electric Sunburst",
            subtitle = "Diffused sunburst energy rays fanning upward from a bottom anchor point",
            category = "Radiant"
        ),
        GlowAnimationStyle(
            id = "cyberpunk_synth",
            name = "Cyberpunk Synthwave",
            subtitle = "Dual-gradient electric magenta and cyan laser-diffuse upward glow",
            category = "Cyberpunk"
        ),
        GlowAnimationStyle(
            id = "supernova_bloom",
            name = "Supernova Flash",
            subtitle = "Radiant high-intensity ambient core that bursts outward upon voice attack",
            category = "Explosive"
        ),
        GlowAnimationStyle(
            id = "zenith_pillar",
            name = "Zenith Light Pillar",
            subtitle = "Vertical luminous pillar ascending through the center of the card",
            category = "Pillar"
        ),
        GlowAnimationStyle(
            id = "bioluminescent_deep",
            name = "Bioluminescent Shimmer",
            subtitle = "Subtle underwater bioluminescent luminance rippling organically",
            category = "Organic"
        ),
        GlowAnimationStyle(
            id = "sunset_mirage",
            name = "Sunset Mirage",
            subtitle = "Warm molten gold, coral orange, and deep rose gradient twilight swell",
            category = "Warm Sunset"
        ),
        GlowAnimationStyle(
            id = "cosmic_vortex",
            name = "Cosmic Galaxy Vortex",
            subtitle = "Swirling soft spiral of starlight luminescence with gentle rotational drift",
            category = "Cosmic"
        ),
        GlowAnimationStyle(
            id = "harmonic_ribbon",
            name = "Harmonic Crest Ribbon",
            subtitle = "Dual overlapping translucent glowing wave ribbons with phase harmonics",
            category = "Harmonic"
        ),
        GlowAnimationStyle(
            id = "plasma_fusion",
            name = "Plasma Fusion Wave",
            subtitle = "Intertwining hot and cool plasma streams merging and dancing with volume",
            category = "Plasma"
        ),
        GlowAnimationStyle(
            id = "quantum_halos",
            name = "Quantum Halos",
            subtitle = "Concentric diffused ambient halos pulsing softly from bottom center",
            category = "Quantum"
        ),
        GlowAnimationStyle(
            id = "liquid_glass",
            name = "Liquid Specular Glass",
            subtitle = "Crystalline refraction wash reflecting along the base like liquid glass",
            category = "Crystalline"
        ),
        GlowAnimationStyle(
            id = "velvet_aura",
            name = "Velvet Ambient Calm",
            subtitle = "Ultra-minimalist, deep, smooth ambient illumination for zero distraction",
            category = "Minimalist"
        )
    )

    fun getById(id: String): GlowAnimationStyle {
        return styles.find { it.id == id } ?: styles.first()
    }
}

/**
 * Master renderer for all glowing container animations.
 * Completely hardware accelerated within Compose DrawScope.
 */
fun DrawScope.renderGlowAnimation(
    styleId: String,
    palette: AuroraColorPalette,
    effectiveAmp: Float,
    glowBrightness: Float,
    phase1: Float,
    phase2: Float
) {
    val w = size.width
    val h = size.height

    when (styleId) {
        // 1. Gemini Live Aurora (Default)
        "gemini_live" -> {
            val swellHeight = (42.dp.toPx() + (h * 0.72f) * effectiveAmp).coerceAtMost(h)
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color.Transparent,
                        palette.secondary.copy(alpha = 0.28f * glowBrightness),
                        palette.primaryVibrant.copy(alpha = 0.70f * glowBrightness),
                        palette.primaryLight.copy(alpha = 0.92f * glowBrightness)
                    ),
                    startY = (h - swellHeight).coerceAtLeast(0f),
                    endY = h
                )
            )
            val bloomRadius = w * 0.70f + (w * 0.40f) * effectiveAmp
            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(
                        palette.primaryLight.copy(alpha = 0.80f * glowBrightness),
                        palette.primaryVibrant.copy(alpha = 0.50f * glowBrightness),
                        palette.secondary.copy(alpha = 0.22f * glowBrightness),
                        Color.Transparent
                    ),
                    center = Offset(w * 0.50f, h + 12.dp.toPx()),
                    radius = bloomRadius
                )
            )
            val textBacklightRadius = w * 0.60f + (w * 0.35f) * effectiveAmp
            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(
                        palette.primaryVibrant.copy(alpha = (0.30f * glowBrightness * effectiveAmp + 0.08f).coerceIn(0f, 0.45f)),
                        palette.secondary.copy(alpha = (0.15f * glowBrightness * effectiveAmp).coerceIn(0f, 0.30f)),
                        Color.Transparent
                    ),
                    center = Offset(w * 0.40f, h * 0.60f),
                    radius = textBacklightRadius
                )
            )
        }

        // 2. Gentle Wave Slice (User Requested Luminous Slice Wave)
        "gentle_slice" -> {
            // Soft base ambient glow
            val baseHeight = (28.dp.toPx() + 45.dp.toPx() * effectiveAmp).coerceAtMost(h)
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color.Transparent,
                        palette.secondary.copy(alpha = 0.20f * glowBrightness),
                        palette.primaryVibrant.copy(alpha = 0.55f * glowBrightness)
                    ),
                    startY = (h - baseHeight).coerceAtLeast(0f),
                    endY = h
                )
            )

            // Flowing harmonic slice body
            val steps = 40
            val stepW = w / steps
            val slicePath = Path()
            slicePath.moveTo(0f, h)
            for (i in 0..steps) {
                val x = i * stepW
                val normX = (i.toFloat() / steps.toFloat()) * 2f - 1f
                val envelope = (1f - normX * normX * 0.70f).coerceAtLeast(0.2f)
                val waveOffset = sin(phase1.toDouble() + (x / w) * 2.5 * PI).toFloat()
                val waveH = 14.dp.toPx() + (36.dp.toPx() * effectiveAmp * envelope * (0.6f + 0.4f * waveOffset))
                val y = (h - waveH).coerceAtLeast(0f)
                slicePath.lineTo(x, y)
            }
            slicePath.lineTo(w, h)
            slicePath.close()

            drawPath(
                path = slicePath,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        palette.primaryLight.copy(alpha = 0.65f * glowBrightness),
                        palette.primaryVibrant.copy(alpha = 0.75f * glowBrightness),
                        palette.deep.copy(alpha = 0.35f * glowBrightness)
                    ),
                    startY = h - 50.dp.toPx(),
                    endY = h
                )
            )

            // Luminous gentle slice crest line
            val crestPath = Path()
            for (i in 0..steps) {
                val x = i * stepW
                val normX = (i.toFloat() / steps.toFloat()) * 2f - 1f
                val envelope = (1f - normX * normX * 0.70f).coerceAtLeast(0.2f)
                val waveOffset = sin(phase1.toDouble() + (x / w) * 2.5 * PI).toFloat()
                val waveH = 14.dp.toPx() + (36.dp.toPx() * effectiveAmp * envelope * (0.6f + 0.4f * waveOffset))
                val y = (h - waveH).coerceAtLeast(0f)
                if (i == 0) crestPath.moveTo(x, y) else crestPath.lineTo(x, y)
            }
            drawPath(
                path = crestPath,
                brush = Brush.horizontalGradient(
                    colors = listOf(
                        palette.secondary.copy(alpha = 0.15f * glowBrightness),
                        palette.primaryLight.copy(alpha = 0.90f * glowBrightness),
                        palette.primaryVibrant.copy(alpha = 0.70f * glowBrightness),
                        palette.secondary.copy(alpha = 0.15f * glowBrightness)
                    )
                ),
                style = Stroke(width = 1.75.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
            )
        }

        // 3. Northern Lights Curtain
        "northern_lights" -> {
            val curtainCount = 5
            for (c in 0 until curtainCount) {
                val curX = (w / (curtainCount + 1)) * (c + 1) + (18.dp.toPx() * sin((phase1 + c * 1.2).toDouble())).toFloat()
                val curW = w * 0.35f
                val curAlpha = (0.25f + 0.55f * effectiveAmp) * (0.7f + 0.3f * cos((phase2 + c).toDouble()).toFloat())
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            if (c % 2 == 0) palette.primaryLight.copy(alpha = curAlpha * glowBrightness)
                            else palette.secondary.copy(alpha = curAlpha * glowBrightness),
                            palette.primaryVibrant.copy(alpha = curAlpha * 0.5f * glowBrightness),
                            Color.Transparent
                        ),
                        center = Offset(curX, h * 0.5f),
                        radius = curW
                    )
                )
            }
        }

        // 4. Flashlight Core Beacon
        "pulsing_spotlight" -> {
            val spotRadius = (w * 0.85f) * (0.75f + 0.45f * effectiveAmp)
            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(
                        palette.primaryLight.copy(alpha = 0.88f * glowBrightness),
                        palette.primaryVibrant.copy(alpha = 0.55f * glowBrightness),
                        palette.secondary.copy(alpha = 0.20f * glowBrightness),
                        Color.Transparent
                    ),
                    center = Offset(w * 0.5f, h + 15.dp.toPx()),
                    radius = spotRadius
                )
            )
        }

        // 5. Dual Orbital Fusion
        "dual_orbit" -> {
            val orbitRadius = 45.dp.toPx() + 25.dp.toPx() * effectiveAmp
            val angle = phase1.toDouble()
            val o1X = w * 0.5f + (cos(angle) * w * 0.26f).toFloat()
            val o1Y = h * 0.78f + (sin(angle) * 16.dp.toPx()).toFloat()

            val o2X = w * 0.5f - (cos(angle) * w * 0.26f).toFloat()
            val o2Y = h * 0.78f - (sin(angle) * 16.dp.toPx()).toFloat()

            // Orb 1
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        palette.primaryLight.copy(alpha = 0.82f * glowBrightness),
                        palette.primaryVibrant.copy(alpha = 0.40f * glowBrightness),
                        Color.Transparent
                    ),
                    center = Offset(o1X, o1Y),
                    radius = orbitRadius
                ),
                center = Offset(o1X, o1Y),
                radius = orbitRadius
            )
            // Orb 2
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        palette.secondary.copy(alpha = 0.78f * glowBrightness),
                        palette.primary.copy(alpha = 0.35f * glowBrightness),
                        Color.Transparent
                    ),
                    center = Offset(o2X, o2Y),
                    radius = orbitRadius
                ),
                center = Offset(o2X, o2Y),
                radius = orbitRadius
            )
        }

        // 6. Breathing Horizon
        "breathing_horizon" -> {
            val barH = (18.dp.toPx() + (h * 0.55f) * effectiveAmp).coerceAtMost(h)
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color.Transparent,
                        palette.secondary.copy(alpha = 0.35f * glowBrightness),
                        palette.primaryLight.copy(alpha = 0.85f * glowBrightness),
                        palette.primaryVibrant.copy(alpha = 0.90f * glowBrightness)
                    ),
                    startY = h - barH,
                    endY = h
                )
            )
        }

        // 7. Liquid Neon Pool
        "liquid_neon" -> {
            val poolCenterY = h + 10.dp.toPx()
            val morphX = w * 0.5f + (14.dp.toPx() * sin(phase1.toDouble())).toFloat()
            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(
                        palette.primaryVibrant.copy(alpha = 0.90f * glowBrightness),
                        palette.primaryLight.copy(alpha = 0.65f * glowBrightness),
                        palette.secondary.copy(alpha = 0.30f * glowBrightness),
                        Color.Transparent
                    ),
                    center = Offset(morphX, poolCenterY),
                    radius = w * 0.75f + (w * 0.35f) * effectiveAmp
                )
            )
        }

        // 8. Ethereal Nebula Vapor
        "ethereal_vapor" -> {
            val cloudSpread = w * 0.65f + 40.dp.toPx() * effectiveAmp
            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(
                        palette.secondary.copy(alpha = 0.60f * glowBrightness),
                        palette.primaryLight.copy(alpha = 0.35f * glowBrightness),
                        Color.Transparent
                    ),
                    center = Offset(w * 0.32f, h * 0.55f),
                    radius = cloudSpread
                )
            )
            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(
                        palette.primaryVibrant.copy(alpha = 0.55f * glowBrightness),
                        palette.primary.copy(alpha = 0.25f * glowBrightness),
                        Color.Transparent
                    ),
                    center = Offset(w * 0.68f, h * 0.65f),
                    radius = cloudSpread
                )
            )
        }

        // 9. Prism Ripple Slice
        "prism_ripple" -> {
            val rippleCount = 4
            for (r in 1..rippleCount) {
                val rRadius = (22.dp.toPx() * r) + (35.dp.toPx() * effectiveAmp)
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color.Transparent,
                            palette.primaryLight.copy(alpha = (0.45f / r) * glowBrightness),
                            palette.secondary.copy(alpha = (0.25f / r) * glowBrightness),
                            Color.Transparent
                        ),
                        center = Offset(w * 0.5f, h),
                        radius = rRadius
                    ),
                    center = Offset(w * 0.5f, h),
                    radius = rRadius
                )
            }
        }

        // 10. Electric Sunburst
        "electric_sunburst" -> {
            val rayH = (32.dp.toPx() + (h * 0.65f) * effectiveAmp).coerceAtMost(h)
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color.Transparent,
                        palette.primaryLight.copy(alpha = 0.40f * glowBrightness),
                        palette.primaryVibrant.copy(alpha = 0.85f * glowBrightness)
                    ),
                    startY = h - rayH,
                    endY = h
                )
            )
            val rayCenter = Offset(w * 0.5f, h)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        palette.primaryLight.copy(alpha = 0.95f * glowBrightness),
                        palette.secondary.copy(alpha = 0.45f * glowBrightness),
                        Color.Transparent
                    ),
                    center = rayCenter,
                    radius = 48.dp.toPx() + 65.dp.toPx() * effectiveAmp
                ),
                center = rayCenter,
                radius = 48.dp.toPx() + 65.dp.toPx() * effectiveAmp
            )
        }

        // 11. Cyberpunk Synthwave
        "cyberpunk_synth" -> {
            val cyberH = (30.dp.toPx() + (h * 0.68f) * effectiveAmp).coerceAtMost(h)
            drawRect(
                brush = Brush.horizontalGradient(
                    colors = listOf(
                        Color(0xFF00E5FF).copy(alpha = 0.70f * glowBrightness),
                        palette.primaryLight.copy(alpha = 0.85f * glowBrightness),
                        Color(0xFFFF007F).copy(alpha = 0.70f * glowBrightness)
                    ),
                    startX = 0f,
                    endX = w
                )
            )
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color.Transparent,
                        palette.primaryVibrant.copy(alpha = 0.80f * glowBrightness)
                    ),
                    startY = h - cyberH,
                    endY = h
                )
            )
        }

        // 12. Supernova Flash
        "supernova_bloom" -> {
            val coreRadius = 40.dp.toPx() + 85.dp.toPx() * effectiveAmp
            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.90f * glowBrightness),
                        palette.primaryLight.copy(alpha = 0.75f * glowBrightness),
                        palette.primaryVibrant.copy(alpha = 0.45f * glowBrightness),
                        Color.Transparent
                    ),
                    center = Offset(w * 0.5f, h * 0.75f),
                    radius = coreRadius
                )
            )
        }

        // 13. Zenith Light Pillar
        "zenith_pillar" -> {
            val pillarW = 60.dp.toPx() + 50.dp.toPx() * effectiveAmp
            drawRect(
                brush = Brush.horizontalGradient(
                    colors = listOf(
                        Color.Transparent,
                        palette.primaryLight.copy(alpha = 0.80f * glowBrightness),
                        palette.primaryVibrant.copy(alpha = 0.65f * glowBrightness),
                        Color.Transparent
                    ),
                    startX = (w - pillarW) / 2f,
                    endX = (w + pillarW) / 2f
                )
            )
        }

        // 14. Bioluminescent Shimmer
        "bioluminescent_deep" -> {
            val bioH = (26.dp.toPx() + 55.dp.toPx() * effectiveAmp).coerceAtMost(h)
            val shimmerAlpha = (0.35f + 0.45f * effectiveAmp) * (0.85f + 0.15f * sin(phase2.toDouble() * 2).toFloat())
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color.Transparent,
                        palette.secondary.copy(alpha = shimmerAlpha * 0.5f * glowBrightness),
                        palette.primaryVibrant.copy(alpha = shimmerAlpha * glowBrightness)
                    ),
                    startY = h - bioH,
                    endY = h
                )
            )
        }

        // 15. Sunset Mirage
        "sunset_mirage" -> {
            val sunsetH = (35.dp.toPx() + (h * 0.65f) * effectiveAmp).coerceAtMost(h)
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color.Transparent,
                        Color(0xFF7C4DFF).copy(alpha = 0.35f * glowBrightness),
                        Color(0xFFFF5252).copy(alpha = 0.65f * glowBrightness),
                        Color(0xFFFFD740).copy(alpha = 0.85f * glowBrightness)
                    ),
                    startY = h - sunsetH,
                    endY = h
                )
            )
        }

        // 16. Cosmic Galaxy Vortex
        "cosmic_vortex" -> {
            val vortexRadius = w * 0.65f + 35.dp.toPx() * effectiveAmp
            val vx = w * 0.5f + (10.dp.toPx() * cos(phase1.toDouble())).toFloat()
            val vy = h + (8.dp.toPx() * sin(phase1.toDouble())).toFloat()
            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(
                        palette.primaryLight.copy(alpha = 0.75f * glowBrightness),
                        palette.secondary.copy(alpha = 0.50f * glowBrightness),
                        palette.deep.copy(alpha = 0.25f * glowBrightness),
                        Color.Transparent
                    ),
                    center = Offset(vx, vy),
                    radius = vortexRadius
                )
            )
        }

        // 17. Harmonic Crest Ribbon
        "harmonic_ribbon" -> {
            val steps = 36
            val stepW = w / steps
            val ribbonPath = Path()
            ribbonPath.moveTo(0f, h)
            for (i in 0..steps) {
                val x = i * stepW
                val wave = sin(phase1.toDouble() + (x / w) * 3.0 * PI).toFloat()
                val waveH = 12.dp.toPx() + (32.dp.toPx() * effectiveAmp * (0.5f + 0.5f * wave))
                val y = (h - waveH).coerceAtLeast(0f)
                ribbonPath.lineTo(x, y)
            }
            ribbonPath.lineTo(w, h)
            ribbonPath.close()

            drawPath(
                path = ribbonPath,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        palette.secondary.copy(alpha = 0.60f * glowBrightness),
                        palette.primaryVibrant.copy(alpha = 0.75f * glowBrightness),
                        Color.Transparent
                    ),
                    startY = h - 45.dp.toPx(),
                    endY = h
                )
            )
        }

        // 18. Plasma Fusion Wave
        "plasma_fusion" -> {
            val plasmaH = (30.dp.toPx() + 65.dp.toPx() * effectiveAmp).coerceAtMost(h)
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color.Transparent,
                        palette.secondary.copy(alpha = 0.40f * glowBrightness),
                        palette.primaryLight.copy(alpha = 0.85f * glowBrightness)
                    ),
                    startY = h - plasmaH,
                    endY = h
                )
            )
            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(
                        palette.primaryVibrant.copy(alpha = 0.70f * glowBrightness),
                        Color.Transparent
                    ),
                    center = Offset(w * 0.5f, h * 0.75f),
                    radius = w * 0.5f * (0.6f + 0.4f * effectiveAmp)
                )
            )
        }

        // 19. Quantum Halos
        "quantum_halos" -> {
            for (k in 1..3) {
                val kRadius = (35.dp.toPx() * k) + (45.dp.toPx() * effectiveAmp)
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color.Transparent,
                            palette.primaryLight.copy(alpha = (0.50f / k) * glowBrightness),
                            Color.Transparent
                        ),
                        center = Offset(w * 0.5f, h + 5.dp.toPx()),
                        radius = kRadius
                    ),
                    center = Offset(w * 0.5f, h + 5.dp.toPx()),
                    radius = kRadius
                )
            }
        }

        // 20. Liquid Specular Glass
        "liquid_glass" -> {
            val glassH = (22.dp.toPx() + 45.dp.toPx() * effectiveAmp).coerceAtMost(h)
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color.Transparent,
                        Color.White.copy(alpha = 0.25f * glowBrightness),
                        palette.primaryLight.copy(alpha = 0.75f * glowBrightness),
                        palette.primaryVibrant.copy(alpha = 0.55f * glowBrightness)
                    ),
                    startY = h - glassH,
                    endY = h
                )
            )
        }

        // 21. Velvet Ambient Calm
        "velvet_aura" -> {
            val calmH = (20.dp.toPx() + 35.dp.toPx() * effectiveAmp).coerceAtMost(h)
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color.Transparent,
                        palette.primary.copy(alpha = 0.35f * glowBrightness),
                        palette.deep.copy(alpha = 0.50f * glowBrightness)
                    ),
                    startY = h - calmH,
                    endY = h
                )
            )
        }

        else -> {
            // Fallback to Gemini Live
            val swellHeight = (42.dp.toPx() + (h * 0.72f) * effectiveAmp).coerceAtMost(h)
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color.Transparent,
                        palette.secondary.copy(alpha = 0.28f * glowBrightness),
                        palette.primaryVibrant.copy(alpha = 0.70f * glowBrightness),
                        palette.primaryLight.copy(alpha = 0.92f * glowBrightness)
                    ),
                    startY = (h - swellHeight).coerceAtLeast(0f),
                    endY = h
                )
            )
        }
    }
}
