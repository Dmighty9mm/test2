#!/bin/sh
# Builds PlebMcBridge.jar (Spigot/Paper 1.13+) with only a JDK 8+. The Bukkit classes
# in stubs/ are compile-only stand-ins and are not put in the jar.
set -e
cd "$(dirname "$0")"
rm -rf build && mkdir -p build/stubs build/classes
javac --release 8 -nowarn -d build/stubs $(find stubs -name '*.java')
javac --release 8 -Xlint:-options -cp build/stubs -d build/classes $(find src/main/java -name '*.java')
cp src/main/resources/plugin.yml src/main/resources/config.yml build/classes/
rm -f PlebMcBridge.jar
jar cf PlebMcBridge.jar -C build/classes .
echo "built $(pwd)/PlebMcBridge.jar"
