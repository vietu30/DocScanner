package com.example.docscanner

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter

class SettingsPagerAdapter(activity: FragmentActivity) : FragmentStateAdapter(activity) {

    override fun getItemCount(): Int = 4

    override fun createFragment(position: Int): Fragment = when (position) {
        0 -> AccountFragment()
        1 -> CameraSettingsFragment()
        2 -> AppInfoFragment()
        3 -> LicenseFragment()
        else -> AccountFragment()
    }
}
