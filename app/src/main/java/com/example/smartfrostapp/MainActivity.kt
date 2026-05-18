package com.example.smartfrostapp

import android.os.Bundle
import android.widget.Toast
import android.util.Log
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanIntentResult
import com.journeyapps.barcodescanner.ScanOptions
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

class MainActivity : ComponentActivity() {
    private val barcodeLauncher = registerForActivityResult(
        ScanContract()
    ) { result: ScanIntentResult ->

        if (result.contents == null) {

            Toast.makeText(
                this,
                "Сканирование отменено",
                Toast.LENGTH_SHORT
            ).show()

        } else {

            val rawQrString = result.contents

            sendToBackend(rawQrString)
        }
    }

    private fun startScanner() {

        val options = ScanOptions()

        options.setDesiredBarcodeFormats(ScanOptions.QR_CODE)
        options.setPrompt("Наведите камеру на QR код")
        options.setCameraId(0)
        options.setBeepEnabled(true)
        options.setBarcodeImageEnabled(false)

        options.setCaptureActivity(
            CustomScannerActivity::class.java
        )

        barcodeLauncher.launch(options)
    }

    private fun sendToBackend(qr: String) {

        val request = QrRequest(qr)

        RetrofitClient.api.sendQr(request)
            .enqueue(object : retrofit2.Callback<okhttp3.ResponseBody> {

                override fun onResponse(
                    call: retrofit2.Call<okhttp3.ResponseBody>,
                    response: retrofit2.Response<okhttp3.ResponseBody>
                ) {

                    Toast.makeText(
                        this@MainActivity,
                        "Отправлено на сервер",
                        Toast.LENGTH_SHORT
                    ).show()
                }

                override fun onFailure(
                    call: retrofit2.Call<okhttp3.ResponseBody>,
                    t: Throwable
                ) {

                    Toast.makeText(
                        this@MainActivity,
                        "Ошибка: ${t.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            })
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            MaterialTheme {
                var isLoggedIn by remember { mutableStateOf(false) }

                if (isLoggedIn) {
                    ProductsScreen(
                        onStartScanner = {
                            startScanner()
                        }
                    )
                } else {
                    LoginScreen(onLoginSuccess = { isLoggedIn = true })
                }
            }
        }
    }
}
