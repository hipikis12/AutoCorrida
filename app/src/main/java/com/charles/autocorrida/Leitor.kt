package com.charles.autocorrida

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import kotlin.math.abs

data class Oferta(
    val porKm: Double,
    val valor: Double?,
    val kmEmbarque: Double?,
    val botao: Rect,
    val node: AccessibilityNodeInfo?   // null quando veio da leitura por imagem
)

class Tela(val buscando: Boolean, val ofertas: List<Oferta>, val area: Rect, val origem: String) {
    val vazia get() = !buscando && ofertas.isEmpty()
}

/** Um pedaço de texto na tela com sua posição (vem da acessibilidade OU do OCR). */
class Item(val texto: String, val r: Rect, val node: AccessibilityNodeInfo? = null)

/** Interpreta os textos e separa os cards pelo botão "Escolher" de cada um. */
object Leitor {

    // Tolerante a erros comuns do OCR: "RS", "R5", espaço sobrando, "/ km", "lkm"
    private val RE_KM = Regex("""R\s*[${"$"}S5]\s*(\d{1,3}\s*[.,]\s*\d{1,2})\s*/\s*[kK]\s*[mM]""")
    private val RE_VALOR = Regex("""R\s*[${"$"}S5]\s*(\d{1,4}(?:\.\d{3})*\s*,\s*\d{2})(?!\d)(?!\s*/\s*[kK])""")
    private val RE_EMBARQUE = Regex("""\(\s*\d+\s*min\s*[,.]?\s*(\d+(?:[.,]\d+)?)\s*km""", RegexOption.IGNORE_CASE)

    fun num(s: String): Double? {
        val t = s.replace(" ", "")
        return if (t.contains(',')) t.replace(".", "").replace(',', '.').toDoubleOrNull() else t.toDoubleOrNull()
    }

    /** Lê pela árvore de acessibilidade. */
    fun ler(root: AccessibilityNodeInfo, cfg: Config): Tela {
        val itens = ArrayList<Item>(80)
        coletar(root, itens, 0)
        val area = Rect()
        root.getBoundsInScreen(area)
        return analisar(itens, area, cfg, "acessibilidade")
    }

    fun analisar(itens: List<Item>, area: Rect, cfg: Config, origem: String): Tela {
        val kBusca = cfg.textoBuscando.lowercase()
        val kEsc = cfg.textoEscolher.lowercase()
        val buscando = kBusca.isNotEmpty() && itens.any { it.texto.lowercase().contains(kBusca) }

        // Botões "Escolher" (aceita pequena sujeira do OCR em volta, ex.: "Escolher >")
        val botoes = ArrayList<Item>()
        for (it in itens.filter { ehBotao(it.texto, kEsc) }.sortedBy { it.r.centerY() }) {
            if (botoes.none { b -> abs(b.r.centerY() - it.r.centerY()) < 20 }) botoes += it
        }

        // Cada card = tudo entre o botão anterior e o botão atual
        val ofertas = ArrayList<Oferta>()
        var topo = area.top
        for (b in botoes) {
            val txt = itens.asSequence()
                .filter { it !== b && it.r.top >= topo - 4 && it.r.bottom <= b.r.bottom + 4 }
                .sortedWith(compareBy({ it.r.top / 15 }, { it.r.left }))
                .joinToString(" ") { it.texto }
            val km = RE_KM.find(txt)?.groupValues?.get(1)?.let(::num)
            if (km != null && km in 0.3..30.0) {
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
        return Tela(buscando && ofertas.isEmpty(), ofertas, area, origem)
    }

    private fun ehBotao(texto: String, k: String): Boolean {
        val t = texto.trim().lowercase()
        if (k.isEmpty()) return false
        return t == k || (t.contains(k) && t.length <= k.length + 4)
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
