package com.charles.autocorrida

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import kotlin.math.abs

/**
 * Máquina de estados:
 *  BUSCANDO   -> "Ainda buscando solicitações": arrasta pra baixo no intervalo configurado
 *  OFERTAS    -> cards visíveis: PARA de arrastar, lê R$/km, escolhe o melhor que passar no filtro
 *  AGUARDANDO -> depois de clicar: não faz nada até a tela de busca voltar (ou timeout)
 */
class AutoService : AccessibilityService() {

    enum class Estado { PARADO, BUSCANDO, OFERTAS, AGUARDANDO, OUTRA }

    companion object {
        @Volatile var instance: AutoService? = null
        @Volatile var ultimoPacote: String = "-"
    }

    private lateinit var cfg: Config
    private val h = Handler(Looper.getMainLooper())
    private var overlay: Overlay? = null

    private var rodando = false
    private var estado = Estado.PARADO
    private var gestoEmAndamento = false
    private var cliqueAgendado = false
    private var proximaAtualizacao = 0L
    private var cliqueEm = 0L
    private var jaRetentou = false
    private var ofertasDesde = 0L
    private var ultimoPasso = 0L

    // ---------- ciclo de vida ----------
    override fun onServiceConnected() {
        instance = this
        cfg = Config(this)
        overlay = Overlay(this).also { it.mostrar() }
        atualizarOverlay()
    }

    override fun onUnbind(intent: Intent?): Boolean {
        parar()
        overlay?.remover(); overlay = null
        instance = null
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        h.removeCallbacksAndMessages(null)
        overlay?.remover(); overlay = null
        instance = null
        super.onDestroy()
    }

    override fun onInterrupt() {}

