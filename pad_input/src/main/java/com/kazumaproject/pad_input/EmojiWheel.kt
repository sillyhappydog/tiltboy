package com.kazumaproject.pad_input

/**
 * The "recent emoji" Daisywheel: 2 pages (L1 = second page) × 8 petals × 4 emoji, filled from
 * Sumire's emoji usage history (most recent / most used first) and topped up with defaults.
 */
object EmojiWheel {
    const val PETALS = 8
    const val PER_PETAL = 4
    const val PAGE_SIZE = PETALS * PER_PETAL // 32
    const val PAGES = 2

    /** Sensible defaults until there is history (popular emoji, common in Japanese chat). */
    val DEFAULTS: List<String> = listOf(
        "😀", "😂", "🥹", "😊", "😍", "😭", "😅", "🤣",
        "👍", "🙏", "👏", "🙌", "💪", "👀", "🫶", "✌️",
        "❤️", "💕", "✨", "🔥", "🎉", "💯", "⭐", "💦",
        "😎", "🤔", "😴", "😱", "🥳", "🙇", "🫡", "😇",
        "🐱", "🐶", "🐼", "🐧", "🌸", "🍀", "🌙", "☀️",
        "🍣", "🍜", "🍙", "🍺", "☕", "🍰", "🎮", "🎵",
        "✅", "❌", "⭕", "❓", "❗", "💤", "🆗", "🙆",
        "😢", "😡", "🥲", "😳", "🤗", "😋", "🤝", "👋",
    )

    /**
     * Build the full slot list: history first (duplicates removed, blanks dropped), then
     * defaults not already present, cut to [PAGES] × [PAGE_SIZE].
     */
    fun slots(history: List<String>, defaults: List<String> = DEFAULTS): List<String> {
        val out = LinkedHashSet<String>()
        history.map { it.trim() }.filter { it.isNotEmpty() }.forEach { out += it }
        for (d in defaults) {
            if (out.size >= PAGES * PAGE_SIZE) break
            out += d
        }
        return out.take(PAGES * PAGE_SIZE)
    }

    /** Page `page` (0 or 1) as 8 petals of 4 strings; missing slots are "". */
    fun page(slots: List<String>, page: Int): List<List<String>> =
        List(PETALS) { petal ->
            List(PER_PETAL) { pos ->
                slots.getOrNull(page * PAGE_SIZE + petal * PER_PETAL + pos) ?: ""
            }
        }
}
