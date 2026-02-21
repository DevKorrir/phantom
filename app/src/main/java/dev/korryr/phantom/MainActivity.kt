package dev.korryr.phantom

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dagger.hilt.android.AndroidEntryPoint
import dev.korryr.phantom.service.OverlayService
import dev.korryr.phantom.ui.theme.OverlayBorder
import dev.korryr.phantom.ui.theme.PhantomAbyss
import dev.korryr.phantom.ui.theme.PhantomCyan
import dev.korryr.phantom.ui.theme.PhantomCyanDark
import dev.korryr.phantom.ui.theme.PhantomGhost
import dev.korryr.phantom.ui.theme.PhantomGreen
import dev.korryr.phantom.ui.theme.PhantomMist
import dev.korryr.phantom.ui.theme.PhantomPurple
import dev.korryr.phantom.ui.theme.PhantomSurface
import dev.korryr.phantom.ui.theme.PhantomTheme
import dev.korryr.phantom.ui.theme.PhantomVoid
import kotlinx.coroutines.delay

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val mediaProjectionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            startOverlayService(result.resultCode, result.data!!)
            finish()
        } else {
            Toast.makeText(this, "Screen capture permission denied", Toast.LENGTH_SHORT).show()
        }
    }

    private val overlayPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (Settings.canDrawOverlays(this)) {
            requestMediaProjection()
        } else {
            Toast.makeText(this, "Overlay permission denied", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            PhantomTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    StartScreen(onStartClick = { handleStartClick() })
                }
            }
        }
    }

    private fun handleStartClick() {
        if (!Settings.canDrawOverlays(this)) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            overlayPermissionLauncher.launch(intent)
        } else {
            requestMediaProjection()
        }
    }

    private fun requestMediaProjection() {
        val projectionManager =
            getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        mediaProjectionLauncher.launch(projectionManager.createScreenCaptureIntent())
    }

    private fun startOverlayService(resultCode: Int, data: Intent) {
        val serviceIntent = Intent(this, OverlayService::class.java).apply {
            putExtra(OverlayService.EXTRA_RESULT_CODE, resultCode)
            putExtra(OverlayService.EXTRA_RESULT_DATA, data)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }
    }
}

// ─── ANIMATED BACKGROUND RADAR ────────────────────────────────────────────────
@Composable
fun RadarBackground() {
    val infiniteTransition = rememberInfiniteTransition(label = "radar")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = LinearEasing)
        ),
        label = "rotation"
    )
    val pulse by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    Canvas(modifier = Modifier.fillMaxSize()) {
        val center = Offset(size.width * 0.5f, size.height * 0.38f)
        val maxRadius = size.width * 0.72f

        // Concentric radar rings
        for (i in 1..4) {
            val radius = maxRadius * (i / 4f)
            drawCircle(
                color = PhantomCyan.copy(alpha = 0.04f + (0.02f * (4 - i))),
                radius = radius,
                center = center,
                style = Stroke(width = 1.dp.toPx())
            )
        }

        // Glowing sweep line
        val sweepRadians = Math.toRadians(rotation.toDouble())
        val sweepEnd = Offset(
            center.x + (maxRadius * 0.75f) * kotlin.math.cos(sweepRadians).toFloat(),
            center.y + (maxRadius * 0.75f) * kotlin.math.sin(sweepRadians).toFloat()
        )
        drawLine(
            brush = Brush.linearGradient(
                colors = listOf(
                    PhantomCyan.copy(alpha = 0f),
                    PhantomCyan.copy(alpha = pulse * 0.6f)
                ),
                start = center,
                end = sweepEnd
            ),
            start = center,
            end = sweepEnd,
            strokeWidth = 2.dp.toPx()
        )

        // Center glow dot
        drawCircle(
            color = PhantomCyan.copy(alpha = pulse * 0.8f),
            radius = 4.dp.toPx(),
            center = center
        )
        drawCircle(
            color = PhantomCyan.copy(alpha = pulse * 0.2f),
            radius = 20.dp.toPx(),
            center = center
        )
    }
}

// ─── GHOST ICON with pulsing glow ─────────────────────────────────────────────
@Composable
fun GhostIcon() {
    val infiniteTransition = rememberInfiniteTransition(label = "ghost")
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow"
    )
    val floatY by infiniteTransition.animateFloat(
        initialValue = -6f,
        targetValue = 6f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "float"
    )

    Box(contentAlignment = Alignment.Center) {
        // Outer glow ring
        Box(
            modifier = Modifier
                .size(130.dp)
                .graphicsLayer { alpha = glowAlpha * 0.25f }
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(PhantomCyan, Color.Transparent)
                    ),
                    shape = CircleShape
                )
        )
        // Inner glow ring
        Box(
            modifier = Modifier
                .size(90.dp)
                .graphicsLayer { alpha = glowAlpha * 0.4f }
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(PhantomCyan.copy(alpha = 0.6f), Color.Transparent)
                    ),
                    shape = CircleShape
                )
        )
        // Ghost emoji floating
        Text(
            text = "👻",
            fontSize = 62.sp,
            modifier = Modifier.graphicsLayer { translationY = floatY }
        )
    }
}