    /** Eventos da tela só "acordam" o loop mais cedo, para reagir rápido quando os cards aparecem. */
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (!rodando) return
        if (SystemClock.uptimeMillis() - ultimoPasso > 60) {
            h.removeCallbacks(loop)
            h.post(loop)
        }
    }

    // ---------- controles do overlay ----------
    fun alternar() = if (rodando) parar() else iniciar()

    private fun iniciar() {
        rodando = true
        estado = Estado.OUTRA
        proximaAtualizacao = SystemClock.uptimeMillis() + 800
        h.post(loop)
        atualizarOverlay()
    }

    private fun parar() {
        rodando = false
        estado = Estado.PARADO
        cliqueAgendado = false
        h.removeCallbacksAndMessages(null)
        atualizarOverlay()
    }

    fun abrirConfig() {
        startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    private fun mudar(novo: Estado) {
        if (estado != novo) { estado = novo; atualizarOverlay() }
    }

    private fun atualizarOverlay() = overlay?.atualizar(rodando, estado)

    // ---------- loop principal ----------
    private val loop = object : Runnable {
        override fun run() {
            if (!rodando) return
            ultimoPasso = SystemClock.uptimeMillis()
            try { passo() } catch (_: Throwable) {}
            h.removeCallbacks(this)
            h.postDelayed(this, 200)
        }
    }

    private fun passo() {
        if (gestoEmAndamento || cliqueAgendado) return
        val now = SystemClock.uptimeMillis()
        val root = raizDoApp() ?: return
        val pkg = root.packageName?.toString() ?: return
        ultimoPacote = pkg
        if (cfg.pacoteAlvo.isNotEmpty() && pkg != cfg.pacoteAlvo) return  // outro app na frente

        val tela = Leitor.ler(root, cfg)

        when {
            tela.buscando -> {
                if (estado != Estado.BUSCANDO) {
                    mudar(Estado.BUSCANDO)
                    proximaAtualizacao = now + intervalo()
                } else if (now >= proximaAtualizacao) {
                    arrastar(tela.area)
                    proximaAtualizacao = now + intervalo()
                }
            }

            tela.ofertas.isNotEmpty() -> {
                if (estado == Estado.AGUARDANDO) {
                    // O clique pode não ter pegado: tenta de novo UMA vez se a lista continuar ali.
                    if (!jaRetentou && now - cliqueEm > 4000) jaRetentou = true else return
                }
                if (estado != Estado.OFERTAS) { mudar(Estado.OFERTAS); ofertasDesde = now }

                val melhor = tela.ofertas.filter(::aprovada).maxByOrNull { it.porKm }
                if (melhor != null) {
                    agendarEscolha(melhor)
                } else if (cfg.ofertasRuinsSeg > 0 && now - ofertasDesde > cfg.ofertasRuinsSeg * 1000) {
                    arrastar(tela.area)
                    ofertasDesde = now
                }
            }

            else -> {
                // Tela de corrida, confirmação, mapa...: fica quieto.
                if (estado == Estado.AGUARDANDO) {
                    if (now - cliqueEm > cfg.timeoutAguardarSeg * 1000) mudar(Estado.OUTRA)
                } else mudar(Estado.OUTRA)
            }
        }
    }

    /**
     * Raiz da janela do app que está na tela, IGNORANDO a nossa barrinha e a barra do sistema.
     * (Antes usava rootInActiveWindow, que vira a barrinha depois de tocar no ▶ — por isso não lia nada.)
     */
    private fun raizDoApp(): AccessibilityNodeInfo? {
        val todas: List<AccessibilityWindowInfo> = try { windows } catch (_: Exception) { emptyList() }
        val candidatas = todas
            .filter { it.type == AccessibilityWindowInfo.TYPE_APPLICATION }
            .mapNotNull { w -> w.root?.let { w to it } }
            .filter { (_, r) -> r.packageName?.toString() != packageName }
        val escolhida = candidatas.firstOrNull { it.first.isActive || it.first.isFocused }
            ?: candidatas.maxByOrNull { (w, _) -> Rect().also { w.getBoundsInScreen(it) }.let { it.width() * it.height() } }
        if (escolhida != null) return escolhida.second
        val r = rootInActiveWindow ?: return null
        return if (r.packageName?.toString() == packageName) null else r
    }

    /** Botão 🔍: salva tudo o que o serviço consegue ler em todas as janelas. */
    fun diagnosticar() {
        val sb = StringBuilder()
        var total = 0
        val ws = try { windows } catch (_: Exception) { emptyList<AccessibilityWindowInfo>() }
        sb.append("Janelas: ${ws.size}\n")
        for (w in ws) {
            val r = w.root
            val b = Rect().also { w.getBoundsInScreen(it) }
            sb.append("\n=== janela tipo=${w.type} ativa=${w.isActive} foco=${w.isFocused} pkg=${r?.packageName} $b\n")
            if (r != null) total += despejar(r, sb, 0)
        }
        val raiz = raizDoApp()
        val tela = raiz?.let { Leitor.ler(it, cfg) }
        val resumo = "App lido: ${raiz?.packageName}\nTextos encontrados: $total\n" +
            "Tela de busca detectada: ${tela?.buscando}\nOfertas detectadas: ${tela?.ofertas?.size ?: 0}" +
            (tela?.ofertas?.joinToString("") { "\n  • R$/km=${it.porKm} valor=${it.valor} embarque=${it.kmEmbarque}km" } ?: "") +
            "\n\n"
        try { java.io.File(filesDir, "diagnostico.txt").writeText(resumo + sb) } catch (_: Exception) {}
        android.widget.Toast.makeText(this,
            "Diagnóstico: $total textos em ${raiz?.packageName ?: "nenhum app"}. Veja em ⚙", android.widget.Toast.LENGTH_LONG).show()
    }

    private fun despejar(n: AccessibilityNodeInfo?, sb: StringBuilder, prof: Int): Int {
        if (n == null || prof > 45 || sb.length > 200_000) return 0
        var c = 0
        val t = n.text?.toString()
        val cd = n.contentDescription?.toString()
        val id = n.viewIdResourceName
        if (!t.isNullOrBlank() || !cd.isNullOrBlank() || prof < 3) {
            val r = Rect().also { n.getBoundsInScreen(it) }
            sb.append("  ".repeat(prof.coerceAtMost(12)))
              .append(n.className?.toString()?.substringAfterLast('.'))
            if (!t.isNullOrBlank()) { sb.append(" text=\"").append(t).append('"'); c++ }
            if (!cd.isNullOrBlank()) { sb.append(" desc=\"").append(cd).append('"'); c++ }
            if (id != null) sb.append(" id=").append(id)
            if (n.isClickable) sb.append(" [clicável]")
            sb.append(' ').append(r).append('\n')
        }
        for (i in 0 until n.childCount) c += despejar(n.getChild(i), sb, prof + 1)
        return c
    }

    private fun aprovada(o: Oferta): Boolean {
        if (o.porKm < cfg.minPorKm) return false
        if (cfg.minValor > 0 && (o.valor ?: 0.0) < cfg.minValor) return false
        if (cfg.maxKmEmbarque > 0 && (o.kmEmbarque ?: Double.MAX_VALUE) > cfg.maxKmEmbarque) return false
        return true
    }

    /** Intervalo com variação aleatória + às vezes uma pausa "distraída". */
    private fun intervalo(): Long {
        val v = (cfg.variacaoPct / 100.0).coerceIn(0.0, 0.9)
        var ms = cfg.intervaloSeg * 1000 * Human.range(1 - v, 1 + v)
        if (Human.chance(cfg.pausaChancePct)) ms += Human.range(cfg.pausaMinSeg, cfg.pausaMaxSeg) * 1000
        return ms.toLong().coerceAtLeast(800)
    }

    // ---------- ações ----------
    private fun agendarEscolha(oferta: Oferta) {
        cliqueAgendado = true
        val atraso = Human.rangeL(cfg.reacaoMinMs, cfg.reacaoMaxMs)   // "tempo de reação" humano
        h.postDelayed({
            cliqueAgendado = false
            if (!rodando) return@postDelayed
            // Relê a tela: o card ainda está lá e ainda passa no filtro?
            val root = raizDoApp() ?: return@postDelayed
            val alvo = Leitor.ler(root, cfg).ofertas.firstOrNull {
                abs(it.botao.centerY() - oferta.botao.centerY()) < 60 && aprovada(it)
            } ?: return@postDelayed
            tocar(alvo)
            cliqueEm = SystemClock.uptimeMillis()
            if (estado != Estado.AGUARDANDO) jaRetentou = false
            mudar(Estado.AGUARDANDO)
            cfg.aceitas = cfg.aceitas + 1
            vibrar()
        }, atraso)
    }

    private fun arrastar(area: Rect) {
        if (area.isEmpty) return
        executar(Human.arrastarParaBaixo(area, overlay?.area()), null)
    }

    private fun tocar(o: Oferta) {
        executar(Human.tocar(o.botao, overlay?.area())) { clicarNo(o.node) }
    }

    private fun executar(g: GestureDescription, seFalhar: (() -> Unit)?) {
        gestoEmAndamento = true
        val ok = dispatchGesture(g, object : GestureResultCallback() {
            override fun onCompleted(d: GestureDescription?) { gestoEmAndamento = false }
            override fun onCancelled(d: GestureDescription?) { gestoEmAndamento = false; seFalhar?.invoke() }
        }, null)
        if (!ok) { gestoEmAndamento = false; seFalhar?.invoke() }
    }

    /** Plano B: clique direto pelo nó de acessibilidade. */
    private fun clicarNo(n: AccessibilityNodeInfo?) {
        var x = n
        var i = 0
        while (x != null && !x.isClickable && i < 6) { x = x.parent; i++ }
        x?.performAction(AccessibilityNodeInfo.ACTION_CLICK)
    }

    @Suppress("DEPRECATION")
    private fun vibrar() {
        if (!cfg.vibrar) return
        val v = getSystemService(Vibrator::class.java) ?: return
        if (Build.VERSION.SDK_INT >= 26) v.vibrate(VibrationEffect.createOneShot(120, VibrationEffect.DEFAULT_AMPLITUDE))
    }
}
