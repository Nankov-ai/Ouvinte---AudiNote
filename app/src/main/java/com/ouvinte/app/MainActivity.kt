package com.ouvinte.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.ouvinte.app.data.repository.SettingsRepository
import com.ouvinte.app.presentation.navigation.OuvinteNavGraph
import com.ouvinte.app.presentation.theme.OuvinteTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var settingsRepository: SettingsRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            OuvinteTheme {
                OuvinteNavGraph(settingsRepository = settingsRepository)
            }
        }
    }
}
