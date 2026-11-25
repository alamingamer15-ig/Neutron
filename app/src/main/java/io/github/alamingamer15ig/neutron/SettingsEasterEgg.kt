package io.github.alamingamer15ig.neutron

import android.content.Context
import android.graphics.Typeface
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView

/**
 * The tiny explanation of Neutron's name.
 * Kept in Kotlin purely because we can.
 */
object SettingsEasterEgg {
    @JvmStatic
    fun addTo(parent: LinearLayout, context: Context, topMarginPx: Int) {
        val message = TextView(context).apply {
            text = "Proton + Neutron = Atom"
            textSize = 12f
            setTextColor(context.getColor(R.color.neutron_muted))
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 0)
            contentDescription = "Proton plus Neutron equals Atom"
        }

        parent.addView(
            message,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = topMarginPx
            }
        )
    }
}
