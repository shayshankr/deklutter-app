package com.example.deklutter_app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.deklutter_app.navigation.DeKlutterApp
import com.example.deklutter_app.ui.theme.Deklutter_appTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            Deklutter_appTheme {
                DeKlutterApp()
            }
        }
    }
}
