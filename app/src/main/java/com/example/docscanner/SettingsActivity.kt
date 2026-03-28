package com.example.docscanner

import android.app.Activity
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.example.docscanner.databinding.ActivitySettingsBinding
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var googleSignInClient: GoogleSignInClient

    private val WEB_CLIENT_ID = "844235246700-kn0bh775r08a76pb6v3eqti5pi3phtcn.apps.googleusercontent.com"

    private val signInLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
            try {
                val account = task.getResult(ApiException::class.java)
                firebaseAuthWithGoogle(account.idToken!!)
            } catch (e: ApiException) {
                Toast.makeText(this, "Đăng nhập thất bại: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = FirebaseAuth.getInstance()

        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(WEB_CLIENT_ID)
            .requestEmail()
            .build()
        googleSignInClient = GoogleSignIn.getClient(this, gso)

        binding.btnLogin.setOnClickListener {
            signInLauncher.launch(googleSignInClient.signInIntent)
        }

        binding.btnLogout.setOnClickListener {
            auth.signOut()
            googleSignInClient.signOut()
            updateUI()
        }

        updateUI()
    }

    private fun firebaseAuthWithGoogle(idToken: String) {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        auth.signInWithCredential(credential)
            .addOnSuccessListener {
                Toast.makeText(this, "Đăng nhập thành công!", Toast.LENGTH_SHORT).show()
                updateUI()
            }
            .addOnFailureListener {
                Toast.makeText(this, "Lỗi: ${it.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun updateUI() {
        val user = auth.currentUser
        if (user != null) {
            binding.tvName.text  = user.displayName ?: "Không có tên"
            binding.tvEmail.text = user.email ?: ""
            Glide.with(this).load(user.photoUrl).circleCrop().into(binding.imgAvatar)
            binding.btnLogin.visibility  = View.GONE
            binding.btnLogout.visibility = View.VISIBLE
        } else {
            binding.tvName.text  = "Chưa đăng nhập"
            binding.tvEmail.text = ""
            binding.imgAvatar.setImageResource(R.mipmap.ic_launcher_round)
            binding.btnLogin.visibility  = View.VISIBLE
            binding.btnLogout.visibility = View.GONE
        }
    }
}
