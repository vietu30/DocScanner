package com.example.docscanner

import android.content.Context
import android.net.Uri
import com.itextpdf.io.image.ImageDataFactory
import com.itextpdf.kernel.geom.PageSize
import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.PdfWriter
import com.itextpdf.layout.Document
import com.itextpdf.layout.element.AreaBreak
import com.itextpdf.layout.element.Image

fun createPdf(context: Context, imagePaths: List<String>, outputPath: String) {

    val writer = PdfWriter(outputPath)
    val pdfDoc = PdfDocument(writer)

    // Khởi tạo document KHÔNG có margins để ảnh lấp đầy trang
    val document = Document(pdfDoc)
    document.setMargins(0f, 0f, 0f, 0f)

    for ((index, path) in imagePaths.withIndex()) {

        val imageData = if (path.startsWith("content://")) {
            val inputStream = context.contentResolver.openInputStream(Uri.parse(path))
            ImageDataFactory.create(inputStream!!.readBytes())
        } else {
            ImageDataFactory.create(path)
        }

        // Tính kích thước trang theo ảnh (giữ tỉ lệ)
        val imgWidth = imageData.width
        val imgHeight = imageData.height
        val pageSize = PageSize(imgWidth, imgHeight)

        if (index == 0) {
            // Trang đầu: set kích thước cho trang đã được tạo sẵn
            pdfDoc.defaultPageSize = pageSize
            pdfDoc.addNewPage(pageSize)
        } else {
            // Trang tiếp theo: dùng AreaBreak để sang trang mới đúng cách
            document.add(AreaBreak(pageSize))
        }

        val image = Image(imageData)
        image.setWidth(imgWidth)
        image.setHeight(imgHeight)
        image.setFixedPosition(index + 1, 0f, 0f)

        document.add(image)
    }

    document.close()
}
