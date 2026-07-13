package com.rusertech.mobile.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.File
import java.io.FileOutputStream

/**
 * Comprime una foto tomada por la cámara a un JPEG liviano apto para subir
 * con datos móviles. 1280px de lado largo y calidad 72% son suficientes
 * para validar visualmente el estado de una carga sin generar archivos pesados.
 */
object ImageCompressor {
    private const val MAX_DIMENSION = 1280
    private const val JPEG_QUALITY = 72

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

            FileOutputStream(targetFile).use { out ->
                scaled.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
            }
            if (scaled != original) scaled.recycle()
            original.recycle()
            true
        } catch (_: Exception) {
            false
        }
    }
}
