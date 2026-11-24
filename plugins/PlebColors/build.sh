#!/bin/sh
# Builds PlebColors.jar (Spigot/Paper 1.16.5+, needs ProtocolLib on the server) with a JDK 8+.
# Compile-only jars (not put in PlebColors.jar): ProtocolLib 5.4.0, Gson and bungeecord-chat from
# Maven Central, downloaded into build/lib. The Bukkit classes in stubs/ are compile-only too.
set -e
cd "$(dirname "$0")"
LIB=build/lib
mkdir -p "$LIB"
M=https://repo1.maven.org/maven2
[ -f "$LIB/ProtocolLib.jar" ] || curl -sfL -o "$LIB/ProtocolLib.jar" "$M/net/dmulloy2/ProtocolLib/5.4.0/ProtocolLib-5.4.0-all.jar"
[ -f "$LIB/gson.jar" ] || curl -sfL -o "$LIB/gson.jar" "$M/com/google/code/gson/gson/2.8.9/gson-2.8.9.jar"
[ -f "$LIB/bchat.jar" ] || curl -sfL -o "$LIB/bchat.jar" "$M/net/md-5/bungeecord-chat/1.16-R0.4/bungeecord-chat-1.16-R0.4.jar"
CP="$LIB/ProtocolLib.jar:$LIB/gson.jar:$LIB/bchat.jar"
rm -rf build/stubs build/classes && mkdir -p build/stubs build/classes
javac --release 8 -nowarn -cp "$CP" -d build/stubs $(find stubs -name '*.java')
javac --release 8 -nowarn -Xlint:-options -cp "build/stubs:$CP" -d build/classes $(find src/main/java -name '*.java')
cp src/main/resources/plugin.yml build/classes/
rm -f PlebColors.jar
jar cf PlebColors.jar -C build/classes .
echo "built $(pwd)/PlebColors.jar"
