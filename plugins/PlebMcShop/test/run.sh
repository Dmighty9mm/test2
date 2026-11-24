#!/bin/sh
# Builds the plugin, then runs it against a fake in-memory server (test/fake).
set -e
cd "$(dirname "$0")/.."
./build.sh >/dev/null
S=lib/snakeyaml-1.27.jar
[ -f "$S" ] || curl -sSfL -o "$S" https://repo1.maven.org/maven2/org/yaml/snakeyaml/1.27/snakeyaml-1.27.jar
rm -rf build/test && mkdir -p build/test/fake build/test/classes
javac --release 8 -nowarn -cp lib/snakeyaml-1.27.jar -d build/test/fake $(find test/fake -name '*.java')
C=../../sources/main/java/net/plebmc/client
javac -nowarn -cp build/test/fake:PlebMcShop.jar:lib/gson-2.8.0.jar -d build/test/classes test/ShopTest.java $(find $C/cosmetics $C/network $C/apollo -name "*.java")
java -cp build/test/classes:build/test/fake:PlebMcShop.jar:lib/gson-2.8.0.jar:lib/snakeyaml-1.27.jar ShopTest
