package com.example.docscanner

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.docscanner.network.ApiClient
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch
import java.io.File

class MainActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private val auth by lazy { FirebaseAuth.getInstance() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val toolbar = findViewById<Toolbar>(R.id.topBar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(false)

        recyclerView = findViewById(R.id.scanList)
        recyclerView.layoutManager = LinearLayoutManager(this)

        val scanButton = findViewById<FloatingActionButton>(R.id.scanButton)
        scanButton.setOnClickListener {
            startActivity(Intent(this, ScannerActivity::class.java))
        }

        val bottomNav = findViewById<com.google.android.material.bottomnavigation.BottomNavigationView>(R.id.bottomNav)
        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> true
                R.id.nav_settings -> {
                    startActivity(Intent(this, SettingsActivity::class.java))
                    true
                }
                else -> false
            }
        }
    }

    override fun onResume() {
        super.onResume()
        val uid = auth.currentUser?.uid
        if (uid != null) {
            loadFromServer(uid)
        } else {
            // Chưa đăng nhập → danh sách trống
            recyclerView.adapter = ScanAdapter(mutableListOf()) {}
        }
    }

    // ===== LOAD TỪ SERVER =====
    private fun loadFromServer(userId: String) {
        lifecycleScope.launch {
            try {
                val response = ApiClient.api.getImages(userId)
                if (response.isSuccessful && response.body()?.status == true) {
                    val items = response.body()!!.data.map { img ->
                        val date = java.text.SimpleDateFormat("dd/MM/yyyy HH:mm", java.util.Locale.getDefault())
                            .format(java.util.Date(img.uploadDate))
                        val fileName = img.imageUrl.substringAfterLast("/")
                        val fixedUrl = "${BuildConfig.BASE_URL}uploads/$fileName"
                        ScanItem(
                            fileName = img.description?.ifEmpty { "Ảnh scan" } ?: "Ảnh scan",
                            fileInfo = date,
                            file = null,
                            imageUrl = fixedUrl,
                            serverId = img.id
                        )
                    }
                    val mutableItems = items.toMutableList()
                    val adapter = ScanAdapter(mutableItems) { item ->
                        item.imageUrl?.let { url -> downloadAndOpen(url) }
                    }
                    // Long-press → hiện menu xoá
                    recyclerView.addOnItemTouchListener(object : androidx.recyclerview.widget.RecyclerView.SimpleOnItemTouchListener() {})
                    adapter.setOnLongClickListener { item, position ->
                        if (item.serverId != null) {
                            androidx.appcompat.app.AlertDialog.Builder(this@MainActivity)
                                .setTitle("Xoá tài liệu")
                                .setMessage("Bạn có chắc muốn xoá \"${item.fileName}\"?")
                                .setPositiveButton("Xoá") { _, _ ->
                                    deleteServerItem(item.serverId, mutableItems, adapter, position)
                                }
                                .setNegativeButton("Huỷ", null)
                                .show()
                        }
                    }
                    recyclerView.adapter = adapter
                } else {
                    // Server trả về lỗi / không có data → danh sách trống
                    // KHÔNG dùng local fallback vì file local không phân biệt account
                    recyclerView.adapter = ScanAdapter(mutableListOf()) {}
                    Toast.makeText(this@MainActivity, "Chưa có tài liệu nào", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                // Không kết nối được server → hiện danh sách rỗng, báo lỗi
                // KHÔNG dùng local fallback vì file local không phân biệt account
                recyclerView.adapter = ScanAdapter(mutableListOf()) {}
                Toast.makeText(this@MainActivity, "Không kết nối được server", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // ===== LOAD LOCAL PDF =====
    private fun loadPdfList() {
        val folder = getExternalFilesDir(null)
        val files = folder?.listFiles()
            ?.filter { it.extension == "pdf" }
            ?.sortedByDescending { it.lastModified() }
            ?: listOf()

        val items = files.map {
            ScanItem(
                fileName = it.name,
                fileInfo = "${it.length() / 1024} KB",
                file = it,
                imageUrl = null
            )
        }.toMutableList()

        val adapter = ScanAdapter(items) { item ->
            if (item.file != null) openPdf(item.file)
        }
        recyclerView.adapter = adapter
    }

    // ===== MỞ PDF =====
    private fun openPdf(file: File) {
        val uri: Uri = FileProvider.getUriForFile(this, "${packageName}.provider", file)
        val intent = Intent(Intent.ACTION_VIEW)
        intent.setDataAndType(uri, "application/pdf")
        intent.flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
        startActivity(intent)
    }

    // ===== XOÁ FILE TRÊN SERVER =====
    private fun deleteServerItem(id: Int, items: MutableList<ScanItem>, adapter: ScanAdapter, position: Int) {
        val userId = auth.currentUser?.uid ?: return
        lifecycleScope.launch {
            try {
                val response = ApiClient.api.deleteImage(userId, id)
                if (response.isSuccessful && response.body()?.status == true) {
                    adapter.removeAt(position)
                    Toast.makeText(this@MainActivity, "Đã xoá thành công", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this@MainActivity, "Xoá thất bại", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@MainActivity, "Lỗi kết nối: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // ===== DOWNLOAD + MỞ FILE TỪ SERVER (bypass ngrok warning) =====
    private fun downloadAndOpen(url: String) {
        android.util.Log.d("DownloadOpen", "URL: $url")

        // Kiểm tra URL hợp lệ trước
        if (!url.startsWith("http")) {
            Toast.makeText(this, "URL không hợp lệ: $url", Toast.LENGTH_LONG).show()
            return
        }

        Toast.makeText(this, "Đang tải file...", Toast.LENGTH_SHORT).show()
        lifecycleScope.launch {
            try {
                // Phải dùng IO dispatcher cho network call
                val response = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    ApiClient.okHttpClient.newCall(
                        okhttp3.Request.Builder().url(url).build()
                    ).execute()
                }

                if (!response.isSuccessful) {
                    Toast.makeText(this@MainActivity, "Không tải được file (${response.code})", Toast.LENGTH_SHORT).show()
                    return@launch
                }

                val ext = url.substringAfterLast(".").lowercase().substringBefore("?")
                val mimeType = if (ext == "pdf") "application/pdf" else "image/jpeg"
                val tempFile = File(cacheDir, "temp_view.$ext")

                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    response.body?.byteStream()?.use { input ->
                        tempFile.outputStream().use { output -> input.copyTo(output) }
                    }
                }

                val fileUri = FileProvider.getUriForFile(this@MainActivity, "${packageName}.provider", tempFile)
                val intent = Intent(Intent.ACTION_VIEW)
                intent.setDataAndType(fileUri, mimeType)
                intent.flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                startActivity(intent)

            } catch (e: Exception) {
                android.util.Log.e("DownloadOpen", "Error", e)
                Toast.makeText(this@MainActivity, "Lỗi: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }
}