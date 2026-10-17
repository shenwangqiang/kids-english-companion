package com.kidsenglish.companion

import android.Manifest
import android.content.pm.PackageManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.os.Build
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import com.kidsenglish.companion.conversation.ConversationForegroundService
import com.kidsenglish.companion.ui.AppViewModel
import com.kidsenglish.companion.ui.KidsEnglishApp
import com.kidsenglish.companion.ui.theme.KidsEnglishTheme

class MainActivity : ComponentActivity() {
    private val viewModel: AppViewModel by viewModels()
    private val stopVoiceReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == ConversationForegroundService.ACTION_STOP_VOICE) stopVoiceSession()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ContextCompat.registerReceiver(
            this,
            stopVoiceReceiver,
            IntentFilter(ConversationForegroundService.ACTION_STOP_VOICE),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        val practiceMicrophonePermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) viewModel.startListening()
        }
        val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) startVoiceSession()
            else viewModel.reportVoicePermissionError(getString(R.string.notification_permission_required))
        }
        val requestNotificationThenStart = {
            if (
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                startVoiceSession()
            }
        }
        val voiceMicrophonePermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) requestNotificationThenStart()
            else viewModel.reportVoicePermissionError(getString(R.string.microphone_permission_required_voice))
        }
        setContent {
            KidsEnglishTheme {
                KidsEnglishApp(
                    viewModel = viewModel,
                    onRequestPracticeMicrophone = {
                        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                            viewModel.startListening()
                        } else {
                            practiceMicrophonePermission.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    },
                    onRequestVoiceMicrophone = {
                        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                            requestNotificationThenStart()
                        } else {
                            voiceMicrophonePermission.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    },
                    onStopVoiceConversation = ::stopVoiceSession,
                )
            }
        }
    }

    private fun startVoiceSession() {
        if (viewModel.startVoiceConversation()) {
            updateShowWhenLocked(true)
            ConversationForegroundService.start(this)
        }
    }

    private fun stopVoiceSession() {
        viewModel.stopVoiceConversation()
        ConversationForegroundService.stop(this)
        updateShowWhenLocked(false)
    }

    private fun updateShowWhenLocked(enabled: Boolean) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(enabled)
        } else if (enabled) {
            @Suppress("DEPRECATION")
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED)
        } else {
            @Suppress("DEPRECATION")
            window.clearFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED)
        }
    }

    override fun onDestroy() {
        if (isFinishing) {
            viewModel.stopVoiceConversation()
            ConversationForegroundService.stop(this)
        }
        unregisterReceiver(stopVoiceReceiver)
        super.onDestroy()
    }
}
