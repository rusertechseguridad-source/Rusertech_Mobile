package com.rusertech.mobile.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import java.io.File
import java.io.FileOutputStream

/**
 * Comprime una foto tomada por la cámara a un JPEG liviano apto para subir
 * con datos móviles. 1280px de lado largo alcanza para validar visualmente
 * el estado de una carga sin generar archivos pesados.
 *
 * FIX-9: objetivo ≤ 500 KB por foto (spec), con escalones de calidad.
 *
 * A3 (tanda 6): la cámara escribe el sensor en horizontal y la orientación
 * real viaja en el tag EXIF — al recomprimir sin leerlo, las fotos verticales
 * quedaban acostadas. Ahora la rotación EXIF se aplica al bitmap ANTES de
 * comprimir (y el preview usa el mismo helper). Se usa android.media.
 * ExifInterface por InputStream (API 24+, minSdk 26): cero dependencias nuevas.
 *
 * TODO el trabajo de acá es pesado: los llamadores DEBEN estar en
 * Dispatchers.IO (AttachmentRepository ya lo garantiza; el preview usa
 * produceState + IO).
 */
object ImageCompressor {
    private const val MAX_DIMENSION = 1280
    const val TARGET_MAX_BYTES = 500 * 1024L  // objetivo del spec: ≤ 500 KB
    private val QUALITY_STEPS = intArrayOf(72, 60, 50, 40)

    /** Lee la orientación EXIF del original. ORIENTATION_NORMAL si no hay tag. */
    fun readExifOrientation(context: Context, uri: Uri): Int =
        runCatching {
            context.contentResolver.openInputStream(uri)?.use { input ->
                ExifInterface(input).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL
                )
            }
        }.getOrNull() ?: ExifInterface.ORIENTATION_NORMAL

    /**
     * Aplica la transformación EXIF al bitmap (rotaciones y espejados).
     * Devuelve el mismo bitmap si la orientación ya es normal.
     */
    fun applyExifOrientation(bitmap: Bitmap, orientation: Int): Bitmap {
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> { matrix.postRotate(90f); matrix.postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_TRANSVERSE -> { matrix.postRotate(270f); matrix.postScale(-1f, 1f) }
            else -> return bitmap
        }
        val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        if (rotated != bitmap) bitmap.recycle()
        return rotated
    }

    /**
     * Decodifica el original downsampleado a ~targetMaxDim y CON la rotación
     * EXIF aplicada. Lo usa el preview de la pantalla de fotos (en IO).
     */
    fun decodeOriented(context: Context, uri: Uri, targetMaxDim: Int): Bitmap? =
        runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, bounds)
            }
            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= targetMaxDim) sample *= 2
            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            val decoded = context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, opts)
            } ?: return null
            applyExifOrientation(decoded, readExifOrientation(context, uri))
        }.getOrNull()

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
            if (scaled != original) original.recycle()

            // A3: rotar DESPUÉS de escalar (misma imagen final, menos memoria:
            // rotar 8 MP para después tirar 3/4 de los píxeles era al revés).
            val oriented = applyExifOrientation(scaled, readExifOrientation(context, sourceUri))

            // Bajar la calidad en escalones hasta cumplir el objetivo de 500 KB.
            var done = false
            for (quality in QUALITY_STEPS) {
                FileOutputStream(targetFile).use { out ->
                    oriented.compress(Bitmap.CompressFormat.JPEG, quality, out)
                }
                if (targetFile.length() <= TARGET_MAX_BYTES) { done = true; break }
            }
            if (!done && targetFile.length() == 0L) return false

            oriented.recycle()
            true
        } catch (_: Exception) {
            false
        }
    }
}
