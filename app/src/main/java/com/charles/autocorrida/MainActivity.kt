package com.charles.autocorrida

import android.app.Activity
import android.content.Intent
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.text.InputType
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast

/** Tela de configurações montada em código (sem XML/bibliotecas = APK leve). */
class MainActivity : Activity() {

    private lateinit var cfg: Config
    private lateinit var status: TextView
    private lateinit var contador: TextView
    private val edits = LinkedHashMap<String, Pair<EditText, Boolean>>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        cfg = Config(this)
        val dp = resources.displayMetrics.density
        val pad = (16 * dp).toInt()
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad * 3)
        }

        fun titulo(t: String, tam: Float = 18f) = col.addView(TextView(this).apply {
            text = t; textSize = tam; setTypeface(null, Typeface.BOLD)
            setPadding(0, (16 * dp).toInt(), 0, (4 * dp).toInt())
        })
        fun botao(t: String, acao: () -> Unit) = col.addView(Button(this).apply {
            text = t; setOnClickListener { acao() }
        })

        titulo("AutoCorrida", 24f)
        status = TextView(this).also { col.addView(it) }
        contador = TextView(this).also { col.addView(it) }

        botao("1. Ativar serviço de acessibilidade") {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        botao("2. Não deixar o Android fechar o app (bateria)") {
            val pm = getSystemService(PowerManager::class.java)
            if (pm.isIgnoringBatteryOptimizations(packageName)) toast("Já está liberado")
            else startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName")))
        }
        botao("Zerar contador") { cfg.aceitas = 0; atualizarStatus() }

        var secao = ""
        for (c in Config.CAMPOS) {
            if (c.secao != secao) { titulo(c.secao); secao = c.secao }
            col.addView(TextView(this).apply { text = c.rotulo; setPadding(0, (8 * dp).toInt(), 0, 0) })
            val e = EditText(this).apply {
                setText(cfg.texto(c.chave))
                isSingleLine = true
                inputType = if (c.numero) InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
                            else InputType.TYPE_CLASS_TEXT
            }
            edits[c.chave] = e to c.numero
            col.addView(e)
            if (c.ajuda.isNotEmpty()) col.addView(TextView(this).apply { text = c.ajuda; textSize = 12f; alpha = 0.7f })
        }

        titulo("Outros")
        val sw = Switch(this).apply { text = "Vibrar ao escolher corrida"; isChecked = cfg.vibrar }
        col.addView(sw)

        botao("SALVAR") {
            for ((k, par) in edits) {
                val (e, numero) = par
                var v = e.text.toString().trim()
                if (numero) v = v.replace(',', '.')
                cfg.salvar(k, v)
            }
            cfg.vibrar = sw.isChecked
            toast("Salvo! Vale na hora, sem reiniciar.")
            finish()
        }

        setContentView(ScrollView(this).apply { addView(col) })
    }

    override fun onResume() {
        super.onResume()
        atualizarStatus()
    }

    private fun atualizarStatus() {
        val ativo = AutoService.instance != null
        status.text = if (ativo)
            "Serviço: ATIVO ✅  (use a barrinha flutuante: ▶ inicia / ■ para)\nÚltimo app lido: ${AutoService.ultimoPacote}"
        else "Serviço: INATIVO ❌ — toque no botão 1 e ative \"AutoCorrida\""
        contador.text = "Corridas escolhidas: ${cfg.aceitas}"
    }

    private fun toast(t: String) = Toast.makeText(this, t, Toast.LENGTH_SHORT).show()
}
