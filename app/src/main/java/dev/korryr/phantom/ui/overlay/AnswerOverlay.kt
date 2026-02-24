package dev.korryr.phantom.ui.overlay

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.korryr.phantom.ui.theme.*
import dev.korryr.phantom.viewmodel.OverlayState
import kotlinx.coroutines.flow.StateFlow

// ─── STOP BUTTON (tiny X badge) ───────────────────────────────────────────────
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
            .size(20.dp)
            .clip(CircleShape)
            .background(PhantomRed.copy(alpha = 0.85f))
            .border(1.dp, PhantomRed, CircleShape)
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = { pressed = true; tryAwaitRelease(); pressed = false },
                    onTap = { onClick() }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Text("X", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.ExtraBold)
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
            .widthIn(min = 160.dp, max = 200.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(OverlaySurface)
            .border(1.dp, OverlayBorder, RoundedCornerShape(14.dp))
            .padding(16.dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("👻", fontSize = 24.sp)
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Stop Phantom?",
                color = PhantomGhost,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "Overlay will be removed",
                color = PhantomMist,
                fontSize = 10.sp,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // Cancel
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(PhantomMist.copy(alpha = 0.15f))
                        .pointerInput(Unit) { detectTapGestures(onTap = { onCancel() }) }
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Cancel", color = PhantomMist, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                }
                // Stop
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(PhantomRed.copy(alpha = 0.2f))
                        .border(1.dp, PhantomRed.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                        .pointerInput(Unit) { detectTapGestures(onTap = { onConfirm() }) }
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Stop", color = PhantomRed, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// ─── ANSWER CONTENT INSIDE CARD ───────────────────────────────────────────────
@Composable
fun AnswerContent(state: OverlayState) {
    AnimatedContent(
        targetState = state.answer,
        transitionSpec = {
            fadeIn(tween(200)) + slideInVertically { it / 2 } togetherWith
                    fadeOut(tween(150)) + slideOutVertically { -it / 2 }
        },
        label = "answerAnim"
    ) { answer ->
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(horizontal = 4.dp)
        ) {
            // State dot indicator
            val dotColor = when {
                answer.startsWith("Error:") -> PhantomRed
                answer == "Watching..." || answer == "Tap to scan" -> PhantomMist
                else -> PhantomGreen
            }
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(dotColor)
            )

            Text(
                text = answer,
                color = if (answer == "Watching..." || answer == "Tap to scan") PhantomMist else PhantomGhost,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 3,
                lineHeight = 17.sp
            )
        }
    }
}

// ─── MAIN OVERLAY COMPOSABLE ──────────────────────────────────────────────────
@Composable
fun AnswerOverlay(
    stateFlow: StateFlow<OverlayState>,
    onScanClick: () -> Unit,
    onStopClick: () -> Unit
) {
    val state by stateFlow.collectAsState()
    var showExitDialog by remember { mutableStateOf(false) }

    AnimatedVisibility(
        visible = showExitDialog,
        enter = fadeIn(tween(150)) + slideInVertically { -it / 3 },
        exit = fadeOut(tween(150)) + slideOutVertically { -it / 3 }
    ) {
        StopConfirmCard(
            onCancel = { showExitDialog = false },
            onConfirm = onStopClick
        )
    }

    AnimatedVisibility(
        visible = !showExitDialog,
        enter = fadeIn(tween(150)),
        exit = fadeOut(tween(150))
    ) {
        // ── Answer card — tap anywhere to scan ─────────────────────────────
        Box(
            modifier = Modifier
                .widthIn(min = 140.dp, max = 220.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(OverlayBackground.copy(alpha = 0.5f))
                .border(
                    width = 1.dp,
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            PhantomCyan.copy(alpha = 0.4f),
                            OverlayBorder
                        )
                    ),
                    shape = RoundedCornerShape(14.dp)
                )
                .pointerInput(Unit) {
                    detectTapGestures(onTap = { if (!state.isLoading) onScanClick() })
                }
                .padding(horizontal = 14.dp, vertical = 12.dp),
            contentAlignment = Alignment.TopStart
        ) {
            // Answer text (leave right padding for the X badge)
            Box(modifier = Modifier.padding(end = 18.dp)) {
                AnswerContent(state = state)
            }

            // X stop badge — pinned top-right inside card
            Box(modifier = Modifier.align(Alignment.TopEnd)) {
                StopBadge(onClick = { showExitDialog = true })
            }
        }
    }
}