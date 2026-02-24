package dev.korryr.phantom.viewmodel

import android.graphics.Bitmap
import dev.korryr.phantom.ai.GroqRepository
import dev.korryr.phantom.capture.ScreenCaptureManager
import dev.korryr.phantom.ocr.TextRecognitionManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

data class OverlayState(
    val answer: String = "Tap to scan",
    val isLoading: Boolean = false
)

class OverlayViewModel(
    private val groqRepository: GroqRepository,
    private val textRecognitionManager: TextRecognitionManager = TextRecognitionManager()
) {
    companion object {
        private const val MIN_QUESTION_LENGTH = 10
        private const val SIMILARITY_THRESHOLD = 0.85
    }

    private val _state = MutableStateFlow(OverlayState())
    val state: StateFlow<OverlayState> = _state.asStateFlow()

    private var captureJob: Job? = null
    private var lastQuestionText: String = ""
    private var screenCaptureManager: ScreenCaptureManager? = null

    init {
        // Pre-warm MLKit so the FIRST user tap is not slowed by model initialisation.
        // A 1×1 blank bitmap is enough to trigger the native model load.
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val dummy = Bitmap.createBitmap(1, 1, Bitmap.Config.RGB_565)
                textRecognitionManager.recognizeText(dummy)
                dummy.recycle()
                Timber.d("MLKit pre-warm complete")
            } catch (e: Exception) {
                Timber.w(e, "MLKit pre-warm failed (non-fatal)")
            }
        }
    }

    fun setScreenCaptureManager(manager: ScreenCaptureManager) {
        screenCaptureManager = manager
        Timber.d("ScreenCaptureManager set")
    }

    // Simple flag to prevent double-taps — NOT exposed to UI (no spinner)
    @Volatile
    private var scanInProgress = false

    fun scanOnce(scope: CoroutineScope) {
        if (scanInProgress) {
            Timber.d("Scan already in progress, ignoring")
            return
        }
        Timber.i("Manual scan triggered")
        scanInProgress = true

        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val bitmap = screenCaptureManager?.captureFrame()
                        ?: screenCaptureManager?.captureNextFrame()
                        ?: return@runCatching "Capture failed"

                    Timber.v("Frame captured: ${bitmap.width}x${bitmap.height}")
                    val text = textRecognitionManager.recognizeText(bitmap)
                    // bitmap is a copy from captureFrame(), safe to let GC recycle

                    Timber.d("OCR (${text.length} chars): ${text.take(100)}")

                    if (text.length < MIN_QUESTION_LENGTH) {
                        Timber.w("Text too short (${text.length} < $MIN_QUESTION_LENGTH)")
                        return@runCatching "No question found"
                    }

                    // Skip API call if question hasn't changed (Jaccard similarity)
                    if (lastQuestionText.isNotBlank() && isSimilar(lastQuestionText, text)) {
                        Timber.d("Question unchanged — reusing previous answer")
                        val cached = _state.value.answer
                        if (!cached.startsWith("Error") && cached != "Tap to scan"
                            && cached != "No question found" && cached != "Capture failed") {
                            return@runCatching cached
                        }
                    }

                    Timber.i("Calling Groq API")
                    lastQuestionText = text
                    groqRepository.getAnswer(text)
                }.getOrElse { e ->
                    Timber.e(e, "Scan error")
                    "Error: ${e.message?.take(40)}"
                }
            }

            // Swap answer in instantly — no loading state was ever shown
            _state.value = OverlayState(answer = result)
            Timber.i("Answer displayed: $result")
            scanInProgress = false
        }
    }

    /**
     * Jaccard word-set similarity — O(n) with HashSet intersect.
     * Returns true when two texts share ≥ [SIMILARITY_THRESHOLD] of their words.
     * Used to skip redundant API calls when the screen content hasn't changed.
     */
    private fun isSimilar(a: String, b: String): Boolean {
        if (a.isBlank() || b.isBlank()) return false
        val wordsA = a.lowercase().splitToSequence("\\s+".toRegex()).filter { it.isNotEmpty() }.toHashSet()
        val wordsB = b.lowercase().splitToSequence("\\s+".toRegex()).filter { it.isNotEmpty() }.toHashSet()
        if (wordsA.isEmpty() || wordsB.isEmpty()) return false
        val overlap = wordsA.intersect(wordsB).size
        val maxSize = maxOf(wordsA.size, wordsB.size)
        val similarity = overlap.toDouble() / maxSize
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
