from pathlib import Path
import sys

p = Path(sys.argv[1]) / "platform-teavm/src/main/java/net/lax1dude/eaglercraft/v1_8/internal/PlatformRuntime.java"
s = p.read_text(encoding="utf-8")
custom = "private static final java.util.Map<Integer, AsyncCallback<Void>> vbvPendingSleepCallbacks"
expected = """private static void sleep(int millis, final AsyncCallback<Void> callback) {
\t\tWindow.setTimeout(() -> callback.complete(null), millis);
\t}"""

if custom in s:
    a = s.index(custom)
    b = s.find("\n\tpublic static void postCreate()", a)
    if b < 0:
        raise SystemExit("Could not locate the end of the custom VBVSleepTimer bridge")
    s = s[:a] + expected + "\n\n" + s[b:]
    p.write_text(s, encoding="utf-8")
    print("Removed custom VBVSleepTimer bridge and restored upstream TeaVM sleep callback")
elif expected in s:
    print("Upstream TeaVM sleep callback already present; no change needed")
else:
    raise SystemExit("Unknown PlatformRuntime.sleep implementation; refusing to patch blindly")
