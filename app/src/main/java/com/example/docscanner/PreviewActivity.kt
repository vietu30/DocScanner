package com.example.docscanner

import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.viewpager2.widget.ViewPager2
import java.io.File

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

            // xoá file
            File(imageList[position]).delete()

            // xoá khỏi list
            imageList.removeAt(position)

            adapter.notifyDataSetChanged()

            if (imageList.isEmpty()) {
                finish()
            }
        }

        // ===== SAVE PDF =====


                findViewById<View>(R.id.btnSavePdf).setOnClickListener {

                    if (imageList.isEmpty()) {
                        Toast.makeText(this, "Không có ảnh", Toast.LENGTH_SHORT).show()
                        return@setOnClickListener
                    }

                    val editText = EditText(this)
                    editText.hint = "Nhập tên file PDF"

                    AlertDialog.Builder(this)
                        .setTitle("Đặt tên file")
                        .setView(editText)
                        .setPositiveButton("Lưu") { _, _ ->

                            val fileName = editText.text.toString().ifEmpty {
                                "scan_${System.currentTimeMillis()}"
                            }

                            val pdfFile = File(
                                getExternalFilesDir(null),
                                "$fileName.pdf"
                            )

                            createPdf(this, imageList, pdfFile.absolutePath)

                            Toast.makeText(this, "Đã lưu:\n${pdfFile.name}", Toast.LENGTH_LONG).show()

                            finish()
                        }
                        .setNegativeButton("Huỷ", null)
                        .show()
                }
    }
}