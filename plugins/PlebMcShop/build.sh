#!/bin/sh
# Builds PlebMcShop.jar with only a JDK (8+). The Bukkit classes in stubs/ are
# compile-only stand-ins and are not put in the jar; the server provides the
# real ones (and Gson, which Spigot/Paper bundle).
set -e
cd "$(dirname "$0")"
GSON=lib/gson-2.8.0.jar
if [ ! -f "$GSON" ]; then
  mkdir -p lib
  curl -sSfL -o "$GSON" https://repo1.maven.org/maven2/com/google/code/gson/gson/2.8.0/gson-2.8.0.jar
fi
rm -rf build
mkdir -p build/stubs build/classes
javac --release 8 -nowarn -d build/stubs $(find stubs -name '*.java')
javac --release 8 -Xlint:-options -cp build/stubs:$GSON -d build/classes $(find src/main/java -name '*.java')
cp src/main/resources/plugin.yml src/main/resources/config.yml build/classes/
jar cf PlebMcShop.jar -C build/classes .
echo "built $(pwd)/PlebMcShop.jar"
