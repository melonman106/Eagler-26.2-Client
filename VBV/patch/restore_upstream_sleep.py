from pathlib import Path
import sys

p = Path(sys.argv[1]) / "platform-teavm/src/main/java/net/lax1dude/eaglercraft/v1_8/internal/PlatformRuntime.java"
s = p.read_text()
a = s.find("private static final java.util.Map<Integer, AsyncCallback<Void>> vbvPendingSleepCallbacks")
b = s.find("\n\tpublic static void postCreate()", a)
if a < 0:
    raise SystemExit("custom sleep bridge not found")
if b < 0:
    raise SystemExit("sleep bridge end not found")
s = s[:a] + "private static void sleep(int millis, final AsyncCallback<Void> callback) {\n\t\tWindow.setTimeout(() -> callback.complete(null), millis);\n\t}\n\n" + s[b:]
p.write_text(s)
print("Restored upstream TeaVM sleep callback")
