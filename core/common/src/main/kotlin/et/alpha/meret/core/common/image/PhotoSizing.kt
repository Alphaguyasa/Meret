package et.alpha.meret.core.common.image

/** Pixel dimensions of an image. */
public data class PixelSize(
    public val width: Int,
    public val height: Int,
) {
    init {
        require(width > 0 && height > 0) { "An image has at least one pixel each way" }
    }

    public val longSide: Int get() = maxOf(width, height)
}

/**
 * How a waypoint photo is shrunk before it is stored: BUILD_PLAN Phase 8's "1600 px q80".
 *
 * A 12-megapixel capture is about 48 MB once decoded, more than a low-RAM phone gives the
 * app. So the decoder first skips pixels by a power of two ([sampleSize]), which costs a
 * fraction of that, and only the result is scaled to the exact size ([targetSize]).
 */
public object PhotoSizing {
    /** The long side of a stored photo, in pixels. */
    public const val MAX_LONG_SIDE: Int = 1600

    /** JPEG quality of a stored photo. */
    public const val JPEG_QUALITY: Int = 80

    /**
     * The stored size of a [source] image: the long side brought down to [maxLongSide], the
     * aspect ratio kept, and never enlarged.
     */
    public fun targetSize(
        source: PixelSize,
        maxLongSide: Int = MAX_LONG_SIDE,
    ): PixelSize {
        require(maxLongSide > 0) { "maxLongSide must be positive" }
        if (source.longSide <= maxLongSide) return source
        val scale = maxLongSide.toDouble() / source.longSide
        return if (source.width >= source.height) {
            PixelSize(maxLongSide, scaled(source.height, scale))
        } else {
            PixelSize(scaled(source.width, scale), maxLongSide)
        }
    }

    /**
     * The largest power of two to skip pixels by while decoding [source] such that the decoded
     * image is still no smaller than [target] each way, so the final scale only ever shrinks.
     */
    public fun sampleSize(
        source: PixelSize,
        target: PixelSize,
    ): Int {
        var sample = 1
        while (source.width / (sample * 2) >= target.width &&
            source.height / (sample * 2) >= target.height
        ) {
            sample *= 2
        }
        return sample
    }

    private fun scaled(
        side: Int,
        scale: Double,
    ): Int = Math.round(side * scale).toInt().coerceAtLeast(1)
}
