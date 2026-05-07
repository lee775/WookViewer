package com.wook.viewer.render.text

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Typeface
import android.os.Build
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import com.wook.viewer.domain.model.RenderedPage

/**
 * TextPage 1개를 A4 비율 Bitmap으로 그린다.
 * 시스템 기본 Typeface 사용 — Android의 한글 fallback이 자동 처리.
 *
 * 모든 TEXT_ONLY 포맷(HWP/DOCX/...) 공통.
 */
internal object TextPageRenderer {

    private const val A4_RATIO = 842f / 595f

    fun render(page: TextPage, targetWidthPx: Int, index: Int): RenderedPage {
        val w = targetWidthPx.coerceAtLeast(320)
        val h = (w * A4_RATIO).toInt()

        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)

        val padding = (w * 0.06f).coerceAtLeast(16f)
        val textWidth = (w - 2 * padding).toInt().coerceAtLeast(1)

        val paint = TextPaint().apply {
            color = Color.BLACK
            textSize = (w * 0.028f).coerceAtLeast(14f)
            isAntiAlias = true
            typeface = Typeface.DEFAULT
        }

        val layout: StaticLayout = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            StaticLayout.Builder
                .obtain(page.text, 0, page.text.length, paint, textWidth)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setLineSpacing(0f, 1.25f)
                .setIncludePad(false)
                .build()
        } else {
            @Suppress("DEPRECATION")
            StaticLayout(
                page.text, paint, textWidth,
                Layout.Alignment.ALIGN_NORMAL, 1.25f, 0f, false
            )
        }

        canvas.save()
        canvas.translate(padding, padding)
        layout.draw(canvas)
        canvas.restore()

        return RenderedPage(index = index, widthPx = w, heightPx = h, bitmap = bitmap)
    }
}
