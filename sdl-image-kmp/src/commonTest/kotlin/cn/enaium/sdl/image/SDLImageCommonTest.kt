/*
 * Copyright (c) 2026 Enaium
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package cn.enaium.sdl.image

import cn.enaium.sdl.SDL
import cn.enaium.sdl.SDLIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SDLImageCommonTest {

    /**
     * Writes [bytes] to a file under the module's build/ directory (tests run
     * with the module directory as the working directory on every platform)
     * and returns its path.
     */
    private fun writeTempFile(name: String, bytes: ByteArray): String {
        val file = "build/sdl-image-kmp-test-$name"
        val stream = SDLIO.openFile(file, "wb")
            ?: error("SDLIO.openFile failed: ${SDL.error()}")
        try {
            stream.write(bytes)
        } finally {
            stream.close()
        }
        return file
    }

    // =========================================================================
    // Test data (generated in memory, no sample images in the repo)
    // =========================================================================

    /**
     * A 2x2 24-bit BMP (red row on top, green row at the bottom). Rows are
     * padded to SDL's 4-byte pitch alignment (8 bytes per row for a 2px row).
     */
    private fun bmpBytes(): ByteArray {
        val data = ByteArray(54 + 16)
        fun putU16(offset: Int, value: Int) {
            data[offset] = (value and 0xFF).toByte()
            data[offset + 1] = ((value shr 8) and 0xFF).toByte()
        }

        fun putU32(offset: Int, value: Int) {
            for (i in 0 until 4) {
                data[offset + i] = ((value shr (8 * i)) and 0xFF).toByte()
            }
        }

        // BITMAPFILEHEADER
        data[0] = 'B'.code.toByte()
        data[1] = 'M'.code.toByte()
        putU32(2, 70) // file size
        putU32(10, 54) // pixel data offset
        // BITMAPINFOHEADER
        putU32(14, 40)
        putU32(18, 2) // width
        putU32(22, 2) // height (bottom-up)
        putU16(26, 1) // planes
        putU16(28, 24) // bpp
        putU32(34, 16) // image size
        // pixels, bottom row first: green, green, 2 padding bytes
        data[54] = 0x00; data[55] = 0xFF.toByte(); data[56] = 0x00
        data[57] = 0x00; data[58] = 0xFF.toByte(); data[59] = 0x00
        data[60] = 0x00; data[61] = 0x00
        // top row: red, red, 2 padding bytes
        data[62] = 0x00; data[63] = 0x00; data[64] = 0xFF.toByte()
        data[65] = 0x00; data[66] = 0x00; data[67] = 0xFF.toByte()
        data[68] = 0x00; data[69] = 0x00
        return data
    }

    /** A minimal 1x1 GIF89a (white pixel, 1 frame). */
    private fun gifBytes(): ByteArray =
        byteArrayOf(
            0x47, 0x49, 0x46, 0x38, 0x39, 0x61, // GIF89a
            0x01, 0x00, 0x01, 0x00, // 1x1
            0x80.toByte(), 0x00, 0x00, // GCT present, 2 colors, bg=0, aspect=0
            0x00, 0x00, 0x00, 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), // black, white
            0x2C, 0x00, 0x00, 0x00, 0x00, 0x01, 0x00, 0x01, 0x00, 0x00, // image descriptor
            0x02, 0x02, 0x44, 0x01, 0x00, // LZW data
            0x3B, // trailer
        )

    private fun svgBytes(): ByteArray =
        """<svg xmlns="http://www.w3.org/2000/svg" width="16" height="16"><rect width="16" height="16" fill="#ff0000"/></svg>"""
            .encodeToByteArray()

    private fun xpmLines(): List<String> = listOf(
        "3 2 2 1",
        "a c #ff0000",
        "b c #00ff00",
        "aaa",
        "bbb",
    )

    // =========================================================================
    // Core
    // =========================================================================

    @Test
    fun version() {
        val version = SDLImage.version()
        assertTrue(version >= 3000000, "expected SDL_image 3.x, got $version")
    }

    // =========================================================================
    // Loading
    // =========================================================================

    @Test
    fun loadFromFile() {
        val file = writeTempFile("sample.bmp", bmpBytes())
        val surface = SDLImage.load(file)
        assertNotNull(surface, "IMG_Load failed: ${SDLImage.error()}")
        assertEquals(2, surface.width)
        assertEquals(2, surface.height)
        surface.close()
    }

    @Test
    fun loadFromStreamAndDetect() {
        val stream = writeTempFile("sample.bmp", bmpBytes()).let { SDLIO.openFile(it, "rb") }
        assertNotNull(stream, "SDLIO.openFile failed: ${SDL.error()}")
        try {
            assertTrue(SDLImage.isBMP(stream), "isBMP: ${SDLImage.error()}")
            assertFalse(SDLImage.isPNG(stream), "isPNG should be false for BMP data")
            val surface = SDLImage.loadIO(stream)
            assertNotNull(surface, "IMG_Load_IO failed: ${SDLImage.error()}")
            assertEquals(2, surface.width)
            assertEquals(2, surface.height)
            surface.close()
        } finally {
            stream.close()
        }
    }

    @Test
    fun loadTypedIO() {
        val stream = writeTempFile("sample.bmp", bmpBytes()).let { SDLIO.openFile(it, "rb") }
        assertNotNull(stream)
        try {
            val surface = SDLImage.loadTypedIO(stream, type = "BMP")
            assertNotNull(surface, "IMG_LoadTyped_IO failed: ${SDLImage.error()}")
            assertEquals(2, surface.width)
            surface.close()
        } finally {
            stream.close()
        }
    }

    @Test
    fun loadSvg() {
        val stream = writeTempFile("sample.svg", svgBytes()).let { SDLIO.openFile(it, "rb") }
        assertNotNull(stream)
        try {
            val surface = SDLImage.loadSVG(stream)
            assertNotNull(surface, "IMG_LoadSVG_IO failed: ${SDLImage.error()}")
            assertEquals(16, surface.width)
            assertEquals(16, surface.height)
            surface.close()
        } finally {
            stream.close()
        }
    }

    @Test
    fun readXpm() {
        val surface = SDLImage.readXPMFromArray(xpmLines())
        assertNotNull(surface, "IMG_ReadXPMFromArray failed: ${SDLImage.error()}")
        assertEquals(3, surface.width)
        assertEquals(2, surface.height)
        surface.close()

        val rgb888 = SDLImage.readXPMFromArrayToRGB888(xpmLines())
        assertNotNull(rgb888, "IMG_ReadXPMFromArrayToRGB888 failed: ${SDLImage.error()}")
        assertEquals(3, rgb888.width)
        rgb888.close()
    }

    // =========================================================================
    // Saving
    // =========================================================================

    @Test
    fun saveAndReload() {
        val file = "build/sdl-image-kmp-test-save.png"
        val stream = writeTempFile("sample.bmp", bmpBytes()).let { SDLIO.openFile(it, "rb") }
        assertNotNull(stream)
        val surface = try {
            SDLImage.loadIO(stream)
        } finally {
            stream.close()
        }
        assertNotNull(surface)

        assertTrue(SDLImage.savePNG(surface, file), "IMG_SavePNG failed: ${SDLImage.error()}")
        surface.close()

        val reloaded = SDLImage.load(file)
        assertNotNull(reloaded, "reload failed: ${SDLImage.error()}")
        assertEquals(2, reloaded.width)
        assertEquals(2, reloaded.height)
        reloaded.close()
    }

    // =========================================================================
    // Vendored formats (WebP/TIFF are statically linked into the klib)
    // =========================================================================

    @Test
    fun webpRoundTrip() {
        val file = "build/sdl-image-kmp-test.webp"
        val surface = loadSampleSurface()
        assertTrue(
            SDLImage.saveWEBP(surface, file, 90f),
            "IMG_SaveWEBP failed: ${SDLImage.error()}",
        )
        surface.close()

        val stream = SDLIO.openFile(file, "rb")
        assertNotNull(stream)
        try {
            assertTrue(SDLImage.isWEBP(stream), "isWEBP: ${SDLImage.error()}")
            val reloaded = SDLImage.loadWEBP(stream)
            assertNotNull(reloaded, "IMG_LoadWEBP_IO failed: ${SDLImage.error()}")
            assertEquals(2, reloaded.width)
            assertEquals(2, reloaded.height)
            reloaded.close()
        } finally {
            stream.close()
        }
    }

    /** A minimal 1x1 8-bit grayscale TIFF (little-endian, uncompressed). */
    private fun tiffBytes(): ByteArray {
        val data = ByteArray(122 + 1)
        fun u16(offset: Int, value: Int) {
            data[offset] = (value and 0xFF).toByte()
            data[offset + 1] = ((value shr 8) and 0xFF).toByte()
        }

        fun u32(offset: Int, value: Int) {
            for (i in 0 until 4) {
                data[offset + i] = ((value shr (8 * i)) and 0xFF).toByte()
            }
        }
        data[0] = 'I'.code.toByte()
        data[1] = 'I'.code.toByte()
        u16(2, 42)
        u32(4, 8) // first IFD at offset 8
        // IFD: 9 entries (256,257,258,259,262,273,277,278,279) + next-IFD = 114 bytes
        u16(8, 9)
        fun entry(index: Int, tag: Int, type: Int, count: Int, value: Int) {
            val base = 10 + index * 12
            u16(base, tag)
            u16(base + 2, type)
            u32(base + 4, count)
            u32(base + 8, value)
        }
        entry(0, 256, 4, 1, 1) // ImageWidth
        entry(1, 257, 4, 1, 1) // ImageLength
        entry(2, 258, 3, 1, 8) // BitsPerSample
        entry(3, 259, 3, 1, 1) // Compression = none
        entry(4, 262, 3, 1, 1) // PhotometricInterpretation = BlackIsZero
        entry(5, 273, 4, 1, 122) // StripOffsets
        entry(6, 277, 3, 1, 1) // SamplesPerPixel
        entry(7, 278, 4, 1, 1) // RowsPerStrip
        entry(8, 279, 4, 1, 1) // StripByteCounts
        u32(8 + 2 + 9 * 12, 0) // next IFD
        data[122] = 0x00 // black pixel
        return data
    }

    @Test
    fun loadTiff() {
        val stream = writeTempFile("sample.tif", tiffBytes()).let { SDLIO.openFile(it, "rb") }
        assertNotNull(stream)
        try {
            assertTrue(SDLImage.isTIF(stream), "isTIF: ${SDLImage.error()}")
            val surface = SDLImage.loadTIF(stream)
            assertNotNull(surface, "IMG_LoadTIF_IO failed: ${SDLImage.error()}")
            assertEquals(1, surface.width)
            assertEquals(1, surface.height)
            surface.close()
        } finally {
            stream.close()
        }
    }

    /** Loads the sample BMP and returns its surface (caller closes it). */
    private fun loadSampleSurface(): cn.enaium.sdl.SDLSurface {
        val stream = writeTempFile("sample.bmp", bmpBytes()).let { SDLIO.openFile(it, "rb") }
        assertNotNull(stream)
        return try {
            SDLImage.loadIO(stream)
                ?: error("IMG_Load_IO failed: ${SDLImage.error()}")
        } finally {
            stream.close()
        }
    }

    // =========================================================================
    // Animations
    // =========================================================================

    @Test
    fun loadGifAnimation() {
        val stream = writeTempFile("sample.gif", gifBytes()).let { SDLIO.openFile(it, "rb") }
        assertNotNull(stream)
        try {
            val animation = SDLImage.loadAnimationIO(stream)
            assertNotNull(animation, "IMG_LoadAnimation_IO failed: ${SDLImage.error()}")
            assertEquals(1, animation.width)
            assertEquals(1, animation.height)
            assertEquals(1, animation.count)
            assertEquals(1, animation.frames.size)
            assertEquals(animation.count, animation.delays.size)
            animation.close()
        } finally {
            stream.close()
        }
    }

    @Test
    fun streamDecoder() {
        val stream = writeTempFile("sample.gif", gifBytes()).let { SDLIO.openFile(it, "rb") }
        assertNotNull(stream)
        try {
            val decoder = SDLImage.createAnimationDecoderIO(stream, type = "GIF")
            assertNotNull(decoder, "IMG_CreateAnimationDecoder_IO failed: ${SDLImage.error()}")
            val frame = decoder.getFrame()
            assertNotNull(frame, "IMG_GetAnimationDecoderFrame failed: ${SDLImage.error()}")
            assertEquals(1, frame.surface.width)
            frame.surface.close()
            decoder.close()
        } finally {
            stream.close()
        }
    }
}
