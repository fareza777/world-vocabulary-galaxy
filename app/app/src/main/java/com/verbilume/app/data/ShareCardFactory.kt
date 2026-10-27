package com.verbilume.app.data

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.net.Uri
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.core.content.FileProvider
import androidx.core.content.res.ResourcesCompat
import com.verbilume.app.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.min
import kotlin.random.Random

class ShareCardFactory(
    private val context: Context,
    private val content: ContentRepository,
) {
    suspend fun create(detail: WordDetail): Uri = withContext(Dispatchers.IO) {
        val bitmap = render(detail)
        val dir = File(context.cacheDir, "share").apply { mkdirs() }
        val safeName = detail.id.replace(Regex("[^a-z0-9-]"), "-")
        val file = File(dir, "verbilume-$safeName.png")
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }

    fun shareIntent(uri: Uri, detail: WordDetail): Intent {
        val definition = detail.senses.firstOrNull()?.definition.orEmpty()
        val text = "${detail.word} — $definition\n\nA word from Verbilume. English, illuminated."
        return Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TEXT, text)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    private fun render(detail: WordDetail): Bitmap {
        val width = 1080
        val height = 1350
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paper = Color.rgb(245, 237, 222)
        val ink = Color.rgb(18, 32, 43)
        val softInk = Color.rgb(79, 92, 98)
        val terracotta = Color.rgb(181, 83, 54)
        val brass = Color.rgb(173, 124, 43)
        val cream = Color.rgb(251, 246, 236)

        val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f, 0f, width.toFloat(), height.toFloat(),
                intArrayOf(Color.rgb(248, 241, 229), Color.rgb(238, 225, 204)),
                floatArrayOf(0f, 1f), Shader.TileMode.CLAMP,
            )
        }
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bg)

        val random = Random(ContentRepository.fnv1a(detail.id))
        val starPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = brass }
        repeat(32) {
            val x = random.nextFloat() * width
            val y = random.nextFloat() * height
            val radius = 1.4f + random.nextFloat() * 3.2f
            starPaint.alpha = 25 + random.nextInt(60)
            canvas.drawCircle(x, y, radius, starPaint)
        }

        val margin = 72f
        val imageRect = RectF(margin, margin, width - margin, 690f)
        val clip = Path().apply { addRoundRect(imageRect, 34f, 34f, Path.Direction.CW) }
        val image = loadImage(detail.image)
        if (image != null) {
            canvas.save()
            canvas.clipPath(clip)
            val dest = RectF(imageRect)
            val scale = maxOf(dest.width() / image.width, dest.height() / image.height)
            val cropW = (dest.width() / scale).toInt().coerceAtMost(image.width)
            val cropH = (dest.height() / scale).toInt().coerceAtMost(image.height)
            val src = Rect(
                (image.width - cropW) / 2,
                (image.height - cropH) / 2,
                (image.width + cropW) / 2,
                (image.height + cropH) / 2,
            )
            val imagePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
            canvas.drawBitmap(image, src, dest, imagePaint)
            canvas.restore()
            image.recycle()
        } else {
            canvas.drawRoundRect(imageRect, 34f, 34f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(236, 225, 205) })
        }
        canvas.drawRoundRect(imageRect, 34f, 34f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 2f
            color = Color.argb(40, 18, 32, 43)
        })

        val fraunces = ResourcesCompat.getFont(context, R.font.fraunces)
        val manrope = ResourcesCompat.getFont(context, R.font.manrope)
        val topic = content.meta.topics.getOrNull(detail.summary.topic)?.label ?: "Atlas"
        val level = CEFR_LEVELS[detail.summary.cefr.coerceIn(0, 5)]
        val pillText = "$level  ·  $topic"
        val pillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = terracotta
            textSize = 27f
            typeface = Typeface.create(manrope, Typeface.BOLD)
            letterSpacing = 0.04f
        }
        val pillWidth = pillPaint.measureText(pillText) + 56f
        val pillRect = RectF(margin, 734f, margin + pillWidth, 796f)
        canvas.drawRoundRect(pillRect, 31f, 31f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(22, 181, 83, 54) })
        canvas.drawText(pillText, margin + 28f, 778f, pillPaint)

        val wordPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ink
            typeface = Typeface.create(fraunces, Typeface.BOLD)
            textSize = when {
                detail.word.length <= 9 -> 108f
                detail.word.length <= 14 -> 86f
                else -> 68f
            }
        }
        canvas.drawText(detail.word, margin, 914f, wordPaint)

        val ipa = detail.summary.ipaUs ?: detail.summary.ipaUk
        if (!ipa.isNullOrBlank()) {
            val ipaPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = softInk
                textSize = 34f
                typeface = Typeface.create(manrope, Typeface.NORMAL)
            }
            canvas.drawText(ipa, margin, 966f, ipaPaint)
        }

        val definition = detail.senses.firstOrNull()?.definition.orEmpty()
        val bodyPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ink
            textSize = 38f
            typeface = Typeface.create(manrope, Typeface.NORMAL)
        }
        val layout = StaticLayout.Builder.obtain(definition, 0, definition.length, bodyPaint, 936)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setLineSpacing(8f, 1f)
            .setMaxLines(4)
            .setEllipsize(android.text.TextUtils.TruncateAt.END)
            .build()
        canvas.save()
        canvas.translate(margin, 1010f)
        layout.draw(canvas)
        canvas.restore()

        val footerY = 1242f
        canvas.drawLine(margin, footerY - 46f, width - margin, footerY - 46f, Paint().apply {
            color = Color.argb(42, 18, 32, 43)
            strokeWidth = 2f
        })
        val logoPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ink
            textSize = 38f
            typeface = Typeface.create(fraunces, Typeface.BOLD)
        }
        canvas.drawText("Verbilume", margin, footerY + 18f, logoPaint)
        val taglinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = softInk
            textSize = 25f
            typeface = Typeface.create(manrope, Typeface.NORMAL)
        }
        canvas.drawText("Every word, a little light.", margin, footerY + 58f, taglinePaint)
        val starPaint2 = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = terracotta }
        val cx = width - margin - 18f
        val cy = footerY - 4f
        drawStar(canvas, cx, cy, 20f, starPaint2)

        val attributionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(130, 79, 92, 98)
            textSize = 19f
            typeface = Typeface.create(manrope, Typeface.NORMAL)
            textAlign = Paint.Align.RIGHT
        }
        canvas.drawText("Vocabulary & art: Word Atlas contributors · CC BY 4.0", width - margin, footerY + 58f, attributionPaint)
        return bitmap
    }

    private fun drawStar(canvas: Canvas, cx: Float, cy: Float, radius: Float, paint: Paint) {
        val path = Path()
        val points = 8
        for (i in 0 until points * 2) {
            val r = if (i % 2 == 0) radius else radius * 0.28f
            val angle = Math.PI * i / points - Math.PI / 2
            val x = cx + (r * kotlin.math.cos(angle)).toFloat()
            val y = cy + (r * kotlin.math.sin(angle)).toFloat()
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        canvas.drawPath(path, paint)
    }

    private fun loadImage(path: String): Bitmap? = try {
        context.assets.open(path).use { BitmapFactory.decodeStream(it) }
    } catch (e: Exception) {
        null
    }
}
