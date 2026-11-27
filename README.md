# Nzel (نزّل)

Download videos, images and reels from a link - YouTube, Instagram, TikTok, Facebook, X and 1000+ other sites (yt-dlp engine).

Native Android app, Kotlin + Jetpack Compose, Material 3. English, Arabic (RTL) and French.

## Features
- Paste or share a link (share target from any app)
- Platform auto-detection
- Fetch title/thumbnail/duration, choose format: best quality, 720p, 480p, or audio MP3
- Progress, cancel, saves to Downloads/Nzel
- Download history with open/share/delete
- Self-updating yt-dlp engine (Settings)

## Build
CI builds signed per-ABI APKs and runs an emulator smoke test on every push (see Actions).
`./gradlew :app:assembleRelease`

## Note
Download only content you have the right to save.
