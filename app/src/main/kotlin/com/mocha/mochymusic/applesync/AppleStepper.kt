// AppleStepper.kt

package com.mocha.mochymusic.applesync

/** What one stage produced: the log so far, and whether the run should keep going. */
data class StepReport(val ok: Boolean, val lines: List<String>)

/**
 * A run that advances one write at a time.
 *
 * The stepped and the automatic forms are the same code: [next] runs the current stage and moves on,
 * and an automatic run is [next] in a loop. A UI that offers only one of them is a UI that drifts
 * from the other, so both sandbox runs share this.
 */
interface AppleStepper {
    /** The stage the next call runs, in words. Empty when finished. */
    val nextLabel: String
    val finished: Boolean
    suspend fun next(): StepReport
}
