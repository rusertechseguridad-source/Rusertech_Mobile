package com.rusertech.mobile.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.File
import java.io.FileOutputStream

/**
 * Comprime una foto tomada por la cámara a un JPEG liviano apto para subir
 * con datos móviles. 1280px de lado largo alcanza para validar visualmente
 * el estado de una carga sin generar archivos pesados.
 *
 * FIX-9: objetivo ≤ 500 KB por foto (spec). Si con la calidad inicial el
 * archivo queda más pesado (fotos con mucho detalle), se baja la calidad en
 * escalones hasta cumplir el objetivo. El techo duro del backend es 2 MB;
 * con este esquema jamás nos acercamos.
 */
object ImageCompressor {
    private const val MAX_DIMENSION = 1280
    const val TARGET_MAX_BYTES = 500 * 1024L  // objetivo del spec: ≤ 500 KB
    private val QUALITY_STEPS = intArrayOf(72, 60, 50, 40)

    fun compressToFile(context: Context, sourceUri: Uri, targetFile: File): Boolean {
        return try {
            val input = context.contentResolver.openInputStream(sourceUri) ?: return false
            val original = BitmapFactory.decodeStream(input)
            input.close()

            val scale = MAX_DIMENSION.toFloat() / maxOf(original.width, original.height)
            val scaled = if (scale < 1f) {
                Bitmap.createScaledBitmap(
                    original, (original.width * scale).toInt(), (original.height * scale).toInt(), true
                )
            } else original

            // Bajar la calidad en escalones hasta cumplir el objetivo de 500 KB.
            // El último escalón se acepta como esté: una foto algo más pesada es
            // mejor que ninguna, y 1280px @ 40% nunca supera el techo de 2 MB.
            var done = false
            for (quality in QUALITY_STEPS) {
                FileOutputStream(targetFile).use { out ->
                    scaled.compress(Bitmap.CompressFormat.JPEG, quality, out)
                }
                if (targetFile.length() <= TARGET_MAX_BYTES) { done = true; break }
            }
            if (!done && targetFile.length() == 0L) return false

            if (scaled != original) scaled.recycle()
            original.recycle()
            true
        } catch (_: Exception) {
            false
        }
    }
}
