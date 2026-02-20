package dev.korryr.phantom.viewmodel

import android.graphics.Bitmap
import dev.korryr.phantom.ai.GeminiRepository
import dev.korryr.phantom.capture.ScreenCaptureManager
import dev.korryr.phantom.ocr.TextRecognitionManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import timber.log.Timber

data class OverlayState(
    val answer: String = "Waiting...",
    val isLoading: Boolean = false
)

class OverlayViewModel(
    private val geminiRepository: GeminiRepository,
    private val textRecognitionManager: TextRecognitionManager = TextRecognitionManager()
) {
    companion object {
        private const val CAPTURE_INTERVAL_MS = 1500L
        private const val MIN_QUESTION_LENGTH = 10
        private const val COOLDOWN_MS = 5000L
        private const val SIMILARITY_THRESHOLD = 0.85
    }

    private val _state = MutableStateFlow(OverlayState())
    val state: StateFlow<OverlayState> = _state.asStateFlow()

    private var captureJob: Job? = null
    private var lastQuestionText: String = ""
    private var lastApiCallTime: Long = 0L
    private var screenCaptureManager: ScreenCaptureManager? = null

    fun setScreenCaptureManager(manager: ScreenCaptureManager) {
        screenCaptureManager = manager
        Timber.d("ScreenCaptureManager set")
    }

    fun startCaptureLoop(scope: CoroutineScope) {
        captureJob?.cancel()
        Timber.i("Starting capture loop (interval=${CAPTURE_INTERVAL_MS}ms, cooldown=${COOLDOWN_MS}ms)")
        captureJob = scope.launch(Dispatchers.IO) {
            while (isActive) {
                try {
                    val bitmap: Bitmap? = screenCaptureManager?.captureFrame()
                    if (bitmap != null) {
                        Timber.v("Captured frame: ${bitmap.width}x${bitmap.height}")
                        val text = textRecognitionManager.recognizeText(bitmap)
                        bitmap.recycle()

                        Timber.d("OCR result (${text.length} chars): ${text.take(100)}...")

                        val now = System.currentTimeMillis()
                        val cooldownPassed = (now - lastApiCallTime) >= COOLDOWN_MS
                        val textChanged = !isSimilar(text, lastQuestionText)

                        if (text.length < MIN_QUESTION_LENGTH) {
                            Timber.v("Text too short (${text.length} < $MIN_QUESTION_LENGTH), skipping")
                        } else if (!textChanged) {
                            Timber.v("Text similar to last question, skipping")
                        } else if (!cooldownPassed) {
                            val remaining = COOLDOWN_MS - (now - lastApiCallTime)
                            Timber.v("Cooldown active (${remaining}ms remaining), skipping")
                        } else {
                            Timber.i("New question detected, calling Gemini API")
                            lastQuestionText = text
                            lastApiCallTime = now
                            _state.value = _state.value.copy(isLoading = true)

                            val answer = geminiRepository.getAnswer(text)
                            _state.value = OverlayState(
                                answer = answer,
                                isLoading = false
                            )
                            Timber.i("Answer displayed: $answer")
                        }
                    } else {
                        Timber.v("No frame captured (null bitmap)")
                    }
                } catch (e: Exception) {
                    Timber.e(e, "Capture loop error")
                }
                delay(CAPTURE_INTERVAL_MS)
            }
        }
    }

    /**
     * Compares two strings by word overlap ratio.
     * Returns true if they share >= SIMILARITY_THRESHOLD of words.
     */
    private fun isSimilar(a: String, b: String): Boolean {
        if (a.isBlank() || b.isBlank()) return false
        val wordsA = a.lowercase().split("\\s+".toRegex()).toSet()
        val wordsB = b.lowercase().split("\\s+".toRegex()).toSet()
        if (wordsA.isEmpty() || wordsB.isEmpty()) return false
        val overlap = wordsA.intersect(wordsB).size
        val maxSize = maxOf(wordsA.size, wordsB.size)
        val similarity = overlap.toDouble() / maxSize
        Timber.v("Text similarity: %.2f (threshold: %.2f)".format(similarity, SIMILARITY_THRESHOLD))
        return similarity >= SIMILARITY_THRESHOLD
    }

    fun stop() {
        Timber.i("Stopping OverlayViewModel — cancelling capture, releasing resources")
        captureJob?.cancel()
        captureJob = null
        textRecognitionManager.close()
        screenCaptureManager?.release()
        screenCaptureManager = null
    }
}
