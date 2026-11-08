package com.charles.autocorrida

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import kotlin.math.abs

data class Oferta(
    val porKm: Double,
    val valor: Double?,
    val kmEmbarque: Double?,
    val botao: Rect,
    val node: AccessibilityNodeInfo
)

class Tela(val buscando: Boolean, val ofertas: List<Oferta>, val area: Rect)

/** Lê a árvore de acessibilidade e separa os cards pelo botão "Escolher" de cada um. */
object Leitor {
    private class Item(val texto: String, val r: Rect, val node: AccessibilityNodeInfo)

    private val RE_KM = Regex("""R\$\s*(\d{1,3}(?:[.,]\d{1,2})?)\s*/\s*km""", RegexOption.IGNORE_CASE)
    private val RE_VALOR = Regex("""R\$\s*(\d{1,4}(?:\.\d{3})*,\d{2})(?!\d)(?!\s*/\s*km)""", RegexOption.IGNORE_CASE)
    private val RE_EMBARQUE = Regex("""\(\s*\d+\s*min\s+(\d+(?:[.,]\d+)?)\s*km\s*\)""", RegexOption.IGNORE_CASE)

    fun num(s: String): Double? =
        if (s.contains(',')) s.replace(".", "").replace(',', '.').toDoubleOrNull()
        else s.toDoubleOrNull()

    fun ler(root: AccessibilityNodeInfo, cfg: Config): Tela {
        val itens = ArrayList<Item>(80)
        coletar(root, itens, 0)
        val area = Rect()
        root.getBoundsInScreen(area)

        val kBusca = cfg.textoBuscando.lowercase()
        val kEsc = cfg.textoEscolher.lowercase()
        val buscando = kBusca.isNotEmpty() && itens.any { it.texto.lowercase().contains(kBusca) }

        // Botões "Escolher" (sem duplicar quando texto e contêiner repetem o mesmo rótulo)
        val botoes = ArrayList<Item>()
        for (it in itens.filter { it.texto.trim().lowercase() == kEsc }.sortedBy { it.r.centerY() }) {
            if (botoes.none { b -> abs(b.r.centerY() - it.r.centerY()) < 20 }) botoes += it
        }

        // Cada card = tudo entre o botão anterior e o botão atual
        val ofertas = ArrayList<Oferta>()
        var topo = area.top
        for (b in botoes) {
            val txt = itens.asSequence()
                .filter { it !== b && it.r.top >= topo - 4 && it.r.bottom <= b.r.bottom + 4 }
                .joinToString(" ") { it.texto }
            val km = RE_KM.find(txt)?.groupValues?.get(1)?.let(::num)
            if (km != null) {
                ofertas += Oferta(
                    porKm = km,
                    valor = RE_VALOR.find(txt)?.groupValues?.get(1)?.let(::num),
                    kmEmbarque = RE_EMBARQUE.find(txt)?.groupValues?.get(1)?.let(::num),
                    botao = Rect(b.r),
                    node = b.node
                )
            }
            topo = b.r.bottom
        }
        return Tela(buscando && ofertas.isEmpty(), ofertas, area)
    }

    private fun coletar(n: AccessibilityNodeInfo?, out: MutableList<Item>, prof: Int) {
        if (n == null || prof > 45 || !n.isVisibleToUser) return
        val t = n.text?.toString()
        val cd = n.contentDescription?.toString()
        if (!t.isNullOrBlank() || !cd.isNullOrBlank()) {
            val r = Rect()
            n.getBoundsInScreen(r)
            if (!t.isNullOrBlank()) out += Item(t.replace(' ', ' '), r, n)
            if (!cd.isNullOrBlank() && cd != t) out += Item(cd.replace(' ', ' '), r, n)
        }
        for (i in 0 until n.childCount) coletar(n.getChild(i), out, prof + 1)
    }
}
