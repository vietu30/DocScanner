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
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.widget.ViewPager2
import com.example.docscanner.network.ApiClient
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.FileOutputStream

class PreviewActivity : AppCompatActivity() {

    private lateinit var imageList: ArrayList<String>
    private lateinit var viewPager: ViewPager2
    private lateinit var adapter: ImagePagerAdapter
    private val auth by lazy { FirebaseAuth.getInstance() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_preview)

        imageList = intent.getStringArrayListExtra("image_list") ?: arrayListOf()

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

        // ===== SAVE =====
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
        val editText = EditText(this).apply { hint = "Nhập tên file (không cần đuôi)" }
        AlertDialog.Builder(this)
            .setTitle("Lưu tài liệu")
            .setView(editText)
            .setPositiveButton("Lưu PDF") { _, _ ->
                val name = editText.text.toString().trim().ifEmpty { "scan_${System.currentTimeMillis()}" }
                savePdf(name)
            }
            .setNeutralButton("Lưu ảnh") { _, _ ->
                val name = editText.text.toString().trim().ifEmpty { "scan_${System.currentTimeMillis()}" }
                saveImages(name)
            }
            .setNegativeButton("Huỷ", null)
            .show()
    }

    // ===== LƯU PDF + UPLOAD =====
    private fun savePdf(name: String) {
        val pdfFile = File(getExternalFilesDir(null), "$name.pdf")
        createPdf(this, imageList, pdfFile.absolutePath)
        Toast.makeText(this, "Đã lưu PDF: ${pdfFile.name}", Toast.LENGTH_LONG).show()

        // Upload lên server nếu đã đăng nhập
        uploadImageToServer(pdfFile, name)
        goHome()
    }

    // ===== LƯU ẢNH + UPLOAD =====
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
                // Upload từng ảnh lên server
                uploadImageToServer(destFile, baseName)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        Toast.makeText(this, "Đã lưu ${imageList.size} ảnh", Toast.LENGTH_LONG).show()
        goHome()
    }

    // ===== UPLOAD LÊN CI3 =====
    private fun uploadImageToServer(file: File, description: String) {
        val uid = auth.currentUser?.uid ?: return // chưa login → bỏ qua

        lifecycleScope.launch {
            try {
                val mimeType = if (file.extension == "pdf") "application/pdf" else "image/jpeg"

                val userIdBody    = uid.toRequestBody("text/plain".toMediaTypeOrNull())
                val descBody      = description.toRequestBody("text/plain".toMediaTypeOrNull())
                val fileBody      = file.asRequestBody(mimeType.toMediaTypeOrNull())
                val filePart      = MultipartBody.Part.createFormData("image", file.name, fileBody)

                val response = ApiClient.api.uploadImage(userIdBody, filePart, descBody)
                if (response.isSuccessful && response.body()?.status == true) {
                    // Upload thành công — file đã có trên server
                } else {
                    Toast.makeText(this@PreviewActivity, "Upload thất bại: ${response.body()?.message}", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                // Không kết nối được server → chỉ lưu local, không báo lỗi ầm ĩ
                android.util.Log.w("Upload", "Server không khả dụng: ${e.message}")
            }
        }
    }

    // ===== VỀ MAIN =====
    private fun goHome() {
        val intent = Intent(this, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        startActivity(intent)
        finish()
    }
}