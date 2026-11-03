package et.alpha.meret.core.data.photo

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.media.ExifInterface
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import et.alpha.meret.core.common.dispatcher.DispatcherProvider
import et.alpha.meret.core.common.result.AppError
import et.alpha.meret.core.common.result.AppResult
import et.alpha.meret.core.common.time.TimeProvider
import et.alpha.meret.core.common.uuid.UuidV7Generator
import et.alpha.meret.domain.waypoint.StoredPhoto
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.security.MessageDigest

/**
 * Real JPEG encoding and decoding (Robolectric's native graphics), on files the size a phone
 * camera writes.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class JpegPhotoStoreTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var store: JpegPhotoStore
    private lateinit var photos: File

    @Before
    fun setUp() {
        photos = File(context.filesDir, JpegPhotoStore.DIRECTORY)
        photos.deleteRecursively()
        store =
            JpegPhotoStore(
                context = context,
                ids = UuidV7Generator(FixedTime),
                time = FixedTime,
                dispatchers = TestDispatchers(Dispatchers.IO),
            )
    }

    @Test
    fun `a 12 MP landscape capture is stored at 1600 by 1200 with its GPS and orientation`() =
        runBlocking<Unit> {
            val capture = capture(4000, 3000, latitude = 9.0301, longitude = 38.7402)

            val photo = stored(store.store(capture.path))

            assertThat(photo.width).isEqualTo(1600)
            assertThat(photo.height).isEqualTo(1200)
            val file = File(photo.path)
            assertThat(file.parentFile).isEqualTo(photos)
            assertThat(file.name).isEqualTo("${photo.id}.jpg")
            val decoded = bounds(file)
            assertThat(decoded.outWidth to decoded.outHeight).isEqualTo(1600 to 1200)
            assertThat(decoded.outMimeType).isEqualTo("image/jpeg")
            assertThat(photo.latitude!!).isWithin(1e-5).of(9.0301)
            assertThat(photo.longitude!!).isWithin(1e-5).of(38.7402)
            val exif = ExifInterface(file.path)
            assertThat(exif.getAttribute(ExifInterface.TAG_GPS_LATITUDE_REF)).isEqualTo("N")
            assertThat(exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, 0))
                .isEqualTo(ExifInterface.ORIENTATION_ROTATE_90)
            assertThat(exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL))
                .isEqualTo("2026:11:03 09:30:00")
        }

    @Test
    fun `the phone's make and model are not kept`() = runBlocking<Unit> {
        val capture = capture(2000, 1500)

        val photo = stored(store.store(capture.path))

        val exif = ExifInterface(photo.path)
        assertThat(exif.getAttribute(ExifInterface.TAG_MAKE)).isNull()
        assertThat(exif.getAttribute(ExifInterface.TAG_MODEL)).isNull()
    }

    @Test
    fun `a portrait capture with an odd ratio is scaled exactly`() = runBlocking<Unit> {
        val photo = stored(store.store(capture(3000, 2000).path))

        assertThat(photo.width to photo.height).isEqualTo(1600 to 1067)
        assertThat(bounds(File(photo.path)).outWidth).isEqualTo(1600)

        val portrait = stored(store.store(capture(1200, 2400).path))
        assertThat(portrait.width to portrait.height).isEqualTo(800 to 1600)
    }

    @Test
    fun `a small photo is not enlarged`() = runBlocking<Unit> {
        val photo = stored(store.store(capture(800, 600).path))

        assertThat(photo.width to photo.height).isEqualTo(800 to 600)
    }

    @Test
    fun `size and checksum describe the stored file, and the capture is gone`() =
        runBlocking<Unit> {
            val capture = capture(2000, 1500)

            val photo = stored(store.store(capture.path))

            val file = File(photo.path)
            assertThat(photo.bytes).isEqualTo(file.length())
            assertThat(
                photo.sha256,
            ).isEqualTo(hex(MessageDigest.getInstance("SHA-256").digest(file.readBytes())))
            assertThat(photo.sha256).matches("[0-9a-f]{64}")
            assertThat(photo.capturedAt).isEqualTo(FixedTime.nowMillis())
            assertThat(capture.exists()).isFalse()
            assertThat(photos.listFiles()!!.map { it.name }).containsExactly(file.name)
        }

    @Test
    fun `a capture without GPS is stored without a position`() = runBlocking<Unit> {
        val photo = stored(store.store(capture(2000, 1500, latitude = null).path))

        assertThat(photo.latitude).isNull()
        assertThat(photo.longitude).isNull()
    }

    @Test
    fun `a file that is not an image stores nothing and keeps the file`() = runBlocking<Unit> {
        val notAnImage = File(context.cacheDir, "notes.jpg").apply { writeText("not a JPEG") }

        val result = store.store(notAnImage.path)

        assertThat(result)
            .isEqualTo(AppResult.Failure(AppError.Storage(AppError.Storage.Kind.Corrupt)))
        assertThat(notAnImage.exists()).isTrue()
        assertThat(photos.listFiles().orEmpty()).isEmpty()
    }

    @Test
    fun `a capture that is not there is not found`() = runBlocking<Unit> {
        val result = store.store(File(context.cacheDir, "missing.jpg").path)

        assertThat(result)
            .isEqualTo(AppResult.Failure(AppError.Storage(AppError.Storage.Kind.NotFound)))
    }

    @Test
    fun `sha256 matches a known vector`() {
        val file = File(context.cacheDir, "abc.txt").apply { writeText("abc") }

        assertThat(sha256(file))
            .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad")
    }

    /** A camera-like JPEG in the cache, with the EXIF a phone camera writes. */
    private fun capture(
        width: Int,
        height: Int,
        latitude: Double? = 9.0301,
        longitude: Double = 38.7402,
    ): File {
        val file = File.createTempFile("capture", ".jpg", context.cacheDir)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.rgb(120, 160, 90))
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 95, it) }
        bitmap.recycle()
        val exif = ExifInterface(file.path)
        if (latitude != null) {
            exif.setAttribute(ExifInterface.TAG_GPS_LATITUDE, dms(latitude))
            exif.setAttribute(ExifInterface.TAG_GPS_LATITUDE_REF, if (latitude >= 0) "N" else "S")
            exif.setAttribute(ExifInterface.TAG_GPS_LONGITUDE, dms(longitude))
            exif.setAttribute(ExifInterface.TAG_GPS_LONGITUDE_REF, if (longitude >= 0) "E" else "W")
        }
        exif.setAttribute(
            ExifInterface.TAG_ORIENTATION,
            ExifInterface.ORIENTATION_ROTATE_90.toString(),
        )
        exif.setAttribute(ExifInterface.TAG_DATETIME_ORIGINAL, "2026:11:03 09:30:00")
        exif.setAttribute(ExifInterface.TAG_MAKE, "TECNO")
        exif.setAttribute(ExifInterface.TAG_MODEL, "KI5k")
        exif.saveAttributes()
        return file
    }

    /** Degrees as EXIF writes them: three rationals, seconds to 1/10000. */
    private fun dms(degrees: Double): String {
        val total = Math.abs(degrees)
        val whole = total.toInt()
        val minutes = ((total - whole) * 60).toInt()
        val seconds = Math.round(((total - whole) * 60 - minutes) * 60 * 10_000)
        return "$whole/1,$minutes/1,$seconds/10000"
    }

    private fun stored(result: AppResult<StoredPhoto>): StoredPhoto {
        check(result is AppResult.Success) { "not stored: $result" }
        return result.value
    }

    private fun bounds(file: File) = BitmapFactory.Options().apply {
        inJustDecodeBounds = true
        BitmapFactory.decodeFile(file.path, this)
    }

    private fun hex(bytes: ByteArray) = bytes.joinToString("") { "%02x".format(it) }

    private object FixedTime : TimeProvider {
        override fun nowMillis(): Long = 1_762_160_000_000L

        override fun elapsedRealtimeNanos(): Long = 0L
    }

    private class TestDispatchers(
        dispatcher: CoroutineDispatcher,
    ) : DispatcherProvider {
        override val default: CoroutineDispatcher = dispatcher
        override val io: CoroutineDispatcher = dispatcher
        override val main: CoroutineDispatcher = dispatcher
        override val mainImmediate: CoroutineDispatcher = dispatcher
    }
}
