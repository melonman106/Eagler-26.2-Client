# WASM-GC sleep-timer crash

## Observed failure

The 2026-11-03 client report showed:

```text
TypeError: Cannot convert 0 to a BigInt
at PlatformRuntime$VBVSleepTimer.eval [as onTimer]
```

The exception surfaced in the custom `PlatformRuntime$VBVSleepTimer.onTimer()` callback after the client had reached its ready state and was entering a multiplayer server. This points to the browser runtime/timer callback path, not a save-file parsing failure.

## Root-cause interpretation

WASM-GC JavaScript interop distinguishes JavaScript `Number` values from `BigInt` values used for WebAssembly 64-bit integer parameters. Passing a Number where the generated WASM bridge expects a BigInt can produce this TypeError.

The stack identifies the custom timer callback as the point where the exception surfaced. It does not, by itself, prove which generated call or parameter performed the invalid conversion. The exact failing boundary should be confirmed from the generated JavaScript/WASM glue or a fresh de-obfuscated trace.

Browser background-tab throttling can delay `setTimeout` callbacks. It does not normally change a Number into a BigInt, so keeping the tab visible is a useful controlled test, not a root-cause fix.

## Build-side mitigation

Do not install the custom `VBVSleepTimer` token-map workaround. The build patch now retains the upstream TeaVM async sleep implementation:

```java
private static void sleep(int millis, final AsyncCallback<Void> callback) {
    Window.setTimeout(() -> callback.complete(null), millis);
}
```

This avoids the custom `TimerHandler` trampoline that appeared in the crash stack. It is a targeted rollback, not yet proof that every Number/BigInt interop issue in the build is resolved.

## Diagnostic procedure

1. Build a fresh HTML client with the upstream sleep callback.
2. Keep the tab visible while loading and joining a server for the first controlled test.
3. Test a second server as a comparison. A failure limited to one server may indicate a server-specific packet/timing path, but does not by itself prove the server is at fault.
4. If the exception returns, enable `openDebugConsoleOnLaunch` in the client options and capture the complete new stack and runtime log.
5. Compare the generated callback/interop code around the failing frame and identify the exact i64/BigInt argument before changing the ABI.

Do not use `crashOnUncaughtExceptions: false` as the fix. It may hide the fatal overlay while leaving the callback broken and later game state unreliable. Keep exception reporting enabled during diagnosis.

## Unrelated observations from the same report

- Approximately 286 MB of JavaScript heap was in use against a reported limit of about 3.76 GB; this does not suggest an out-of-memory crash.
- The WebGL2 shader report showed 106 successful compilations and zero failures; that does not implicate shader compilation in this exception.
- The `debug-profile.json` reset warning was separate and non-fatal.
- A relay ping failure for a different server was not the same event as the successful connection attempt to `beta.arch.lol`.

## Verification status

The source patch has been changed to preserve upstream `PlatformRuntime.sleep`. A successful source commit does not establish that the generated client is fixed; the manual build and a multiplayer join test are still required.
