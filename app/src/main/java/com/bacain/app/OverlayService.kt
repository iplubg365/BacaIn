package com.bacain.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.Outline
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

class OverlayService : Service() {

    private enum class State { IDLE, COUNTDOWN, RUNNING, PAUSED }

    private lateinit var wm: WindowManager
    private lateinit var store: Store
    private lateinit var params: WindowManager.LayoutParams
    private val handler = Handler(Looper.getMainLooper())

    private var container: View? = null
    private lateinit var scroll: ScrollView
    private lateinit var tv: TextView
    private lateinit var prompter: FrameLayout
    private lateinit var speedLabel: TextView
    private lateinit var actionBtn: TextView
    private lateinit var countdownView: TextView
    private lateinit var guide: View

    private var state = State.IDLE
    private var pos = 0f
    private var lastT = 0L
    private var count = 3

    private var downX = 0f
    private var downY = 0f
    private var startA = 0
    private var startB = 0

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        store = Store(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        goForeground()
        if (container == null) buildOverlay() else reload()
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        container?.let { try { wm.removeView(it) } catch (_: Exception) {} }
        container = null
        super.onDestroy()
    }

    private fun goForeground() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel("bacain", "BacaIn overlay", NotificationManager.IMPORTANCE_LOW)
        )
        val pi = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        val n = Notification.Builder(this, "bacain")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle("BacaIn aktif")
            .setContentText("Overlay teleprompter sedang tampil")
            .setContentIntent(pi)
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(1, n)
        }
    }

    // ---------- UI ----------
    private fun bar(t: String, w: Int): TextView = TextView(this).apply {
        text = t
        setTextColor(Color.WHITE)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
        typeface = Typeface.DEFAULT_BOLD
        gravity = Gravity.CENTER
        layoutParams = LinearLayout.LayoutParams(w, ViewGroup.LayoutParams.MATCH_PARENT)
    }

    private fun buildOverlay() {
        val dm = resources.displayMetrics
        val w = (dm.widthPixels * 0.92f).toInt()
        val h = (dm.heightPixels * 0.34f).toInt()
        params = WindowManager.LayoutParams(
            w, h,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = (dm.widthPixels - w) / 2
            y = dp(90)
        }

        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(view: View, outline: Outline) {
                    outline.setRoundRect(0, 0, view.width, view.height, dp(16).toFloat())
                }
            }
            clipToOutline = true
        }

        // Bar kontrol atas
        val top = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = navyGradient()
            setPadding(dp(6), 0, dp(6), 0)
        }
        speedLabel = bar("≡ ${store.speed}", 0).apply {
            gravity = Gravity.CENTER_VERTICAL or Gravity.START
            setPadding(dp(8), 0, 0, 0)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f)
            setOnTouchListener { _, e ->
                when (e.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        downX = e.rawX; downY = e.rawY
                        startA = params.x; startB = params.y
                    }
                    MotionEvent.ACTION_MOVE -> {
                        params.x = startA + (e.rawX - downX).toInt()
                        params.y = startB + (e.rawY - downY).toInt()
                        container?.let { wm.updateViewLayout(it, params) }
                    }
                }
                true
            }
        }
        top.addView(speedLabel)
        top.addView(bar("−", dp(32)).apply { setOnClickListener { changeSpeed(-1) } })
        top.addView(bar("+", dp(32)).apply { setOnClickListener { changeSpeed(1) } })
        top.addView(bar("A-", dp(34)).apply { setOnClickListener { changeSize(-2) } })
        top.addView(bar("A+", dp(34)).apply { setOnClickListener { changeSize(2) } })
        actionBtn = bar("Mulai", 0).apply {
            background = rounded(0x33FFFFFF, dp(10).toFloat())
            setPadding(dp(10), 0, dp(10), 0)
            minWidth = dp(54)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(28)
            ).apply { setMargins(dp(4), 0, dp(4), 0) }
            setOnClickListener { onAction() }
        }
        top.addView(actionBtn)
        top.addView(bar("X", dp(30)).apply { setOnClickListener { stopSelf() } })
        box.addView(top, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(40)))

        // Area prompter
        prompter = FrameLayout(this)
        scroll = ScrollView(this).apply {
            isVerticalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
        }
        tv = TextView(this).apply {
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER_HORIZONTAL
            setLineSpacing(0f, 1.15f)
            setPadding(dp(14), dp(10), dp(14), 0)
        }
        scroll.addView(tv, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        scroll.addOnLayoutChangeListener { _, _, t, _, b, _, _, _, _ ->
            val hh = b - t
            if (hh > 0 && tv.paddingBottom != hh) {
                tv.setPadding(tv.paddingLeft, tv.paddingTop, tv.paddingRight, hh)
            }
        }
        scroll.setOnTouchListener { _, _ -> state == State.RUNNING || state == State.COUNTDOWN }
        prompter.addView(scroll, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        countdownView = TextView(this).apply {
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 72f)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setShadowLayer(8f, 0f, 2f, Color.BLACK)
            visibility = View.GONE
        }
        // Kotak penanda baris baca (di atas teks, di bawah countdown)
        guide = View(this).apply {
            background = GradientDrawable().apply {
                setColor(0x33FFB300)
                setStroke(dp(2), 0xAAFFC107.toInt())
                cornerRadius = dp(10).toFloat()
            }
            isClickable = false
        }
        prompter.addView(guide, prompter.indexOfChild(scroll) + 1, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(60)).apply {
            setMargins(dp(8), dp(12), dp(8), 0)
        })
        prompter.addView(countdownView, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        box.addView(prompter, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        // Handle ubah ukuran
        val handle = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            background = navyGradient()
            setOnTouchListener { _, e ->
                when (e.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        downX = e.rawX; downY = e.rawY
                        startA = params.width; startB = params.height
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dm2 = resources.displayMetrics
                        params.width = (startA + (e.rawX - downX).toInt())
                            .coerceIn(dp(240), dm2.widthPixels)
                        params.height = (startB + (e.rawY - downY).toInt())
                            .coerceIn(dp(140), dm2.heightPixels)
                        container?.let { wm.updateViewLayout(it, params) }
                    }
                }
                true
            }
        }
        handle.addView(View(this).apply { background = rounded(0x99FFFFFF.toInt(), dp(2).toFloat()) },
            LinearLayout.LayoutParams(dp(26), dp(3)))
        handle.addView(TextView(this).apply {
            text = "tarik untuk ubah ukuran"
            setTextColor(0xCCFFFFFF.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 9f)
            setPadding(dp(8), 0, dp(8), 0)
        })
        handle.addView(View(this).apply { background = rounded(0x99FFFFFF.toInt(), dp(2).toFloat()) },
            LinearLayout.LayoutParams(dp(26), dp(3)))
        box.addView(handle, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(26)))

        applyStyles()
        tv.text = store.script
        wm.addView(box, params)
        container = box
    }

    private fun applyStyles() {
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, store.textSize.toFloat())
        prompter.setBackgroundColor(Color.argb(store.bgOpacity * 255 / 100, 0, 0, 0))
        speedLabel.text = "≡ ${store.speed}"
        updateGuide()
    }

    private fun updateGuide() {
        val lh = tv.lineHeight
        val bandH = (lh * 1.35f).toInt()
        val lp = guide.layoutParams as FrameLayout.LayoutParams
        lp.height = bandH
        guide.layoutParams = lp
        tv.setPadding(dp(14), dp(12) + (bandH - lh) / 2, dp(14), tv.paddingBottom)
    }

    private fun reload() {
        stopLoops()
        state = State.IDLE
        actionBtn.text = "Mulai"
        countdownView.visibility = View.GONE
        tv.alpha = 1f
        tv.text = store.script
        scroll.scrollTo(0, 0)
        applyStyles()
    }

    private fun changeSpeed(d: Int) {
        store.speed = (store.speed + d).coerceIn(5, 100)
        speedLabel.text = "≡ ${store.speed}"
    }

    private fun changeSize(d: Int) {
        store.textSize = (store.textSize + d).coerceIn(16, 60)
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, store.textSize.toFloat())
        updateGuide()
    }

    // ---------- Logika prompter ----------
    private fun onAction() {
        when (state) {
            State.IDLE -> { scroll.scrollTo(0, 0); startCountdown() }
            State.PAUSED -> startRunning()
            State.COUNTDOWN -> cancelCountdown()
            State.RUNNING -> pause()
        }
    }

    private val countRunnable = object : Runnable {
        override fun run() {
            if (count > 0) {
                countdownView.text = count.toString()
                count--
                handler.postDelayed(this, 1000)
            } else {
                countdownView.visibility = View.GONE
                tv.alpha = 1f
                startRunning()
            }
        }
    }

    private val scrollRunnable = object : Runnable {
        override fun run() {
            val now = SystemClock.uptimeMillis()
            val dt = (now - lastT) / 1000f
            lastT = now
            pos += store.speed * 1.2f * resources.displayMetrics.density * dt
            val max = (tv.height - scroll.height).coerceAtLeast(0)
            val end = (max - scroll.height * 0.25f).coerceAtLeast(0f)
            if (pos >= end) {
                scroll.scrollTo(0, end.toInt())
                state = State.IDLE
                actionBtn.text = "Mulai"
                return
            }
            scroll.scrollTo(0, pos.toInt())
            handler.postDelayed(this, 16)
        }
    }

    private fun startCountdown() {
        state = State.COUNTDOWN
        actionBtn.text = "Batal"
        count = 3
        countdownView.visibility = View.VISIBLE
        tv.alpha = 0.25f
        handler.post(countRunnable)
    }

    private fun cancelCountdown() {
        handler.removeCallbacks(countRunnable)
        countdownView.visibility = View.GONE
        tv.alpha = 1f
        state = State.IDLE
        actionBtn.text = "Mulai"
    }

    private fun startRunning() {
        state = State.RUNNING
        actionBtn.text = "Jeda"
        pos = scroll.scrollY.toFloat()
        lastT = SystemClock.uptimeMillis()
        handler.removeCallbacks(scrollRunnable)
        handler.post(scrollRunnable)
    }

    private fun pause() {
        handler.removeCallbacks(scrollRunnable)
        state = State.PAUSED
        actionBtn.text = "Lanjut"
    }

    private fun stopLoops() {
        handler.removeCallbacks(countRunnable)
        handler.removeCallbacks(scrollRunnable)
    }
}
