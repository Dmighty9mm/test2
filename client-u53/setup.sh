#!/bin/sh
# Builds the PlebClient browser client on EaglercraftX u53.
#
#   sh client-u53/setup.sh [workspace-dir]
#
# 1. Clones lax1dude's u53 workspace (gitflic.ru, the "u53" commit) if the
#    folder doesn't exist yet.
# 2. Copies in the PlebClient hooks (client-u53/overlay), the PlebClient code
#    (sources/main/java/net/plebmc) and its assets.
# 3. Builds the offline HTML (JavaScript) and, with "wasm" as the 2nd
#    argument, the WASM-GC bundle too.
#
# Needs JDK 17 and network access to gitflic.ru and
# eaglercraft-teavm-fork.github.io (the TeaVM fork's Maven repo).
set -e
REPO="$(cd "$(dirname "$0")/.." && pwd)"
WS="${1:-$REPO/../eaglercraft_u53_workspace}"
U53_COMMIT=746793a11eb67608b4e60660a4dfe205f5f68970

if [ ! -d "$WS/.git" ]; then
	git clone https://gitflic.ru/project/lax1dude/eaglercraft-1_8-workspace.git "$WS"
	git -C "$WS" checkout -q "$U53_COMMIT"
fi

cp -a "$REPO/client-u53/overlay/." "$WS/"
mkdir -p "$WS/src/main/java/net"
rm -rf "$WS/src/main/java/net/plebmc"
cp -a "$REPO/sources/main/java/net/plebmc" "$WS/src/main/java/net/"
rm -rf "$WS/desktopRuntime/resources/assets/plebitems" "$WS/desktopRuntime/resources/plebclient"
cp -a "$REPO/sources/resources/assets/plebitems" "$WS/desktopRuntime/resources/assets/"
cp -a "$REPO/sources/resources/plebclient" "$WS/desktopRuntime/resources/"

cd "$WS"
chmod +x gradlew
JDK17="${JDK17:-/usr/lib/jvm/java-17-openjdk-amd64}"
./gradlew target_teavm_javascript:makeMainOfflineDownload -Porg.gradle.java.installations.paths="$JDK17"
echo "Offline HTML: $WS/target_teavm_javascript/javascript/EaglercraftX_1.8_Offline_en_US.html"

if [ "$2" = "wasm" ]; then
	cd target_teavm_wasm_gc
	sh MakeWASMClientBundle.sh
	echo "WASM bundle: $WS/target_teavm_wasm_gc/javascript_dist"
fi
