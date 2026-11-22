package com.studiomogli.smytgemini

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.io.FileOutputStream

object ThumbnailBrander {

    private const val TARGET_WIDTH = 1280
    private const val TARGET_HEIGHT = 720

    fun stampLogoOverWatermark(
        sourceThumbFile: File,
        brandLogoFile: File,
        outputFile: File
    ): Boolean {
        if (!sourceThumbFile.exists() || !brandLogoFile.exists()) return false

        val rawThumb = BitmapFactory.decodeFile(sourceThumbFile.absolutePath) ?: return false
        val rawLogo = BitmapFactory.decodeFile(brandLogoFile.absolutePath) ?: run {
            rawThumb.recycle()
            return false
        }

        val canvasBitmap = Bitmap.createBitmap(TARGET_WIDTH, TARGET_HEIGHT, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(canvasBitmap)

        val scale = maxOf(
            TARGET_WIDTH.toFloat() / rawThumb.width.toFloat(),
            TARGET_HEIGHT.toFloat() / rawThumb.height.toFloat()
        )
        val scaledW = rawThumb.width * scale
        val scaledH = rawThumb.height * scale
        val left = (TARGET_WIDTH - scaledW) / 2f
        val top = (TARGET_HEIGHT - scaledH) / 2f

        val smoothPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        canvas.drawBitmap(rawThumb, null, RectF(left, top, left + scaledW, top + scaledH), smoothPaint)

        val badgeSize = 128f
        val marginRight = 18f
        val marginBottom = 18f
        val badgeLeft = TARGET_WIDTH - badgeSize - marginRight
        val badgeTop = TARGET_HEIGHT - badgeSize - marginBottom
        val badgeRect = RectF(badgeLeft, badgeTop, badgeLeft + badgeSize, badgeTop + badgeSize)

        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(235, 15, 17, 21)
            style = Paint.Style.FILL
        }
        canvas.drawRoundRect(badgeRect, 24f, 24f, bgPaint)

        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(90, 255, 255, 255)
            style = Paint.Style.STROKE
            strokeWidth = 3f
        }
        canvas.drawRoundRect(badgeRect, 24f, 24f, borderPaint)

        val pad = 14f
        val innerSize = badgeSize - (pad * 2f)
        val logoAspect = rawLogo.width.toFloat() / rawLogo.height.toFloat().coerceAtLeast(1f)
        val drawW: Float
        val drawH: Float
        if (logoAspect >= 1f) {
            drawW = innerSize
            drawH = innerSize / logoAspect
        } else {
            drawH = innerSize
            drawW = innerSize * logoAspect
        }
        val logoLeft = badgeLeft + pad + (innerSize - drawW) / 2f
        val logoTop = badgeTop + pad + (innerSize - drawH) / 2f
        canvas.drawBitmap(rawLogo, null, RectF(logoLeft, logoTop, logoLeft + drawW, logoTop + drawH), smoothPaint)

        FileOutputStream(outputFile).use { out ->
            canvasBitmap.compress(Bitmap.CompressFormat.JPEG, 94, out)
        }

        rawThumb.recycle()
        rawLogo.recycle()
        canvasBitmap.recycle()
        return true
    }

    fun saveToPublicGallery(context: Context, imageFile: File, displayName: String): Boolean {
        if (!imageFile.exists()) return false
        return try {
            val safeName = displayName.replace(Regex("[^a-zA-Z0-9_-]"), "_").take(32)
            val fileName = "YTGemini_${safeName}_${System.currentTimeMillis() % 10000}.jpg"

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                    put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                    put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/YTGemini")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
                val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                    ?: return false
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    imageFile.inputStream().use { input -> input.copyTo(out) }
                }
                values.clear()
                values.put(MediaStore.Images.Media.IS_PENDING, 0)
                context.contentResolver.update(uri, values, null, null)
                true
            } else {
                val picDir = File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
                    "YTGemini"
                )
                if (!picDir.exists()) picDir.mkdirs()
                val dest = File(picDir, fileName)
                imageFile.copyTo(dest, overwrite = true)
                true
            }
        } catch (_: Exception) {
            false
        }
    }
}
