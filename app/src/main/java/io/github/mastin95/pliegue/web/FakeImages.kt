package io.github.mastin95.pliegue.web

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.BitmapDrawable
import coil.Coil
import coil.ImageLoader
import coil.decode.DataSource
import coil.intercept.Interceptor
import coil.request.ImageResult
import coil.request.SuccessResult

/**
 * Solo para las pruebas en el Android virtual (red simulada): las imágenes de X (pbs.twimg.com) no se
 * descargan; cada una es un cuadro de color con su nombre, para que el feed y el visor se vean.
 */
object FakeImages {
    fun install(context: Context) {
        val app = context.applicationContext
        Coil.setImageLoader(
            ImageLoader.Builder(app).components {
                add(object : Interceptor {
                    override suspend fun intercept(chain: Interceptor.Chain): ImageResult {
                        val data = chain.request.data.toString()
                        if (!data.startsWith("https://pbs.twimg.com/")) return chain.proceed(chain.request)
                        return SuccessResult(BitmapDrawable(app.resources, draw(data)), chain.request, DataSource.MEMORY)
                    }
                })
            }.build(),
        )
    }

    private fun draw(url: String): Bitmap {
        val name = url.substringAfterLast('/').substringBefore('?')
        val bmp = Bitmap.createBitmap(640, 400, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(Color.HSVToColor(floatArrayOf(((name.hashCode() and 0x7fffffff) % 360).toFloat(), 0.45f, 0.55f)))
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 44f
            textAlign = Paint.Align.CENTER
        }
        c.drawText(name, 320f, 216f, p)
        return bmp
    }
}
