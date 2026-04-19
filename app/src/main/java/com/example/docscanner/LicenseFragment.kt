package com.example.docscanner

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment

class LicenseFragment : Fragment() {
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_license, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // Nút gia hạn (fake)
        view.findViewById<android.widget.Button>(R.id.btnRenewLicense)?.setOnClickListener {
            Toast.makeText(requireContext(), "✅ Giấy phép đã được gia hạn đến 15/04/2028!", Toast.LENGTH_LONG).show()
        }
    }
}
