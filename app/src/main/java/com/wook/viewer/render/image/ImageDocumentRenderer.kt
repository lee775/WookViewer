package com.wook.viewer.render.image

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import com.wook.viewer.domain.error.DocumentError
import com.wook.viewer.domain.model.DocumentFormat
import com.wook.viewer.domain.model.PageSize
import com.wook.viewer.domain.model.RenderedPage
import com.wook.viewer.domain.repository.DocumentHandle
import com.wook.viewer.domain.repository.DocumentRenderer
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 이미지 렌더러 (jpg/png/gif/webp/bmp/heic/heif).
 *
 * - 1 이미지 = 1 페이지
 * - EXIF 회전 자동 적용 (스마트폰 카메라 사진 정상 표시)
 * - 거대 이미지는 BitmapFactory.inSampleSize 로 다운샘플링
 * - HEIC/HEIF는 Android 9.0(API 28) 이상에서 자동 지원
 */
@Singleton
class ImageDocumentRenderer @Inject constructor(
    @ApplicationContext private val context: Context
) : DocumentRenderer {

    override val supportedFormats: Set<DocumentFormat> = setOf(DocumentFormat.IMAGE)

    private class Handle(
        override val uri: Uri,
        val width: Int,
        val height: Int,
        val rotationDegrees: Int
    ) : DocumentHandle

    override suspend fun open(uri: Uri): DocumentHandle = withContext(Dispatchers.IO) {
        // bounds 만 디코드 (이미지 자체는 메모리 안 올림)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                BitmapFactory.decodeStream(input, null, bounds)
            }
        } catch (t: Throwable) {
            Timber.e(t, "이미지 bounds 조회 실패")
            throw DocumentError.IoError(t)
        }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            throw DocumentError.Corrupted()
        }

        val rotation = readExifRotation(uri)
        Timber.d("이미지 열기: ${bounds.outWidth}x${bounds.outHeight}, rotation=$rotation")
        Handle(uri, bounds.outWidth, bounds.outHeight, rotation)
    }

    override suspend fun pageCount(handle: DocumentHandle): Int = 1

    override suspend fun pageSize(handle: DocumentHandle, index: Int): PageSize {
        val h = handle as Handle
        // 회전 90/270 시 width/height 스왑
        return if (h.rotationDegrees == 90 || h.rotationDegrees == 270) {
            PageSize(h.height.toFloat(), h.width.toFloat())
        } else {
            PageSize(h.width.toFloat(), h.height.toFloat())
        }
    }

    override suspend fun renderPage(
        handle: DocumentHandle,
        index: Int,
        targetWidthPx: Int
    ): RenderedPage = withContext(Dispatchers.IO) {
        val h = handle as Handle
        if (index != 0) throw DocumentError.Corrupted()

        val targetW = targetWidthPx.coerceAtLeast(1)
        val sampleSize = computeSampleSize(h.width, h.height, targetW)

        val opts = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }

        val raw = try {
            context.contentResolver.openInputStream(h.uri)?.use { input ->
                BitmapFactory.decodeStream(input, null, opts)
            } ?: throw DocumentError.IoError()
        } catch (t: Throwable) {
            if (t is DocumentError) throw t
            throw DocumentError.IoError(t)
        } ?: throw DocumentError.Corrupted()

        val rotated = applyRotation(raw, h.rotationDegrees)

        RenderedPage(
            index = 0,
            widthPx = rotated.width,
            heightPx = rotated.height,
            bitmap = rotated
        )
    }

    override suspend fun close(handle: DocumentHandle) {
        // 리소스 없음 — bitmap은 GC가 정리
    }

    private fun readExifRotation(uri: Uri): Int = runCatching {
        context.contentResolver.openInputStream(uri)?.use { input ->
            val exif = ExifInterface(input)
            when (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90
                ExifInterface.ORIENTATION_ROTATE_180 -> 180
                ExifInterface.ORIENTATION_ROTATE_270 -> 270
                else -> 0
            }
        } ?: 0
    }.getOrDefault(0)

    private fun computeSampleSize(width: Int, height: Int, targetWidth: Int): Int {
        // 메모리 보호: 타겟 너비의 2배 이상이면 절반으로 다운샘플
        var sample = 1
        var w = width
        while (w / 2 >= targetWidth * 2) {
            sample *= 2
            w /= 2
        }
        return sample
    }

    private fun applyRotation(bitmap: Bitmap, degrees: Int): Bitmap {
        if (degrees == 0) return bitmap
        val matrix = Matrix().apply { postRotate(degrees.toFloat()) }
        val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        if (rotated !== bitmap) bitmap.recycle()
        return rotated
    }
}
