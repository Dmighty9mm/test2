#!/bin/sh
# Runs the PlebMc JVM unit tests (Apollo codec, Apollo client, client mods,
# plugin channels). Needs only a JDK 11+; no Minecraft files are involved.
# Usage (from the repo root): sh tests/plebmc/run_tests.sh
set -e
OUT="${TMPDIR:-/tmp}/plebmc-tests"
rm -rf "$OUT" && mkdir -p "$OUT"
SRC=sources/main/java/net/plebmc/client
javac -d "$OUT" $(find "$SRC/apollo" "$SRC/mods" "$SRC/network" "$SRC/cosmetics" -name '*.java') tests/plebmc/*.java
for t in PluginChannelsTest ApolloProtoTest ApolloClientTest ClientModsTest CosmeticsTest; do
	echo "== $t"
	java -cp "$OUT" "$t"
done
