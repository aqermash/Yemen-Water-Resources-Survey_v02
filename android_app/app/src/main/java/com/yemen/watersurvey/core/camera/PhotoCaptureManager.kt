package com.yemen.watersurvey.core.camera

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.*

/**
 * Phase 10A — CameraX Core Pipeline.
 *
 * Handles all raw file I/O, Bitmap downsampling, JPEG compression, and SHA-256 checksum
 * computation for captured survey photos.
 *
 * Architecture boundary:
 * - This class has NO awareness of Room, DAOs, or ViewModels.
 * - The output of [processAndSavePhoto] is a [PhotoSaveResult] value object.
 * - [AttachmentRepository] is the only caller.
 *
 * Storage target:
 *   <context.filesDir>/attachments/ATT_<uuid>.jpg
 *
 * The [PhotoSaveResult.relativePath] (`attachments/ATT_<uuid>.jpg`) is stored in
 * [SurveyAttachmentEntity.localFilePath] so that [SurveySyncExporter.resolveAttachmentFile]
 * can resolve it as `context.filesDir/<relativePath>`.
 *
 * Compression spec:
 * - Max resolution: 1920 × 1080 (landscape), 1080 × 1920 (portrait). Aspect ratio preserved.
 * - JPEG quality: 80 (primary) → 65 (retry 1) → 50 (retry 2) if output > 500 KB.
 * - Hard limit: 500 KB.
 */
class PhotoCaptureManager(private val context: Context) {

    companion object {
        /** Landscape cap */
        const val MAX_WIDTH = 1920
        /** Portrait / shorter-axis cap */
        const val MAX_HEIGHT = 1080
        /** JPEG quality — primary attempt */
        const val JPEG_QUALITY_PRIMARY = 80
        /** JPEG quality — first retry if file exceeds hard limit */
        const val JPEG_QUALITY_RETRY_1 = 65
        /** JPEG quality — second retry if file still exceeds hard limit */
        const val JPEG_QUALITY_RETRY_2 = 50
        /** Hard maximum file size in bytes (500 KB) */
        const val HARD_MAX_BYTES = 500 * 1024L
        /** Subdirectory name relative to context.filesDir */
        const val ATTACHMENTS_SUBDIR = "attachments"
    }

    /**
     * Reads a raw JPEG file (e.g. from CameraX ImageCapture output), applies EXIF rotation
     * correction, downsamples to max 1920×1080, compresses to JPEG, writes to internal storage,
     * and returns a [PhotoSaveResult].
     *
     * @param sourceFile Temporary source file produced by CameraX ImageCapture.
     * @param surveyUUID UUID of the survey this photo belongs to.
     * @return [PhotoSaveResult] on success, throws on unrecoverable error.
     */
    fun processAndSavePhoto(sourceFile: File, surveyUUID: String): PhotoSaveResult {
        // 1. Decode source Bitmap with EXIF rotation correction
        val decoded = decodeBitmapWithRotation(sourceFile)

        // 2. Downsample to max resolution
        val scaled = downscaleIfNeeded(decoded)

        // 3. Compress with quality fallback
        val (compressedBytes, finalQuality) = compressWithFallback(scaled)

        // 4. Write to internal storage
        val attachmentId = UUID.randomUUID().toString()
        val fileName = "ATT_$attachmentId.jpg"
        val attachmentsDir = File(context.filesDir, ATTACHMENTS_SUBDIR)
        if (!attachmentsDir.exists()) {
            attachmentsDir.mkdirs()
        }
        val outputFile = File(attachmentsDir, fileName)
        FileOutputStream(outputFile).use { it.write(compressedBytes) }

        // 5. Compute SHA-256 of the written file
        val sha256 = computeSha256(compressedBytes)

        // 6. Capture timestamp (ISO-8601)
        val capturedAt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())

        // 7. Recycle intermediate bitmaps
        if (decoded !== scaled) decoded.recycle()
        scaled.recycle()

