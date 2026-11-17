package com.bettey.assistant

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.provider.Settings
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import android.widget.Button
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : Activity(), TextToSpeech.OnInitListener {
    private lateinit var statusText: TextView
    private lateinit var heardText: TextView
    private lateinit var tts: TextToSpeech
    private var ttsReady = false

    companion object {
        private const val REQUEST_AUDIO = 100
        private const val REQUEST_SPEECH = 101
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusText = findViewById(R.id.statusText)
        heardText = findViewById(R.id.heardText)
        tts = TextToSpeech(this, this)

        findViewById<Button>(R.id.listenButton).setOnClickListener {
            if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                startSpeechRecognition()
            } else {
                requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), REQUEST_AUDIO)
            }
        }
    }

    private fun startSpeechRecognition() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak to Bettey in Telugu, English or Hindi")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
        }
        try {
            startActivityForResult(intent, REQUEST_SPEECH)
            updateStatus("Listening…")
        } catch (_: Exception) {
            updateStatus("No speech recognition service is available on this phone.")
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_AUDIO) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startSpeechRecognition()
            } else {
                updateStatus("Microphone permission is required for voice commands.")
            }
        }
    }

    @Deprecated("Uses the platform activity result API for a dependency-free prototype")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_SPEECH) {
            if (resultCode == RESULT_OK) {
                val spoken = data
                    ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                    ?.firstOrNull()
                if (!spoken.isNullOrBlank()) {
                    heardText.text = spoken
                    handleCommand(spoken)
                } else {
                    updateStatus("I didn't catch that. Please try again.")
                }
            } else {
                updateStatus("Listening cancelled.")
            }
        }
    }

    private fun handleCommand(raw: String) {
        val command = raw.lowercase(Locale.ROOT).trim()

        when {
            command.contains("open settings") || command.contains("సెట్టింగ్స్") ||
                command.contains("सेटिंग") -> {
                updateStatus("Opening phone settings.")
                speak("Opening settings")
                startActivity(Intent(Settings.ACTION_SETTINGS))
            }

            command.contains("open camera") || command.contains("కెమెరా") ||
                command.contains("कैमरा") -> {
                updateStatus("Opening camera.")
                speak("Opening camera")
                try {
                    startActivity(Intent(MediaStore.ACTION_IMAGE_CAPTURE))
                } catch (_: Exception) {
                    updateStatus("No camera app found.")
                }
            }

            command.contains("call ") || command.startsWith("dial ") ||
                command.contains("కాల్") || command.contains("कॉल") -> {
                val number = Regex("[+]?[0-9][0-9 ()-]{5,}[0-9]").find(raw)?.value
                    ?.replace(Regex("[^+0-9]"), "")
                if (number != null) {
                    updateStatus("Opening dialer for $number.")
                    speak("Opening the dialer. Please confirm the call.")
                    startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$number")))
                } else {
                    updateStatus("For this first build, say call followed by a phone number.")
                    speak("Please say call followed by the phone number.")
                }
            }

            command.contains("what time") || command.contains("time is it") ||
                command.contains("సమయం") || command.contains("कितने बजे") -> {
                val time = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date())
                updateStatus("The time is $time.")
                speak("The time is $time")
            }

            else -> {
                updateStatus("I heard: $raw. I can open settings, open camera, or dial a number in V1.")
                speak("I heard you. That command is not available yet.")
            }
        }
    }

    private fun updateStatus(message: String) {
        statusText.text = message
    }

    private fun speak(message: String) {
        if (ttsReady) tts.speak(message, TextToSpeech.QUEUE_FLUSH, null, "bettey-response")
    }

    override fun onInit(status: Int) {
        ttsReady = status == TextToSpeech.SUCCESS
        if (ttsReady) tts.language = Locale.US
    }

    override fun onDestroy() {
        if (::tts.isInitialized) {
            tts.stop()
            tts.shutdown()
        }
        super.onDestroy()
    }
}
