package com.bacain.app

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.util.TypedValue
import android.view.Gravity
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {

    private lateinit var store: Store
    private lateinit var root: FrameLayout
    private val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
    private val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = Store(this)
        root = FrameLayout(this)
        setContentView(root)
        showSplash()
    }

    private fun label(t: String, sp: Float, color: Int, bold: Boolean = false): TextView =
        TextView(this).apply {
            text = t
            setTextSize(TypedValue.COMPLEX_UNIT_SP, sp)
            setTextColor(color)
            if (bold) typeface = Typeface.DEFAULT_BOLD
        }

    // ---------- Splash ----------
    private fun showSplash() {
        root.removeAllViews()
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            background = navyGradient(GradientDrawable.Orientation.TL_BR)
            setPadding(dp(32), 0, dp(32), 0)
        }
        col.addView(
            ImageView(this).apply { setImageResource(R.drawable.ic_logo) },
            LinearLayout.LayoutParams(dp(96), dp(96))
        )
        col.addView(label("BacaIn", 34f, Color.WHITE, true).apply {
            gravity = Gravity.CENTER
            setPadding(0, dp(20), 0, dp(6))
        })
        col.addView(label("Ngonten tanpa hafal naskah.", 15f, Color.WHITE).apply {
            gravity = Gravity.CENTER
        })
        col.addView(label("Tinggal baca, langsung rekam.", 12f, 0xCCFFFFFF.toInt()).apply {
            gravity = Gravity.CENTER
            setPadding(0, dp(4), 0, 0)
        })
        val start = label("Mulai", 16f, Palette.NAVY, true).apply {
            gravity = Gravity.CENTER
            background = rounded(Color.WHITE, dp(26).toFloat())
            setOnClickListener { showMain() }
        }
        col.addView(start, LinearLayout.LayoutParams(dp(220), dp(52)).apply { topMargin = dp(48) })
        root.addView(col, FrameLayout.LayoutParams(MATCH, MATCH))
    }

    // ---------- Layar utama ----------
    private fun card(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        background = rounded(Color.WHITE, dp(16).toFloat())
        elevation = dp(3).toFloat()
        setPadding(dp(16), dp(14), dp(16), dp(14))
    }

    private fun cardLp() = LinearLayout.LayoutParams(MATCH, WRAP).apply {
        setMargins(dp(16), 0, dp(16), 0)
    }

    private fun sectionTitle(t: String): TextView =
        label(t, 11f, Palette.MUTED, true).apply {
            letterSpacing = 0.1f
            setPadding(dp(20), dp(20), dp(20), dp(8))
        }

    private fun slider(
        parent: LinearLayout, title: String, unit: String,
        lo: Int, hi: Int, init: Int, onChange: (Int) -> Unit
    ) {
        val lbl = label("$title: $init$unit", 13f, Palette.INK)
        val bar = SeekBar(this).apply {
            this.max = hi - lo
            progress = init - lo
            progressTintList = ColorStateList.valueOf(Palette.NAVY)
            thumbTintList = ColorStateList.valueOf(Palette.NAVY)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {
                    val v = p + lo
                    lbl.text = "$title: $v$unit"
                    onChange(v)
                }
                override fun onStartTrackingTouch(s: SeekBar?) {}
                override fun onStopTrackingTouch(s: SeekBar?) {}
            })
        }
        parent.addView(lbl, LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = dp(6) })
        parent.addView(bar, LinearLayout.LayoutParams(MATCH, WRAP))
    }

    private fun counterText(t: String): String {
        val words = t.trim().split(Regex("\\s+")).count { it.isNotEmpty() }
        val secs = Math.ceil(words / 2.8).toInt()
        val dur = if (secs >= 60) "${secs / 60} mnt ${secs % 60} dtk" else "$secs dtk"
        return "$words kata  •  ± $dur"
    }

    private fun showMain() {
        root.removeAllViews()
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }

        val sv = ScrollView(this).apply {
            setBackgroundColor(Palette.BG)
            isFillViewport = true
        }
        val page = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        sv.addView(page, FrameLayout.LayoutParams(MATCH, WRAP))

        // Header
        val r = dp(28).toFloat()
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(20), dp(24), dp(20), dp(26))
            background = navyGradient(GradientDrawable.Orientation.TL_BR).apply {
                cornerRadii = floatArrayOf(0f, 0f, 0f, 0f, r, r, r, r)
            }
        }
        header.addView(
            ImageView(this).apply { setImageResource(R.drawable.ic_logo) },
            LinearLayout.LayoutParams(dp(48), dp(48))
        )
        val titles = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), 0, 0, 0)
        }
        titles.addView(label("BacaIn", 22f, Color.WHITE, true))
        titles.addView(label("Ngonten tanpa hafal naskah.", 12f, 0xDDFFFFFF.toInt()))
        header.addView(titles)
        page.addView(header, LinearLayout.LayoutParams(MATCH, WRAP))

        // Naskah
        page.addView(sectionTitle("NASKAH"))
        val naskah = card()
        val input = EditText(this).apply {
            setText(store.script)
            hint = "Tulis atau tempel naskah di sini…"
            setTextColor(Palette.INK)
            setHintTextColor(Palette.MUTED)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            inputType = InputType.TYPE_CLASS_TEXT or
                InputType.TYPE_TEXT_FLAG_MULTI_LINE or
                InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            gravity = Gravity.TOP or Gravity.START
            minLines = 7
            background = null
            setPadding(0, 0, 0, 0)
        }
        val counter = label(counterText(store.script), 11f, Palette.MUTED).apply {
            gravity = Gravity.END
        }
        input.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val t = s?.toString() ?: ""
                store.script = t
                counter.text = counterText(t)
            }
        })
        naskah.addView(input, LinearLayout.LayoutParams(MATCH, WRAP))
        naskah.addView(counter, LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = dp(8) })
        page.addView(naskah, cardLp())

        // Tampilan prompter
        page.addView(sectionTitle("TAMPILAN PROMPTER"))
        val tampil = card()
        slider(tampil, "Kecepatan scroll", "", 5, 100, store.speed) { store.speed = it }
        slider(tampil, "Ukuran huruf", " sp", 16, 60, store.textSize) { store.textSize = it }
        slider(tampil, "Kegelapan latar", " %", 0, 100, store.bgOpacity) { store.bgOpacity = it }
        page.addView(tampil, cardLp())

        // PLAY
        val play = label("▶   PLAY", 16f, Color.WHITE, true).apply {
            gravity = Gravity.CENTER
            background = navyGradient().apply { cornerRadius = dp(28).toFloat() }
            elevation = dp(4).toFloat()
            setOnClickListener { startOverlay() }
        }
        page.addView(play, LinearLayout.LayoutParams(MATCH, dp(56)).apply {
            setMargins(dp(16), dp(22), dp(16), 0)
        })
        page.addView(
            label(
                "Setelah Play, buka TikTok dan mulai rekam, lalu ketuk Mulai di overlay.",
                11f, Palette.MUTED
            ).apply {
                gravity = Gravity.CENTER
                setPadding(dp(24), dp(12), dp(24), 0)
            }
        )
        page.addView(label("Matikan overlay", 13f, Palette.DANGER, true).apply {
            gravity = Gravity.CENTER
            setPadding(0, dp(16), 0, dp(28))
            setOnClickListener {
                stopService(Intent(this@MainActivity, OverlayService::class.java))
                Toast.makeText(this@MainActivity, "Overlay dimatikan", Toast.LENGTH_SHORT).show()
            }
        })

        root.addView(sv, FrameLayout.LayoutParams(MATCH, MATCH))
    }

    private fun startOverlay() {
        if (store.script.trim().isEmpty()) {
            Toast.makeText(this, "Isi naskah dulu ya", Toast.LENGTH_SHORT).show()
            return
        }
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(
                this,
                "Aktifkan \"Tampil di atas aplikasi lain\" untuk BacaIn, lalu tekan PLAY lagi",
                Toast.LENGTH_LONG
            ).show()
            startActivity(
                Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
            )
            return
        }
        startForegroundService(Intent(this, OverlayService::class.java))
        Toast.makeText(this, "Overlay aktif. Buka TikTok lalu ketuk Mulai", Toast.LENGTH_LONG).show()
        moveTaskToBack(true)
    }
}
