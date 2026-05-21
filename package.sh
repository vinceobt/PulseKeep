#!/bin/bash
set -euo pipefail

# ─────────────────────────────────────────────────────────────────────────────
# package.sh — Build PulseKeep.app (self-contained macOS application bundle)
#
# Usage:
#   ./package.sh
#
# Prerequisites (build machine only):
#   - JDK 21+
#   - Maven 3.8+
#   - Internet access (to download JavaFX jmods on first run)
#
# Output:
#   target/dist/PulseKeep.app
# ─────────────────────────────────────────────────────────────────────────────

JAVAFX_VERSION="21.0.2"
APP_VERSION="1.0.0"

# Detect architecture
ARCH=$(uname -m)
if [[ "$ARCH" == "arm64" ]]; then
    JAVAFX_CLASSIFIER="osx-aarch64"
else
    JAVAFX_CLASSIFIER="osx-x64"  # Intel Macs
fi

JMODS_DIR=".javafx-jmods/javafx-jmods-${JAVAFX_VERSION}"
RUNTIME_DIR="target/runtime"

echo "╔══════════════════════════════════════════╗"
echo "║  PulseKeep Packager                      ║"
echo "║  Target: macOS .app (${ARCH})            ║"
echo "╚══════════════════════════════════════════╝"
echo ""

# ── Step 1: Download JavaFX jmods if not cached ──────────────────────────────
if [[ ! -d "$JMODS_DIR" ]]; then
    echo "⏬ Downloading JavaFX ${JAVAFX_VERSION} jmods (${JAVAFX_CLASSIFIER})..."
    JMODS_URL="https://download2.gluonhq.com/openjfx/${JAVAFX_VERSION}/openjfx-${JAVAFX_VERSION}_${JAVAFX_CLASSIFIER}_bin-jmods.zip"
    
    mkdir -p .javafx-jmods
    curl -fSL "$JMODS_URL" -o .javafx-jmods/javafx-jmods.zip
    unzip -qo .javafx-jmods/javafx-jmods.zip -d .javafx-jmods/
    rm .javafx-jmods/javafx-jmods.zip
    
    # Handle variations in zip structure
    if [[ ! -d "$JMODS_DIR" ]]; then
        extracted=$(find .javafx-jmods -maxdepth 1 -type d -name "javafx-jmods*" | head -1)
        if [[ -n "$extracted" && "$extracted" != "$JMODS_DIR" ]]; then
            mv "$extracted" "$JMODS_DIR"
        fi
    fi
    echo "✅ JavaFX jmods ready"
else
    echo "✅ JavaFX jmods already cached"
fi

# ── Step 2: Build the shaded JAR ─────────────────────────────────────────────
echo ""
echo "🔨 Building shaded JAR..."
mvn clean package -q -DskipTests
echo "✅ JAR built: target/pulsekeep-${APP_VERSION}.jar"

# ── Step 3: Create custom JDK runtime with jlink ─────────────────────────────
echo ""
echo "🔗 Creating minimal JDK runtime with jlink..."
rm -rf "$RUNTIME_DIR"

JAVA_HOME_DIR=$(/usr/libexec/java_home 2>/dev/null || echo "$JAVA_HOME")
JMODS_PATH="${JAVA_HOME_DIR}/jmods:${JMODS_DIR}"

jlink \
    --module-path "$JMODS_PATH" \
    --add-modules java.base,java.desktop,java.logging,java.prefs,javafx.controls,javafx.graphics,javafx.base \
    --output "$RUNTIME_DIR" \
    --strip-debug \
    --no-man-pages \
    --no-header-files \
    --compress zip-6

RUNTIME_SIZE=$(du -sh "$RUNTIME_DIR" | cut -f1)
echo "✅ Runtime created (${RUNTIME_SIZE})"

# ── Step 4: Package with jpackage ────────────────────────────────────────────
echo ""
echo "📦 Creating PulseKeep.app with jpackage..."
rm -rf target/dist

jpackage \
    --input target \
    --name PulseKeep \
    --app-version "$APP_VERSION" \
    --vendor "PulseKeep" \
    --main-jar "pulsekeep-${APP_VERSION}.jar" \
    --main-class com.pulsekeep.Launcher \
    --runtime-image "$RUNTIME_DIR" \
    --type app-image \
    --dest target/dist \
    --java-options "-Djava.awt.headless=false" \
    --java-options "-Dapple.awt.UIElement=false" \
    --mac-package-name "PulseKeep"

echo "✅ PulseKeep.app created"

# ── Done ─────────────────────────────────────────────────────────────────────
echo ""
APP_SIZE=$(du -sh target/dist/PulseKeep.app | cut -f1)
echo "════════════════════════════════════════════"
echo "  Done! App size: ${APP_SIZE}"
echo ""
echo "  Output: target/dist/PulseKeep.app"
echo ""
echo "  To install:"
echo "    cp -R target/dist/PulseKeep.app /Applications/"
echo ""
echo "  To run:"
echo "    open target/dist/PulseKeep.app"
echo ""
echo "  Note: On first launch, macOS Gatekeeper may block the app."
echo "  Right-click → Open → Open to bypass."
echo "════════════════════════════════════════════"
