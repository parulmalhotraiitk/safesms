# SafeSMS 🛡️

**SafeSMS** is a cutting-edge Android application that uses on-device Artificial Intelligence to protect your inbox from spam, scams, and malicious text messages in real-time. By leveraging the power of **Gemma 4 LiteRT**, SafeSMS analyzes your incoming messages locally, ensuring your privacy remains completely intact while keeping you secure.

## Features ✨

* **Real-time SMS Scanning:** Actively monitors your incoming messages in the background.
* **On-Device AI Inference:** Powered by Google's Gemma 4 LiteRT. Your messages never leave your device, ensuring 100% privacy.
* **Threat Assessment:** Categorizes messages into SAFE, SUSPICIOUS, or SCAM with confidence scores.
* **Detailed History & Analytics:** View your past scans and visual statistics of threat vectors over time.
* **Modern UI:** A beautiful, animated, dark-mode-first user interface built with Jetpack Compose.

## Architecture & Technology Stack 🛠️

* **Language:** Kotlin
* **UI Framework:** Jetpack Compose (Material Design 3)
* **AI/ML Engine:** Gemma 4 LiteRT (On-Device LLM Inference)
* **Background Processing:** Android Foreground Services
* **Build System:** Gradle (Kotlin DSL)

## Privacy First 🔒

Unlike traditional cloud-based SMS scanners, SafeSMS uses local AI inference. This means your private messages are analyzed directly on your device's processor and are **never** sent to any external servers or APIs.

## Getting Started 🚀

### Prerequisites

* Android Studio (latest version)
* An Android device or emulator running Android 8.0 (API level 26) or higher.

### Quick Test (Try it out!) 📱

If you just want to test the app without building from source, a ready-to-use APK is available in this repository.

1. Navigate to the `release/` folder in this repository.
2. Download `SafeSMS.apk` to your Android device.
3. Open the file to install the app (you may need to allow "Install unknown apps" from your browser/file manager).
4. Launch SafeSMS, grant the necessary permissions, and test the real-time background protection!

### Building from Source

1. Clone this repository:
   ```bash
   git clone https://github.com/parulmalhotraiitk/safesms.git
   ```
2. Open the project in Android Studio.
3. Sync the project with Gradle files.
4. Build and run the `app` module on your connected device or emulator.
5. Grant the necessary permissions (SMS and Notifications) when prompted to enable real-time background protection.

## License 📄

This project is open-source and available under the MIT License.
