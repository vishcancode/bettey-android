package com.bettey.assistant

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.ContactsContract
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
    private var pendingContactName: String? = null

    companion object {
        private const val REQUEST_AUDIO = 100
        private const val REQUEST_SPEECH = 101
        private const val REQUEST_CONTACTS = 102
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusText = findViewById(R.id.statusText)
        heardText = findViewById(R.id.heardText)
        tts = TextToSpeech(this, this)

        findViewById<Button>(R.id.listenButton).setOnClickListener {
            if (checkSelfPermission(Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED) {
                startSpeechRecognition()
            } else {
                requestPermissions(
                    arrayOf(Manifest.permission.RECORD_AUDIO),
                    REQUEST_AUDIO
                )
            }
        }
    }

    private fun startSpeechRecognition() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE,
                Locale.getDefault().toLanguageTag()
            )
            putExtra(
                RecognizerIntent.EXTRA_PROMPT,
                "Speak to Bettey in Telugu, English or Hindi"
            )
        }

        try {
            startActivityForResult(intent, REQUEST_SPEECH)
            updateStatus("Listening...")
        } catch (_: Exception) {
            updateStatus("Speech recognition service unavailable.")
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        val granted = grantResults.isNotEmpty() &&
                grantResults[0] == PackageManager.PERMISSION_GRANTED

        if (!granted) {
            updateStatus("Permission denied.")
            return
        }

        when (requestCode) {
            REQUEST_AUDIO -> startSpeechRecognition()
            REQUEST_CONTACTS -> {
                pendingContactName?.let { findContactAndDial(it) }
            }
        }
    }

    @Deprecated("Platform speech activity result API")
    override fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        data: Intent?
    ) {
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
                    updateStatus("I didn't understand. Try again.")
                }
            } else {
                updateStatus("Listening cancelled.")
            }
        }
    }

    private fun handleCommand(raw: String) {
        val command = raw.lowercase(Locale.ROOT).trim()

        when {
            command.contains("open settings") ||
            command.contains("సెట్టింగ్స్") ||
            command.contains("सेटिंग") -> {
                updateStatus("Opening settings.")
                speak("Opening settings")
                startActivity(Intent(Settings.ACTION_SETTINGS))
            }

            command.contains("open camera") ||
            command.contains("కెమెరా") ||
            command.contains("कैमरा") -> {
                try {
                    startActivity(Intent(MediaStore.ACTION_IMAGE_CAPTURE))
                    updateStatus("Opening camera.")
                    speak("Opening camera")
                } catch (_: Exception) {
                    updateStatus("Camera app not found.")
                }
            }

            command.startsWith("open ") ||
            command.startsWith("launch ") -> {
                val appName = raw.substringAfter(' ').trim()
                launchApp(appName)
            }

            command.startsWith("call ") ||
            command.startsWith("dial ") ||
            command.startsWith("కాల్ ") ||
            command.startsWith("कॉल ") -> {
                val target = raw.substringAfter(' ').trim()

                if (target.matches(Regex("[+]?[0-9][0-9 ()-]{5,}[0-9]"))) {
                    val number = target.replace(Regex("[^+0-9]"), "")
                    openDialer(number)
                } else if (target.isNotBlank()) {
                    pendingContactName = target

                    if (checkSelfPermission(Manifest.permission.READ_CONTACTS)
                        == PackageManager.PERMISSION_GRANTED) {
                        findContactAndDial(target)
                    } else {
                        requestPermissions(
                            arrayOf(Manifest.permission.READ_CONTACTS),
                            REQUEST_CONTACTS
                        )
                    }
                }
            }

            command.contains("what time") ||
            command.contains("time is it") ||
            command.contains("సమయం") ||
            command.contains("कितने बजे") -> {
                val time = SimpleDateFormat(
                    "h:mm a",
                    Locale.getDefault()
                ).format(Date())

                updateStatus("The time is $time.")
                speak("The time is $time")
            }

            else -> {
                updateStatus(
                    "I heard: $raw. Try calling a contact or opening an app."
                )
                speak("I heard you. That command is not available yet.")
            }
        }
    }

    private fun findContactAndDial(name: String) {
        try {
            val projection = arrayOf(
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER
            )

            contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                projection,
                "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?",
                arrayOf("%$name%"),
                "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} ASC"
            )?.use { cursor ->

                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndexOrThrow(
                        ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME
                    )

                    val numberIndex = cursor.getColumnIndexOrThrow(
                        ContactsContract.CommonDataKinds.Phone.NUMBER
                    )

                    val contactName = cursor.getString(nameIndex)
                    val number = cursor.getString(numberIndex)

                    updateStatus("Found $contactName. Opening dialer.")
                    speak("I found $contactName. Please confirm the call.")

                    openDialer(number)
                } else {
                    updateStatus("Contact not found: $name")
                    speak("I couldn't find that contact.")
                }
            }

        } catch (_: SecurityException) {
            updateStatus("Contacts permission is unavailable.")
        } finally {
            pendingContactName = null
        }
    }

    private fun openDialer(number: String) {
        val intent = Intent(
            Intent.ACTION_DIAL,
            Uri.parse("tel:${Uri.encode(number)}")
        )
        startActivity(intent)
        updateStatus("Dialer opened. Confirm the call.")
    }

    private fun launchApp(requestedName: String) {
        val aliases = mapOf(
            "whatsapp" to "com.whatsapp",
            "youtube" to "com.google.android.youtube",
            "chrome" to "com.android.chrome",
            "instagram" to "com.instagram.android",
            "maps" to "com.google.android.apps.maps",
            "gmail" to "com.google.android.gm",
            "telegram" to "org.telegram.messenger"
        )

        val wanted = requestedName.lowercase(Locale.ROOT).trim()

        val packageName = aliases[wanted]

        val directIntent = packageName?.let {
            packageManager.getLaunchIntentForPackage(it)
        }

        if (directIntent != null) {
            startActivity(directIntent)
            updateStatus("Opening $requestedName.")
            speak("Opening $requestedName")
            return
        }

        val launcherIntent = Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_LAUNCHER)

        val matches = packageManager.queryIntentActivities(
            launcherIntent,
            0
        )

        val match = matches.firstOrNull {
            it.loadLabel(packageManager).toString()
                .equals(requestedName, ignoreCase = true)
        }

        val launchIntent = match?.let {
            packageManager.getLaunchIntentForPackage(
                it.activityInfo.packageName
            )
        }

        if (launchIntent != null) {
            startActivity(launchIntent)
            updateStatus("Opening $requestedName.")
            speak("Opening $requestedName")
        } else {
            updateStatus("Couldn't find app: $requestedName")
            speak("I couldn't find that application.")
        }
    }

    private fun updateStatus(message: String) {
        statusText.text = message
    }

    private fun speak(message: String) {
        if (ttsReady) {
            tts.speak(
                message,
                TextToSpeech.QUEUE_FLUSH,
                null,
                "bettey-response"
            )
        }
    }

    override fun onInit(status: Int) {
        ttsReady = status == TextToSpeech.SUCCESS

        if (ttsReady) {
            tts.language = Locale.US
        }
    }

    override fun onDestroy() {
        if (::tts.isInitialized) {
            tts.stop()
            tts.shutdown()
        }
        super.onDestroy()
    }
}
