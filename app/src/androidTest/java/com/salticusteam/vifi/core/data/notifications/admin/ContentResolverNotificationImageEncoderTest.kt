package com.salticusteam.vifi.core.data.notifications.admin

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.salticusteam.vifi.core.model.NotificationImage
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The Bitmap side of iOS `NotificationComposerAttachmentTests.downsamples`/`unusableImage`, on device: a picked photo
 * comes out as a JPEG within 1 MiB with its longest side brought down to 1600 px.
 */
@RunWith(AndroidJUnit4::class)
class ContentResolverNotificationImageEncoderTest {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val encoder = ContentResolverNotificationImageEncoder(context)
    private val files = mutableListOf<File>()

    @After
    fun tearDown() {
        files.forEach(File::delete)
    }

    private fun file(name: String, write: (File) -> Unit): String {
        val file = File(context.cacheDir, name).also(files::add)
        write(file)
        return Uri.fromFile(file).toString()
    }

    private fun png(width: Int, height: Int): String = file("picked-$width-$height.png") { file ->
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.rgb(251, 145, 13)) }
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }

    @Test
    fun aLargePhoto_isDownsampledTo1600AndEncodedWithin1MiB() = runBlocking {
        val encoded = encoder.encode(png(2400, 1200))

        val jpeg = (encoded as EncodedNotificationImage.Jpeg).bytes
        assertEquals(0xFF.toByte(), jpeg[0])
        assertEquals(0xD8.toByte(), jpeg[1])
        assertTrue(jpeg.size <= NotificationImage.MAX_BYTES)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size, bounds)
        assertEquals(1600, bounds.outWidth)
        assertEquals(800, bounds.outHeight)
    }

    @Test
    fun aSmallPhoto_isNeverUpscaled() = runBlocking {
        val jpeg = (encoder.encode(png(400, 300)) as EncodedNotificationImage.Jpeg).bytes

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size, bounds)
        assertEquals(400, bounds.outWidth)
        assertEquals(300, bounds.outHeight)
    }

    @Test
    fun dataThatIsNotAnImage_isUnusable() = runBlocking {
        val uri = file("not-an-image.jpg") { it.writeBytes(byteArrayOf(0, 1, 2, 3)) }

        assertSame(EncodedNotificationImage.Unusable, encoder.encode(uri))
    }

    @Test
    fun aMissingFile_isUnreadable() = runBlocking {
        val uri = Uri.fromFile(File(context.cacheDir, "gone.jpg")).toString()

        assertSame(EncodedNotificationImage.Unreadable, encoder.encode(uri))
    }
}