// ─── FEATURE CHIP ─────────────────────────────────────────────────────────────
@Composable
fun FeatureChip(icon: String, label: String, color: Color) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(color.copy(alpha = 0.1f))
            .border(1.dp, color.copy(alpha = 0.3f), RoundedCornerShape(20.dp))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(text = icon, fontSize = 12.sp)
        Text(
            text = label,
            color = color,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.5.sp
        )
    }
}

// ─── START BUTTON ─────────────────────────────────────────────────────────────
@Composable
fun PhantomStartButton(onClick: () -> Unit) {
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.94f else 1f,
        animationSpec = tween(100),
        label = "scale"
    )
    val infiniteTransition = rememberInfiniteTransition(label = "btn")
    val borderAlpha by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "borderAlpha"
    )

    Box(
        modifier = Modifier
            .scale(scale)
            .fillMaxWidth(0.72f)
            .height(56.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(
                brush = Brush.horizontalGradient(
                    colors = listOf(PhantomCyanDark, PhantomPurple.copy(alpha = 0.8f))
                )
            )
            .border(
                width = 1.5.dp,
                brush = Brush.horizontalGradient(
                    colors = listOf(
                        PhantomCyan.copy(alpha = borderAlpha),
                        PhantomPurple.copy(alpha = borderAlpha * 0.6f)
                    )
                ),
                shape = RoundedCornerShape(28.dp)
            ),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(28.dp))
                .pointerInput(Unit) {
                    detectTapGestures(
                        onPress = {
                            pressed = true
                            tryAwaitRelease()
                            pressed = false
                        },
                        onTap = { onClick() }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(text = "⚡", fontSize = 18.sp)
                Text(
                    text = "ACTIVATE PHANTOM",
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 1.5.sp
                )
            }
        }
    }
}

// ─── MAIN START SCREEN ────────────────────────────────────────────────────────
@Composable
fun StartScreen(onStartClick: () -> Unit) {
    // Staggered entry animation
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { delay(100); visible = true }

    val contentAlpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(800, easing = FastOutSlowInEasing),
        label = "contentAlpha"
    )
    val contentSlide by animateFloatAsState(
        targetValue = if (visible) 0f else 40f,
        animationSpec = tween(800, easing = FastOutSlowInEasing),
        label = "slide"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(PhantomVoid)
    ) {
        // Subtle gradient mesh background
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            PhantomCyanDark.copy(alpha = 0.08f),
                            Color.Transparent
                        ),
                        radius = 800f,
                        center = Offset.Unspecified
                    )
                )
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            PhantomPurple.copy(alpha = 0.06f),
                            Color.Transparent
                        ),
                        radius = 1000f
                    )
                )
        )

        // Animated radar
        RadarBackground()

        // Main content
        Column(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    alpha = contentAlpha
                    translationY = contentSlide
                }
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {

            // Ghost icon with glow
            GhostIcon()

            Spacer(modifier = Modifier.height(28.dp))

            // App name with cyan accent
            Text(
                text = "PHANTOM",
                color = PhantomGhost,
                fontSize = 36.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 8.sp
            )

            Spacer(modifier = Modifier.height(4.dp))

            // Cyan underline accent
            Box(
                modifier = Modifier
                    .width(48.dp)
                    .height(2.dp)
                    .background(
                        brush = Brush.horizontalGradient(
                            colors = listOf(Color.Transparent, PhantomCyan, Color.Transparent)
                        )
                    )
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "Silent AI overlay. Always one step ahead.",
                color = PhantomMist,
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
                letterSpacing = 0.3.sp,
                lineHeight = 20.sp
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Feature chips
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(horizontal = 8.dp)
            ) {
                FeatureChip("👁", "INVISIBLE", PhantomCyan)
                FeatureChip("⚡", "< 2s", PhantomGreen)
                FeatureChip("🔇", "SILENT", PhantomPurple)
            }

            Spacer(modifier = Modifier.height(48.dp))

            // Start button
            PhantomStartButton(onClick = onStartClick)

            Spacer(modifier = Modifier.height(24.dp))

            // Disclaimer
            Text(
                text = "Will request overlay & screen capture permissions",
                color = PhantomMist.copy(alpha = 0.5f),
                fontSize = 11.sp,
                textAlign = TextAlign.Center,
                letterSpacing = 0.2.sp
            )
        }

        // Top version tag
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(20.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(PhantomSurface)
                .border(1.dp, OverlayBorder, RoundedCornerShape(8.dp))
                .padding(horizontal = 10.dp, vertical = 4.dp)
        ) {
            Text(
                text = "v1.0",
                color = PhantomCyan.copy(alpha = 0.7f),
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
        }
    }
}