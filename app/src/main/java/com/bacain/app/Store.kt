package com.bacain.app

import android.content.Context

class Store(ctx: Context) {
    private val p = ctx.getSharedPreferences("bacain", Context.MODE_PRIVATE)

    var script: String
        get() = p.getString("script", "") ?: ""
        set(v) { p.edit().putString("script", v).apply() }

    var speed: Int
        get() = p.getInt("speed", 25)
        set(v) { p.edit().putInt("speed", v).apply() }

    var textSize: Int
        get() = p.getInt("textSize", 28)
        set(v) { p.edit().putInt("textSize", v).apply() }

    var bgOpacity: Int
        get() = p.getInt("bgOpacity", 70)
        set(v) { p.edit().putInt("bgOpacity", v).apply() }
}
