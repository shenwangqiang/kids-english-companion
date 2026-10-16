package com.kidsenglish.companion

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import com.kidsenglish.companion.ui.AppViewModel
import com.kidsenglish.companion.ui.KidsEnglishApp
import com.kidsenglish.companion.ui.theme.KidsEnglishTheme

class MainActivity : ComponentActivity() {
    private val viewModel: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val microphonePermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) viewModel.startListening()
        }
        setContent {
            KidsEnglishTheme {
                KidsEnglishApp(
                    viewModel = viewModel,
                    onRequestMicrophone = {
                        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                            viewModel.startListening()
                        } else {
                            microphonePermission.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    },
                )
            }
        }
    }
}