        return PhotoSaveResult(
            attachmentId = attachmentId,
            fileName = fileName,
            relativePath = "$ATTACHMENTS_SUBDIR/$fileName",
            absolutePath = outputFile.absolutePath,
            fileSizeBytes = compressedBytes.size.toLong(),
            fileSha256 = sha256,
            capturedAt = capturedAt,
            surveyUUID = surveyUUID,
            finalJpegQuality = finalQuality,
            outputWidth = scaled.width,
            outputHeight = scaled.height
        )
    }

    /**
     * Processes a raw JPEG byte array (useful for testing without the filesystem).
     * Downsamples, compresses, computes SHA-256, and returns the result WITHOUT writing to disk.
     *
     * This overload is intentionally kept for unit testing and for the compression gate tests.
     *
     * @param jpegBytes Raw JPEG bytes.
     * @param surveyUUID UUID of the survey this photo belongs to.
     * @return [PhotoSaveResult] with `absolutePath` and `relativePath` empty strings.
     */
    fun processInMemory(jpegBytes: ByteArray, surveyUUID: String): PhotoSaveResult {
        val decoded = BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.size)
            ?: throw IllegalArgumentException("Could not decode provided JPEG bytes into a Bitmap.")

        val scaled = downscaleIfNeeded(decoded)
        val (compressedBytes, finalQuality) = compressWithFallback(scaled)
        val sha256 = computeSha256(compressedBytes)
        val capturedAt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())

        val attachmentId = UUID.randomUUID().toString()
        val fileName = "ATT_$attachmentId.jpg"

        if (decoded !== scaled) decoded.recycle()
        scaled.recycle()

        return PhotoSaveResult(
            attachmentId = attachmentId,
            fileName = fileName,
            relativePath = "",
            absolutePath = "",
            fileSizeBytes = compressedBytes.size.toLong(),
            fileSha256 = sha256,
            capturedAt = capturedAt,
            surveyUUID = surveyUUID,
            finalJpegQuality = finalQuality,
            outputWidth = scaled.width,
            outputHeight = scaled.height
        )
    }

    /**
     * Computes the SHA-256 hex digest of the given byte array.
     * Exposed as `internal` so the unit-test companion helper can call it directly.
     */
    internal fun computeSha256(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(bytes)
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /**
     * Decodes the JPEG file into a Bitmap, applying any EXIF rotation so the result is
     * always correctly oriented regardless of camera hardware orientation.
     */
    private fun decodeBitmapWithRotation(file: File): Bitmap {
        val raw = BitmapFactory.decodeFile(file.absolutePath)
            ?: throw IllegalStateException("BitmapFactory failed to decode: ${file.name}")

        val exif = try {
            ExifInterface(file.absolutePath)
        } catch (_: Exception) {
            return raw
        }

        val orientation = exif.getAttributeInt(
            ExifInterface.TAG_ORIENTATION,
            ExifInterface.ORIENTATION_NORMAL
        )

        val degrees = when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }

        if (degrees == 0f) return raw

        val matrix = Matrix().apply { postRotate(degrees) }
        val rotated = Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, matrix, true)
        raw.recycle()
        return rotated
    }

    /**
     * Scales [bitmap] so that neither dimension exceeds [MAX_WIDTH] × [MAX_HEIGHT],
     * preserving aspect ratio. Portrait images (height > width) are handled by swapping
     * the cap axes.
     *
     * Returns the original bitmap unchanged if it is already within bounds.
     */
    internal fun downscaleIfNeeded(bitmap: Bitmap): Bitmap {
        val srcW = bitmap.width
        val srcH = bitmap.height

        // Determine cap based on orientation
        val capW: Int
        val capH: Int
        if (srcW >= srcH) {
            // Landscape or square
            capW = MAX_WIDTH
            capH = MAX_HEIGHT
        } else {
            // Portrait
            capW = MAX_HEIGHT
            capH = MAX_WIDTH
        }

        if (srcW <= capW && srcH <= capH) return bitmap

        val scaleW = capW.toFloat() / srcW
        val scaleH = capH.toFloat() / srcH
        val scale = minOf(scaleW, scaleH)

        val newW = (srcW * scale).toInt().coerceAtLeast(1)
        val newH = (srcH * scale).toInt().coerceAtLeast(1)

        return Bitmap.createScaledBitmap(bitmap, newW, newH, true)
    }

    /**
     * Compresses [bitmap] to JPEG. Retries with lower quality if output exceeds [HARD_MAX_BYTES].
     * Returns the compressed byte array and the final quality value used.
     */
    private fun compressWithFallback(bitmap: Bitmap): Pair<ByteArray, Int> {
        val qualities = listOf(JPEG_QUALITY_PRIMARY, JPEG_QUALITY_RETRY_1, JPEG_QUALITY_RETRY_2)
        for (quality in qualities) {
            val bytes = compressToJpeg(bitmap, quality)
            if (bytes.size <= HARD_MAX_BYTES || quality == qualities.last()) {
                return Pair(bytes, quality)
            }
        }
        // Unreachable, but satisfies Kotlin exhaustion
        return Pair(compressToJpeg(bitmap, JPEG_QUALITY_RETRY_2), JPEG_QUALITY_RETRY_2)
    }

    private fun compressToJpeg(bitmap: Bitmap, quality: Int): ByteArray {
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, quality, stream)
        return stream.toByteArray()
    }
}
