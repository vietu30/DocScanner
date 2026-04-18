package com.example.docscanner

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.SeekBar
import androidx.fragment.app.Fragment
import com.example.docscanner.databinding.FragmentCameraSettingsBinding

class CameraSettingsFragment : Fragment() {

    private var _binding: FragmentCameraSettingsBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCameraSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val prefs = requireActivity().getSharedPreferences("camera_settings", Context.MODE_PRIVATE)

        // ── Resolution Spinner ────────────────────────────────────────────
        val resolutions = listOf("Tự động", "4K (3840×2160)", "FHD (1920×1080)", "HD (1280×720)", "SD (640×480)")
        binding.spinnerResolution.adapter =
            ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, resolutions)
        binding.spinnerResolution.setSelection(prefs.getInt("resolution", 0))
        binding.spinnerResolution.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: android.widget.AdapterView<*>?, v: View?, pos: Int, id: Long) {
                prefs.edit().putInt("resolution", pos).apply()
            }
            override fun onNothingSelected(p: android.widget.AdapterView<*>?) {}
        }

        // ── Filter Spinner ────────────────────────────────────────────────
        val filters = listOf("Không có", "Tài liệu (Trắng đen)", "Ảnh (Màu sắc)", "Làm sắc nét", "Tương phản cao")
        binding.spinnerFilter.adapter =
            ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, filters)
        binding.spinnerFilter.setSelection(prefs.getInt("filter", 0))
        binding.spinnerFilter.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: android.widget.AdapterView<*>?, v: View?, pos: Int, id: Long) {
                prefs.edit().putInt("filter", pos).apply()
            }
            override fun onNothingSelected(p: android.widget.AdapterView<*>?) {}
        }

        // ── JPEG Quality SeekBar ──────────────────────────────────────────
        val savedQuality = prefs.getInt("jpeg_quality", 85)
        binding.seekJpegQuality.progress = savedQuality
        binding.tvJpegQuality.text = "$savedQuality%"
        binding.seekJpegQuality.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                binding.tvJpegQuality.text = "$progress%"
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {
                prefs.edit().putInt("jpeg_quality", sb?.progress ?: 85).apply()
            }
        })

        // ── Switches ──────────────────────────────────────────────────────
        binding.switchAutoFlash.isChecked = prefs.getBoolean("auto_flash", false)
        binding.switchGridLines.isChecked = prefs.getBoolean("grid_lines", true)
        binding.switchAutoDetect.isChecked = prefs.getBoolean("auto_detect", true)

        binding.switchAutoFlash.setOnCheckedChangeListener { _, checked ->
            prefs.edit().putBoolean("auto_flash", checked).apply()
        }
        binding.switchGridLines.setOnCheckedChangeListener { _, checked ->
            prefs.edit().putBoolean("grid_lines", checked).apply()
        }
        binding.switchAutoDetect.setOnCheckedChangeListener { _, checked ->
            prefs.edit().putBoolean("auto_detect", checked).apply()
        }

        // ── Aspect Ratio Radio ────────────────────────────────────────────
        val savedRatio = prefs.getInt("aspect_ratio", 0)
        when (savedRatio) {
            0 -> binding.radioAuto.isChecked = true
            1 -> binding.radio43.isChecked = true
            2 -> binding.radio169.isChecked = true
            3 -> binding.radioA4.isChecked = true
        }
        binding.radioAspectRatio.setOnCheckedChangeListener { _, checkedId ->
            val ratio = when (checkedId) {
                R.id.radioAuto -> 0
                R.id.radio43   -> 1
                R.id.radio169  -> 2
                R.id.radioA4   -> 3
                else           -> 0
            }
            prefs.edit().putInt("aspect_ratio", ratio).apply()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
