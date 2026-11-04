package com.kazumaproject.markdownhelperkeyboard.pad

/**
 * Read-only view of the IME's state for the emulator test, which runs in the IME's own
 * process. Nothing reads this in normal use; it costs one list copy per candidate update.
 *
 * Readiness signals (amendment 14): the test used to poll [candidates] for a fixed 8 s. Run
 * 41278495334 failed that way with an empty list because the hook then sat in
 * `SuggestionAdapter.suggestions`' setter, which the candidate strip no longer goes through
 * (strip updates use `submitContent`), so the list stayed empty although candidates were shown.
 * Now the IME publishes, for every candidate update, the reading it was computed for
 * ([candidatesInput]) and a [candidatesRevision] counter, and says when the kana-kanji engine
 * (core dictionaries) has loaded ([engineReady]). The test waits for exactly those events.
 */
object PadTestHooks {
    /** The candidate strings currently shown, in strip order. */
    @Volatile
    var candidates: List<String> = emptyList()

    /** The composing reading the last candidate update belongs to ("" before the first one). */
    @Volatile
    var candidatesInput: String = ""
        private set

    /** Bumped once per candidate update for a reading (see [publish]). */
    @Volatile
    var candidatesRevision: Int = 0
        private set

    /** The kana-kanji engine (core dictionaries) has finished loading in this process. */
    @Volatile
    var engineReady: Boolean = false

    /**
     * Amendment 18: the full-placement guide (the controller map over the touch keys), so the
     * emulator test can tap a touch digit at its on-screen position ([PadGuideView.digitCentreOnScreen])
     * and check that the guide stays up.
     */
    @Volatile
    var fullGuide: java.lang.ref.WeakReference<PadGuideView>? = null

    /**
     * Amendment 19: what the touch keyboard shows ("tenkey/kana", "qwerty/english", "tenkey/number", …)
     * and the keyboard root, so the emulator test can tap the touch keyboard's own switch key.
     */
    interface TouchKeyboardProbe {
        fun state(): String
        fun keyboardRoot(): android.view.View?
    }

    @Volatile
    var touchKeyboard: java.lang.ref.WeakReference<TouchKeyboardProbe>? = null

    /**
     * Amendment 23: the IME's composition, so the emulator test can check a D-pad range change: the
     * reading being converted (Sumire's `inputString`), the unconverted rest after it
     * (`stringInTail`), whether a conversion is active (`isHenkan`) and the candidate the field shows.
     * Read on the main thread.
     */
    interface CompositionProbe {
        fun reading(): String
        fun rest(): String
        fun converting(): Boolean
        fun shownCandidate(): String?
    }

    @Volatile
    var composition: java.lang.ref.WeakReference<CompositionProbe>? = null

    /** Called by the IME after the candidate strip shows the list computed for [input]. */
    fun publish(input: String) {
        candidatesInput = input
        candidatesRevision++
    }
}
