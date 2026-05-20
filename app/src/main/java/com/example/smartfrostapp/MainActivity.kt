package com.example.smartfrostapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            var selectedTheme by remember { mutableStateOf("System") }
            var notificationsEnabled by remember { mutableStateOf(true) }
            var notificationDaysBefore by remember { mutableFloatStateOf(3f) }

            SmartFrostTheme(theme = selectedTheme) {
                var isLoggedIn by remember { mutableStateOf(false) }

                if (isLoggedIn) {
                    ProductsScreen(
                        selectedTheme = selectedTheme,
                        onThemeSelected = { selectedTheme = it },
                        notificationsEnabled = notificationsEnabled,
                        onNotificationsEnabledChange = { notificationsEnabled = it },
                        notificationDaysBefore = notificationDaysBefore,
                        onNotificationDaysBeforeChange = { notificationDaysBefore = it }
                    )
                } else {
                    LoginScreen(onLoginSuccess = { isLoggedIn = true })
                }
            }
        }
    }
}
