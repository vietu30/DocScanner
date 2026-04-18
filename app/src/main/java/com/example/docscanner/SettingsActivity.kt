package com.example.docscanner

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.docscanner.databinding.ActivitySettingsBinding
import com.google.android.material.tabs.TabLayoutMediator

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Toolbar với nút back
        setSupportActionBar(binding.settingsToolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.settingsToolbar.setNavigationOnClickListener { finish() }

        // Gắn adapter vào ViewPager2
        val adapter = SettingsPagerAdapter(this)
        binding.viewPager.adapter = adapter

        // Gắn TabLayout với ViewPager2
        TabLayoutMediator(binding.tabLayout, binding.viewPager) { tab, position ->
            tab.text = when (position) {
                0 -> "Tài khoản"
                1 -> "Camera"
                2 -> "Ứng dụng"
                3 -> "Giấy phép"
                else -> ""
            }
        }.attach()

        // Nếu mở từ intent có tab cụ thể
        val startTab = intent.getIntExtra("START_TAB", 0)
        if (startTab > 0) {
            binding.viewPager.setCurrentItem(startTab, false)
        }
    }
}
