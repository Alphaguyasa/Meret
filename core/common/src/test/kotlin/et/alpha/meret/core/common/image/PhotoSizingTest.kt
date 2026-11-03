package et.alpha.meret.core.common.image

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PhotoSizingTest {
    @Test
    fun `a 12 megapixel landscape capture is stored at 1600 by 1200`() {
        assertThat(PhotoSizing.targetSize(PixelSize(4000, 3000))).isEqualTo(PixelSize(1600, 1200))
    }

    @Test
    fun `a portrait capture keeps its long side vertical`() {
        assertThat(PhotoSizing.targetSize(PixelSize(3000, 4000))).isEqualTo(PixelSize(1200, 1600))
    }

    @Test
    fun `odd ratios round to the nearest pixel`() {
        // 4208 x 3120 (a common 13 MP sensor): 3120 * 1600 / 4208 = 1186.3
        assertThat(PhotoSizing.targetSize(PixelSize(4208, 3120))).isEqualTo(PixelSize(1600, 1186))
    }

    @Test
    fun `a small image is never enlarged`() {
        assertThat(PhotoSizing.targetSize(PixelSize(800, 600))).isEqualTo(PixelSize(800, 600))
        assertThat(PhotoSizing.targetSize(PixelSize(1600, 900))).isEqualTo(PixelSize(1600, 900))
    }

    @Test
    fun `a very thin image keeps at least one pixel`() {
        assertThat(PhotoSizing.targetSize(PixelSize(10_000, 1))).isEqualTo(PixelSize(1600, 1))
    }

    @Test
    fun `the decoder skips as many pixels as it can without going below the target`() {
        val source = PixelSize(4000, 3000)
        val target = PhotoSizing.targetSize(source)

        // 4000 / 2 = 2000 >= 1600, but 4000 / 4 = 1000 < 1600.
        assertThat(PhotoSizing.sampleSize(source, target)).isEqualTo(2)
        assertThat(PhotoSizing.sampleSize(PixelSize(8000, 6000), target)).isEqualTo(4)
        assertThat(PhotoSizing.sampleSize(PixelSize(1600, 1200), target)).isEqualTo(1)
    }

    @Test
    fun `an image that is not stored smaller is decoded whole`() {
        val small = PixelSize(800, 600)

        assertThat(PhotoSizing.sampleSize(small, PhotoSizing.targetSize(small))).isEqualTo(1)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `an empty image is refused`() {
        PixelSize(0, 10)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `a zero long side is refused`() {
        PhotoSizing.targetSize(PixelSize(10, 10), maxLongSide = 0)
    }

    @Test
    fun `the stored long side and quality are the plan's`() {
        assertThat(PhotoSizing.MAX_LONG_SIDE).isEqualTo(1600)
        assertThat(PhotoSizing.JPEG_QUALITY).isEqualTo(80)
        assertThat(PixelSize(3, 7).longSide).isEqualTo(7)
    }
}
