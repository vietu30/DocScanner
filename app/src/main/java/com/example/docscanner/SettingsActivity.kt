package com.example.docscanner

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.docscanner.databinding.ActivitySettingsBinding
import com.google.firebase.auth.FirebaseAuth

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private lateinit var auth: FirebaseAuth

    // true = đang ở chế độ Register, false = Login
    private var isRegisterMode = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = FirebaseAuth.getInstance()

        // Nút hành động chính: Đăng nhập hoặc Tạo tài khoản
        binding.btnLogin.setOnClickListener {
            val email    = binding.etEmail.text.toString().trim()
            val password = binding.etPassword.text.toString()

            if (email.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Vui lòng nhập email và mật khẩu", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (isRegisterMode) {
                // --- CHẾ ĐỘ ĐĂNG KÝ ---
                val confirm = binding.etConfirmPassword.text.toString()
                if (password != confirm) {
                    Toast.makeText(this, "Mật khẩu xác nhận không khớp", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                if (password.length < 6) {
                    Toast.makeText(this, "Mật khẩu phải ít nhất 6 ký tự", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                auth.createUserWithEmailAndPassword(email, password)
                    .addOnSuccessListener {
                        Toast.makeText(this, "Tạo tài khoản thành công!", Toast.LENGTH_SHORT).show()
                        updateUI()
                    }
                    .addOnFailureListener {
                        Toast.makeText(this, "Lỗi: ${it.message}", Toast.LENGTH_LONG).show()
                    }
            } else {
                // --- CHẾ ĐỘ ĐĂNG NHẬP ---
                auth.signInWithEmailAndPassword(email, password)
                    .addOnSuccessListener {
                        Toast.makeText(this, "Đăng nhập thành công!", Toast.LENGTH_SHORT).show()
                        updateUI()
                    }
                    .addOnFailureListener {
                        Toast.makeText(this, "Lỗi: ${it.message}", Toast.LENGTH_LONG).show()
                    }
            }
        }

        // Nút chuyển mode Login ↔ Register
        binding.btnToggleMode.setOnClickListener {
            isRegisterMode = !isRegisterMode
            if (isRegisterMode) {
                // Chuyển sang Register: hiện ô confirm, đổi tên nút
                binding.layoutConfirmPassword.visibility = View.VISIBLE
                binding.btnLogin.text = "Tạo tài khoản"
                binding.btnToggleMode.text = "← Quay lại đăng nhập"
            } else {
                // Quay về Login: ẩn ô confirm, reset nút
                binding.layoutConfirmPassword.visibility = View.GONE
                binding.etConfirmPassword.text?.clear()
                binding.btnLogin.text = "Đăng nhập"
                binding.btnToggleMode.text = "Tạo tài khoản mới"
            }
        }

        binding.btnLogout.setOnClickListener {
            auth.signOut()
            isRegisterMode = false
            updateUI()
        }

        updateUI()
    }

    private fun updateUI() {
        val user = auth.currentUser
        if (user != null) {
            binding.tvName.text  = user.displayName ?: user.email ?: "Người dùng"
            binding.tvEmail.text = user.email ?: ""
            binding.imgAvatar.setImageResource(R.mipmap.ic_launcher_round)
            binding.layoutLoginForm.visibility = View.GONE
            binding.btnLogout.visibility       = View.VISIBLE
        } else {
            binding.tvName.text  = "Chưa đăng nhập"
            binding.tvEmail.text = ""
            binding.imgAvatar.setImageResource(R.mipmap.ic_launcher_round)

            binding.layoutConfirmPassword.visibility = View.GONE
            binding.btnLogin.text      = "Đăng nhập"
            binding.btnToggleMode.text = "Tạo tài khoản mới"
            isRegisterMode = false
            binding.layoutLoginForm.visibility = View.VISIBLE
            binding.btnLogout.visibility       = View.GONE
        }
    }
}
