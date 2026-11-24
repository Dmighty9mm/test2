#!/bin/sh
# Builds PlebioAPI.jar (a BungeeCord plugin) with only a JDK 8+. The BungeeCord API,
# Gson and SnakeYAML are downloaded from Maven Central for compiling only; BungeeCord
# provides them at runtime.
set -e
cd "$(dirname "$0")"
V=1.21-R0.4
mkdir -p lib
get() { [ -f "lib/$2" ] || curl -sSfL -o "lib/$2" "https://repo1.maven.org/maven2/$1/$2"; }
for a in api chat config event protocol; do get net/md-5/bungeecord-$a/$V bungeecord-$a-$V.jar; done
get com/google/code/gson/gson/2.8.0 gson-2.8.0.jar
get org/yaml/snakeyaml/2.2 snakeyaml-2.2.jar
CP=$(ls lib/bungeecord-*-$V.jar | tr '\n' ':')lib/gson-2.8.0.jar:lib/snakeyaml-2.2.jar
rm -rf build/classes && mkdir -p build/classes
javac --release 8 -Xlint:-options -cp "$CP" -d build/classes $(find src/main/java -name '*.java')
cp -r src/main/resources/bungee.yml src/main/resources/config.yml src/main/resources/cloaks build/classes/
rm -f PlebioAPI.jar
jar cf PlebioAPI.jar -C build/classes .
echo "built $(pwd)/PlebioAPI.jar"
