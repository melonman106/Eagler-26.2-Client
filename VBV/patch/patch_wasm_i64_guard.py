#!/usr/bin/env python3
"""Install a final JS-side guard for TeaVM WASM-GC i64 export calls."""
from pathlib import Path
import json
import sys

ROOT = Path(sys.argv[1]).resolve()
PACKAGER = ROOT / "wasm-toolchain" / "build-single-html.js"
if not PACKAGER.is_file():
    raise SystemExit(f"::error::WASM single-HTML packager is missing: {PACKAGER}")

text = PACKAGER.read_text(encoding="utf-8")
marker = "VBV_WASM_I64_EXPORT_GUARD_V1"
if marker in text:
    print("[VBV i64 guard] already installed")
    raise SystemExit(0)

needle = "const output = path.resolve(repo, valueAfter("
pos = text.find(needle)
if pos < 0:
    raise SystemExit("::error::Could not find the u2 single-HTML output declaration")

guard = r'''/* VBV_WASM_I64_EXPORT_GUARD_V1 */
function vbvWrapWasmInstance(instance) {
  if (!instance || !instance.exports || instance.__vbvI64GuardWrapped) return instance;
  const cache = new Map();
  const wrappedExports = new Proxy(instance.exports, {
    get(target, property, receiver) {
      const value = Reflect.get(target, property, receiver);
      if (typeof value !== "function") return value;
      if (cache.has(property)) return cache.get(property);
      const wrapped = function(...args) {
        try { return Reflect.apply(value, this, args); }
        catch (error) {
          const message = String(error && error.message || error);
          if (!(error instanceof TypeError) || !/(?:BigInt|i64)/i.test(message)) throw error;
          const numeric = args.map((arg, index) =>
            typeof arg === "number" && Number.isSafeInteger(arg) ? index : -1
          ).filter(index => index >= 0);
          if (!numeric.length) throw error;
          const limit = Math.min(numeric.length, 8);
          const count = 1 << limit;
          for (let mask = 1; mask < count; ++mask) {
            const candidate = args.slice();
            for (let bit = 0; bit < limit; ++bit) {
              if (mask & (1 << bit)) candidate[numeric[bit]] = BigInt(candidate[numeric[bit]]);
            }
            try { return Reflect.apply(value, this, candidate); }
            catch (_) {}
          }
          throw error;
        }
      };
      cache.set(property, wrapped);
      return wrapped;
    }
  });
  Object.defineProperty(instance, "__vbvI64GuardWrapped", { value: true });
  return new Proxy(instance, {
    get(target, property, receiver) {
      if (property === "exports") return wrappedExports;
      return Reflect.get(target, property, receiver);
    }
  });
}

const vbvNativeInstance = WebAssembly.Instance;
WebAssembly.Instance = new Proxy(vbvNativeInstance, {
  construct(target, args) { return vbvWrapWasmInstance(Reflect.construct(target, args)); },
  apply(target, thisArg, args) { return vbvWrapWasmInstance(Reflect.apply(target, thisArg, args)); }
});

const vbvNativeInstantiate = WebAssembly.instantiate;
WebAssembly.instantiate = async function(...args) {
  const result = await vbvNativeInstantiate.apply(this, args);
  if (result && result.instance) result.instance = vbvWrapWasmInstance(result.instance);
  return result;
};

const vbvNativeInstantiateStreaming = WebAssembly.instantiateStreaming;
if (typeof vbvNativeInstantiateStreaming === "function") {
  WebAssembly.instantiateStreaming = async function(...args) {
    const result = await vbvNativeInstantiateStreaming.apply(this, args);
    if (result && result.instance) result.instance = vbvWrapWasmInstance(result.instance);
    return result;
  };
}
'''

patch = r'''/* VBV WASM i64 guard packager hook */
const vbvI64GuardScript = __VBV_I64_GUARD_JSON__;
const vbvOriginalWriteFileSyncI64Guard = fs.writeFileSync;
fs.writeFileSync = function(file, data, ...args) {
  if (path.resolve(String(file)) === output) {
    let html = Buffer.isBuffer(data) ? data.toString("utf8") : String(data);
    if (!html.includes("VBV_WASM_I64_EXPORT_GUARD_V1")) {
      const head = html.lastIndexOf("</head>");
      if (head < 0) throw new Error("VBV i64 guard: standalone HTML has no closing </head> tag");
      html = html.slice(0, head) + "<script>" + vbvI64GuardScript + "</script>" + html.slice(head);
    }
    data = Buffer.from(html, "utf8");
  }
  return vbvOriginalWriteFileSyncI64Guard.call(fs, file, data, ...args);
};
'''.replace("__VBV_I64_GUARD_JSON__", json.dumps(guard))

text = text[:pos] + patch + text[pos:]
PACKAGER.write_text(text, encoding="utf-8")
print("[VBV i64 guard] installed final HTML WASM export boundary guard")
