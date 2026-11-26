#!/bin/sh
# Builds the plugin, then runs ShopCore against a fake proxy, with the real
# PlebMc client code on the other end. Usage: sh test/run.sh
set -e
cd "$(dirname "$0")/.."
./build.sh >/dev/null
rm -rf build/test && mkdir -p build/test
C=../../sources/main/java/net/plebmc/client
L=lib/gson-2.8.0.jar:lib/snakeyaml-2.2.jar:../../gateway/EaglercraftXBungee/EaglerXBungee-Latest.jar:$(ls lib/bungeecord-*.jar | tr "\n" ":")
javac -nowarn -cp build/classes:$L -d build/test $(find test -name '*.java') $(find $C/cosmetics $C/network $C/apollo -name '*.java') $C/mods/SettingsStore.java
java -cp build/test:build/classes:$L net.plebmc.shop.ShopTest
