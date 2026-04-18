package com.example.docscanner

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.viewpager2.widget.ViewPager2
import com.example.docscanner.databinding.FragmentAccountBinding
import com.google.firebase.auth.FirebaseAuth

class AccountFragment : Fragment() {

    private var _binding: FragmentAccountBinding? = null
    private val binding get() = _binding!!
    private lateinit var auth: FirebaseAuth
    private var isRegisterMode = false

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAccountBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        auth = FirebaseAuth.getInstance()

        // Nút đăng nhập / tạo tài khoản
        binding.btnLogin.setOnClickListener {
            val email = binding.etEmail.text.toString().trim()
            val password = binding.etPassword.text.toString()

            if (email.isEmpty() || password.isEmpty()) {
                Toast.makeText(requireContext(), "Vui lòng nhập email và mật khẩu", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (isRegisterMode) {
                val confirm = binding.etConfirmPassword.text.toString()
                if (password != confirm) {
                    Toast.makeText(requireContext(), "Mật khẩu xác nhận không khớp", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                if (password.length < 6) {
                    Toast.makeText(requireContext(), "Mật khẩu phải ít nhất 6 ký tự", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                auth.createUserWithEmailAndPassword(email, password)
                    .addOnSuccessListener {
                        Toast.makeText(requireContext(), "Tạo tài khoản thành công!", Toast.LENGTH_SHORT).show()
                        updateUI()
                    }
                    .addOnFailureListener {
                        Toast.makeText(requireContext(), "Lỗi: ${it.message}", Toast.LENGTH_LONG).show()
                    }
            } else {
                auth.signInWithEmailAndPassword(email, password)
                    .addOnSuccessListener {
                        Toast.makeText(requireContext(), "Đăng nhập thành công!", Toast.LENGTH_SHORT).show()
                        updateUI()
                    }
                    .addOnFailureListener {
                        Toast.makeText(requireContext(), "Lỗi: ${it.message}", Toast.LENGTH_LONG).show()
                    }
            }
        }

        // Chuyển mode Login ↔ Register
        binding.btnToggleMode.setOnClickListener {
            isRegisterMode = !isRegisterMode
            if (isRegisterMode) {
                binding.layoutConfirmPassword.visibility = View.VISIBLE
                binding.btnLogin.text = "Tạo tài khoản"
                binding.btnToggleMode.text = "← Quay lại đăng nhập"
            } else {
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

        // Nút mũi tên → chuyển sang tab Camera (tab kế tiếp)
        binding.btnNext.setOnClickListener {
            val viewPager = activity?.findViewById<ViewPager2>(R.id.viewPager)
            viewPager?.currentItem = 1  // Tab Camera Settings
        }

        updateUI()
    }

    override fun onResume() {
        super.onResume()
        updateUI()
    }

    private fun updateUI() {
        val user = auth.currentUser
        if (user != null) {
            binding.tvName.text = user.displayName ?: user.email ?: "Người dùng"
            binding.tvEmail.text = user.email ?: ""
            binding.imgAvatar.setImageResource(R.mipmap.ic_launcher_round)
            binding.layoutLoginForm.visibility = View.GONE
            binding.cardLoginForm.visibility = View.GONE
            binding.btnLogout.visibility = View.VISIBLE
            binding.btnNext.visibility = View.VISIBLE   // Hiện nút → sau khi login
        } else {
            binding.tvName.text = "Chưa đăng nhập"
            binding.tvEmail.text = ""
            binding.imgAvatar.setImageResource(R.mipmap.ic_launcher_round)
            binding.layoutConfirmPassword.visibility = View.GONE
            binding.btnLogin.text = "Đăng nhập"
            binding.btnToggleMode.text = "Tạo tài khoản mới"
            isRegisterMode = false
            binding.layoutLoginForm.visibility = View.VISIBLE
            binding.cardLoginForm.visibility = View.VISIBLE
            binding.btnLogout.visibility = View.GONE
            binding.btnNext.visibility = View.GONE
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
