package com.charles.autocorrida

import android.accessibilityservice.AccessibilityService
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Build
import android.os.SystemClock
import android.view.Display
import android.annotation.TargetApi
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions

/**
 * Leitura por imagem: o próprio serviço de acessibilidade tira um print (Android 11+,
 * sem pedir "gravar a tela") e o ML Kit lê os textos, offline.
 */
class Ocr(private val svc: AccessibilityService) {

    private val reconhecedor = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    @Volatile var ocupado = false
        private set
    private var ultimo = 0L
    var ultimoErro: String = ""
        private set

    val disponivel get() = Build.VERSION.SDK_INT >= 30

    /** Entrega a lista de textos com posição na tela, ou null se não conseguiu. */
    fun ler(intervaloMinMs: Long, cb: (List<Item>?) -> Unit) {
        if (!disponivel) { ultimoErro = "Android abaixo do 11"; cb(null); return }
        val agora = SystemClock.uptimeMillis()
        if (ocupado || agora - ultimo < intervaloMinMs.coerceAtLeast(350)) { cb(null); return }
        ocupado = true
        ultimo = agora
        capturar(cb)
    }

    @TargetApi(30)
    private fun capturar(cb: (List<Item>?) -> Unit) {
        try {
            svc.takeScreenshot(Display.DEFAULT_DISPLAY, svc.mainExecutor,
                object : AccessibilityService.TakeScreenshotCallback {
                    override fun onSuccess(res: AccessibilityService.ScreenshotResult) {
                        val hb = res.hardwareBuffer
                        val bmp: Bitmap? = try {
                            Bitmap.wrapHardwareBuffer(hb, res.colorSpace)?.let { hw ->
                                hw.copy(Bitmap.Config.ARGB_8888, false).also { hw.recycle() }
                            }
                        } catch (e: Exception) { null } finally { hb.close() }
                        if (bmp == null) { fim(null, "não converteu o print", cb); return }
                        reconhecer(bmp, cb)
                    }
                    override fun onFailure(code: Int) {
                        fim(null, "print falhou (código $code)", cb)
                    }
                })
        } catch (e: Throwable) {
            fim(null, "print: ${e.message}", cb)
        }
    }

    private fun reconhecer(bmp: Bitmap, cb: (List<Item>?) -> Unit) {
        reconhecedor.process(InputImage.fromBitmap(bmp, 0))
            .addOnSuccessListener { txt ->
                val itens = ArrayList<Item>()
                for (bloco in txt.textBlocks) for (linha in bloco.lines) {
                    val r = linha.boundingBox ?: continue
                    itens += Item(linha.text, Rect(r))
                }
                bmp.recycle()
                fim(itens, "", cb)
            }
            .addOnFailureListener { e -> bmp.recycle(); fim(null, "OCR: ${e.message}", cb) }
    }

    private fun fim(itens: List<Item>?, erro: String, cb: (List<Item>?) -> Unit) {
        ultimoErro = erro
        ocupado = false
        cb(itens)
    }

    fun fechar() = try { reconhecedor.close() } catch (_: Exception) {}
}
