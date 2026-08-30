#!/bin/bash
echo "Stopping Gradle daemons..."
./gradlew --stop
echo "Killing any remaining Gradle daemon processes..."
pkill -9 -f 'gradle'
echo "Done."
