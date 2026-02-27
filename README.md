# JavaOpenGL2 (Media Player Demo)

A local media player demo built in Java using LWJGL (OpenGL/GLFW/OpenAL) + FFmpeg (JavaCPP).
Focused on smooth playback, seeking, and a renderer/UI I’m building for long-running desktop apps.

> macOS note: requires `-XstartOnFirstThread` to run (GLFW/Cocoa).

## Demo
<!-- Add a screenshot or GIF -->
<!-- ![Screenshot](docs/screenshot.png) -->

## Features
- Audio playback via OpenAL streaming
- FFmpeg decoding (JavaCPP)
- Fast seeking path for MP3 (legacy fallback for edge cases)
- Custom renderer/UI pipeline (OpenGL)

## Quick start (Run a file)
### macOS
```bash
java --enable-native-access=ALL-UNNAMED -XstartOnFirstThread \
  -jar build/libs/JavaOpenGL2-1.0-macos-arm64.jar \
  -path "/path/to/song.m4a"
