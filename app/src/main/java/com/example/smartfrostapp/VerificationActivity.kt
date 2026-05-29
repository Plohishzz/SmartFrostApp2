package com.example.smartfrostapp

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.smartfrostapp.databinding.ScreenVerificationBinding
import com.example.smartfrostapp.network.ApiClient
import kotlinx.coroutines.launch

class VerificationActivity : AppCompatActivity() {

    private lateinit var binding: ScreenVerificationBinding
    private var userEmail = ""
    private var userName = ""
    private var userPassword = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ScreenVerificationBinding.inflate(layoutInflater)
        setContentView(binding.root)

        userEmail = intent.getStringExtra("email") ?: ""
        userName = intent.getStringExtra("name") ?: ""
        userPassword = intent.getStringExtra("password") ?: ""

        binding.txtVerificationInfo.text = "Мы отправили 6-значный код на $userEmail"

        binding.btnVerify.setOnClickListener {
            val code = binding.editVerificationCode.text.toString().trim()
            if (code.length == 6) {
                verifyCode(code)
            } else {
                binding.txtVerificationError.visibility = View.VISIBLE
                binding.txtVerificationError.text = "Введите 6-значный код"
            }
        }

        binding.btnResendCode.setOnClickListener {
            resendCode()
        }

        binding.btnBackToRegister.setOnClickListener {
            finish()
        }
    }

    private fun verifyCode(code: String) {
        binding.btnVerify.isEnabled = false
        binding.txtVerificationError.visibility = View.GONE
        binding.txtVerificationError.text = "Проверяем код..."

        lifecycleScope.launch {
            val result = ApiClient.verifyCode(userEmail, code)
            runOnUiThread {
                binding.btnVerify.isEnabled = true
                result.fold(
                    onSuccess = { verificationToken ->
                        registerWithToken(verificationToken)
                    },
                    onFailure = { error ->
                        binding.txtVerificationError.visibility = View.VISIBLE
                        binding.txtVerificationError.text = error.message ?: "Ошибка верификации"
                    }
                )
            }
        }
    }

    private fun registerWithToken(verificationToken: String) {
        binding.txtVerificationError.visibility = View.VISIBLE
        binding.txtVerificationError.text = "Регистрация..."

        lifecycleScope.launch {
            val result = ApiClient.register(userName, userEmail, userPassword, verificationToken)
            runOnUiThread {
                result.fold(
                    onSuccess = { authResponse ->
                        val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
                        prefs.edit().apply {
                            putBoolean("is_logged_in", true)
                            putInt("user_id", authResponse.userId)
                            putString("user_token", authResponse.token)
                            apply()
                        }

                        Toast.makeText(this@VerificationActivity, "Регистрация успешна!", Toast.LENGTH_SHORT).show()

                        val intent = Intent(this@VerificationActivity, MainActivity::class.java)
                        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                        startActivity(intent)
                        finish()
                    },
                    onFailure = { error ->
                        binding.txtVerificationError.visibility = View.VISIBLE
                        binding.txtVerificationError.text = error.message ?: "Ошибка регистрации"
                    }
                )
            }
        }
    }

    private fun resendCode() {
        binding.btnResendCode.isEnabled = false
        binding.txtVerificationError.visibility = View.GONE
        binding.txtVerificationError.text = "Отправляем код повторно..."

        lifecycleScope.launch {
            val result = ApiClient.sendVerificationCode(userEmail)
            runOnUiThread {
                binding.btnResendCode.isEnabled = true
                result.fold(
                    onSuccess = { message ->
                        Toast.makeText(this@VerificationActivity, message, Toast.LENGTH_SHORT).show()
                    },
                    onFailure = { error ->
                        binding.txtVerificationError.visibility = View.VISIBLE
                        binding.txtVerificationError.text = error.message ?: "Ошибка отправки кода"
                    }
                )
            }
        }
    }
}
