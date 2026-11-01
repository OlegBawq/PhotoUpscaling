package com.matuncnn.app.processor

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import java.io.File
import java.io.FileOutputStream

object NaturalBlendProcessor {
    private const val MAX_OUTPUT_PIXELS = 20_000_000L

    fun recommendedScale(inputPath: String): Int {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(inputPath, options)
        require(options.outWidth > 0 && options.outHeight > 0) { "Cannot read image size" }
        val pixels = options.outWidth.toLong() * options.outHeight
        return when {
            pixels * 16 <= MAX_OUTPUT_PIXELS -> 4
            pixels * 4 <= MAX_OUTPUT_PIXELS -> 2
            else -> 1
        }
    }

    fun prepareAiInput(inputPath: String, outputPath: String, targetScale: Int): Result<Unit> =
        runCatching {
            val source = BitmapFactory.decodeFile(inputPath)
                ?: error("Cannot decode the source image")
            val width = (source.width * targetScale / 4).coerceAtLeast(1)
            val height = (source.height * targetScale / 4).coerceAtLeast(1)
            val prepared = if (width == source.width && height == source.height) source else
                Bitmap.createScaledBitmap(source, width, height, true)
            FileOutputStream(outputPath).use { stream ->
                check(prepared.compress(Bitmap.CompressFormat.PNG, 100, stream)) {
                    "Cannot prepare image for AI"
                }
            }
            if (prepared !== source) prepared.recycle()
            source.recycle()
        }

    fun render(
        inputPath: String,
        aiPath: String?,
        outputPath: String,
        aiStrength: Int,
        scale: Int = 4
    ): Result<Unit> = runCatching {
        val source = BitmapFactory.decodeFile(inputPath)
            ?: error("Cannot decode the source image")
        val targetWidth = source.width * scale
        val targetHeight = source.height * scale
        require(targetWidth.toLong() * targetHeight <= MAX_OUTPUT_PIXELS) {
            "The result is larger than 20 megapixels"
        }

        val baseline = Bitmap.createScaledBitmap(source, targetWidth, targetHeight, true)
        val result = if (aiStrength <= 0 || aiPath == null) {
            baseline
        } else {
            val aiSource = BitmapFactory.decodeFile(aiPath)
                ?: error("Cannot decode the AI result")
            val ai = if (aiSource.width == targetWidth && aiSource.height == targetHeight) {
                aiSource
            } else {
                Bitmap.createScaledBitmap(aiSource, targetWidth, targetHeight, true)
            }
            val mixed = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(mixed)
            canvas.drawBitmap(baseline, 0f, 0f, null)
            canvas.drawBitmap(ai, 0f, 0f, Paint(Paint.FILTER_BITMAP_FLAG).apply {
                alpha = (aiStrength.coerceIn(0, 100) * 255 / 100)
            })
            if (ai !== aiSource) ai.recycle()
            aiSource.recycle()
            mixed
        }

        File(outputPath).parentFile?.mkdirs()
        FileOutputStream(outputPath).use { stream ->
            check(result.compress(Bitmap.CompressFormat.PNG, 100, stream)) {
                "Cannot save the processed image"
            }
        }

        if (result !== baseline) result.recycle()
        baseline.recycle()
        source.recycle()
    }
}
