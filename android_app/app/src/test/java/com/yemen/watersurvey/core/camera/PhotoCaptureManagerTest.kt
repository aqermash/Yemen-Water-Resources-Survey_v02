package com.yemen.watersurvey.core.camera

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream

/**
 * Phase 10A — Unit tests for [PhotoCaptureManager].
 *
 * Tests cover:
 * 1. Compression output size gate (≤ 500 KB hard limit)
 * 2. Resolution cap at 1920 × 1080
 * 3. Portrait orientation cap at 1080 × 1920
 * 4. SHA-256 determinism (same input → same hash)
 * 5. SHA-256 uniqueness (different inputs → different hashes)
 * 6. Output filename format (`ATT_<uuid>.jpg`)
 *
 * All tests run under Robolectric to access [android.graphics.Bitmap] and
 * [android.content.Context] without a physical device.
 */
@RunWith(RobolectricTestRunner::class)
class PhotoCaptureManagerTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var context: Context
    private lateinit var manager: PhotoCaptureManager

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        manager = PhotoCaptureManager(context)
    }

    // -------------------------------------------------------------------------
    // 1. Compression gate — output must not exceed 500 KB
    // -------------------------------------------------------------------------

    @Test
    fun testCompressionProducesFileUnder500KB() {
        // A large, detail-rich bitmap (2000×2000) should compress below 500 KB.
        val bitmap = createDetailedBitmap(2000, 2000)
        val jpegBytes = bitmapToJpegBytes(bitmap, 100)

        val result = manager.processInMemory(jpegBytes, "test-uuid-001")

        assertTrue(
            "Expected file size ≤ 500 KB but got ${result.fileSizeBytes} bytes",
            result.fileSizeBytes <= PhotoCaptureManager.HARD_MAX_BYTES
        )
        bitmap.recycle()
    }

    // -------------------------------------------------------------------------
    // 2. Compression smoke — output should be at least a small size
    // -------------------------------------------------------------------------

    @Test
    fun testCompressionProducesNonEmptyOutput() {
        val bitmap = createDetailedBitmap(800, 600)
        val jpegBytes = bitmapToJpegBytes(bitmap, 95)

        val result = manager.processInMemory(jpegBytes, "test-uuid-002")

        assertTrue(
            "Expected non-empty compressed output, got ${result.fileSizeBytes} bytes",
            result.fileSizeBytes > 0L
        )
        bitmap.recycle()
    }

    // -------------------------------------------------------------------------
    // 3. Resolution cap — landscape input wider than 1920px must be scaled down
    // -------------------------------------------------------------------------

    @Test
    fun testResolutionCapAt1920x1080_Landscape() {
        // Input: 3000×2000 (landscape, exceeds cap)
        val bitmap = createSolidBitmap(3000, 2000, Color.BLUE)
        val scaled = manager.downscaleIfNeeded(bitmap)

        assertTrue(
            "Width ${scaled.width} should be ≤ ${PhotoCaptureManager.MAX_WIDTH}",
            scaled.width <= PhotoCaptureManager.MAX_WIDTH
        )
        assertTrue(
            "Height ${scaled.height} should be ≤ ${PhotoCaptureManager.MAX_HEIGHT}",
            scaled.height <= PhotoCaptureManager.MAX_HEIGHT
        )
        if (bitmap !== scaled) bitmap.recycle()
        scaled.recycle()
    }

    @Test
    fun testResolutionCapAt1080x1920_Portrait() {
        // Input: 2000×3000 (portrait, exceeds cap)
        val bitmap = createSolidBitmap(2000, 3000, Color.GREEN)
        val scaled = manager.downscaleIfNeeded(bitmap)

        // For portrait, caps are swapped: width ≤ 1080, height ≤ 1920
        assertTrue(
            "Portrait width ${scaled.width} should be ≤ ${PhotoCaptureManager.MAX_HEIGHT}",
            scaled.width <= PhotoCaptureManager.MAX_HEIGHT
        )
        assertTrue(
            "Portrait height ${scaled.height} should be ≤ ${PhotoCaptureManager.MAX_WIDTH}",
            scaled.height <= PhotoCaptureManager.MAX_WIDTH
        )
        if (bitmap !== scaled) bitmap.recycle()
        scaled.recycle()
    }

    @Test
    fun testSmallBitmapIsNotUpscaled() {
        // Input: 640×480 — already within bounds, must NOT be upscaled
        val bitmap = createSolidBitmap(640, 480, Color.RED)
        val scaled = manager.downscaleIfNeeded(bitmap)

        assertEquals("Width should remain 640", 640, scaled.width)
        assertEquals("Height should remain 480", 480, scaled.height)
        // Returned the same bitmap instance (no allocation)
        assertSame("Should return original bitmap when within bounds", bitmap, scaled)
        bitmap.recycle()
    }

    // -------------------------------------------------------------------------
    // 4. SHA-256 — same input always produces the same digest
    // -------------------------------------------------------------------------

    @Test
    fun testSha256IsConsistentForSameInput() {
        val bytes = ByteArray(1024) { it.toByte() }
        val hash1 = manager.computeSha256(bytes)
        val hash2 = manager.computeSha256(bytes)
        assertEquals("SHA-256 should be deterministic for the same input", hash1, hash2)
    }

    // -------------------------------------------------------------------------
    // 5. SHA-256 — different inputs produce different digests
    // -------------------------------------------------------------------------

    @Test
    fun testSha256DiffersForDifferentInputs() {
        val bytes1 = ByteArray(1024) { 0xAA.toByte() }
        val bytes2 = ByteArray(1024) { 0xBB.toByte() }
        val hash1 = manager.computeSha256(bytes1)
        val hash2 = manager.computeSha256(bytes2)
        assertNotEquals("SHA-256 should differ for different inputs", hash1, hash2)
    }

    // -------------------------------------------------------------------------
    // 6. Output filename format — must match ATT_<uuid>.jpg
    // -------------------------------------------------------------------------

    @Test
    fun testFileNameFormatIsCorrect() {
        val bitmap = createSolidBitmap(640, 480, Color.CYAN)
        val jpegBytes = bitmapToJpegBytes(bitmap, 80)

        val result = manager.processInMemory(jpegBytes, "test-uuid-006")

        assertTrue(
            "fileName '${result.fileName}' should start with 'ATT_' and end with '.jpg'",
            result.fileName.startsWith("ATT_") && result.fileName.endsWith(".jpg")
        )
        // The UUID portion must not be blank
        val uuidPart = result.fileName.removePrefix("ATT_").removeSuffix(".jpg")
        assertTrue(
            "UUID portion of filename should not be blank",
            uuidPart.isNotBlank()
        )
        bitmap.recycle()
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Creates a solid-colour Bitmap. Used for size/resolution assertions.
     */
    private fun createSolidBitmap(width: Int, height: Int, color: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(color)
        return bitmap
    }

    /**
     * Creates a Bitmap filled with a pseudo-random pixel pattern to simulate real photo content
     * (solid bitmaps can over-compress due to RLE, giving unrealistically small outputs).
     */
    private fun createDetailedBitmap(width: Int, height: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(width * height) { index ->
            // Pseudo-random colour based on index — simulates textured real-world image content
            val r = (index * 37) and 0xFF
            val g = (index * 73) and 0xFF
            val b = (index * 113) and 0xFF
            android.graphics.Color.rgb(r, g, b)
        }
        bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
        return bitmap
    }

    /**
     * Encodes a [Bitmap] to a JPEG byte array at the given quality.
     */
    private fun bitmapToJpegBytes(bitmap: Bitmap, quality: Int): ByteArray {
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, quality, stream)
        return stream.toByteArray()
    }
}
