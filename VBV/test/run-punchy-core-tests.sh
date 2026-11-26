#!/usr/bin/env bash
# Compiles and runs the plain-JVM checks for the Punchy core (animation parser, rules, rig maths, controller).
# Everything under src/.../punchy/ is free of game imports (Gson is the only library it uses).
# Gson: set GSON_JAR, or have a gson-*.jar in ~/.m2, /opt/apache-maven*/lib or the Gradle cache.
# Optional: PUNCHY_PACK=<dir of an unzipped Punchy resource pack, e.g. HyperPunchy> adds checks against real files.
set -euo pipefail
here="$(cd "$(dirname "$0")" && pwd)"
src="$here/../src/main/java/com/melonman106/vbvclient/punchy"
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

javac -Xlint:all,-classfile,-processing -cp "$gson" -d "$out" "$src"/*.java "$here"/com/melonman106/vbvclient/punchy/*.java
java -Xmx512m -Dvbv.punchy="${PUNCHY_PACK:-}" -cp "$out:$gson" com.melonman106.vbvclient.punchy.PunchySelfTest
