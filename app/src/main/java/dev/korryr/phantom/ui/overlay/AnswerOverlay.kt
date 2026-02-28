package dev.korryr.phantom.ui.overlay

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.korryr.phantom.ui.theme.*
import dev.korryr.phantom.viewmodel.OverlayState
import kotlinx.coroutines.flow.StateFlow

// ─── STOP BUTTON (tiny near-invisible dot) ────────────────────────────────────
@Composable
fun StopBadge(onClick: () -> Unit) {
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.85f else 1f,
        animationSpec = tween(80),
        label = "stopScale"
    )
    Box(
        modifier = Modifier
            .scale(scale)
            .size(15.dp)
            .clip(CircleShape)
            .background(PhantomRed.copy(alpha = 0.35f))
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = { pressed = true; tryAwaitRelease(); pressed = false },
                    onTap = { onClick() }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Text("×", color = Color.White.copy(alpha = 0.6f), fontSize = 8.sp, fontWeight = FontWeight.Bold)
    }
}

// ─── CONFIRM STOP DIALOG ──────────────────────────────────────────────────────
@Composable
fun StopConfirmCard(
    onCancel: () -> Unit,
    onConfirm: () -> Unit
) {
    Box(
        modifier = Modifier
            .widthIn(min = 140.dp, max = 180.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(Color.Black.copy(alpha = 0.7f))
            .padding(12.dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "Stop?",
                color = Color.White.copy(alpha = 0.7f),
                fontWeight = FontWeight.SemiBold,
                fontSize = 11.sp
            )
            Spacer(modifier = Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // Cancel
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color.White.copy(alpha = 0.08f))
                        .pointerInput(Unit) { detectTapGestures(onTap = { onCancel() }) }
                        .padding(horizontal = 12.dp, vertical = 5.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No", color = Color.White.copy(alpha = 0.5f), fontSize = 10.sp, fontWeight = FontWeight.Medium)
                }
                // Stop
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(PhantomRed.copy(alpha = 0.15f))
                        .pointerInput(Unit) { detectTapGestures(onTap = { onConfirm() }) }
                        .padding(horizontal = 12.dp, vertical = 5.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Yes", color = PhantomRed.copy(alpha = 0.7f), fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

// ─── ANSWER CONTENT INSIDE CARD ───────────────────────────────────────────────
@Composable
fun AnswerContent(state: OverlayState) {
    val answer = state.answer
    val statusText = state.statusText
    val isIdle = answer == "Tap to scan" && !state.isLoading

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.padding(horizontal = 2.dp)
    ) {
        if (state.isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(8.dp),
                color = Color.White.copy(alpha = 0.4f),
                strokeWidth = 1.5.dp
            )
        } else {
            // Tiny dot indicator — almost invisible
            val dotColor = when {
                answer.startsWith("Error:") -> PhantomRed.copy(alpha = 0.5f)
                isIdle -> Color.White.copy(alpha = 0.15f)
                else -> PhantomGreen.copy(alpha = 0.5f)
            }
            Box(
                modifier = Modifier
                    .size(4.dp)
                    .clip(CircleShape)
                    .background(dotColor)
            )
        }

        // Show phased status or streaming answer
        val displayText = when {
            state.isLoading && statusText.isNotEmpty() && answer == "Tap to scan" -> statusText
            state.isLoading && statusText.isNotEmpty() && !answer.startsWith("Error") -> {
                if (answer != "Tap to scan") answer else statusText
            }
            state.isLoading && answer == "Tap to scan" -> "..."
            isIdle -> "·"   // Single dot when idle — maximum stealth
            else -> answer
        }

        // Dim text when idle, slightly brighter for answers
        val textAlpha = when {
            isIdle -> 0.25f
            state.isLoading -> 0.45f
            answer.startsWith("Error") -> 0.5f
            else -> 0.75f   // Answer text — visible but not screaming
        }

        Text(
            text = displayText,
            color = Color.White.copy(alpha = textAlpha),
            fontSize = if (isIdle) 10.sp else 11.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 3,
            lineHeight = 14.sp
        )
    }
}

// MAIN OVERLAY COMPOSABLE
@Composable
fun AnswerOverlay(
    stateFlow: StateFlow<OverlayState>,
    onScanClick: () -> Unit,
    onStopClick: () -> Unit
) {
    val state by stateFlow.collectAsState()
    var showExitDialog by remember { mutableStateOf(false) }
    val isIdle = state.answer == "Tap to scan" && !state.isLoading
    val hasAnswer = !isIdle && !state.isLoading

    // Card fades to near-invisible when idle, slightly more visible with an answer
    val cardAlpha by animateFloatAsState(
        targetValue = when {
            showExitDialog -> 0f      // Hide card when dialog is showing
            isIdle -> 0.32f           // Almost invisible when idle
            state.isLoading -> 0.35f  // Slightly visible during scan
            hasAnswer -> 0.35f        // More visible with answer — but still subtle
            else -> 0.25f
        },
        animationSpec = tween(300),
        label = "cardAlpha"
    )

    AnimatedVisibility(
        visible = showExitDialog,
        enter = fadeIn(tween(120)),
        exit = fadeOut(tween(120))
    ) {
        StopConfirmCard(
            onCancel = { showExitDialog = false },
            onConfirm = onStopClick
        )
    }

    AnimatedVisibility(
        visible = !showExitDialog,
        enter = fadeIn(tween(120)),
        exit = fadeOut(tween(120))
    ) {
        // ── Stealth answer card
        Box(
            modifier = Modifier
                .graphicsLayer { alpha = cardAlpha }
                .widthIn(min = if (isIdle) 32.dp else 100.dp, max = if (isIdle) 60.dp else 200.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color.Black.copy(alpha = 0.6f))
                .pointerInput(Unit) {
                    detectTapGestures(onTap = { if (!state.isLoading) onScanClick() })
                }
                .padding(
                    horizontal = if (isIdle) 6.dp else 10.dp,
                    vertical = if (isIdle) 4.dp else 8.dp
                ),
            contentAlignment = Alignment.TopStart
        ) {
            // Answer text
            Box(
                modifier = Modifier
                    .padding(end = if (isIdle) 0.dp else 14.dp)
            ) {
                AnswerContent(state = state)
            }

            // X stop badge — only visible on long-press area, not idle
            if (!isIdle) {
                Box(modifier = Modifier.align(Alignment.TopEnd)) {
                    StopBadge(onClick = { showExitDialog = true })
                }
            }
        }
    }
}