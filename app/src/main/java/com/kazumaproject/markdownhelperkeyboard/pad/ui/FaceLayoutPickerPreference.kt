package com.kazumaproject.markdownhelperkeyboard.pad.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.preference.Preference
import androidx.preference.PreferenceViewHolder
import com.google.android.material.color.MaterialColors
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.pad_input.FaceLayout

/**
 * v14 (user, 2026-11-04): 「ボタンの並び」 first under はじめに. Two pictures of the four face
 * buttons (Nintendo: X top, Y left, A right, B bottom / Xbox: Y top, X left, B right, A bottom);
 * the user picks the one that looks like their device, by touch or with the D-pad and A
 * ([TiltBoyControllerKeys] turns A into D-pad centre). Mock:
 * docs/cloud-tasks/assets/2026-11-04_v14_face_layout_mock_*.png.
 *
 * Stores the same setting as 細かい調整's 「ボタンの並び（案内の表示）」 ([com.kazumaproject.markdownhelperkeyboard.pad.PadPreferences.FACE_LAYOUT]):
 * "auto" (nothing picked yet; the guide uses the device guess, both pictures unmarked, the summary
 * asks) or "nintendo" / "xbox" (that picture marked, the summary says 済み). Never stores a default.
 */
class FaceLayoutPickerPreference @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : Preference(context, attrs) {

    init {
        layoutResource = R.layout.pref_tiltboy_face_layout
        isSelectable = false // the two pictures take the focus, not the row
    }

    private val value: String get() = getPersistedString(AUTO) ?: AUTO

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)
        (holder.findViewById(android.R.id.summary) as? TextView)?.apply {
            text = context.getString(if (value == AUTO) R.string.tiltboy_face_pick else R.string.tiltboy_setup_done)
            visibility = View.VISIBLE
        }
        val labels = context.resources.getStringArray(R.array.pad_face_layout_entries)
        bindCard(holder.findViewById(R.id.tiltboy_face_nintendo) as FrameLayout, NINTENDO, FaceLayout.NINTENDO, labels[1])
        bindCard(holder.findViewById(R.id.tiltboy_face_xbox) as FrameLayout, XBOX, FaceLayout.XBOX, labels[2])
    }

    private fun bindCard(card: FrameLayout, key: String, layout: FaceLayout, description: String) {
        val selected = value == key
        if (card.childCount == 0) {
            card.addView(FaceDiamondView(card.context), FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
            card.addView(TextView(card.context).apply {
                tag = BADGE
                text = "✓"
                gravity = Gravity.CENTER
                setTypeface(typeface, Typeface.BOLD)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            }, FrameLayout.LayoutParams(dp(card, 24), dp(card, 24), Gravity.TOP or Gravity.END).apply {
                topMargin = dp(card, 6); marginEnd = dp(card, 6)
            })
        }
        (card.getChildAt(0) as FaceDiamondView).layout = layout
        val accent = MaterialColors.getColor(card.context, androidx.appcompat.R.attr.colorPrimary, Color.rgb(138, 180, 248))
        val outline = MaterialColors.getColor(card.context, com.google.android.material.R.attr.colorOutline, Color.GRAY)
        val surface = MaterialColors.getColor(card.context, com.google.android.material.R.attr.colorSurfaceVariant, Color.DKGRAY)
        card.background = GradientDrawable().apply {
            cornerRadius = dp(card, 14).toFloat()
            setColor(surface)
            setStroke(dp(card, if (selected) 3 else 1), if (selected) accent else outline)
        }
        // D-pad focus must be visible: a light veil over the focused picture.
        card.foreground = android.graphics.drawable.StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_focused), GradientDrawable().apply {
                cornerRadius = dp(card, 14).toFloat()
                setColor(Color.argb(56, 255, 255, 255))
                setStroke(dp(card, 3), accent)
            })
        }
        card.findViewWithTag<TextView>(BADGE)?.apply {
            visibility = if (selected) View.VISIBLE else View.GONE
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(accent) }
            setTextColor(surface)
        }
        card.contentDescription = description
        card.isSelected = selected
        card.setOnClickListener { pick(key) }
    }

    private fun pick(key: String) {
        if (key == value || !callChangeListener(key)) return
        persistString(key)
        notifyChanged()
    }

    private fun dp(v: View, n: Int): Int = (n * v.resources.displayMetrics.density).toInt()

    /** The four face buttons as the device shows them. */
    private class FaceDiamondView(context: Context) : View(context) {
        var layout: FaceLayout = FaceLayout.NINTENDO
            set(value) { field = value; invalidate() }

        private val circle = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(90, 103, 130) }
        private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textAlign = Paint.Align.CENTER
            typeface = Typeface.DEFAULT_BOLD
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val d = resources.displayMetrics.density
            val r = 13f * d
            val gap = 21f * d
            val cx = width / 2f
            val cy = height / 2f
            text.textSize = 15f * d
            // top, left, right, bottom
            val letters = if (layout == FaceLayout.NINTENDO) listOf("X", "Y", "A", "B") else listOf("Y", "X", "B", "A")
            val spots = listOf(cx to cy - gap, cx - gap to cy, cx + gap to cy, cx to cy + gap)
            for ((i, p) in spots.withIndex()) {
                canvas.drawCircle(p.first, p.second, r, circle)
                canvas.drawText(letters[i], p.first, p.second - (text.ascent() + text.descent()) / 2f, text)
            }
        }
    }

    companion object {
        const val AUTO = "auto"
        const val NINTENDO = "nintendo"
        const val XBOX = "xbox"
        private const val BADGE = "badge"
    }
}
