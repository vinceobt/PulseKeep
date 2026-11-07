# PulseKeep

> A lightweight macOS desktop utility that prevents system sleep and maintains an active presence in collaboration apps (Slack, Teams, Zoom).

---

## How it works

PulseKeep does two things simultaneously:

### 1. `caffeinate` — prevents macOS sleep

PulseKeep spawns the built-in macOS `caffeinate` command:

```
caffeinate -dimsu
```

| Flag | Effect |
|------|--------|
| `-d` | Prevents the **display** from sleeping |
| `-i` | Prevents **idle** system sleep |
| `-m` | Prevents **disk idle** sleep |
| `-s` | Prevents **system** sleep (AC power only) |
| `-u` | Declares **user activity**, resetting the idle timer |

The process runs as a child of the JVM. When you click **Stop** or quit the app, `Process.destroy()` is called immediately, releasing the sleep assertion.

### 2. Tiny mouse nudges — maintains "active" status

Apps like Slack detect user presence by monitoring cursor movement and keyboard activity. PulseKeep uses `java.awt.Robot` to:

1. Record the current cursor position.
2. Move the cursor by a random delta of **−1, 0, or +1 pixels** in X and Y.
3. Immediately return the cursor to its original position.

The entire round-trip happens in under a millisecond — the cursor never visibly moves, but the OS registers pointer activity.

Nudges fire at **random intervals between 2–5 minutes** (re-randomised after each cycle) to avoid a predictable pattern.

---

## Prerequisites

| Requirement | Version |
|-------------|---------|
| macOS       | 10.14 Mojave or later |
| Java JDK    | 21 |
| Maven       | 3.8+ |

### Install Java 21 (Temurin, via Homebrew)

```bash
brew install --cask temurin@21
```

### Install Maven (via Homebrew)

```bash
brew install maven
```

Verify:

```bash
java -version    # should print: openjdk version "21 ..."
mvn  -version    # should print: Apache Maven 3.x.x
```

---

## Build

```bash
cd PulseKeep
mvn clean compile
```

---

## Run

```bash
mvn javafx:run
```

The application window opens. Click **▶ Start** to begin.

---

## Package as a fat JAR

> **Note:** JavaFX native libraries are platform-specific and cannot be bundled
> with a standard fat JAR. The shaded JAR works when run on a JDK distribution
> that already ships JavaFX (e.g. [Liberica JDK](https://bell-sw.com/pages/downloads/),
> [Azul Zulu FX](https://www.azul.com/downloads/?package=jdk-fx)).

```bash
mvn clean package
java -jar target/pulsekeep-1.0.0.jar
```

---

## Native macOS App (jpackage)

Build a self-contained `PulseKeep.app` that bundles its own Java runtime — **no Java installation required on the target machine**.

```bash
./package.sh
```

This single command:
1. Downloads JavaFX jmods (cached after first run)
2. Builds the shaded JAR (`mvn clean package`)
3. Creates a minimal JDK runtime via `jlink` (~90 MB)
4. Packages everything into `target/dist/PulseKeep.app` via `jpackage`

### Install on another Mac

```bash
# Copy the .app to the target machine (AirDrop, USB, scp, etc.)
cp -R target/dist/PulseKeep.app /Applications/
```

Then just double-click **PulseKeep** in Finder or Launchpad.

> **Gatekeeper note:** Since the app is not code-signed, macOS will block it on
> first launch. Right-click → **Open** → **Open** to bypass.

### Build requirements (build machine only)

| Requirement | Version |
|-------------|---------|
| JDK         | 21+     |
| Maven       | 3.8+    |
| Internet    | First build only (downloads JavaFX jmods) |

### Architecture

`jpackage` produces a single-architecture bundle matching the build machine.
Build on Apple Silicon (M1/M2/M3) for `arm64`, or on Intel for `x86_64`.

---

## macOS Permissions

PulseKeep requires **Accessibility access** so that `java.awt.Robot` can move the mouse cursor.

1. Open **System Settings → Privacy & Security → Accessibility**
2. Click **+** and add your Java runtime  
   (usually `/Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home/bin/java`)
3. Restart PulseKeep

If this permission is absent the scheduler will still run (caffeinate remains active) but every nudge attempt will be skipped and a warning will appear in the log.

---

## Project Structure

```
PulseKeep/
├── pom.xml
├── README.md
└── src/main/
    ├── java/com/pulsekeep/
    │   ├── App.java                     # Entry point; wires all services; shutdown hook
    │   │
    │   ├── model/
    │   │   └── AppState.java            # RUNNING / STOPPED enum
    │   │
    │   ├── util/
    │   │   └── Logger.java              # Timestamped console + UI log writer
    │   │
    │   ├── service/
    │   │   ├── CaffeinateService.java   # Manages the caffeinate OS process
    │   │   ├── MouseMovementService.java# Robot-based ±1 px cursor nudge
    │   │   └── SchedulerService.java    # Daemon scheduler with random intervals
    │   │
    │   └── ui/
    │       ├── MainView.java            # JavaFX primary window
    │       └── TrayManager.java         # System-tray icon + context menu
    │
    └── resources/
        └── styles.css                   # Dark theme for the JavaFX scene
```

---

## UI Overview

```
┌─────────────────────────────────────────┐
│  PulseKeep                              │
│  Keep your Mac awake & presence active  │
├─────────────────────────────────────────┤
│  Status:  ● RUNNING                     │
│  Next nudge in: 3 min                   │
│                                         │
│  [ ▶  Start ]   [ ■  Stop ]             │
├─────────────────────────────────────────┤
│  ACTIVITY LOG                           │
│  [12:04:21] PulseKeep started           │
│  [12:04:21] caffeinate active           │
│  [12:04:21] Scheduler started           │
│  [12:07:18] Mouse nudged                │
│  [12:10:45] Mouse nudged                │
└─────────────────────────────────────────┘
```

---

## System Tray

When you click the window's close button, PulseKeep minimises to the menu bar instead of quitting. A notification balloon confirms it is running in the background.

**Tray menu items:**

| Item | Action |
|------|--------|
| Open PulseKeep | Restore the main window |
| Start / Stop PulseKeep | Toggle running state |
| Quit | Stop services and exit cleanly |

---

## Screenshots

<!-- Add screenshots after running the app -->

| Main Window | System Tray |
|-------------|-------------|
| *(screenshot)* | *(screenshot)* |

---

## Bonus / Roadmap

- [ ] **Launch at Login** — register a Login Item via `SMLoginItemSetEnabled` or a `launchd` plist
- [ ] **Config file** — load `~/.pulsekeep.properties` for custom intervals and sensitivity
- [ ] **Adjustable sensitivity** — slider to control the max pixel movement (currently hard-coded to ±1)
- [ ] **Menu-bar-only mode** — pass `-Dapple.awt.UIElement=true` to hide the Dock icon entirely
- [ ] **Dark/light tray icon** — swap the icon colour based on `NSAppearance`

---

## License

PulseKeep is free software, released under the [GNU General Public License v3.0](LICENSE).

Copyright (C) 2026 Vincent Obeta

This program is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version. It is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the [LICENSE](LICENSE) file for details.
