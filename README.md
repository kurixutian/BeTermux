# BeTermux

[![Latest release](https://img.shields.io/github/v/release/kurixutian/BeTermux?label=release)](https://github.com/kurixutian/BeTermux/releases/latest)
[![Android 7+](https://img.shields.io/badge/Android-7.0%2B-3DDC84?logo=android&logoColor=white)](#installation)
[![License: GPLv3](https://img.shields.io/badge/License-GPLv3-blue.svg)](LICENSE.md)

BeTermux is a customized terminal emulator for Android with a modern Warp-inspired interface, tabbed workflow, integrated Dev Browser with Eruda DevTools, enhanced File Manager with multi-format previews, and process termination safety. It installs under `com.termux` and remains 100% compatible with the entire [Termux package ecosystem](https://github.com/termux/termux-packages).

> **Latest stable:** `v1.0.0` — [**Download the APK →**](https://github.com/kurixutian/BeTermux/releases/latest)

---

## Contents

- [Features](#features)
- [Installation](#installation)
- [Building from source](#building-from-source)
- [Credits & license](#credits--license)
- [Community](#community)

---

## Features

### Terminal & shell
- Full terminal emulator built on the upstream Termux engine — **100% package compatible**.
- **zsh + Oh My Zsh** configured automatically on first launch, with an optional toggle for autosuggestions and syntax highlighting (bundled, no download required).
- Always-visible **session tabs**: one-tap `×` to close, long-press to rename.

### Modern UI & theming
- Clean **Material 3 dark** interface, built in **Jetpack Compose** (v1.6.0).
- Consistent rounded, outlined pop-out menus and drawers throughout.
- **9 accent presets** plus a fully custom accent via an HSV color wheel or RGB sliders.
- **13 built-in terminal color themes** (Dracula, Nord, Gruvbox, Tokyo Night, Solarized, and more) plus a live **custom-theme editor** for the background, foreground, cursor and all 16 ANSI colors.

### Built-in managers
- **Package Manager** — browse installed packages, search the catalog, and install or remove from the toolbar.
- **File Manager** — browse, view, edit, create, delete and share files in your Termux home directory.
- **SSH Manager** — save connection profiles (host, port, user, key path and port-forwarding tunnels) and connect in a tap.

### Productivity
- **Text Expansion** — type a short trigger (e.g. `;ll`) then Space/Tab to expand it (e.g. `ls -la`).
- **Startup script** — sourced into every new session so aliases and env vars persist; editable from Settings.
- **Speech-to-Text** input, inline **command autocorrect**, and **URL detection** (long-press a link to open or copy).

### Customizable toolbar & drawer
- Toolbar buttons you can show/hide individually: **AC** (autocorrect), **Root** shell, **STT**, **Packages**, **Clear**, and **Settings**.
- Left-drawer utilities — **Export Screen**, **Make Script**, **Pkg Update** — plus up to **10 custom command buttons** (long-press to edit, `+`/`−` to resize).
- Collapsible **extra-keys** row, optionally moved into a swipe-in right-side drawer.

### Backup & restore
- One-tap **basic** (home) or **full** (home + usr) `.tar.gz` backup and restore through the system file picker.

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
