package com.example.safecopy.domain.scan

sealed interface AutoCaptureState {
    /** Документ не найден или рамка ещё не успокоилась. */
    data object Searching : AutoCaptureState

    /** Документ найден, но телефон наклонён сильнее допустимого. */
    data class Tilted(val degrees: Float) : AutoCaptureState

    /** Все условия выполнены, идёт обратный отсчёт до снимка. */
    data class Countdown(val remainingMillis: Long) : AutoCaptureState

    /** Пора снимать. */
    data object Ready : AutoCaptureState
}

/**
 * Автоспуск по ТЗ: снимок делается, когда границы документа найдены, а наклон не превышает [maxTiltDegrees].
 * Чтобы не снимать смазанный кадр, рамка должна продержаться [stableFrames] кадров подряд
 * без скачков больше [maxCornerShift], после чего идёт отсчёт [holdMillis].
 */
class AutoCaptureController(
    private val clock: () -> Long,
    private val maxTiltDegrees: Float = 3f,
    private val stableFrames: Int = 5,
    private val maxCornerShift: Float = 0.05f,
    private val holdMillis: Long = 1_000L,
) {
    private var lastQuad: Quad? = null
    private var stableCount = 0
    private var countdownStartedAt: Long? = null

    fun update(quad: Quad?, tiltDegrees: Float): AutoCaptureState {
        val previous = lastQuad
        lastQuad = quad
        if (quad == null) {
            stableCount = 0
            countdownStartedAt = null
            return AutoCaptureState.Searching
        }
        if (previous != null && quad.maxCornerShift(previous) > maxCornerShift) {
            stableCount = 0
            countdownStartedAt = null
        }
        stableCount++
        if (stableCount < stableFrames) return AutoCaptureState.Searching

        if (tiltDegrees > maxTiltDegrees) {
            countdownStartedAt = null
            return AutoCaptureState.Tilted(tiltDegrees)
        }
        val now = clock()
        val startedAt = countdownStartedAt ?: now.also { countdownStartedAt = it }
        val remaining = holdMillis - (now - startedAt)
        return if (remaining <= 0) AutoCaptureState.Ready else AutoCaptureState.Countdown(remaining)
    }

    fun reset() {
        lastQuad = null
        stableCount = 0
        countdownStartedAt = null
    }
}
