package com.salticusteam.vifi.core.data.files

/**
 * The sizing and quality rules for turning a picked image into an uploadable JPEG (iOS SubmissionFileEncoder).
 *
 * The Storage rules accept only `image/jpeg` (and `application/pdf`) up to 15 MiB, and a picked image may be
 * HEIC, PNG or a very large JPEG. The longest side is brought down to [MAX_PIXEL_SIZE] and the quality stepped
 * down through [QUALITY_STEPS] until the encoded JPEG fits. Pure Kotlin, so every computation is unit-tested;
 * the Bitmap work lives in [ContentResolverSubmissionFileSource].
 */
object SubmissionJpegEncoder {

    /** Longest-side pixel limit a page is downsampled to (keeps camera photos well under the cap, still readable). */
    const val MAX_PIXEL_SIZE = 3000

    /** JPEG qualities tried in turn until the encoded size fits (iOS 0.8, 0.6, 0.45, 0.3). */
    val QUALITY_STEPS = listOf(80, 60, 45, 30)

    /**
     * The largest power-of-two `inSampleSize` that still decodes the longest side at or above [maxPixelSize],
     * so the full-resolution bitmap is never held in memory; the final step down is an exact scale.
     */
    fun sampleSize(width: Int, height: Int, maxPixelSize: Int = MAX_PIXEL_SIZE): Int {
        val longest = maxOf(width, height)
        var sample = 1
        while (longest / (sample * 2) >= maxPixelSize) sample *= 2
        return sample
    }

    /** The size to scale a decoded [width] x [height] bitmap to, so its longest side is at most [maxPixelSize]. */
    fun targetSize(width: Int, height: Int, maxPixelSize: Int = MAX_PIXEL_SIZE): Pair<Int, Int> {
        val longest = maxOf(width, height)
        if (longest <= maxPixelSize) return width to height
        val scale = maxPixelSize.toDouble() / longest
        val scaledWidth = Math.round(width * scale).toInt().coerceAtLeast(1)
        val scaledHeight = Math.round(height * scale).toInt().coerceAtLeast(1)
        return scaledWidth to scaledHeight
    }

    /** The first encoding (at each of [qualities] in turn) no larger than [maxBytes], or `null` when none fits. */
    fun firstFitting(maxBytes: Int, qualities: List<Int> = QUALITY_STEPS, encode: (quality: Int) -> ByteArray?): ByteArray? {
        for (quality in qualities) {
            val encoded = encode(quality) ?: continue
            if (encoded.size <= maxBytes) return encoded
        }
        return null
    }

    /**
     * The rotation (clockwise degrees) and horizontal mirroring that bring an image stored with the EXIF
     * [orientation] tag (1..8) upright; the identity for an absent or unknown value.
     */
    fun orientationTransform(orientation: Int): OrientationTransform = when (orientation) {
        2 -> OrientationTransform(rotationDegrees = 0, mirrored = true) // FLIP_HORIZONTAL
        3 -> OrientationTransform(rotationDegrees = 180, mirrored = false) // ROTATE_180
        4 -> OrientationTransform(rotationDegrees = 180, mirrored = true) // FLIP_VERTICAL
        5 -> OrientationTransform(rotationDegrees = 270, mirrored = true) // TRANSPOSE
        6 -> OrientationTransform(rotationDegrees = 90, mirrored = false) // ROTATE_90
        7 -> OrientationTransform(rotationDegrees = 90, mirrored = true) // TRANSVERSE
        8 -> OrientationTransform(rotationDegrees = 270, mirrored = false) // ROTATE_270
        else -> OrientationTransform.IDENTITY
    }

    /** How to bring a decoded bitmap upright: mirror horizontally first (when [mirrored]), then rotate. */
    data class OrientationTransform(val rotationDegrees: Int, val mirrored: Boolean) {
        val isIdentity: Boolean get() = rotationDegrees == 0 && !mirrored

        companion object {
            val IDENTITY = OrientationTransform(rotationDegrees = 0, mirrored = false)
        }
    }
}
