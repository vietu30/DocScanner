package com.example.docscanner

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.viewpager2.widget.ViewPager2
import java.io.File
import java.io.FileOutputStream

class PreviewActivity : AppCompatActivity() {

    private lateinit var imageList: ArrayList<String>
    private lateinit var viewPager: ViewPager2
    private lateinit var adapter: ImagePagerAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_preview)

        // ===== GET DATA =====
        imageList = intent.getStringArrayListExtra("image_list") ?: arrayListOf()

        // ===== VIEWPAGER =====
        viewPager = findViewById(R.id.viewPager)
        adapter = ImagePagerAdapter(imageList)
        viewPager.adapter = adapter

        // ===== DELETE =====
        findViewById<View>(R.id.btnDelete).setOnClickListener {
            if (imageList.isEmpty()) return@setOnClickListener
            val position = viewPager.currentItem
            File(imageList[position]).delete()
            imageList.removeAt(position)
            adapter.notifyDataSetChanged()
            if (imageList.isEmpty()) finish()
        }

        // ===== SAVE PDF =====
        findViewById<View>(R.id.btnSavePdf).setOnClickListener {
            if (imageList.isEmpty()) {
                Toast.makeText(this, "Không có ảnh", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            showSaveDialog()
        }
    }

    // ===== DIALOG CHỌN FORMAT =====
    private fun showSaveDialog() {
        val editText = EditText(this).apply {
            hint = "Nhập tên file (không cần đuôi)"
        }

        AlertDialog.Builder(this)
            .setTitle("Lưu tài liệu")
            .setView(editText)
            .setPositiveButton("Lưu PDF") { _, _ ->
                val name = editText.text.toString().trim().ifEmpty {
                    "scan_${System.currentTimeMillis()}"
                }
                savePdf(name)
            }
            .setNeutralButton("Lưu ảnh") { _, _ ->
                val name = editText.text.toString().trim().ifEmpty {
                    "scan_${System.currentTimeMillis()}"
                }
                saveImages(name)
            }
            .setNegativeButton("Huỷ", null)
            .show()
    }

    // ===== LƯU PDF =====
    private fun savePdf(name: String) {
        val pdfFile = File(getExternalFilesDir(null), "$name.pdf")
        createPdf(this, imageList, pdfFile.absolutePath)
        Toast.makeText(this, "Đã lưu PDF: ${pdfFile.name}", Toast.LENGTH_LONG).show()
        goHome()
    }

    // ===== LƯU ẢNH =====
    private fun saveImages(baseName: String) {
        val dir = getExternalFilesDir(null) ?: return
        imageList.forEachIndexed { index, path ->
            val suffix = if (imageList.size > 1) "_${index + 1}" else ""
            val destFile = File(dir, "${baseName}${suffix}.jpg")
            try {
                val bmp: Bitmap = BitmapFactory.decodeFile(path)
                FileOutputStream(destFile).use { out ->
                    bmp.compress(Bitmap.CompressFormat.JPEG, 90, out)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        val count = imageList.size
        Toast.makeText(this, "Đã lưu $count ảnh vào thư mục", Toast.LENGTH_LONG).show()
        goHome()
    }

    // ===== VỀ MAIN =====
    private fun goHome() {
        val intent = Intent(this, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        startActivity(intent)
        finish()
    }
}