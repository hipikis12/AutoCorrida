package com.charles.autocorrida

import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Rect
import java.util.Random

/** Gera gestos com variação: posição, curva, velocidade e duração mudam a cada vez. */
object Human {
    private val rnd = Random()

    fun range(a: Double, b: Double): Double = if (b <= a) a else a + rnd.nextDouble() * (b - a)
    fun rangeL(a: Long, b: Long): Long = if (b <= a) a else a + (rnd.nextDouble() * (b - a)).toLong()
    fun chance(pct: Double): Boolean = rnd.nextDouble() * 100 < pct

    private fun dentro(x: Double, y: Double, evitar: Rect?) =
        evitar != null && evitar.contains(x.toInt(), y.toInt())

    /**
     * Arrastar para baixo (pull-to-refresh) num ponto diferente da tela a cada vez,
     * com leve diagonal, curva e duração aleatórias — como um dedo de verdade.
     */
    fun arrastarParaBaixo(area: Rect, evitar: Rect?): GestureDescription {
        val w = area.width().toDouble()
        val h = area.height().toDouble()

        var x0 = 0.0
        var y0 = 0.0
        for (i in 0 until 12) {
            x0 = area.left + w * range(0.22, 0.80)
            y0 = area.top + h * range(0.30, 0.48)
            if (!dentro(x0, y0, evitar)) break
        }
        val y1 = area.top + h * range(0.62, 0.88)
        val x1 = x0 + w * range(-0.07, 0.07)                 // dedo nunca desce reto
        val cx = (x0 + x1) / 2 + w * range(-0.05, 0.05)      // curva
        val cy = (y0 + y1) / 2 + h * range(-0.05, 0.05)

        val path = Path().apply {
            moveTo(x0.coerceAtLeast(1.0).toFloat(), y0.coerceAtLeast(1.0).toFloat())
            quadTo(cx.coerceAtLeast(1.0).toFloat(), cy.coerceAtLeast(1.0).toFloat(),
                x1.coerceAtLeast(1.0).toFloat(), y1.coerceAtLeast(1.0).toFloat())
        }
        val duracao = rangeL(260, 680)
        return GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, rangeL(0, 40), duracao))
            .build()
    }

    /** Toque num ponto aleatório do botão (longe das bordas), com micro-movimento e duração variável. */
    fun tocar(alvo: Rect, evitar: Rect?): GestureDescription {
        var x = alvo.exactCenterX().toDouble()
        var y = alvo.exactCenterY().toDouble()
        for (i in 0 until 15) {
            val tx = alvo.left + alvo.width() * range(0.22, 0.78)
            val ty = alvo.top + alvo.height() * range(0.30, 0.70)
            if (!dentro(tx, ty, evitar)) { x = tx; y = ty; break }
        }
        val path = Path().apply {
            moveTo(x.toFloat(), y.toFloat())
            lineTo((x + range(-2.5, 2.5)).toFloat(), (y + range(-2.5, 2.5)).toFloat())
        }
        return GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, rangeL(45, 140)))
            .build()
    }
}
