#!/usr/bin/env bash
# Build and run the SIT Equipment Maintenance Tracker.
# Requires a full JDK (17 or newer) with javac on the PATH. Usage: ./run.sh [--selftest]
set -e
cd "$(dirname "$0")"

# always clean up the temporary source list, even if javac fails
trap 'rm -f sources.txt' EXIT

rm -rf out
mkdir -p out
find src -name '*.java' > sources.txt
javac -Xlint:all -d out @sources.txt

echo "Build OK - starting SIT Equipment Maintenance Tracker..."
java -cp out com.sit.tracker.Main "$@"
