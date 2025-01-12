package com.yaros.RadioUrl.helpers

import android.content.Context
import android.graphics.*
import android.net.Uri
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.core.net.toUri
import androidx.palette.graphics.Palette
import com.yaros.RadioUrl.R
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream


object ImageHelper {

    fun getDensityScalingFactor(context: Context): Float {
        return context.resources.displayMetrics.density
    }


    fun getScaledStationImage(context: Context, imageUri: Uri, imageSize: Int): Bitmap {
        val size: Int = (imageSize * getDensityScalingFactor(context)).toInt()
        return decodeSampledBitmapFromUri(context, imageUri, size, size)
    }


    fun getStationImage(context: Context, imageUriString: String): Bitmap {
        var bitmap: Bitmap? = null

        if (imageUriString.isNotEmpty()) {
            try {
                bitmap = BitmapFactory.decodeFile(imageUriString.toUri().path)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        if (bitmap == null) {
            bitmap = ContextCompat.getDrawable(context, R.drawable.ic_default_station_image_72dp)!!
                .toBitmap()
        }

        return bitmap
    }


    fun getStationImageAsByteArray(context: Context, imageUriString: String = String()): ByteArray {
        val coverBitmap: Bitmap = getStationImage(context, imageUriString)
        val stream = ByteArrayOutputStream()
        coverBitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
        val coverByteArray: ByteArray = stream.toByteArray()
        coverBitmap.recycle()
        return coverByteArray
    }


    fun createSquareImage(
        context: Context,
        bitmap: Bitmap,
        backgroundColor: Int,
        size: Int,
        adaptivePadding: Boolean
    ): Bitmap {

        val background = Paint()
        background.style = Paint.Style.FILL
        if (backgroundColor != -1) {
            background.color = backgroundColor
        } else {
            background.color = ContextCompat.getColor(context, R.color.default_neutral_dark)
        }

        val outputImage: Bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val imageCanvas = Canvas(outputImage)

        val right = size.toFloat()
        val bottom = size.toFloat()
        imageCanvas.drawRect(0f, 0f, right, bottom, background)

        val paint = Paint()
        paint.isFilterBitmap = true
        imageCanvas.drawBitmap(
            bitmap,
            createTransformationMatrix(
                size,
                bitmap.height.toFloat(),
                bitmap.width.toFloat(),
                adaptivePadding
            ),
            paint
        )
        return outputImage
    }


    fun getMainColor(context: Context, imageUri: Uri): Int {
        val palette: Palette =
            Palette.from(decodeSampledBitmapFromUri(context, imageUri, 72, 72)).generate()
        val vibrantSwatch = palette.vibrantSwatch
        val mutedSwatch = palette.mutedSwatch

        when {
            vibrantSwatch != null -> {
                val rgb = vibrantSwatch.rgb
                return Color.argb(255, Color.red(rgb), Color.green(rgb), Color.blue(rgb))
            }
            mutedSwatch != null -> {
                val rgb = mutedSwatch.rgb
                return Color.argb(255, Color.red(rgb), Color.green(rgb), Color.blue(rgb))
            }
            else -> {
                return context.resources.getColor(R.color.default_neutral_medium_light, null)
            }
        }
    }


    private fun decodeSampledBitmapFromUri(
        context: Context,
        imageUri: Uri,
        reqWidth: Int,
        reqHeight: Int
    ): Bitmap {
        var bitmap: Bitmap? = null
        if (imageUri.toString().isNotEmpty()) {
            try {
                var stream: InputStream? = context.contentResolver.openInputStream(imageUri)
                val options = BitmapFactory.Options()
                options.inJustDecodeBounds = true
                BitmapFactory.decodeStream(stream, null, options)
                stream?.close()

                options.inSampleSize = calculateSampleParameter(options, reqWidth, reqHeight)

                stream = context.contentResolver.openInputStream(imageUri)
                options.inJustDecodeBounds = false
                bitmap = BitmapFactory.decodeStream(stream, null, options)
                stream?.close()
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }

        if (bitmap == null) {
            bitmap = ContextCompat.getDrawable(context, R.drawable.ic_default_station_image_72dp)!!
                .toBitmap()
        }

        return bitmap
    }


    private fun calculateSampleParameter(
        options: BitmapFactory.Options,
        reqWidth: Int,
        reqHeight: Int
    ): Int {
        val height = options.outHeight
        val width = options.outWidth
        var inSampleSize = 1

        if (height > reqHeight || width > reqWidth) {

            val halfHeight = height / 2
            val halfWidth = width / 2

            while (halfHeight / inSampleSize > reqHeight && halfWidth / inSampleSize > reqWidth) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }


    private fun createTransformationMatrix(
        size: Int,
        inputImageHeight: Float,
        inputImageWidth: Float,
        scaled: Boolean
    ): Matrix {
        val matrix = Matrix()

        var padding = 0f
        if (scaled) {
            padding = size.toFloat() / 4f
        }

        val aspectRatio: Float
        val xTranslation: Float
        val yTranslation: Float

        if (inputImageWidth >= inputImageHeight) {
            aspectRatio = (size - padding * 2) / inputImageWidth
            xTranslation = 0.0f + padding
            yTranslation = (size - inputImageHeight * aspectRatio) / 2.0f
        } else {
            aspectRatio = (size - padding * 2) / inputImageHeight
            yTranslation = 0.0f + padding
            xTranslation = (size - inputImageWidth * aspectRatio) / 2.0f
        }

        matrix.postTranslate(xTranslation, yTranslation)
        matrix.preScale(aspectRatio, aspectRatio)
        return matrix
    }

}
