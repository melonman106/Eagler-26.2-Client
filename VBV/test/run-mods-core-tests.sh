#!/usr/bin/env bash
# Compiles and runs the plain-JVM checks for the importable-mods core (no game classes needed).
# Every class under mods/ listed in PURE must stay free of game imports (Gson is the only library they use).
# Gson: set GSON_JAR, or have a gson-*.jar in ~/.m2, /opt/apache-maven*/lib or the Gradle cache.
set -euo pipefail
here="$(cd "$(dirname "$0")" && pwd)"
src="$here/../src/main/java/com/melonman106/vbvclient/mods"
out="$(mktemp -d)"
trap 'rm -rf "$out"' EXIT
unset JAVA_TOOL_OPTIONS

gson="${GSON_JAR:-}"
if [ -z "$gson" ]; then
  gson="$(find ~/.m2 /opt/apache-maven*/lib ~/.gradle /usr/share/java -name 'gson-*.jar' 2>/dev/null | grep -v sources | sort | tail -n 1 || true)"
fi
if [ -z "$gson" ] || [ ! -f "$gson" ]; then
  echo "Gson jar not found. Set GSON_JAR=/path/to/gson.jar" >&2
  exit 2
fi

PURE=(ModVersion FabricFaker ModManifest ModItems ModPackage ModAssets ModFiles ModStore ModView)
files=()
for name in "${PURE[@]}"; do files+=("$src/$name.java"); done
for extra in "$here"/com/melonman106/vbvclient/mods/*.java; do files+=("$extra"); done
javac -Xlint:all,-classfile -cp "$gson" -d "$out" "${files[@]}"
java -Xmx512m -cp "$out:$gson" com.melonman106.vbvclient.mods.ModsCoreSelfTest
