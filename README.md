# Bettey V1

A phone-first native Android assistant prototype.

## Current V1 capabilities
- Speech input through Android's installed speech-recognition service.
- Uses the device locale for recognition; multilingual Telugu/English/Hindi support depends on the recognizer installed on the phone.
- Text-to-speech response.
- Opens Android Settings.
- Opens the camera app.
- Opens the dialer with a recognized phone number; the user confirms the call.

## Build
This project uses Android Gradle Plugin 8.13.2, Gradle 8.13, Kotlin 2.2.20, JDK 17 and Android SDK 36.

Run:
    gradle assembleDebug

APK:
    app/build/outputs/apk/debug/app-debug.apk

## Phone-only workflow
1. Create a GitHub repository named `bettey-android`.
2. Upload this project contents to the repository root.
3. Open the Actions tab and run `Build Bettey Android APK`.
4. Download the `bettey-debug-apk` artifact from the completed workflow.
5. Extract `app-debug.apk` and install it on the Redmi.

## Important limitations
- This prototype is push-to-talk, not an always-listening wake-word assistant.
- It does not read contacts, send messages, answer calls, or record calls.
- Do not add API keys to the Android app. A later backend should hold provider credentials.
- Do not disable Android/HyperOS security controls to make this prototype work.
