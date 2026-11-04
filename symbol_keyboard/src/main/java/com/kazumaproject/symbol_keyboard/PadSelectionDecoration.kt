package com.kazumaproject.symbol_keyboard

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import androidx.recyclerview.widget.RecyclerView

/**
 * Sumire Pad: draws a highlight frame around the grid item a game controller has selected.
 * The symbol grid has no focus/selection state of its own, so this decoration paints one
 * without touching the adapter or item layouts.
 */
class PadSelectionDecoration(private val selectedPosition: () -> Int) : RecyclerView.ItemDecoration() {
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = 0xFF8AB4F8.toInt()
    }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.argb(60, 0x8A, 0xB4, 0xF8)
    }
    private val rect = RectF()

    override fun onDrawOver(c: Canvas, parent: RecyclerView, state: RecyclerView.State) {
        val position = selectedPosition()
        if (position < 0) return
        val holder = parent.findViewHolderForAdapterPosition(position) ?: return
        val v = holder.itemView
        val inset = v.height * 0.06f
        stroke.strokeWidth = (v.height * 0.07f).coerceAtLeast(2f)
        rect.set(v.left + inset, v.top + inset, v.right - inset, v.bottom - inset)
        val radius = v.height * 0.2f
        c.drawRoundRect(rect, radius, radius, fill)
        c.drawRoundRect(rect, radius, radius, stroke)
    }
}
