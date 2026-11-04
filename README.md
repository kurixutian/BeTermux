# BeTermux

[![Latest release](https://img.shields.io/github/v/release/kurixutian/BeTermux?label=release)](https://github.com/kurixutian/BeTermux/releases/latest)
[![Android 7+](https://img.shields.io/badge/Android-7.0%2B-3DDC84?logo=android&logoColor=white)](#installation)
[![License: GPLv3](https://img.shields.io/badge/License-GPLv3-blue.svg)](LICENSE.md)

BeTermux is a customized terminal emulator for Android with a modern Warp-inspired interface, tabbed workflow, integrated Dev Browser with Eruda DevTools, enhanced File Manager with multi-format previews, and process termination safety. It installs under `com.termux` and remains 100% compatible with the entire [Termux package ecosystem](https://github.com/termux/termux-packages).

> **Latest stable:** `v1.0.0` — [**Download the APK →**](https://github.com/kurixutian/BeTermux/releases/latest)

---

## Contents

- [Features](#features)
  - [Warp-Inspired Design & Toolbar](#warp-inspired-design--toolbar)
  - [Integrated Dev Browser](#integrated-dev-browser)
  - [Enhanced File Manager & Multi-Format Viewer](#enhanced-file-manager--multi-format-viewer)
  - [Process Safety & Session Protection](#process-safety--session-protection)
  - [Unified Tabbed Workflow](#unified-tabbed-workflow)
  - [Package & SSH Management](#package--ssh-management)
  - [Built-In Backup & Restore](#built-in-backup--restore)
- [Installation](#installation)
- [Building from source](#building-from-source)
- [Credits & license](#credits--license)

---

## Features

### Warp-Inspired Design & Toolbar
- **Distilled 4-Action Toolbar**: Cleaned up the cluttered header into 4 clear controls: `Files`, `Extra Keys`, `+ (New Tab)`, and an overflow `⋮ More` popup menu.
- **Deep Slate/Charcoal Visual Palette**: High-contrast, modern dark UI styling inspired by modern desktop terminals like Warp.
- **Accent Theming & Color Schemes**: Customize primary accent colors and switch between terminal color themes (Dracula, Nord, Tokyo Night, Gruvbox, etc.) or design your own ANSI palette.

### Integrated Dev Browser
- **Built-in Web Preview**: Test local web servers and web apps directly inside BeTermux without switching out of the app.
- **Eruda DevTools Injection**: Inspect elements, view console logs, inspect network calls, and run DOM debugging tools directly on mobile.
- **Smart Local Port Shortcuts**: Quick one-tap address bar presets for common local development ports (`3000`, `5173`, `8000`, `8080`, etc.).
- **Browsing History & Navigation**: Forward, back, refresh, and history tracking built into the browser interface.

### Enhanced File Manager & Multi-Format Viewer
- **Real-Time Live Search**: Instantly filter files and folders as you type in your home directory and shared storage.
- **Multi-Format Previews**: View files directly in-app:
  - **Images**: High-resolution image viewer with automatic bitmap downsampling to prevent memory leaks (OOM).
  - **PDF Documents**: Native rendered PDF page previews with page recycling.
  - **Markdown & Code**: Formatted Markdown rendering and built-in code/script editor.
- **Storage Switcher**: Seamlessly toggle between private Termux `$HOME` and device Shared Storage (`/sdcard`).

### Process Safety & Session Protection
- **Accidental Termination Guard**: Prevents closing terminal sessions when background tasks, compilations, or CLI agents (like OpenCode or Python scripts) are actively running.
- **Process Confirmation Dialog**: Warns with active process names and PIDs before session destruction, with a "Do not ask again" preference.

### Unified Tabbed Workflow
- **Multi-Tab Architecture**: Open and manage multiple concurrent terminal sessions, browser instances, and file previews in a clean top tab strip.
- **One-Tap Controls**: Tap `×` to close, tap `+` to add a session, and long-press to rename or customize sessions.

### Package & SSH Management
- **Visual Package Manager**: Search, install, update, and remove packages from the Termux repository with visual confirmation.
- **SSH Connection Manager**: Store host profiles, ports, usernames, and SSH keys for one-tap remote server management.

### Built-In Backup & Restore
- **Complete Environment Backup**: Create compressed `.tar.gz` backups of your `$HOME` directory or full system environment.
- **One-Tap Restore**: Restore projects, dotfiles, and configurations using the Android system file picker with universal tar support.

---

## Installation

**Requires Android 7.0+.** BeTermux installs under `com.termux` with a matching release signature, enabling a seamless in-place upgrade over NewTermux without losing your home directory, packages, or session data.

Download the APK from the [**latest release**](https://github.com/kurixutian/BeTermux/releases/latest):

| APK | Description |
|-----|-------------|
| `universal` | All-in-one upgrade APK for all CPU architectures |

---

## Building from source

Requires **JDK 17** and the Android SDK.

```bash
git clone https://github.com/kurixutian/BeTermux.git
cd BeTermux

# Release app (installs as com.termux)
./gradlew assembleRelease
```

Built APKs are written to `app/build/outputs/apk/release/`.

---

## Credits & license

BeTermux is forked from [NewTermux](https://github.com/The412Banner/NewTermux) by The412Banner, based on the [**Termux**](https://github.com/termux/termux-app) open source project by Fredrik Fornwall, Henrik Grimler & the Termux community. All credit for the terminal engine and Linux environment belongs upstream.

Licensed under the **GNU GPLv3**. See [LICENSE.md](LICENSE.md).
