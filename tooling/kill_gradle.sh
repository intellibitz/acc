#!/bin/bash
# ==============================================================================
# acc BRUTAL CLEANUP - KILL GRADLE & STALE JAVA
# ==============================================================================

echo ">>> Stopping Gradle daemons politely..."
./gradlew --stop 2>/dev/null

echo ">>> Purging all Gradle and Kotlin daemons..."
# Kill Gradle-specific processes
pkill -9 -f 'gradle'
# Kill Kotlin compiler daemons
pkill -9 -f 'KotlinCompileDaemon'
# Kill any Java process launched by Gradle
pkill -9 -f 'java.*\.gradle'

echo ">>> Hunting for orphaned Java processes related to this project..."
# Use the current directory name as a filter for project-specific java processes
PROJECT_DIR_NAME=$(basename "$(pwd)")
# Be surgical: find Java processes with the project directory in their command line
pgrep -f "java.*$PROJECT_DIR_NAME" | xargs -r kill -9 2>/dev/null

# Check for true Zombie (defunct) processes
ZOMBIES=$(ps -ef | awk '/[d]efunct/ {print $2}')
if [ -n "$ZOMBIES" ]; then
    echo ">>> Detected real Zombie (defunct) processes: $ZOMBIES"
    echo "    (Note: Defunct processes are already dead but waiting to be reaped by their parent.)"
fi

echo "[SUCCESS] Java and Gradle environment purged."
