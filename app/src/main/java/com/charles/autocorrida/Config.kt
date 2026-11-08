package com.charles.autocorrida

import android.content.Context

/** Todas as configurações ficam em SharedPreferences como texto, para a tela de config ser genérica. */
class Config(ctx: Context) {

    data class Campo(
        val chave: String,
        val secao: String,
        val rotulo: String,
        val padrao: String,
        val numero: Boolean = true,
        val ajuda: String = ""
    )

    companion object {
        val CAMPOS = listOf(
            Campo("minPorKm", "Filtro da corrida", "R$/km mínimo para aceitar", "2.00",
                ajuda = "O número vermelho do card (ex.: R$3,07/km). Aceita se for MAIOR OU IGUAL."),
            Campo("minValor", "Filtro da corrida", "Valor mínimo da corrida (R$)", "0",
                ajuda = "0 = desligado"),
            Campo("maxKmEmbarque", "Filtro da corrida", "Distância máxima até o embarque (km)", "0",
                ajuda = "Primeiro '(x min y km)' do card. 0 = desligado"),

            Campo("intervaloSeg", "Atualização (arrastar)", "Intervalo entre atualizações (s)", "5"),
            Campo("variacaoPct", "Atualização (arrastar)", "Variação aleatória do intervalo (%)", "30",
                ajuda = "Ex.: 5 s com 30% vira algo entre 3,5 e 6,5 s"),
            Campo("pausaChancePct", "Atualização (arrastar)", "Chance de pausa 'distraída' (%)", "10"),
            Campo("pausaMinSeg", "Atualização (arrastar)", "Pausa distraída mínima (s)", "2"),
            Campo("pausaMaxSeg", "Atualização (arrastar)", "Pausa distraída máxima (s)", "6"),
            Campo("ofertasRuinsSeg", "Atualização (arrastar)", "Atualizar se nenhuma oferta servir após (s)", "0",
                ajuda = "0 = nunca (fica parado na lista até ela mudar)"),

            Campo("reacaoMinMs", "Clique", "Tempo de reação mínimo (ms)", "350"),
            Campo("reacaoMaxMs", "Clique", "Tempo de reação máximo (ms)", "900"),
            Campo("timeoutAguardarSeg", "Clique", "Após escolher, esperar a tela de busca por até (s)", "90"),

            Campo("textoBuscando", "Textos da tela (avançado)", "Texto da tela de busca", "Ainda buscando", numero = false),
            Campo("textoEscolher", "Textos da tela (avançado)", "Texto do botão", "Escolher", numero = false),
            Campo("pacoteAlvo", "Textos da tela (avançado)", "Pacote do app de corrida", "", numero = false,
                ajuda = "Vazio = qualquer app. O status no topo mostra o último pacote lido."),
        )
        private val PADROES = CAMPOS.associate { it.chave to it.padrao }
    }

    private val p = ctx.getSharedPreferences("cfg", Context.MODE_PRIVATE)

    fun texto(k: String): String = p.getString(k, null) ?: PADROES[k] ?: ""
    private fun d(k: String): Double = texto(k).replace(',', '.').toDoubleOrNull() ?: (PADROES[k]?.toDoubleOrNull() ?: 0.0)
    fun salvar(k: String, v: String) = p.edit().putString(k, v).apply()

    val minPorKm get() = d("minPorKm")
    val minValor get() = d("minValor")
    val maxKmEmbarque get() = d("maxKmEmbarque")
    val intervaloSeg get() = d("intervaloSeg")
    val variacaoPct get() = d("variacaoPct")
    val pausaChancePct get() = d("pausaChancePct")
    val pausaMinSeg get() = d("pausaMinSeg")
    val pausaMaxSeg get() = d("pausaMaxSeg")
    val ofertasRuinsSeg get() = d("ofertasRuinsSeg")
    val reacaoMinMs get() = d("reacaoMinMs").toLong()
    val reacaoMaxMs get() = d("reacaoMaxMs").toLong()
    val timeoutAguardarSeg get() = d("timeoutAguardarSeg")
    val textoBuscando get() = texto("textoBuscando").trim()
    val textoEscolher get() = texto("textoEscolher").trim()
    val pacoteAlvo get() = texto("pacoteAlvo").trim()

    var vibrar: Boolean
        get() = p.getBoolean("vibrar", true)
        set(v) = p.edit().putBoolean("vibrar", v).apply()

    var aceitas: Int
        get() = p.getInt("aceitas", 0)
        set(v) = p.edit().putInt("aceitas", v).apply()
}
