package com.bacain.app

import android.content.Context
import android.graphics.drawable.GradientDrawable

object Palette {
    val NAVY = 0xFF0B1F4B.toInt()
    val BLUE = 0xFF2347B8.toInt()
    val BG = 0xFFF2F4FA.toInt()
    val INK = 0xFF16213E.toInt()
    val MUTED = 0xFF7A839B.toInt()
    val DANGER = 0xFFD64545.toInt()
}

fun Context.dp(v: Int): Int = (v * resources.displayMetrics.density + 0.5f).toInt()

fun rounded(color: Int, radius: Float): GradientDrawable =
    GradientDrawable().apply {
        setColor(color)
        cornerRadius = radius
    }

fun navyGradient(
    orientation: GradientDrawable.Orientation = GradientDrawable.Orientation.LEFT_RIGHT
): GradientDrawable = GradientDrawable(orientation, intArrayOf(Palette.NAVY, Palette.BLUE))
