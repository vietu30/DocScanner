package com.example.docscanner

import android.net.Uri
import com.itextpdf.kernel.pdf.PdfWriter
import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.layout.Document
import com.itextpdf.layout.element.Image
import com.itextpdf.io.image.ImageDataFactory
import android.content.Context

fun createPdf(context: Context, imagePaths: List<String>, outputPath: String) {

    val writer = PdfWriter(outputPath)
    val pdfDoc = PdfDocument(writer)
    val document = Document(pdfDoc)

    for ((index, path) in imagePaths.withIndex()) {

        val imageData = if (path.startsWith("content://")) {
            val inputStream = context.contentResolver.openInputStream(Uri.parse(path))
            ImageDataFactory.create(inputStream!!.readBytes())
        } else {
            ImageDataFactory.create(path)
        }

        val image = Image(imageData)
        image.setAutoScale(true)

        document.add(image)

        if (index != imagePaths.lastIndex) {
            pdfDoc.addNewPage()
        }
    }

    document.close()
}
