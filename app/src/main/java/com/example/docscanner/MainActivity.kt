package com.example.docscanner

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val toolbar = findViewById<Toolbar>(R.id.topBar)
        setSupportActionBar(toolbar)
        supportActionBar?.title = "DocScanner"
        supportActionBar?.setDisplayShowTitleEnabled(false)

        val recyclerView = findViewById<RecyclerView>(R.id.scanList)

        val scanList = listOf(
            ScanItem("Invoice_001.pdf","Oct 24, 2023 • 1.2 MB"),
            ScanItem("Document_Scan.pdf","Oct 22, 2023 • 850 KB"),
            ScanItem("Work_Contract_Signed.pdf","Oct 19, 2023 • 2.4 MB"),
            ScanItem("Receipt_Lunch.pdf","Oct 18, 2023 • 420 KB"),
            ScanItem("Identity_Card_Scan.pdf","Oct 15, 2023 • 1.1 MB")
        )

        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = ScanAdapter(scanList)
    }
}