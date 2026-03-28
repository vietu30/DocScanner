package com.example.docscanner

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.content.FileProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import java.io.File

class MainActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Toolbar
        val toolbar = findViewById<Toolbar>(R.id.topBar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(false)

        // RecyclerView
        recyclerView = findViewById(R.id.scanList)
        recyclerView.layoutManager = LinearLayoutManager(this)

        // Button scan
        val scanButton = findViewById<FloatingActionButton>(R.id.scanButton)
        scanButton.setOnClickListener {
            startActivity(Intent(this, ScannerActivity::class.java))
        }

        loadPdfList()

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
        loadPdfList() // reload khi quay lại
    }

    // ===== LOAD PDF =====
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
                file = it
            )
        }

        val adapter = ScanAdapter(items) { item ->
            openPdf(item.file)
        }

        recyclerView.adapter = adapter
    }

    // ===== MỞ PDF =====
    private fun openPdf(file: File) {

        val uri: Uri = FileProvider.getUriForFile(
            this,
            "${packageName}.provider",
            file
        )

        val intent = Intent(Intent.ACTION_VIEW)
        intent.setDataAndType(uri, "application/pdf")
        intent.flags = Intent.FLAG_GRANT_READ_URI_PERMISSION

        startActivity(intent)
    }

}