package com.example.docscanner

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View

/**
 * Custom view vẽ lưới 3x3 lên màn hình camera.
 * Bật/tắt bằng visibility từ ScannerActivity dựa theo SharedPreferences.
 */
class GridOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val paint = Paint().apply {
        color = 0x55FFFFFF   // trắng 1/3 trong suốt
        strokeWidth = 1.2f
        style = Paint.Style.STROKE
        isAntiAlias = true
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()

        // 2 đường dọc chia 3 cột
        canvas.drawLine(w / 3f, 0f, w / 3f, h, paint)
        canvas.drawLine(w * 2f / 3f, 0f, w * 2f / 3f, h, paint)

        // 2 đường ngang chia 3 hàng
        canvas.drawLine(0f, h / 3f, w, h / 3f, paint)
        canvas.drawLine(0f, h * 2f / 3f, w, h * 2f / 3f, paint)
    }
}
