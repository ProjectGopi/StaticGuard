#!/bin/bash

# ============================================================
#  StaticGuard Launcher (macOS / Linux)
#  Usage:
#    ./run.sh          -> Web UI  (open http://localhost:8080)
#    ./run.sh --path . -> CLI mode, scan current directory
# ============================================================

JAR="StaticGuard.jar"

# Check Java is installed
if ! command -v java &> /dev/null; then
    echo ""
    echo "  ERROR: Java is not installed or not in PATH."
    echo "  Please install Java 11 or newer:"
    echo "    macOS  : brew install --cask temurin  (or https://adoptium.net)"
    echo "    Ubuntu : sudo apt install openjdk-17-jre"
    echo "    Other  : https://adoptium.net"
    echo ""
    exit 1
fi

# Check JAR exists
if [ ! -f "$JAR" ]; then
    echo ""
    echo "  ERROR: $JAR not found."
    echo "  Make sure $JAR is in the same folder as this script."
    echo ""
    exit 1
fi

# Make this script executable hint
chmod +x "$0" 2>/dev/null

# If no arguments given, start Web UI
if [ $# -eq 0 ]; then
    echo ""
    echo "  Starting StaticGuard Web UI..."
    echo "  Open your browser at: http://localhost:8080"
    echo "  Press Ctrl+C to stop."
    echo ""
    java -jar "$JAR" --web
else
    # Pass all arguments to CLI mode
    java -jar "$JAR" "$@"
fi
