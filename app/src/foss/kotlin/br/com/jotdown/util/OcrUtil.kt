package br.com.jotdown.util
import android.graphics.Bitmap

object OcrUtil {
    fun extractTextFromBitmap(bitmap: Bitmap, onSuccess: (String) -> Unit, onError: (Exception) -> Unit) {
        onSuccess("A extração de texto (OCR) requer bibliotecas proprietárias e foi desativada nesta versão (FOSS) para cumprir as regras do F-Droid. Baixe a versão completa no IzzyOnDroid ou no GitHub oficial.")
    }
}