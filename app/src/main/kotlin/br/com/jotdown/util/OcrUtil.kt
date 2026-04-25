package br.com.jotdown.util

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions

object OcrUtil {
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    fun extractTextFromBitmap(bitmap: Bitmap, onSuccess: (String) -> Unit, onError: (Exception) -> Unit) {
        val image = InputImage.fromBitmap(bitmap, 0)
        recognizer.process(image)
            .addOnSuccessListener { visionText ->
                onSuccess(visionText.text)
            }
            .addOnFailureListener { e ->
                onError(e)
            }
    }
}
