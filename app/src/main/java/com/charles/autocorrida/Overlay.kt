package com.charles.autocorrida

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView

/** Barrinha flutuante: [▶/■ estado] [⚙ config] [✥ mover]. Usa overlay de acessibilidade (não precisa de permissão extra). */
class Overlay(private val svc: AutoService) {
    private val wm = svc.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val dp = svc.resources.displayMetrics.density
    private val prefs = svc.getSharedPreferences("overlay", Context.MODE_PRIVATE)
    private var root: LinearLayout? = null
    private lateinit var btnEstado: TextView

    private val lp = WindowManager.LayoutParams(
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
        PixelFormat.TRANSLUCENT
    ).apply {
        gravity = Gravity.TOP or Gravity.START
        x = prefs.getInt("x", 0)
        y = prefs.getInt("y", (svc.resources.displayMetrics.heightPixels * 0.45).toInt())
    }

    private fun botao(txt: String, cor: Int) = TextView(svc).apply {
        text = txt
        setTextColor(Color.WHITE)
        textSize = 15f
        gravity = Gravity.CENTER
        val s = (34 * dp).toInt()
        layoutParams = LinearLayout.LayoutParams(s, s).apply { bottomMargin = (4 * dp).toInt() }
        background = GradientDrawable().apply { cornerRadius = 8 * dp; setColor(cor) }
    }

    @SuppressLint("ClickableViewAccessibility")
    fun mostrar() {
        if (root != null) return
        val l = LinearLayout(svc).apply { orientation = LinearLayout.VERTICAL }
        btnEstado = botao("▶", COR_PARADO).also { it.setOnClickListener { svc.alternar() } }
        val cfg = botao("⚙", COR_NEUTRA).also { it.setOnClickListener { svc.abrirConfig() } }
        val mover = botao("✥", COR_NEUTRA).also { it.setOnTouchListener(arrastar) }
        l.addView(btnEstado); l.addView(cfg); l.addView(mover)
        root = l
        wm.addView(l, lp)
    }

    private val arrastar = object : View.OnTouchListener {
        var ix = 0; var iy = 0; var tx = 0f; var ty = 0f
        override fun onTouch(v: View, e: MotionEvent): Boolean {
            when (e.action) {
                MotionEvent.ACTION_DOWN -> { ix = lp.x; iy = lp.y; tx = e.rawX; ty = e.rawY }
                MotionEvent.ACTION_MOVE -> {
                    lp.x = ix + (e.rawX - tx).toInt()
                    lp.y = iy + (e.rawY - ty).toInt()
                    root?.let { wm.updateViewLayout(it, lp) }
                }
                MotionEvent.ACTION_UP -> prefs.edit().putInt("x", lp.x).putInt("y", lp.y).apply()
            }
            return true
        }
    }

    fun atualizar(rodando: Boolean, estado: AutoService.Estado) {
        if (root == null) return
        btnEstado.text = if (rodando) "■" else "▶"
        val cor = if (!rodando) COR_PARADO else when (estado) {
            AutoService.Estado.BUSCANDO -> COR_BUSCANDO
            AutoService.Estado.OFERTAS -> COR_OFERTAS
            AutoService.Estado.AGUARDANDO -> COR_AGUARDANDO
            else -> COR_OUTRA
        }
        (btnEstado.background as GradientDrawable).setColor(cor)
    }

    /** Retângulo da barrinha (com margem) para os gestos nunca tocarem nela. */
    fun area(): Rect? {
        val v = root ?: return null
        val loc = IntArray(2)
        v.getLocationOnScreen(loc)
        val m = (10 * dp).toInt()
        return Rect(loc[0] - m, loc[1] - m, loc[0] + v.width + m, loc[1] + v.height + m)
    }

    fun remover() {
        root?.let { try { wm.removeView(it) } catch (_: Exception) {} }
        root = null
    }

    companion object {
        private const val COR_PARADO = 0xCC616161.toInt()
        private const val COR_NEUTRA = 0xCC303030.toInt()
        private const val COR_BUSCANDO = 0xE02E7D32.toInt()   // verde
        private const val COR_OFERTAS = 0xE0F9A825.toInt()    // amarelo
        private const val COR_AGUARDANDO = 0xE01565C0.toInt() // azul
        private const val COR_OUTRA = 0xE06A1B9A.toInt()      // roxo: tela desconhecida
    }
}
