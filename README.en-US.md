<p align="center">
  <img src="app/src/main/res/mipmap-xxxhdpi/ic_launcher.png" alt="163MusicPro" width="120" height="120">
</p>

<h1 align="center">163MusicPro</h1>

<p align="center">
  <strong>A NetEase Cloud Music player designed for Xiaotianjai smartwatches</strong>
</p>

<p align="center">
  <a href="../../releases/latest"><img src="https://img.shields.io/github/v/release/9xhk-1/163MusicPro?style=flat-square" alt="Latest Release"></a>
  <img src="https://img.shields.io/badge/platform-Android%207.0%2B-brightgreen?style=flat-square" alt="Platform">
  <img src="https://img.shields.io/badge/screen-320%C3%97360-blue?style=flat-square" alt="Screen">
  <img src="https://img.shields.io/github/license/9xhk-1/163MusicPro?style=flat-square" alt="License">
</p>

<p align="center">
  Official Website: <a href='https://163.imoow.com'>https://163.imoow.com</a><br>
  Calls NetEase Cloud Music APIs directly without the need for third-party intermediate servers.<br>
  Carefully adapted for 320×360 smartwatch screens, with all interfaces supporting gesture operations.
</p>

---

## ✨ Features

| Feature | Description |
|------|------|
| 🔍 **Online Search** | Search the entire NetEase Cloud Music library with support for paginated loading |
| ▶️ **Music Playback** | Previous / Next / Pause / Play, automatic track switching |
| 📝 **Lyrics Sync** | Fetch LRC lyrics online with line-by-line highlighted scrolling |
| ❤️ **Favorite Management** | Local / Cloud favorites, data persistence, automatic recovery after re-installation |
| ⬇️ **Offline Download** | Download songs locally for offline playback |
| 🔔 **Ringtone Setup** | Clip song segments to set as smartwatch ringtones |
| 📊 **Charts** | Browse popular NetEase Cloud music charts |
| 📜 **Playback History** | Automatically records the last 200 played tracks |
| ⚡ **Playback Speed** | 0.1x – 5.0x speed control, supports constant pitch / variable pitch |
| 🎲 **Playback Mode** | List Loop / Single Loop / Random Play |
| ⏱ **Sleep Timer** | Automatically stop playback after a set time |
| 🔊 **Volume Control** | Custom volume overlay that disappears automatically after 1.5s |
| 🔑 **Multiple Login** | QR code login / Cookie login |
| 🛡️ **Background Persistence** | Foreground Service + WakeLock to prevent the app from being killed in lock screen or background |
| 👤 **User Center** | View account information, VIP status, and expiration date |
| 🎵 **Personalized Roaming** | Get personalized song recommendations after logging in |

## 📦 Installation

### Install from Release (Recommended)

1. Go to [**Releases**](../../releases/latest) to download the latest APK.
2. Transfer the APK to the watch (via ADB or File Manager).
3. Install and open the application on the watch.

### Build from Source

**Environment Requirements:** JDK 8+, Android SDK 34

```bash
# Clone the repository
git clone https://github.com/9xhk-1/163MusicPro.git
cd 163MusicPro

# Build debug version
./gradlew assembleDebug
# APK → app/build/outputs/apk/debug/app-debug.apk

# Build signed release version
./gradlew assembleRelease
# APK → app/build/outputs/apk/release/app-release.apk
```

> Signed releases require the following environment variables: `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`

## 🚀 Quick Start

1. Open the app to enter the main player interface.
2. **Swipe left** to view lyrics, **swipe right** to close lyrics / go back.
3. Click the **⋯** in the top right corner to enter the function menu.
4. Select **Search** in the menu and enter a song name to play.
5. To play VIP songs, go to the **Login** page:
   - **QR Login**: Scan the QR code using the NetEase Cloud Music App.
   - **Cookie Login**: Manually paste your Cookie.

## 🏗 Project Structure

```
app/src/main/java/com/qinghe/music163pro/
├── activity/          # UI Interfaces (MainActivity, SearchActivity, ...)
├── api/               # NetEase API calls (MusicApiHelper, NeteaseApiCrypto)
├── manager/           # Data management (FavoritesManager, DownloadManager, HistoryManager)
├── model/             # Data models (Song)
├── player/            # Player core (MusicPlayerManager)
├── service/           # Background service (MusicPlaybackService)
└── util/              # Utility classes (MusicLog, QrCodeGenerator)
```

## ⚙️ Technical Specifications

| Item | Value |
|------|------|
| Package Name | `com.qinghe.music163pro` |
| Min SDK | Android 6.0 (API 23) |
| Target SDK | Android 8.1 (API 27) |
| Compile SDK | Android 14 (API 34) |
| Screen Adaptation | 320×360 (Xiaotianjai Watch) |
| API Encryption | WeAPI (AES-128-CBC + RSA) |
| Dependencies | `androidx.appcompat:appcompat:1.6.1` |

## 🔄 CI/CD

After code is merged into the `main` branch, GitHub Actions automatically builds and publishes the signed APK to Releases.

To configure signing keys: **Settings → Secrets and variables → Actions**, add:

| Secret | Description |
|--------|------|
| `KEYSTORE_BASE64` | Base64 encoded keystore file |
| `KEYSTORE_PASSWORD` | Keystore password |
| `KEY_ALIAS` | Key alias |
| `KEY_PASSWORD` | Key password |

## 🤝 Contribution

Issues and Pull Requests are welcome.

1. Fork this repository.
2. Create a feature branch (`git checkout -b feature/amazing-feature`).
3. Commit your changes (`git commit -m 'Add amazing feature'`).
4. Push to the branch (`git push origin feature/amazing-feature`).
5. Open a Pull Request.

## 📄 License

This project is open-sourced under the [MIT License](LICENSE). Copyrights for NetEase Cloud Music APIs and content belong to NetEase Company; this repository only opens the project's own code under the MIT agreement.

## ℹ️ Note

This project was written with the assistance of vibe coding.
